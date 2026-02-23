package io.cognis.core.payment;

import java.util.List;

public record PaymentState(PaymentPolicy policy, List<PaymentTransaction> transactions) {

    public PaymentState {
        policy = policy == null ? PaymentPolicy.defaults() : policy;
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
    }

    public static PaymentState empty() {
        return new PaymentState(PaymentPolicy.defaults(), List.of());
    }
}
