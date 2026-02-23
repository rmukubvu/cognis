package io.cognis.core.payment;

import java.time.Instant;

public record PaymentTransaction(
    String id,
    Instant createdAt,
    Instant updatedAt,
    String merchant,
    String category,
    long amountCents,
    String description,
    String externalRef,
    PaymentStatus status,
    String reason
) {
    public PaymentTransaction {
        id = id == null ? "" : id;
        createdAt = createdAt == null ? Instant.EPOCH : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
        merchant = merchant == null ? "" : merchant.trim();
        category = category == null ? "" : category.trim();
        amountCents = Math.max(0, amountCents);
        description = description == null ? "" : description.trim();
        externalRef = externalRef == null ? "" : externalRef.trim();
        status = status == null ? PaymentStatus.DENIED : status;
        reason = reason == null ? "" : reason.trim();
    }
}
