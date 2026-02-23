package io.cognis.core.tool.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.payment.FilePaymentStore;
import io.cognis.core.payment.PaymentLedgerService;
import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaymentsToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSetPolicyAndReadBackStatus() throws Exception {
        PaymentLedgerService ledger = new PaymentLedgerService(
            new FilePaymentStore(tempDir.resolve("ledger.json")),
            Clock.fixed(Instant.parse("2026-02-21T10:00:00Z"), ZoneOffset.UTC)
        );
        PaymentsTool tool = new PaymentsTool();
        ToolContext context = new ToolContext(tempDir, Map.of("paymentLedgerService", ledger));

        String setPolicy = tool.execute(
            Map.of(
                "action", "set_policy",
                "max_per_tx", 50,
                "max_daily", 120,
                "max_monthly", 500,
                "require_confirmation_over", 20,
                "allowed_merchants", java.util.List.of("amazon", "ticketmaster")
            ),
            context
        );
        String status = tool.execute(Map.of("action", "status"), context);

        assertThat(setPolicy).contains("max_per_tx: 50.00");
        assertThat(setPolicy).contains("allowed_merchants: amazon, ticketmaster");
        assertThat(status).contains("daily used: 0.00");
    }

    @Test
    void shouldRunRequestAndConfirmationFlow() throws Exception {
        PaymentLedgerService ledger = new PaymentLedgerService(
            new FilePaymentStore(tempDir.resolve("ledger.json")),
            Clock.fixed(Instant.parse("2026-02-21T10:00:00Z"), ZoneOffset.UTC)
        );
        PaymentsTool tool = new PaymentsTool();
        ToolContext context = new ToolContext(tempDir, Map.of("paymentLedgerService", ledger));

        tool.execute(
            Map.of(
                "action", "set_policy",
                "max_per_tx", 100,
                "max_daily", 300,
                "max_monthly", 800,
                "require_confirmation_over", 25
            ),
            context
        );

        String requested = tool.execute(
            Map.of(
                "action", "request",
                "merchant", "ticketmaster",
                "category", "tickets",
                "amount", 30,
                "description", "Concert seat"
            ),
            context
        );
        String txId = requested.substring(requested.indexOf("(") + 1, requested.indexOf(")"));

        String confirmed = tool.execute(Map.of("action", "confirm", "transaction_id", txId), context);
        String captured = tool.execute(Map.of("action", "capture", "transaction_id", txId), context);

        assertThat(requested).contains("PENDING_CONFIRMATION");
        assertThat(confirmed).contains("AUTHORIZED");
        assertThat(captured).contains("CAPTURED");
    }
}
