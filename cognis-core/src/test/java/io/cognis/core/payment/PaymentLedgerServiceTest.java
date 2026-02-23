package io.cognis.core.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaymentLedgerServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDenyRequestWhenAmountExceedsPerTransactionPolicy() throws Exception {
        PaymentLedgerService service = new PaymentLedgerService(
            new FilePaymentStore(tempDir.resolve("ledger.json")),
            Clock.fixed(Instant.parse("2026-02-21T10:00:00Z"), ZoneOffset.UTC)
        );
        service.updatePolicy(new PaymentPolicy("USD", 1_000, 20_000, 100_000, 500, null, null, "UTC", null, null));

        PaymentDecision decision = service.request("amazon", "shopping", 1_500, "New keyboard", "ord-1");

        assertThat(decision.status()).isEqualTo(PaymentStatus.DENIED);
        assertThat(decision.message()).contains("per-transaction");
        assertThat(service.list(5)).hasSize(1);
        assertThat(service.list(5).getFirst().status()).isEqualTo(PaymentStatus.DENIED);
    }

    @Test
    void shouldRequireConfirmationThenAuthorizeAndCapture() throws Exception {
        PaymentLedgerService service = new PaymentLedgerService(
            new FilePaymentStore(tempDir.resolve("ledger.json")),
            Clock.fixed(Instant.parse("2026-02-21T10:00:00Z"), ZoneOffset.UTC)
        );
        service.updatePolicy(new PaymentPolicy("USD", 10_000, 20_000, 100_000, 2_000, null, null, "UTC", null, null));

        PaymentDecision request = service.request("ticketmaster", "tickets", 3_500, "Concert ticket", "evt-44");
        PaymentDecision confirm = service.confirm(request.transactionId());
        PaymentDecision capture = service.capture(request.transactionId());
        PaymentSummary summary = service.summary();

        assertThat(request.status()).isEqualTo(PaymentStatus.PENDING_CONFIRMATION);
        assertThat(confirm.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(capture.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(summary.capturedCents()).isEqualTo(3_500);
        assertThat(summary.dailyUsedCents()).isEqualTo(3_500);
    }

    @Test
    void shouldDenyRequestDuringQuietHours() throws Exception {
        PaymentLedgerService service = new PaymentLedgerService(
            new FilePaymentStore(tempDir.resolve("ledger.json")),
            Clock.fixed(Instant.parse("2026-02-21T23:30:00Z"), ZoneOffset.UTC)
        );
        service.updatePolicy(new PaymentPolicy("USD", 10_000, 20_000, 100_000, 2_000, null, null, "UTC", 22, 7));

        PaymentDecision decision = service.request("uber", "transport", 1_200, "Airport ride", "ride-1");

        assertThat(decision.status()).isEqualTo(PaymentStatus.DENIED);
        assertThat(decision.message()).contains("quiet hours");
    }
}
