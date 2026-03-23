package io.cognis.vertical.sa.agriculture;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.heartbeat.HeartbeatJob;
import io.cognis.core.sandbox.VerticalPolicy;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import io.cognis.vertical.sa.agriculture.heartbeat.MarketPriceHeartbeatJob;
import io.cognis.vertical.sa.agriculture.tool.MarketLocatorTool;
import io.cognis.vertical.sa.agriculture.tool.SafexPriceTool;
import io.cognis.vertical.sa.agriculture.tool.SubsidyNavigatorTool;
import io.cognis.vertical.sa.agriculture.webhook.SaSmsWebhookRoute;
import io.cognis.vertical.sa.agriculture.webhook.SaWhatsAppWebhookRoute;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaAgricultureVerticalTest {

    @TempDir Path tempDir;

    @Test
    void nameIsSaAgriculture() {
        assertThat(new SaAgricultureVertical().name()).isEqualTo("sa-agriculture");
    }

    @Test
    void toolsContainsAllThreeAgTools() {
        List<Tool> tools = new SaAgricultureVertical().tools();
        assertThat(tools).hasSize(3);
        assertThat(tools.stream().map(t -> t.getClass().getSimpleName()).toList())
            .containsExactlyInAnyOrder("SafexPriceTool", "SubsidyNavigatorTool", "MarketLocatorTool");
    }

    @Test
    void toolNamesAreCorrect() {
        List<Tool> tools = new SaAgricultureVertical().tools();
        assertThat(tools.stream().map(Tool::name).toList())
            .containsExactlyInAnyOrder("safex_price", "subsidy_navigator", "market_locator");
    }

    @Test
    void routesContainsWhatsAppAndSms() {
        SaAgricultureVertical vertical = new SaAgricultureVertical();
        assertThat(vertical.routes()).hasSize(2);
        assertThat(vertical.routes().stream().map(r -> r.getClass().getSimpleName()).toList())
            .containsExactlyInAnyOrder("SaWhatsAppWebhookRoute", "SaSmsWebhookRoute");
        assertThat(vertical.routes().stream().map(r -> r.path()).toList())
            .containsExactlyInAnyOrder("/webhook/sa/whatsapp", "/webhook/sa/sms");
    }

    @Test
    void heartbeatJobsContainsMarketBrief() {
        List<HeartbeatJob> jobs = new SaAgricultureVertical().heartbeatJobs();
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0)).isInstanceOf(MarketPriceHeartbeatJob.class);
        assertThat(jobs.get(0).name()).isEqualTo("sa-agriculture.morning-market-brief");
        assertThat(jobs.get(0).cronExpression()).isEqualTo("0 4 * * *");
    }

    @Test
    void policyAllowsAgToolsAndDeniesShellPayments() {
        VerticalPolicy policy = new SaAgricultureVertical().policy();
        assertThat(policy.allowsTool("safex_price")).isTrue();
        assertThat(policy.allowsTool("subsidy_navigator")).isTrue();
        assertThat(policy.allowsTool("market_locator")).isTrue();
        assertThat(policy.allowsTool("web")).isTrue();
        assertThat(policy.allowsTool("memory")).isTrue();
        assertThat(policy.allowsTool("shell")).isFalse();
        assertThat(policy.allowsTool("payments")).isFalse();
        assertThat(policy.isPermissive()).isFalse();
    }

    @Test
    void initializeWithEmptyContextDoesNotThrow() {
        SaAgricultureVertical vertical = new SaAgricultureVertical();
        vertical.initialize(new ToolContext(tempDir));
    }

    @Test
    void systemPromptContainsKeyElements() {
        assertThat(SaAgricultureVertical.SYSTEM_PROMPT)
            .contains("SAFEX")
            .contains("CASP")
            .contains("Land Bank")
            .contains("Zulu")
            .contains("Xhosa")
            .contains("Afrikaans")
            .contains("province");
    }

    @Test
    void providersIsEmpty() {
        assertThat(new SaAgricultureVertical().providers()).isEmpty();
    }
}
