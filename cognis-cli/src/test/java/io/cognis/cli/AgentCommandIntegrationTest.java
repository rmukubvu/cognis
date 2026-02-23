package io.cognis.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.agent.AgentOrchestrator;
import io.cognis.core.config.ConfigService;
import io.cognis.core.provider.OpenAiCompatProvider;
import io.cognis.core.provider.ProviderRegistry;
import io.cognis.core.provider.ProviderRouter;
import io.cognis.core.tool.ToolRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class AgentCommandIntegrationTest {

    private MockWebServer server;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldExecuteAgentCommandUsingHttpProvider() throws Exception {
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""
                {
                  "choices": [
                    { "message": { "content": "integration-ok" } }
                  ]
                }
                """));

        Path configPath = tempDir.resolve("config.json");
        Files.writeString(configPath, """
            {
              "agents": {
                "defaults": {
                  "provider": "openrouter",
                  "model": "gpt-4.1",
                  "workspace": "%s"
                }
              },
              "providers": {
                "openrouter": {
                  "apiKey": "sk-test",
                  "apiBase": "%s"
                }
              }
            }
            """.formatted(tempDir.resolve("workspace").toString().replace("\\", "\\\\"), server.url("/v1/").toString()), StandardCharsets.UTF_8);

        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new OpenAiCompatProvider("openrouter", "sk-test", server.url("/v1/").toString(), java.util.Map.of(), 1));

        AgentOrchestrator orchestrator = new AgentOrchestrator(new ProviderRouter(registry), new ToolRegistry());
        CliContext context = new CliContext(orchestrator, new ConfigService(), configPath);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out));
            int code = new CommandLine(new AgentCommand(context)).execute("hello");
            assertThat(code).isEqualTo(0);
        } finally {
            System.setOut(originalOut);
        }

        assertThat(out.toString(StandardCharsets.UTF_8)).contains("integration-ok");
    }
}
