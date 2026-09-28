package com.commercex.order;

import java.time.Instant;

/** Minimal order facts for the payment module. */
public record OrderRef(long id, String orderNumber, long userId, String status, long totalMinor, String currency,
        Instant paymentDueAt) {

    public boolean isUnpaid() {
        return "PENDING_PAYMENT".equals(status) || "PAYMENT_FAILED".equals(status);
    }
}
