package io.cognis.core.payment;

public record PaymentDecision(
    PaymentStatus status,
    String transactionId,
    String message,
    long remainingDailyCents,
    long remainingMonthlyCents
) {
}
