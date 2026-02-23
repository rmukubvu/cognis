package io.cognis.core.payment;

import java.io.IOException;

public interface PaymentStore {
    PaymentState load() throws IOException;

    void save(PaymentState state) throws IOException;
}
