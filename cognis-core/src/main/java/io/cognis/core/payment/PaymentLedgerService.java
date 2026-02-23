package io.cognis.core.payment;

import io.cognis.core.observability.ObservabilityService;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PaymentLedgerService {
    private final PaymentStore store;
    private final Clock clock;
    private final ObservabilityService observabilityService;

    public PaymentLedgerService(PaymentStore store, Clock clock) {
        this(store, clock, null);
    }

    public PaymentLedgerService(PaymentStore store, Clock clock, ObservabilityService observabilityService) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.observabilityService = observabilityService;
    }

    public synchronized PaymentPolicy policy() throws IOException {
        return store.load().policy();
    }

    public synchronized PaymentPolicy updatePolicy(PaymentPolicy policy) throws IOException {
        PaymentState current = store.load();
        PaymentState updated = new PaymentState(policy, current.transactions());
        store.save(updated);
        return updated.policy();
    }

    public synchronized PaymentDecision request(
        String merchant,
        String category,
        long amountCents,
        String description,
        String externalRef
    ) throws IOException {
        emit("payment_request", null, merchant, category, amountCents, externalRef, "");
        PaymentState current = store.load();
        Instant now = clock.instant();
        Validation validation = validate(current, merchant, category, amountCents, now, null);
        if (!validation.allowed()) {
            PaymentTransaction denied = transaction(
                now,
                merchant,
                category,
                amountCents,
                description,
                externalRef,
                PaymentStatus.DENIED,
                validation.reason()
            );
            saveWith(current, denied);
            emit("payment_denied", denied.id(), denied.merchant(), denied.category(), denied.amountCents(), denied.externalRef(), validation.reason());
            return new PaymentDecision(PaymentStatus.DENIED, denied.id(), validation.reason(), validation.remainingDaily(), validation.remainingMonthly());
        }

        boolean needsConfirmation = amountCents > current.policy().requireConfirmationOverCents();
        PaymentStatus status = needsConfirmation ? PaymentStatus.PENDING_CONFIRMATION : PaymentStatus.AUTHORIZED;
        String message = needsConfirmation
            ? "Pending confirmation before execution."
            : "Authorized. Funds reserved for execution.";
        PaymentTransaction approved = transaction(
            now,
            merchant,
            category,
            amountCents,
            description,
            externalRef,
            status,
            ""
        );
        saveWith(current, approved);
        emit(
            needsConfirmation ? "approval_requested" : "payment_authorized",
            approved.id(),
            approved.merchant(),
            approved.category(),
            approved.amountCents(),
            approved.externalRef(),
            needsConfirmation ? "pending_confirmation" : "authorized"
        );

        long remainingDaily = validation.remainingDaily();
        long remainingMonthly = validation.remainingMonthly();
        if (!needsConfirmation) {
            remainingDaily = Math.max(0, remainingDaily - amountCents);
            remainingMonthly = Math.max(0, remainingMonthly - amountCents);
        }
        return new PaymentDecision(status, approved.id(), message, remainingDaily, remainingMonthly);
    }

    public synchronized PaymentDecision confirm(String transactionId) throws IOException {
        PaymentState current = store.load();
        int index = indexOf(current.transactions(), transactionId);
        if (index < 0) {
            return new PaymentDecision(PaymentStatus.DENIED, "", "Transaction not found.", 0, 0);
        }
        PaymentTransaction tx = current.transactions().get(index);
        if (tx.status() != PaymentStatus.PENDING_CONFIRMATION) {
            return new PaymentDecision(PaymentStatus.DENIED, tx.id(), "Only pending transactions can be confirmed.", 0, 0);
        }

        Instant now = clock.instant();
        Validation validation = validate(current, tx.merchant(), tx.category(), tx.amountCents(), now, tx.id());
        if (!validation.allowed()) {
            PaymentTransaction denied = new PaymentTransaction(
                tx.id(),
                tx.createdAt(),
                now,
                tx.merchant(),
                tx.category(),
                tx.amountCents(),
                tx.description(),
                tx.externalRef(),
                PaymentStatus.DENIED,
                validation.reason()
            );
            replaceAndSave(current, index, denied);
            emit("payment_denied", denied.id(), denied.merchant(), denied.category(), denied.amountCents(), denied.externalRef(), validation.reason());
            return new PaymentDecision(PaymentStatus.DENIED, tx.id(), validation.reason(), validation.remainingDaily(), validation.remainingMonthly());
        }

        PaymentTransaction authorized = new PaymentTransaction(
            tx.id(),
            tx.createdAt(),
            now,
            tx.merchant(),
            tx.category(),
            tx.amountCents(),
            tx.description(),
            tx.externalRef(),
            PaymentStatus.AUTHORIZED,
            ""
        );
        replaceAndSave(current, index, authorized);
        emit("payment_authorized", authorized.id(), authorized.merchant(), authorized.category(), authorized.amountCents(), authorized.externalRef(), "authorized_after_confirmation");
        return new PaymentDecision(
            PaymentStatus.AUTHORIZED,
            authorized.id(),
            "Authorized. Funds reserved for execution.",
            Math.max(0, validation.remainingDaily() - tx.amountCents()),
            Math.max(0, validation.remainingMonthly() - tx.amountCents())
        );
    }

    public synchronized PaymentDecision capture(String transactionId) throws IOException {
        return transition(transactionId, PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED, "Captured successfully.");
    }

    public synchronized PaymentDecision cancel(String transactionId) throws IOException {
        PaymentState current = store.load();
        int index = indexOf(current.transactions(), transactionId);
        if (index < 0) {
            return new PaymentDecision(PaymentStatus.DENIED, "", "Transaction not found.", 0, 0);
        }
        PaymentTransaction tx = current.transactions().get(index);
        if (tx.status() != PaymentStatus.AUTHORIZED && tx.status() != PaymentStatus.PENDING_CONFIRMATION) {
            return new PaymentDecision(PaymentStatus.DENIED, tx.id(), "Only authorized or pending transactions can be cancelled.", 0, 0);
        }
        PaymentTransaction cancelled = new PaymentTransaction(
            tx.id(),
            tx.createdAt(),
            clock.instant(),
            tx.merchant(),
            tx.category(),
            tx.amountCents(),
            tx.description(),
            tx.externalRef(),
            PaymentStatus.CANCELLED,
            "Cancelled by user or policy."
        );
        replaceAndSave(current, index, cancelled);
        emit("payment_cancelled", cancelled.id(), cancelled.merchant(), cancelled.category(), cancelled.amountCents(), cancelled.externalRef(), cancelled.reason());
        PaymentSummary summary = summaryInternal(store.load());
        return new PaymentDecision(PaymentStatus.CANCELLED, cancelled.id(), "Cancelled successfully.", summary.availableDailyCents(), summary.availableMonthlyCents());
    }

    public synchronized List<PaymentTransaction> list(int limit) throws IOException {
        int safeLimit = Math.max(1, limit);
        return store.load().transactions().stream()
            .sorted(Comparator.comparing(PaymentTransaction::createdAt).reversed())
            .limit(safeLimit)
            .toList();
    }

    public synchronized PaymentSummary summary() throws IOException {
        return summaryInternal(store.load());
    }

    private PaymentDecision transition(String transactionId, PaymentStatus from, PaymentStatus to, String okMessage) throws IOException {
        PaymentState current = store.load();
        int index = indexOf(current.transactions(), transactionId);
        if (index < 0) {
            return new PaymentDecision(PaymentStatus.DENIED, "", "Transaction not found.", 0, 0);
        }
        PaymentTransaction tx = current.transactions().get(index);
        if (tx.status() != from) {
            return new PaymentDecision(PaymentStatus.DENIED, tx.id(), "Invalid transaction status for this operation.", 0, 0);
        }
        PaymentTransaction transitioned = new PaymentTransaction(
            tx.id(),
            tx.createdAt(),
            clock.instant(),
            tx.merchant(),
            tx.category(),
            tx.amountCents(),
            tx.description(),
            tx.externalRef(),
            to,
            ""
        );
        replaceAndSave(current, index, transitioned);
        emit(
            to == PaymentStatus.CAPTURED ? "payment_captured" : "payment_transition",
            transitioned.id(),
            transitioned.merchant(),
            transitioned.category(),
            transitioned.amountCents(),
            transitioned.externalRef(),
            to.name().toLowerCase()
        );
        PaymentSummary summary = summaryInternal(store.load());
        return new PaymentDecision(to, transitioned.id(), okMessage, summary.availableDailyCents(), summary.availableMonthlyCents());
    }

    private Validation validate(
        PaymentState state,
        String merchant,
        String category,
        long amountCents,
        Instant now,
        String excludeTransactionId
    ) {
        if (amountCents <= 0) {
            return Validation.denied("Amount must be greater than zero.", 0, 0);
        }

        PaymentPolicy policy = state.policy();
        if (!policy.allowsMerchant(merchant)) {
            return Validation.denied("Merchant is blocked by policy.", 0, 0);
        }
        if (!policy.allowsCategory(category)) {
            return Validation.denied("Category is blocked by policy.", 0, 0);
        }
        if (policy.inQuietHours(now)) {
            return Validation.denied("Execution blocked during quiet hours.", 0, 0);
        }
        if (amountCents > policy.maxPerTxCents()) {
            return Validation.denied("Amount exceeds per-transaction limit.", 0, 0);
        }

        Spend spend = usage(state.transactions(), policy, now, excludeTransactionId);
        long remainingDaily = Math.max(0, policy.maxDailyCents() - spend.dailyCents());
        long remainingMonthly = Math.max(0, policy.maxMonthlyCents() - spend.monthlyCents());
        if (amountCents > remainingDaily) {
            return Validation.denied("Amount exceeds remaining daily budget.", remainingDaily, remainingMonthly);
        }
        if (amountCents > remainingMonthly) {
            return Validation.denied("Amount exceeds remaining monthly budget.", remainingDaily, remainingMonthly);
        }
        return Validation.allowed(remainingDaily, remainingMonthly);
    }

    private PaymentSummary summaryInternal(PaymentState state) {
        Instant now = clock.instant();
        PaymentPolicy policy = state.policy();
        Spend spend = usage(state.transactions(), policy, now, null);
        long reserved = state.transactions().stream()
            .filter(tx -> tx.status() == PaymentStatus.AUTHORIZED)
            .mapToLong(PaymentTransaction::amountCents)
            .sum();
        long captured = state.transactions().stream()
            .filter(tx -> tx.status() == PaymentStatus.CAPTURED)
            .mapToLong(PaymentTransaction::amountCents)
            .sum();
        return new PaymentSummary(
            reserved,
            captured,
            spend.dailyCents(),
            spend.monthlyCents(),
            Math.max(0, policy.maxDailyCents() - spend.dailyCents()),
            Math.max(0, policy.maxMonthlyCents() - spend.monthlyCents()),
            state.transactions().size()
        );
    }

    private Spend usage(List<PaymentTransaction> transactions, PaymentPolicy policy, Instant now, String excludeTransactionId) {
        LocalDate today = now.atZone(policy.zoneId()).toLocalDate();
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();

        long daily = 0;
        long monthly = 0;
        for (PaymentTransaction tx : transactions) {
            if (excludeTransactionId != null && excludeTransactionId.equals(tx.id())) {
                continue;
            }
            if (tx.status() != PaymentStatus.AUTHORIZED && tx.status() != PaymentStatus.CAPTURED) {
                continue;
            }
            LocalDate txDate = tx.updatedAt().atZone(policy.zoneId()).toLocalDate();
            if (txDate.equals(today)) {
                daily += tx.amountCents();
            }
            if (txDate.getYear() == currentYear && txDate.getMonthValue() == currentMonth) {
                monthly += tx.amountCents();
            }
        }
        return new Spend(daily, monthly);
    }

    private void saveWith(PaymentState current, PaymentTransaction tx) throws IOException {
        List<PaymentTransaction> all = new ArrayList<>(current.transactions());
        all.add(tx);
        store.save(new PaymentState(current.policy(), all));
    }

    private void replaceAndSave(PaymentState current, int index, PaymentTransaction tx) throws IOException {
        List<PaymentTransaction> all = new ArrayList<>(current.transactions());
        all.set(index, tx);
        store.save(new PaymentState(current.policy(), all));
    }

    private int indexOf(List<PaymentTransaction> all, String id) {
        if (id == null || id.isBlank()) {
            return -1;
        }
        for (int i = 0; i < all.size(); i++) {
            if (id.equals(all.get(i).id())) {
                return i;
            }
        }
        return -1;
    }

    private PaymentTransaction transaction(
        Instant now,
        String merchant,
        String category,
        long amountCents,
        String description,
        String externalRef,
        PaymentStatus status,
        String reason
    ) {
        return new PaymentTransaction(
            UUID.randomUUID().toString(),
            now,
            now,
            merchant,
            category,
            amountCents,
            description,
            externalRef,
            status,
            reason
        );
    }

    private record Spend(long dailyCents, long monthlyCents) {
    }

    private record Validation(boolean allowed, String reason, long remainingDaily, long remainingMonthly) {
        private static Validation allowed(long remainingDaily, long remainingMonthly) {
            return new Validation(true, "", remainingDaily, remainingMonthly);
        }

        private static Validation denied(String reason, long remainingDaily, long remainingMonthly) {
            return new Validation(false, reason, remainingDaily, remainingMonthly);
        }
    }

    private void emit(
        String type,
        String transactionId,
        String merchant,
        String category,
        long amountCents,
        String externalRef,
        String detail
    ) {
        if (observabilityService == null) {
            return;
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("transaction_id", transactionId == null ? "" : transactionId);
        attributes.put("merchant", merchant == null ? "" : merchant);
        attributes.put("category", category == null ? "" : category);
        attributes.put("amount_cents", amountCents);
        attributes.put("amount_usd", amountCents / 100.0);
        attributes.put("external_ref", externalRef == null ? "" : externalRef);
        attributes.put("detail", detail == null ? "" : detail);
        try {
            observabilityService.record(type, attributes);
        } catch (Exception ignored) {
            // best effort
        }
    }
}
