package io.cognis.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.cognis.core.agent.AgentOrchestrator;
import io.cognis.core.agent.AgentSettings;
import io.cognis.core.bus.MessageBus;
import io.cognis.core.model.AgentResult;
import io.cognis.core.observability.ObservabilityService;
import io.cognis.core.payment.PaymentLedgerService;
import io.cognis.core.payment.PaymentPolicy;
import io.cognis.core.payment.PaymentSummary;
import io.cognis.core.voice.Transcriber;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormEncodedDataDefinition;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.form.MultiPartParserDefinition;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import java.io.IOException;
import java.net.URLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GatewayServer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(GatewayServer.class);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);
    private static final int STREAM_CHUNK_SIZE = 80;
    private static final HttpString CORS_ALLOW_ORIGIN = new HttpString("Access-Control-Allow-Origin");
    private static final HttpString CORS_ALLOW_METHODS = new HttpString("Access-Control-Allow-Methods");
    private static final HttpString CORS_ALLOW_HEADERS = new HttpString("Access-Control-Allow-Headers");
    private static final HttpString CORS_MAX_AGE = new HttpString("Access-Control-Max-Age");

    private final ObjectMapper mapper;
    private final Path uploadsDir;
    private final Path tempDir;
    private final Transcriber transcriber;
    private final String host;
    private final int requestedPort;
    private final String wsToken;
    private final AgentOrchestrator orchestrator;
    private final AgentSettings agentSettings;
    private final Path workspace;
    private final MessageBus messageBus;
    private final BusMessageMapper busMessageMapper;
    private final PaymentLedgerService paymentLedgerService;
    private final ObservabilityService observabilityService;

    private final ExecutorService executor;
    private final AtomicBoolean running;
    private final Map<String, WebSocketChannel> clients;
    private Undertow server;
    private int actualPort;

    public GatewayServer(int port, Path workspace, Transcriber transcriber) {
        this(port, "0.0.0.0", workspace, transcriber, null, null, null, "", null, null);
    }

    public GatewayServer(
        int port,
        String host,
        Path workspace,
        Transcriber transcriber,
        AgentOrchestrator orchestrator,
        AgentSettings agentSettings,
        MessageBus messageBus,
        String wsToken
    ) {
        this(
            port,
            host,
            workspace,
            transcriber,
            orchestrator,
            agentSettings,
            messageBus,
            wsToken,
            null,
            null
        );
    }

    public GatewayServer(
        int port,
        String host,
        Path workspace,
        Transcriber transcriber,
        AgentOrchestrator orchestrator,
        AgentSettings agentSettings,
        MessageBus messageBus,
        String wsToken,
        PaymentLedgerService paymentLedgerService,
        ObservabilityService observabilityService
    ) {
        this.requestedPort = port;
        this.host = host == null || host.isBlank() ? "0.0.0.0" : host;
        this.workspace = workspace.toAbsolutePath().normalize();
        this.uploadsDir = this.workspace.resolve("uploads");
        this.tempDir = this.workspace.resolve(".cognis/tmp");
        this.transcriber = Objects.requireNonNull(transcriber, "transcriber must not be null");
        this.orchestrator = orchestrator;
        this.agentSettings = agentSettings;
        this.messageBus = messageBus;
        this.busMessageMapper = new BusMessageMapper();
        this.paymentLedgerService = paymentLedgerService;
        this.observabilityService = observabilityService;
        this.wsToken = wsToken == null ? "" : wsToken.trim();

        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.running = new AtomicBoolean(false);
        this.clients = new ConcurrentHashMap<>();
    }

    public void start() {
        if (running.getAndSet(true)) {
            return;
        }

        HttpHandler wsHandler = Handlers.websocket(this::onWebSocketConnect);
        PathHandler routes = Handlers.path()
            .addExactPath("/healthz", this::handleHealth)
            .addExactPath("/upload", this::handleUpload)
            .addExactPath("/transcribe", this::handleTranscribe)
            .addExactPath("/payments/policy", this::handlePaymentsPolicy)
            .addExactPath("/payments/status", this::handlePaymentsStatus)
            .addExactPath("/dashboard/summary", this::handleDashboardSummary)
            .addExactPath("/audit/events", this::handleAuditEvents)
            .addPrefixPath("/files", this::handleFiles)
            .addExactPath("/ws", wsHandler);

        server = Undertow.builder()
            .addHttpListener(requestedPort, host)
            .setHandler(exchange -> handleWithCors(routes, exchange))
            .build();
        server.start();
        this.actualPort = resolveBoundPort(server, requestedPort);

        if (messageBus != null) {
            executor.submit(this::pumpBusToClients);
        }
    }

    private void handleWithCors(PathHandler routes, HttpServerExchange exchange) throws Exception {
        applyCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
            exchange.setStatusCode(204);
            exchange.endExchange();
            return;
        }
        routes.handleRequest(exchange);
    }

    private void applyCorsHeaders(HttpServerExchange exchange) {
        String origin = header(exchange, "Origin");
        if (origin == null || origin.isBlank()) {
            return;
        }
        if (!isAllowedCorsOrigin(origin)) {
            return;
        }
        exchange.getResponseHeaders().put(CORS_ALLOW_ORIGIN, origin);
        exchange.getResponseHeaders().put(CORS_ALLOW_METHODS, "GET,POST,PUT,DELETE,OPTIONS");
        exchange.getResponseHeaders().put(CORS_ALLOW_HEADERS, "Content-Type,Authorization");
        exchange.getResponseHeaders().put(CORS_MAX_AGE, "86400");
        exchange.getResponseHeaders().put(Headers.VARY, "Origin");
    }

    private boolean isAllowedCorsOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            String scheme = uri.getScheme();
            String hostName = uri.getHost();
            if (scheme == null || hostName == null) {
                return false;
            }
            boolean localHttp = "http".equalsIgnoreCase(scheme)
                && ("localhost".equalsIgnoreCase(hostName) || "127.0.0.1".equals(hostName));
            if (localHttp) {
                return true;
            }
            return "https".equalsIgnoreCase(scheme) && "cognis.local".equalsIgnoreCase(hostName);
        } catch (Exception ignored) {
            return false;
        }
    }

    public int port() {
        return actualPort;
    }

    @Override
    public void close() {
        running.set(false);
        if (server != null) {
            server.stop();
        }
        clients.values().forEach(channel -> {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        });
        clients.clear();
        executor.shutdownNow();
    }

    private void handleHealth(HttpServerExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        sendJson(exchange, 200, Map.of("status", "ok"));
    }

    private void handleUpload(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(() -> {
                try {
                    handleUpload(exchange);
                } catch (Exception e) {
                    sendInternalError(exchange, e);
                }
            });
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }

        UploadPayload payload = readUploadPayload(exchange, "file", "upload.bin");
        if (payload.bytes().length == 0) {
            sendJson(exchange, 400, Map.of("error", "empty_payload"));
            return;
        }

        Files.createDirectories(uploadsDir);
        String storedName = System.currentTimeMillis() + "_" + safeFilename(payload.filename());
        Path target = uploadsDir.resolve(storedName);
        Files.write(target, payload.bytes());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("path", "uploads/" + storedName);
        response.put("url", "/files/" + storedName);
        response.put("type", payload.contentType());
        sendJson(exchange, 200, response);
    }

    private void handleTranscribe(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(() -> {
                try {
                    handleTranscribe(exchange);
                } catch (Exception e) {
                    sendInternalError(exchange, e);
                }
            });
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }

        UploadPayload payload = readUploadPayload(exchange, "file", "audio.webm");
        if (payload.bytes().length == 0) {
            sendJson(exchange, 400, Map.of("error", "empty_payload"));
            return;
        }

        Files.createDirectories(tempDir);
        Path audioPath = tempDir.resolve(UUID.randomUUID() + "-" + safeFilename(payload.filename()));
        Files.write(audioPath, payload.bytes());

        try {
            String text = transcriber.transcribe(audioPath);
            sendJson(exchange, 200, Map.of("text", text));
        } catch (IOException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        } finally {
            Files.deleteIfExists(audioPath);
        }
    }

    private void handlePaymentsPolicy(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(() -> {
                try {
                    handlePaymentsPolicy(exchange);
                } catch (Exception e) {
                    sendInternalError(exchange, e);
                }
            });
            return;
        }
        if (paymentLedgerService == null) {
            sendJson(exchange, 503, Map.of("error", "payments_not_configured"));
            return;
        }
        String method = exchange.getRequestMethod().toString();
        if ("GET".equalsIgnoreCase(method)) {
            sendJson(exchange, 200, toPolicyResponse(paymentLedgerService.policy()));
            return;
        }
        if ("PUT".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) {
            JsonNode body = readJsonBody(exchange);
            PaymentPolicy current = paymentLedgerService.policy();
            PaymentPolicy updated = new PaymentPolicy(
                readString(body, "currency", current.currency()),
                readMoneyCents(body, "max_per_tx", current.maxPerTxCents()),
                readMoneyCents(body, "max_daily", current.maxDailyCents()),
                readMoneyCents(body, "max_monthly", current.maxMonthlyCents()),
                readMoneyCents(body, "require_confirmation_over", current.requireConfirmationOverCents()),
                readStringList(body, "allowed_merchants", current.allowedMerchants()),
                readStringList(body, "allowed_categories", current.allowedCategories()),
                readString(body, "timezone", current.timezone()),
                readInt(body, "quiet_hours_start", current.quietHoursStart()),
                readInt(body, "quiet_hours_end", current.quietHoursEnd())
            );
            sendJson(exchange, 200, toPolicyResponse(paymentLedgerService.updatePolicy(updated)));
            return;
        }
        sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
    }

    private void handlePaymentsStatus(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(() -> {
                try {
                    handlePaymentsStatus(exchange);
                } catch (Exception e) {
                    sendInternalError(exchange, e);
                }
            });
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        if (paymentLedgerService == null) {
            sendJson(exchange, 503, Map.of("error", "payments_not_configured"));
            return;
        }
        PaymentSummary summary = paymentLedgerService.summary();
        sendJson(exchange, 200, Map.of(
            "reserved", summary.reservedCents() / 100.0,
            "captured", summary.capturedCents() / 100.0,
            "daily_used", summary.dailyUsedCents() / 100.0,
            "monthly_used", summary.monthlyUsedCents() / 100.0,
            "available_daily", summary.availableDailyCents() / 100.0,
            "available_monthly", summary.availableMonthlyCents() / 100.0,
            "transactions", summary.totalTransactions()
        ));
    }

    private void handleDashboardSummary(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(() -> {
                try {
                    handleDashboardSummary(exchange);
                } catch (Exception e) {
                    sendInternalError(exchange, e);
                }
            });
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        if (observabilityService == null) {
            sendJson(exchange, 503, Map.of("error", "observability_not_configured"));
            return;
        }
        var summary = observabilityService.summary();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tasks_started", summary.tasksStarted());
        payload.put("tasks_succeeded", summary.tasksSucceeded());
        payload.put("tasks_failed", summary.tasksFailed());
        payload.put("task_success_rate", summary.taskSuccessRate());
        payload.put("p50_latency_ms", summary.p50LatencyMs());
        payload.put("p95_latency_ms", summary.p95LatencyMs());
        payload.put("average_cost_per_task_usd", summary.averageCostPerTaskUsd());
        payload.put("failure_recovery_rate", summary.failureRecoveryRate());
        payload.put("safety_incident_rate", summary.safetyIncidentRate());
        payload.put("weekly_completed_tasks", summary.weeklyCompletedTasks());
        payload.put("active_users_7d", summary.activeUsers7d());
        payload.put("retention_7d", summary.retention7d());
        payload.put("audit_events", summary.auditEvents());
        sendJson(exchange, 200, payload);
    }

    private void handleAuditEvents(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(() -> {
                try {
                    handleAuditEvents(exchange);
                } catch (Exception e) {
                    sendInternalError(exchange, e);
                }
            });
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        if (observabilityService == null) {
            sendJson(exchange, 503, Map.of("error", "observability_not_configured"));
            return;
        }
        int limit = parseQueryInt(exchange, "limit", 100, 1, 1000);
        var events = observabilityService.recent(limit);
        sendJson(exchange, 200, Map.of("events", events));
    }

    private void handleFiles(HttpServerExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
            sendJson(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }

        String path = exchange.getRequestPath();
        String prefix = "/files/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
            sendJson(exchange, 404, Map.of("error", "not_found"));
            return;
        }

        String filename = safeFilename(path.substring(prefix.length()));
        Path target = uploadsDir.resolve(filename).normalize();
        if (!target.startsWith(uploadsDir) || !Files.exists(target)) {
            sendJson(exchange, 404, Map.of("error", "not_found"));
            return;
        }

        byte[] bytes = Files.readAllBytes(target);
        String contentType = URLConnection.guessContentTypeFromName(filename);
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(bytes.length));
        exchange.getResponseSender().send(ByteBuffer.wrap(bytes));
    }

    private void onWebSocketConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
        String token = queryParam(exchange, "token");
        if (!wsToken.isBlank() && !wsToken.equals(token)) {
            try {
                channel.close();
            } catch (Exception ignored) {
            }
            return;
        }

        String clientId = queryParam(exchange, "client_id");
        if (clientId.isBlank()) {
            try {
                channel.close();
            } catch (Exception ignored) {
            }
            return;
        }

        clients.put(clientId, channel);

        channel.getCloseSetter().set(closeChannel -> clients.remove(clientId));
        channel.getReceiveSetter().set(new AbstractReceiveListener() {
            @Override
            protected void onFullTextMessage(WebSocketChannel wsChannel, BufferedTextMessage message) {
                handleInboundWs(clientId, wsChannel, message.getData());
            }
        });
        channel.resumeReceives();
    }

    private void handleInboundWs(String clientId, WebSocketChannel channel, String raw) {
        executor.submit(() -> {
            try {
                WsInboundMessage inbound = mapper.readValue(raw, WsInboundMessage.class);
                if ("ping".equals(inbound.type())) {
                    sendWs(channel, new WsOutboundMessage("pong", null, null, null, null, null, null));
                    return;
                }
                if (!"message".equals(inbound.type())) {
                    return;
                }

                String content = inbound.content() == null ? "" : inbound.content().trim();
                if (content.isBlank()) {
                    sendWs(channel, new WsOutboundMessage("ack", null, null, inbound.msgId(), null, null, null));
                    return;
                }

                if (orchestrator != null && agentSettings != null) {
                    String taskId = UUID.randomUUID().toString();
                    long startedAt = System.currentTimeMillis();
                    recordEvent("user_activity", Map.of("client_id", clientId, "channel", "ws"));
                    recordEvent("task_started", Map.of(
                        "task_id", taskId,
                        "client_id", clientId,
                        "input_chars", content.length(),
                        "channel", "ws"
                    ));
                    sendWs(channel, new WsOutboundMessage("typing", null, clientId, null, null, null, true));
                    try {
                        AgentResult result = orchestrator.run(content, agentSettings, workspace);
                        String responseText = result.content() == null ? "" : result.content();
                        String responseId = UUID.randomUUID().toString();
                        boolean streamed = false;
                        if (!responseText.isBlank()) {
                            for (String chunk : chunkText(responseText, STREAM_CHUNK_SIZE)) {
                                sendWs(channel, new WsOutboundMessage("text_delta", chunk, clientId, null, null, responseId, null));
                                streamed = true;
                            }
                        }
                        if (!streamed) {
                            sendWs(channel, new WsOutboundMessage("message", responseText, clientId, null, responseId, null, null));
                        }
                        sendWs(channel, new WsOutboundMessage("typing", null, clientId, null, null, null, false));
                        drainMessageBusToClient(channel, clientId);
                        Map<String, Object> taskCompleted = new LinkedHashMap<>();
                        taskCompleted.put("task_id", taskId);
                        taskCompleted.put("client_id", clientId);
                        taskCompleted.put("duration_ms", System.currentTimeMillis() - startedAt);
                        taskCompleted.put("output_chars", responseText.length());
                        Number cost = asNumber(result.usage().get("cost_usd"));
                        if (cost != null) {
                            taskCompleted.put("cost_usd", cost.doubleValue());
                        }
                        recordEvent("task_succeeded", taskCompleted);
                    } catch (Exception runError) {
                        recordEvent("task_failed", Map.of(
                            "task_id", taskId,
                            "client_id", clientId,
                            "duration_ms", System.currentTimeMillis() - startedAt,
                            "error", runError.getMessage() == null ? "execution_error" : runError.getMessage()
                        ));
                        throw runError;
                    }
                }

                if (inbound.msgId() != null && !inbound.msgId().isBlank()) {
                    sendWs(channel, new WsOutboundMessage("ack", null, null, inbound.msgId(), null, null, null));
                }
            } catch (Exception e) {
                LOG.warn("Failed to process inbound WebSocket message for client {}", clientId, e);
            }
        });
    }

    private void pumpBusToClients() {
        while (running.get()) {
            try {
                if (messageBus != null) {
                    var next = messageBus.poll();
                    if (next.isPresent()) {
                        BusMessageMapper.OutboundFrame frame = busMessageMapper.map(next.get());
                        broadcast(new WsOutboundMessage(frame.type(), frame.content(), null, null, null, null, null));
                        continue;
                    }
                }
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void drainMessageBusToClient(WebSocketChannel channel, String clientId) {
        if (messageBus == null) {
            return;
        }
        while (true) {
            var next = messageBus.poll();
            if (next.isEmpty()) {
                return;
            }
            BusMessageMapper.OutboundFrame frame = busMessageMapper.map(next.get());
            sendWs(channel, new WsOutboundMessage(frame.type(), frame.content(), clientId, null, null, null, null));
        }
    }

    private void broadcast(WsOutboundMessage message) {
        for (WebSocketChannel channel : clients.values()) {
            sendWs(channel, message);
        }
    }

    private void sendWs(WebSocketChannel channel, WsOutboundMessage message) {
        try {
            WebSockets.sendText(mapper.writeValueAsString(message), channel, null);
        } catch (Exception ignored) {
        }
    }

    private List<String> chunkText(String input, int chunkSize) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        int safeChunk = Math.max(1, chunkSize);
        List<String> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < input.length(); i += safeChunk) {
            chunks.add(input.substring(i, Math.min(input.length(), i + safeChunk)));
        }
        return chunks;
    }

    private UploadPayload readUploadPayload(HttpServerExchange exchange, String field, String fallbackName) throws Exception {
        exchange.startBlocking();
        String contentType = header(exchange, "Content-Type");
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/form-data")) {
            Files.createDirectories(tempDir);
            MultiPartParserDefinition multiPart = new MultiPartParserDefinition();
            multiPart.setTempFileLocation(tempDir);
            FormDataParser parser = FormParserFactory.builder()
                .addParser(multiPart)
                .addParser(new FormEncodedDataDefinition())
                .build()
                .createParser(exchange);
            if (parser != null) {
                FormData form = parser.parseBlocking();
                FormData.FormValue value = form.getFirst(field);
                if (value != null && value.isFileItem()) {
                    FormData.FileItem item = value.getFileItem();
                    byte[] bytes;
                    if (item.isInMemory()) {
                        bytes = item.getInputStream().readAllBytes();
                    } else {
                        bytes = Files.readAllBytes(item.getFile());
                    }
                    String filename = value.getFileName() == null ? fallbackName : value.getFileName();
                    String type = contentTypeFromFilename(filename);
                    return new UploadPayload(bytes, filename, type);
                }
            }
        }

        byte[] raw = exchange.getInputStream().readAllBytes();
        String filename = header(exchange, "X-Filename");
        if (filename == null || filename.isBlank()) {
            filename = fallbackName;
        }
        String type = contentTypeFromFilename(filename);
        return new UploadPayload(raw, filename, type);
    }

    private void sendJson(HttpServerExchange exchange, int status, Map<String, ?> payload) throws IOException {
        byte[] body = mapper.writeValueAsBytes(payload);
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(body.length));
        exchange.getResponseSender().send(ByteBuffer.wrap(body));
    }

    private JsonNode readJsonBody(HttpServerExchange exchange) throws IOException {
        exchange.startBlocking();
        byte[] bytes = exchange.getInputStream().readAllBytes();
        if (bytes.length == 0) {
            return mapper.createObjectNode();
        }
        return mapper.readTree(bytes);
    }

    private int parseQueryInt(HttpServerExchange exchange, String key, int fallback, int min, int max) {
        try {
            Deque<String> values = exchange.getQueryParameters().get(key);
            String raw = values == null || values.isEmpty() ? "" : values.getFirst();
            int parsed = Integer.parseInt(raw);
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Map<String, Object> toPolicyResponse(PaymentPolicy policy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("currency", policy.currency());
        payload.put("max_per_tx", policy.maxPerTxCents() / 100.0);
        payload.put("max_daily", policy.maxDailyCents() / 100.0);
        payload.put("max_monthly", policy.maxMonthlyCents() / 100.0);
        payload.put("require_confirmation_over", policy.requireConfirmationOverCents() / 100.0);
        payload.put("allowed_merchants", policy.allowedMerchants());
        payload.put("allowed_categories", policy.allowedCategories());
        payload.put("timezone", policy.timezone());
        payload.put("quiet_hours_start", policy.quietHoursStart());
        payload.put("quiet_hours_end", policy.quietHoursEnd());
        return payload;
    }

    private String readString(JsonNode body, String field, String fallback) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return fallback;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Number asNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer readInt(JsonNode body, String field, Integer fallback) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.isInt() || node.isLong()) {
            return node.intValue();
        }
        try {
            return Integer.parseInt(node.asText().trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long readMoneyCents(JsonNode body, String field, long fallbackCents) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return fallbackCents;
        }
        String raw = node.asText();
        try {
            java.math.BigDecimal dollars = new java.math.BigDecimal(raw.trim());
            return dollars.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        } catch (Exception ignored) {
            return fallbackCents;
        }
    }

    private List<String> readStringList(JsonNode body, String field, List<String> fallback) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                String value = item.asText("");
                if (!value.isBlank()) {
                    values.add(value.trim());
                }
            }
            return values;
        }
        String raw = node.asText("");
        if (raw.isBlank()) {
            return List.of();
        }
        String[] split = raw.split(",");
        List<String> values = new ArrayList<>();
        for (String value : split) {
            String trimmed = value.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private void recordEvent(String type, Map<String, Object> attributes) {
        if (observabilityService == null) {
            return;
        }
        try {
            observabilityService.record(type, attributes);
        } catch (Exception ignored) {
            // best effort
        }
    }

    private void sendInternalError(HttpServerExchange exchange, Exception error) {
        try {
            sendJson(exchange, 500, Map.of("error", error.getMessage() == null ? "internal_error" : error.getMessage()));
        } catch (IOException ignored) {
        }
    }

    private String queryParam(WebSocketHttpExchange exchange, String key) {
        List<String> values = exchange.getRequestParameters().get(key);
        if (values == null || values.isEmpty()) {
            return "";
        }
        String value = values.getFirst();
        return value == null ? "" : value;
    }

    private String header(HttpServerExchange exchange, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        return value == null ? "" : value;
    }

    private String contentTypeFromFilename(String filename) {
        String guessed = URLConnection.guessContentTypeFromName(filename);
        return guessed == null || guessed.isBlank() ? "application/octet-stream" : guessed;
    }

    private String safeFilename(String raw) {
        String normalized = raw == null ? "" : raw.replace('\\', '/');
        int idx = normalized.lastIndexOf('/');
        if (idx >= 0) {
            normalized = normalized.substring(idx + 1);
        }
        normalized = normalized.replaceAll("[^A-Za-z0-9._-]", "_");
        return normalized.isBlank() ? "file.bin" : normalized;
    }

    private static int resolveBoundPort(Undertow undertow, int fallbackPort) {
        try {
            Object address = undertow.getListenerInfo().getFirst().getAddress();
            if (address instanceof InetSocketAddress socketAddress) {
                return socketAddress.getPort();
            }
        } catch (Exception ignored) {
        }
        return fallbackPort;
    }

    public record WsInboundMessage(
        String type,
        @JsonProperty("client_id") String clientId,
        String content,
        @JsonProperty("msg_id") String msgId,
        Map<String, String> metadata
    ) {
    }

    public record WsOutboundMessage(
        String type,
        String content,
        @JsonProperty("chat_id") String chatId,
        @JsonProperty("msg_id") String msgId,
        String id,
        @JsonProperty("message_id") String messageId,
        @JsonProperty("is_typing") Boolean isTyping
    ) {
    }

    private record UploadPayload(byte[] bytes, String filename, String contentType) {
    }

}
