package io.cognis.core.tool.impl;

import io.cognis.core.payment.FilePaymentStore;
import io.cognis.core.payment.PaymentDecision;
import io.cognis.core.payment.PaymentLedgerService;
import io.cognis.core.payment.PaymentPolicy;
import io.cognis.core.payment.PaymentSummary;
import io.cognis.core.payment.PaymentTransaction;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static java.util.Map.entry;

public final class PaymentsTool implements Tool {
    private static final int DEFAULT_LIST_LIMIT = 10;

    @Override
    public String name() {
        return "payments";
    }

    @Override
    public String description() {
        return "Manage delegated spending policy and guarded purchase reservations";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "type", "object",
            "properties", Map.ofEntries(
                entry("action", Map.of("type", "string")),
                entry("merchant", Map.of("type", "string")),
                entry("category", Map.of("type", "string")),
                entry("amount", Map.of("type", "number")),
                entry("description", Map.of("type", "string")),
                entry("external_ref", Map.of("type", "string")),
                entry("transaction_id", Map.of("type", "string")),
                entry("max_per_tx", Map.of("type", "number")),
                entry("max_daily", Map.of("type", "number")),
                entry("max_monthly", Map.of("type", "number")),
                entry("require_confirmation_over", Map.of("type", "number")),
                entry("allowed_merchants", Map.of("type", "array")),
                entry("allowed_categories", Map.of("type", "array")),
                entry("timezone", Map.of("type", "string")),
                entry("quiet_hours_start", Map.of("type", "integer")),
                entry("quiet_hours_end", Map.of("type", "integer")),
                entry("currency", Map.of("type", "string")),
                entry("limit", Map.of("type", "integer"))
            ),
            "required", new String[] {"action"}
        );
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String action = text(input.get("action"));
        if (action.isBlank()) {
            return "Error: action is required";
        }

        try {
            return switch (action) {
                case "set_policy" -> setPolicy(input, context);
                case "get_policy" -> getPolicy(context);
                case "request" -> request(input, context);
                case "confirm" -> confirm(input, context);
                case "capture" -> capture(input, context);
                case "cancel" -> cancel(input, context);
                case "status" -> status(context);
                case "list" -> list(input, context);
                default -> "Error: unsupported payments action: " + action;
            };
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String setPolicy(Map<String, Object> input, ToolContext context) throws Exception {
        PaymentLedgerService service = resolveService(context);
        PaymentPolicy current = service.policy();
        PaymentPolicy updated = new PaymentPolicy(
            textOrDefault(input.get("currency"), current.currency()),
            dollarsOrDefault(input.get("max_per_tx"), current.maxPerTxCents()),
            dollarsOrDefault(input.get("max_daily"), current.maxDailyCents()),
            dollarsOrDefault(input.get("max_monthly"), current.maxMonthlyCents()),
            dollarsOrDefault(input.get("require_confirmation_over"), current.requireConfirmationOverCents()),
            listOrDefault(input.get("allowed_merchants"), current.allowedMerchants()),
            listOrDefault(input.get("allowed_categories"), current.allowedCategories()),
            textOrDefault(input.get("timezone"), current.timezone()),
            intOrDefault(input.get("quiet_hours_start"), current.quietHoursStart()),
            intOrDefault(input.get("quiet_hours_end"), current.quietHoursEnd())
        );
        service.updatePolicy(updated);
        return formatPolicy(updated);
    }

    private String getPolicy(ToolContext context) throws Exception {
        return formatPolicy(resolveService(context).policy());
    }

    private String request(Map<String, Object> input, ToolContext context) throws Exception {
        String merchant = text(input.get("merchant"));
        String category = text(input.get("category"));
        long amount = requireDollars(input.get("amount"), "amount");
        String description = text(input.get("description"));
        String externalRef = text(input.get("external_ref"));

        PaymentDecision decision = resolveService(context).request(merchant, category, amount, description, externalRef);
        return formatDecision(decision);
    }

    private String confirm(Map<String, Object> input, ToolContext context) throws Exception {
        String transactionId = text(input.get("transaction_id"));
        if (transactionId.isBlank()) {
            return "Error: transaction_id is required";
        }
        return formatDecision(resolveService(context).confirm(transactionId));
    }

    private String capture(Map<String, Object> input, ToolContext context) throws Exception {
        String transactionId = text(input.get("transaction_id"));
        if (transactionId.isBlank()) {
            return "Error: transaction_id is required";
        }
        return formatDecision(resolveService(context).capture(transactionId));
    }

    private String cancel(Map<String, Object> input, ToolContext context) throws Exception {
        String transactionId = text(input.get("transaction_id"));
        if (transactionId.isBlank()) {
            return "Error: transaction_id is required";
        }
        return formatDecision(resolveService(context).cancel(transactionId));
    }

    private String status(ToolContext context) throws Exception {
        PaymentLedgerService service = resolveService(context);
        PaymentPolicy policy = service.policy();
        PaymentSummary summary = service.summary();
        return """
            Wallet Status:
            - currency: %s
            - reserved: %s
            - captured: %s
            - daily used: %s (available: %s)
            - monthly used: %s (available: %s)
            - transactions: %d
            """.formatted(
            policy.currency(),
            dollars(summary.reservedCents()),
            dollars(summary.capturedCents()),
            dollars(summary.dailyUsedCents()),
            dollars(summary.availableDailyCents()),
            dollars(summary.monthlyUsedCents()),
            dollars(summary.availableMonthlyCents()),
            summary.totalTransactions()
        ).trim();
    }

    private String list(Map<String, Object> input, ToolContext context) throws Exception {
        int limit = intOrDefault(input.get("limit"), DEFAULT_LIST_LIMIT);
        List<PaymentTransaction> txs = resolveService(context).list(Math.max(1, limit));
        if (txs.isEmpty()) {
            return "No payment transactions found.";
        }
        List<String> rows = new ArrayList<>();
        for (PaymentTransaction tx : txs) {
            rows.add("- %s | %s | %s | %s | %s".formatted(
                tx.id(),
                tx.status().name(),
                tx.merchant().isBlank() ? "(merchant)" : tx.merchant(),
                tx.category().isBlank() ? "(category)" : tx.category(),
                dollars(tx.amountCents())
            ));
        }
        return "Payment Transactions:\n" + String.join("\n", rows);
    }

    private String formatPolicy(PaymentPolicy policy) {
        return """
            Spending Policy:
            - currency: %s
            - max_per_tx: %s
            - max_daily: %s
            - max_monthly: %s
            - require_confirmation_over: %s
            - allowed_merchants: %s
            - allowed_categories: %s
            - timezone: %s
            - quiet_hours: %s
            """.formatted(
            policy.currency(),
            dollars(policy.maxPerTxCents()),
            dollars(policy.maxDailyCents()),
            dollars(policy.maxMonthlyCents()),
            dollars(policy.requireConfirmationOverCents()),
            policy.allowedMerchants().isEmpty() ? "(any)" : String.join(", ", policy.allowedMerchants()),
            policy.allowedCategories().isEmpty() ? "(any)" : String.join(", ", policy.allowedCategories()),
            policy.timezone(),
            quietHours(policy)
        ).trim();
    }

    private String formatDecision(PaymentDecision decision) {
        String budgets = "remaining_daily=%s, remaining_monthly=%s".formatted(
            dollars(decision.remainingDailyCents()),
            dollars(decision.remainingMonthlyCents())
        );
        return switch (decision.status()) {
            case DENIED -> "Payment DENIED (%s): %s".formatted(decision.transactionId(), decision.message());
            case PENDING_CONFIRMATION -> "Payment PENDING_CONFIRMATION (%s): %s [%s]".formatted(
                decision.transactionId(),
                decision.message(),
                budgets
            );
            case AUTHORIZED -> "Payment AUTHORIZED (%s): %s [%s]".formatted(
                decision.transactionId(),
                decision.message(),
                budgets
            );
            case CAPTURED -> "Payment CAPTURED (%s): %s [%s]".formatted(
                decision.transactionId(),
                decision.message(),
                budgets
            );
            case CANCELLED -> "Payment CANCELLED (%s): %s [%s]".formatted(
                decision.transactionId(),
                decision.message(),
                budgets
            );
        };
    }

    private String quietHours(PaymentPolicy policy) {
        if (policy.quietHoursStart() == null || policy.quietHoursEnd() == null || policy.quietHoursStart().equals(policy.quietHoursEnd())) {
            return "(off)";
        }
        return "%02d:00-%02d:00".formatted(policy.quietHoursStart(), policy.quietHoursEnd());
    }

    private PaymentLedgerService resolveService(ToolContext context) {
        PaymentLedgerService service = context.service("paymentLedgerService", PaymentLedgerService.class);
        if (service != null) {
            return service;
        }
        Path path = context.workspace().resolve(".cognis/payments/ledger.json");
        return new PaymentLedgerService(new FilePaymentStore(path), Clock.systemUTC());
    }

    private long requireDollars(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return dollarsToCents(value);
    }

    private long dollarsOrDefault(Object value, long fallbackCents) {
        if (value == null) {
            return fallbackCents;
        }
        return dollarsToCents(value);
    }

    private long dollarsToCents(Object raw) {
        String text = String.valueOf(raw).trim();
        BigDecimal dollars = new BigDecimal(text);
        return dollars.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private List<String> listOrDefault(Object raw, List<String> fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof List<?> list) {
            List<String> values = new ArrayList<>();
            for (Object item : list) {
                String value = text(item);
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
            return values;
        }
        String text = text(raw);
        if (text.isBlank()) {
            return List.of();
        }
        String[] split = text.split(",");
        List<String> values = new ArrayList<>();
        for (String value : split) {
            String trimmed = value.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private Integer intOrDefault(Object raw, Integer fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String textOrDefault(Object value, String fallback) {
        String text = text(value);
        return text.isBlank() ? fallback : text;
    }

    private String text(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private String dollars(long cents) {
        return String.format(Locale.ROOT, "%.2f", cents / 100.0);
    }
}
