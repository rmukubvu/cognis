package io.cognis.app;

import io.cognis.cli.AgentCommand;
import io.cognis.cli.CliContext;
import io.cognis.cli.GatewayCommand;
import io.cognis.cli.CognisCliCommand;
import io.cognis.cli.OnboardCommand;
import io.cognis.cli.StatusCommand;
import io.cognis.core.agent.AgentOrchestrator;
import io.cognis.core.agent.AgentSettings;
import io.cognis.core.api.GatewayServer;
import io.cognis.core.bus.InMemoryMessageBus;
import io.cognis.core.bus.MessageBus;
import io.cognis.core.model.ChatMessage;
import io.cognis.core.cron.CronService;
import io.cognis.core.cron.FileCronStore;
import io.cognis.core.config.ConfigPaths;
import io.cognis.core.config.ConfigService;
import io.cognis.core.config.model.CognisConfig;
import io.cognis.core.config.model.ProviderConfig;
import io.cognis.core.provider.AnthropicProvider;
import io.cognis.core.provider.CodexResponsesProvider;
import io.cognis.core.provider.DisabledProvider;
import io.cognis.core.provider.FallbackLlmProvider;
import io.cognis.core.provider.LlmProvider;
import io.cognis.core.provider.OpenAiCompatProvider;
import io.cognis.core.provider.ProviderRegistry;
import io.cognis.core.provider.ProviderRouter;
import io.cognis.core.memory.FileMemoryStore;
import io.cognis.core.observability.FileAuditStore;
import io.cognis.core.observability.ObservabilityService;
import io.cognis.core.payment.FilePaymentStore;
import io.cognis.core.payment.PaymentLedgerService;
import io.cognis.core.profile.FileProfileStore;
import io.cognis.core.session.ConversationStore;
import io.cognis.core.session.FileConversationStore;
import io.cognis.core.session.FileSessionSummaryManager;
import io.cognis.core.session.SqliteConversationStore;
import io.cognis.core.tool.ToolRegistry;
import io.cognis.core.tool.impl.CronTool;
import io.cognis.core.tool.impl.FilesystemTool;
import io.cognis.core.tool.impl.MemoryTool;
import io.cognis.core.tool.impl.MessageTool;
import io.cognis.core.tool.impl.NotifyTool;
import io.cognis.core.tool.impl.PaymentsTool;
import io.cognis.core.tool.impl.ProfileTool;
import io.cognis.core.tool.impl.ShellTool;
import io.cognis.core.tool.impl.VisionTool;
import io.cognis.core.tool.impl.WebTool;
import io.cognis.core.tool.impl.WorkflowTool;
import io.cognis.core.voice.NoopTranscriber;
import io.cognis.core.voice.OpenAiTranscriber;
import io.cognis.core.voice.Transcriber;
import io.cognis.core.workflow.WorkflowService;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import picocli.CommandLine;

public final class CognisApplication {

    private CognisApplication() {
    }

    public static void main(String[] args) {
        ConfigService configService = new ConfigService();
        CognisConfig config = loadConfig(configService);

        LlmProvider openrouter = buildOpenAiCompatProvider("openrouter", config.providers().openrouter(), "https://openrouter.ai/api/v1");
        LlmProvider openai = buildOpenAiCompatProvider("openai", config.providers().openai(), "https://api.openai.com/v1");
        LlmProvider anthropic = buildAnthropicProvider("anthropic", config.providers().anthropic(), "https://api.anthropic.com/v1");
        LlmProvider codex = buildCodexProvider(
            "openai_codex",
            config.providers().openaiCodex(),
            "https://chatgpt.com/backend-api/codex/responses"
        );
        LlmProvider copilot = buildOpenAiCompatProvider(
            "github_copilot",
            config.providers().githubCopilot(),
            "https://api.githubcopilot.com"
        );

        ProviderRegistry providerRegistry = new ProviderRegistry();
        providerRegistry.register(new FallbackLlmProvider("openrouter", List.of(openrouter, openai, anthropic)));
        providerRegistry.register(new FallbackLlmProvider("openai", List.of(openai, openrouter, anthropic)));
        providerRegistry.register(new FallbackLlmProvider("anthropic", List.of(anthropic, openrouter, openai)));
        providerRegistry.register(new FallbackLlmProvider("openai_codex", List.of(codex, openai, openrouter, anthropic)));
        providerRegistry.register(new FallbackLlmProvider("github_copilot", List.of(copilot, openai, openrouter, anthropic)));

        Path workspacePath = ConfigPaths.resolveWorkspace(config.agents().defaults().workspace());
        CronService cronService = new CronService(
            new FileCronStore(workspacePath.resolve(".cognis/cron/jobs.json")),
            Clock.systemUTC()
        );
        setupDailyDigest(cronService);
        InMemoryMessageBus messageBus = new InMemoryMessageBus();
        FileMemoryStore memoryStore = new FileMemoryStore(workspacePath.resolve("memory/memories.json"));
        FileProfileStore profileStore = new FileProfileStore(workspacePath.resolve("profile.json"));
        FileSessionSummaryManager sessionSummaryManager = new FileSessionSummaryManager(
            workspacePath.resolve("memory/session-summary.txt"),
            2_000
        );
        ConversationStore conversationStore = buildConversationStore(workspacePath);
        ObservabilityService observabilityService = new ObservabilityService(
            new FileAuditStore(workspacePath.resolve(".cognis/observability/audit-events.json")),
            Clock.systemUTC()
        );
        PaymentLedgerService paymentLedgerService = new PaymentLedgerService(
            new FilePaymentStore(workspacePath.resolve(".cognis/payments/ledger.json")),
            Clock.systemUTC(),
            observabilityService
        );
        WorkflowService workflowService = new WorkflowService(
            profileStore,
            memoryStore,
            sessionSummaryManager,
            conversationStore
        );

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new FilesystemTool());
        toolRegistry.register(new ShellTool(Duration.ofSeconds(30)));
        toolRegistry.register(new WebTool(config.tools().web().search().apiKey(), config.tools().web().search().maxResults()));
        toolRegistry.register(new CronTool());
        toolRegistry.register(new MessageTool());
        toolRegistry.register(new MemoryTool());
        toolRegistry.register(new ProfileTool());
        toolRegistry.register(new NotifyTool());
        toolRegistry.register(new PaymentsTool());
        toolRegistry.register(new WorkflowTool());
        VisionTool visionTool = resolveVisionTool(config);
        if (visionTool != null) {
            toolRegistry.register(visionTool);
        }

        AgentOrchestrator orchestrator = new AgentOrchestrator(
            new ProviderRouter(providerRegistry),
            toolRegistry,
            Map.of(
                "cronService", cronService,
                "messageBus", messageBus,
                "memoryStore", memoryStore,
                "profileStore", profileStore,
                "sessionSummaryManager", sessionSummaryManager,
                "paymentLedgerService", paymentLedgerService,
                "observabilityService", observabilityService,
                "workflowService", workflowService
            ),
            conversationStore
        );
        AgentSettings gatewayAgentSettings = new AgentSettings(
            "You are Cognis, an autonomous intelligence engine focused on precise execution. "
                + "Always present yourself only as Cognis and do not disclose underlying model/provider branding. "
                + "Use the workflow tool for daily briefs, goal execution loops, and relationship nudges when relevant. "
                + "Use the payments tool for guarded purchase flows and always enforce policy before execution.",
            config.agents().defaults().provider(),
            config.agents().defaults().model(),
            config.agents().defaults().maxToolIterations()
        );

        CliContext context = new CliContext(
            orchestrator,
            configService,
            ConfigPaths.defaultConfigPath(),
            (port, workspaceOverride) -> runGateway(
                configService,
                ConfigPaths.defaultConfigPath(),
                port,
                workspaceOverride,
                orchestrator,
                gatewayAgentSettings,
                messageBus,
                cronService,
                workflowService,
                paymentLedgerService,
                observabilityService
            )
        );

        CommandLine commandLine = new CommandLine(new CognisCliCommand());
        commandLine.addSubcommand("onboard", new OnboardCommand(context));
        commandLine.addSubcommand("agent", new AgentCommand(context));
        commandLine.addSubcommand("status", new StatusCommand(context));
        commandLine.addSubcommand("gateway", new GatewayCommand(context));

        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    private static CognisConfig loadConfig(ConfigService configService) {
        try {
            return configService.load(ConfigPaths.defaultConfigPath());
        } catch (Exception ignored) {
            return CognisConfig.defaults();
        }
    }

    private static LlmProvider buildOpenAiCompatProvider(
        String name,
        ProviderConfig providerConfig,
        String defaultBase
    ) {
        if (providerConfig != null && providerConfig.configured()) {
            String apiBase = providerConfig.apiBase() == null || providerConfig.apiBase().isBlank()
                ? defaultBase
                : providerConfig.apiBase();
            return new OpenAiCompatProvider(name, providerConfig.apiKey(), apiBase, Map.of());
        }
        return new DisabledProvider(name, "missing API key");
    }

    private static LlmProvider buildAnthropicProvider(
        String name,
        ProviderConfig providerConfig,
        String defaultBase
    ) {
        if (providerConfig != null && providerConfig.configured()) {
            String apiBase = providerConfig.apiBase() == null || providerConfig.apiBase().isBlank()
                ? defaultBase
                : providerConfig.apiBase();
            return new AnthropicProvider(name, providerConfig.apiKey(), apiBase);
        }
        return new DisabledProvider(name, "missing API key");
    }

    private static LlmProvider buildCodexProvider(
        String name,
        ProviderConfig providerConfig,
        String endpoint
    ) {
        if (providerConfig != null && providerConfig.configured()) {
            String configuredEndpoint = providerConfig.apiBase() == null || providerConfig.apiBase().isBlank()
                ? endpoint
                : providerConfig.apiBase();
            return new CodexResponsesProvider(
                name,
                providerConfig.apiKey(),
                providerConfig.accountId(),
                configuredEndpoint
            );
        }
        return new DisabledProvider(name, "missing API key");
    }

    private static VisionTool resolveVisionTool(CognisConfig config) {
        if (config.providers().openai().configured()) {
            String base = config.providers().openai().apiBase() == null || config.providers().openai().apiBase().isBlank()
                ? "https://api.openai.com/v1"
                : config.providers().openai().apiBase();
            return new VisionTool(base + "/chat/completions", config.providers().openai().apiKey(), "gpt-4o");
        }
        if (config.providers().openrouter().configured()) {
            String base = config.providers().openrouter().apiBase() == null || config.providers().openrouter().apiBase().isBlank()
                ? "https://openrouter.ai/api/v1"
                : config.providers().openrouter().apiBase();
            return new VisionTool(base + "/chat/completions", config.providers().openrouter().apiKey(), "openai/gpt-4o");
        }
        return null;
    }

    private static void setupDailyDigest(CronService cronService) {
        try {
            boolean exists = cronService.list().stream().anyMatch(job -> "daily-digest".equals(job.name()));
            if (!exists) {
                cronService.addEvery(
                    "daily-digest",
                    24 * 60 * 60,
                    "workflow:daily_brief"
                );
            }
        } catch (Exception ignored) {
            // Best-effort bootstrap.
        }
    }

    private static ConversationStore buildConversationStore(Path workspacePath) {
        String backend = System.getenv().getOrDefault("COGNIS_CONVERSATION_STORE", "sqlite").trim().toLowerCase();
        if ("sqlite".equals(backend)) {
            Path sqlitePath = resolveConversationSqlitePath(workspacePath);
            try {
                return new SqliteConversationStore(sqlitePath);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize SQLite conversation store at " + sqlitePath, e);
            }
        }
        return new FileConversationStore(workspacePath.resolve("memory/history.json"));
    }

    private static Path resolveConversationSqlitePath(Path workspacePath) {
        String raw = System.getenv("COGNIS_CONVERSATION_SQLITE_PATH");
        if (raw == null || raw.isBlank()) {
            return workspacePath.resolve(".cognis/conversations.db");
        }
        if (raw.startsWith("~/")) {
            return Path.of(System.getProperty("user.home")).resolve(raw.substring(2));
        }
        return Path.of(raw);
    }

    private static int runGateway(
        ConfigService configService,
        Path configPath,
        int port,
        Path workspaceOverride,
        AgentOrchestrator orchestrator,
        AgentSettings agentSettings,
        MessageBus messageBus,
        CronService cronService,
        WorkflowService workflowService,
        PaymentLedgerService paymentLedgerService,
        ObservabilityService observabilityService
    ) throws Exception {
        CognisConfig config = configService.load(configPath);
        Path workspace = workspaceOverride != null
            ? workspaceOverride.toAbsolutePath().normalize()
            : ConfigPaths.resolveWorkspace(config.agents().defaults().workspace());
        Transcriber transcriber = resolveTranscriber(config);

        CountDownLatch shutdown = new CountDownLatch(1);
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        try (GatewayServer server = new GatewayServer(
            port,
            "0.0.0.0",
            workspace,
            transcriber,
            orchestrator,
            agentSettings,
            messageBus,
            "",
            paymentLedgerService,
            observabilityService
        )) {
            Runtime.getRuntime().addShutdownHook(new Thread(shutdown::countDown));
            server.start();
            scheduler.scheduleAtFixedRate(
                () -> dispatchDueJobs(cronService, messageBus, workflowService),
                2,
                30,
                java.util.concurrent.TimeUnit.SECONDS
            );
            System.out.println("Gateway started on http://127.0.0.1:" + server.port());
            System.out.println("Endpoints: WS /ws?client_id=<id>, POST /upload, POST /transcribe, GET /files/{name}, GET /healthz");
            shutdown.await();
        } finally {
            scheduler.shutdownNow();
        }
        return 0;
    }

    private static void dispatchDueJobs(CronService cronService, MessageBus messageBus, WorkflowService workflowService) {
        try {
            cronService.runDue(job -> {
                String message = job.message() == null ? "" : job.message().trim();
                try {
                    String outbound;
                    if ("workflow:daily_brief".equalsIgnoreCase(message)) {
                        outbound = workflowService.buildDailyExecutiveBrief();
                    } else if (message.toLowerCase().startsWith("workflow:goal_checkin:")) {
                        String goal = message.substring("workflow:goal_checkin:".length()).trim();
                        outbound = workflowService.buildGoalCheckIn(goal);
                    } else if ("workflow:relationship_nudge".equalsIgnoreCase(message)) {
                        String nudge = workflowService.buildRelationshipNudge("");
                        outbound = nudge.isBlank() ? "Relationship nudge: add contacts in profile to enable this workflow." : nudge;
                    } else {
                        outbound = message;
                    }
                if (!outbound.isBlank()) {
                        String tagged = switch (messageType(message)) {
                            case "daily_brief" -> "[workflow:daily_brief]\n" + outbound;
                            case "goal_checkin" -> "[workflow:goal_checkin]\n" + outbound;
                            case "relationship_nudge" -> "[workflow:workflow_result]\n" + outbound;
                            default -> outbound;
                        };
                        messageBus.publish(ChatMessage.assistant(tagged));
                    }
                } catch (Exception ignored) {
                    // skip this tick if workflow payload generation fails
                }
            });
        } catch (Exception ignored) {
            // best-effort periodic dispatch
        }
    }

    private static String messageType(String cronMessage) {
        if (cronMessage == null) {
            return "";
        }
        String message = cronMessage.trim().toLowerCase();
        if ("workflow:daily_brief".equals(message)) {
            return "daily_brief";
        }
        if (message.startsWith("workflow:goal_checkin:")) {
            return "goal_checkin";
        }
        if ("workflow:relationship_nudge".equals(message)) {
            return "relationship_nudge";
        }
        return "";
    }

    private static Transcriber resolveTranscriber(CognisConfig config) {
        ProviderConfig openai = config.providers().openai();
        if (openai != null && openai.configured()) {
            String base = openai.apiBase() == null || openai.apiBase().isBlank()
                ? "https://api.openai.com/v1"
                : openai.apiBase();
            return new OpenAiTranscriber(base, openai.apiKey(), "whisper-1");
        }
        return new NoopTranscriber();
    }
}
