package io.cognis.core.payment;

public enum PaymentStatus {
    PENDING_CONFIRMATION,
    AUTHORIZED,
    CAPTURED,
    CANCELLED,
    DENIED
}
