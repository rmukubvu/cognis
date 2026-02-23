package io.cognis.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.core.agent.AgentOrchestrator;
import io.cognis.core.agent.AgentSettings;
import io.cognis.core.observability.FileAuditStore;
import io.cognis.core.observability.ObservabilityService;
import io.cognis.core.payment.FilePaymentStore;
import io.cognis.core.payment.PaymentLedgerService;
import io.cognis.core.provider.EchoProvider;
import io.cognis.core.provider.ProviderRegistry;
import io.cognis.core.provider.ProviderRouter;
import io.cognis.core.tool.ToolRegistry;
import io.cognis.core.voice.Transcriber;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GatewayServerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldUploadAndServeFile() throws Exception {
        try (GatewayServer server = new GatewayServer(0, tempDir, audioPath -> "ignored")) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest upload = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/upload"))
                .header("X-Filename", "note.txt")
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("hello"))
                .build();

            HttpResponse<String> uploaded = client.send(upload, HttpResponse.BodyHandlers.ofString());
            assertThat(uploaded.statusCode()).isEqualTo(200);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode body = mapper.readTree(uploaded.body());
            String url = body.path("url").asText();
            assertThat(url).startsWith("/files/");

            HttpRequest fetch = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + url))
                .GET()
                .build();
            HttpResponse<String> file = client.send(fetch, HttpResponse.BodyHandlers.ofString());
            assertThat(file.statusCode()).isEqualTo(200);
            assertThat(file.body()).isEqualTo("hello");
        }
    }

    @Test
    void shouldTranscribeAudio() throws Exception {
        Transcriber transcriber = audioPath -> "transcribed-text";
        try (GatewayServer server = new GatewayServer(0, tempDir, transcriber)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/transcribe"))
                .header("X-Filename", "voice.wav")
                .header("Content-Type", "audio/wav")
                .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[] {1, 2, 3}))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("transcribed-text");
        }
    }

    @Test
    void shouldHandleWebSocketPingAndAck() throws Exception {
        try (GatewayServer server = new GatewayServer(0, tempDir, audioPath -> "ignored")) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();

            CompletableFuture<String> firstMessage = new CompletableFuture<>();
            CompletableFuture<String> secondMessage = new CompletableFuture<>();

            WebSocket ws = client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(
                    URI.create("ws://127.0.0.1:" + server.port() + "/ws?client_id=mobile-1"),
                    new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            webSocket.request(1);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            if (!firstMessage.isDone()) {
                                firstMessage.complete(data.toString());
                            } else if (!secondMessage.isDone()) {
                                secondMessage.complete(data.toString());
                            }
                            webSocket.request(1);
                            return null;
                        }
                    }
                )
                .join();

            ws.sendText("{\"type\":\"ping\"}", true).join();
            String pong = firstMessage.get(5, TimeUnit.SECONDS);
            assertThat(pong).contains("\"type\":\"pong\"");

            ws.sendText("{\"type\":\"message\",\"content\":\"hello\",\"msg_id\":\"m1\"}", true).join();
            String ack = secondMessage.get(5, TimeUnit.SECONDS);
            assertThat(ack).contains("\"type\":\"ack\"").contains("\"msg_id\":\"m1\"");
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    @Test
    void shouldEmitTypingAndTextDeltaWithoutDuplicateFinalMessage() throws Exception {
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new EchoProvider("echo"));
        AgentOrchestrator orchestrator = new AgentOrchestrator(new ProviderRouter(registry), new ToolRegistry());
        AgentSettings settings = new AgentSettings("You are Cognis.", "echo", "echo-model", 1);

        try (GatewayServer server = new GatewayServer(
            0,
            "0.0.0.0",
            tempDir,
            audioPath -> "ignored",
            orchestrator,
            settings,
            null,
            ""
        )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            LinkedBlockingQueue<String> frames = new LinkedBlockingQueue<>();

            WebSocket ws = client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(
                    URI.create("ws://127.0.0.1:" + server.port() + "/ws?client_id=mobile-1"),
                    new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            webSocket.request(1);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            frames.offer(data.toString());
                            webSocket.request(1);
                            return null;
                        }
                    }
                )
                .join();

            ws.sendText("{\"type\":\"message\",\"content\":\"hello\",\"msg_id\":\"m1\"}", true).join();

            ObjectMapper mapper = new ObjectMapper();
            boolean sawTypingTrue = false;
            boolean sawTextDelta = false;
            boolean sawFinalMessage = false;
            boolean sawTypingFalse = false;
            boolean sawAck = false;

            long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadlineNanos && !(sawTypingTrue && sawTextDelta && sawTypingFalse && sawAck)) {
                String frame = frames.poll(200, TimeUnit.MILLISECONDS);
                if (frame == null) {
                    continue;
                }
                JsonNode node = mapper.readTree(frame);
                String type = node.path("type").asText();
                if ("typing".equals(type)) {
                    if (node.path("is_typing").asBoolean()) {
                        sawTypingTrue = true;
                    } else {
                        sawTypingFalse = true;
                    }
                } else if ("text_delta".equals(type)) {
                    sawTextDelta = !node.path("message_id").asText().isBlank()
                        && !node.path("content").asText().isBlank();
                } else if ("message".equals(type)) {
                    sawFinalMessage = !node.path("id").asText().isBlank()
                        && !node.path("content").asText().isBlank();
                } else if ("ack".equals(type)) {
                    sawAck = "m1".equals(node.path("msg_id").asText());
                }
            }

            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();

            assertThat(sawTypingTrue).isTrue();
            assertThat(sawTextDelta).isTrue();
            assertThat(sawFinalMessage).isFalse();
            assertThat(sawTypingFalse).isTrue();
            assertThat(sawAck).isTrue();
        }
    }

    @Test
    void shouldGetAndUpdatePaymentsPolicy() throws Exception {
        PaymentLedgerService ledger = new PaymentLedgerService(
            new FilePaymentStore(tempDir.resolve("payments.json")),
            Clock.fixed(Instant.parse("2026-02-21T10:00:00Z"), ZoneOffset.UTC)
        );
        try (GatewayServer server = new GatewayServer(
            0,
            "0.0.0.0",
            tempDir,
            audioPath -> "ignored",
            null,
            null,
            null,
            "",
            ledger,
            null
        )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            HttpRequest getBefore = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/payments/policy"))
                .GET()
                .build();
            HttpResponse<String> before = client.send(getBefore, HttpResponse.BodyHandlers.ofString());
            assertThat(before.statusCode()).isEqualTo(200);
            JsonNode beforeNode = mapper.readTree(before.body());
            assertThat(beforeNode.path("currency").asText()).isEqualTo("USD");

            HttpRequest update = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/payments/policy"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("""
                    {
                      "max_per_tx": 50,
                      "max_daily": 120,
                      "max_monthly": 500,
                      "require_confirmation_over": 20,
                      "allowed_merchants": ["amazon", "ticketmaster"],
                      "allowed_categories": ["shopping", "tickets"],
                      "timezone": "UTC"
                    }
                    """))
                .build();
            HttpResponse<String> updated = client.send(update, HttpResponse.BodyHandlers.ofString());
            assertThat(updated.statusCode()).isEqualTo(200);
            JsonNode updatedNode = mapper.readTree(updated.body());
            assertThat(updatedNode.path("max_per_tx").asDouble()).isEqualTo(50.0);
            assertThat(updatedNode.path("allowed_merchants")).hasSize(2);

            HttpRequest statusReq = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/payments/status"))
                .GET()
                .build();
            HttpResponse<String> status = client.send(statusReq, HttpResponse.BodyHandlers.ofString());
            assertThat(status.statusCode()).isEqualTo(200);
            JsonNode statusNode = mapper.readTree(status.body());
            assertThat(statusNode.path("available_daily").asDouble()).isEqualTo(120.0);
            assertThat(statusNode.path("transactions").asInt()).isEqualTo(0);
        }
    }

    @Test
    void shouldReturnDashboardSummaryAndAuditEvents() throws Exception {
        ObservabilityService observability = new ObservabilityService(
            new FileAuditStore(tempDir.resolve("audit.json")),
            Clock.fixed(Instant.parse("2026-02-21T10:00:00Z"), ZoneOffset.UTC)
        );
        observability.record("user_activity", Map.of("client_id", "alice"));
        observability.record("task_started", Map.of("task_id", "t1", "client_id", "alice"));
        observability.record("task_succeeded", Map.of("task_id", "t1", "client_id", "alice", "duration_ms", 350, "cost_usd", 0.02));

        try (GatewayServer server = new GatewayServer(
            0,
            "0.0.0.0",
            tempDir,
            audioPath -> "ignored",
            null,
            null,
            null,
            "",
            null,
            observability
        )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            HttpResponse<String> summaryRes = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/dashboard/summary"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(summaryRes.statusCode()).withFailMessage(summaryRes.body()).isEqualTo(200);
            JsonNode summary = mapper.readTree(summaryRes.body());
            assertThat(summary.path("tasks_started").asInt()).isEqualTo(1);
            assertThat(summary.path("task_success_rate").asDouble()).isEqualTo(100.0);

            HttpResponse<String> eventsRes = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/audit/events?limit=5"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(eventsRes.statusCode()).isEqualTo(200);
            JsonNode events = mapper.readTree(eventsRes.body());
            assertThat(events.path("events").isArray()).isTrue();
            assertThat(events.path("events").size()).isGreaterThanOrEqualTo(3);
        }
    }
}
