package com.commercex.payment;

import java.time.Instant;

/** Public API of the payment module (dashboard figures). */
public interface PaymentApi {

    long failedPaymentsSince(Instant from);
}
