package io.cognis.core.payment;

public record PaymentSummary(
    long reservedCents,
    long capturedCents,
    long dailyUsedCents,
    long monthlyUsedCents,
    long availableDailyCents,
    long availableMonthlyCents,
    int totalTransactions
) {
}
