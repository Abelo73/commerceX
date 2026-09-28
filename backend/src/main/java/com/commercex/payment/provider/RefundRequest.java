package com.commercex.payment.provider;

/** @param idempotencyKey stable per CommerceX refund; repeated calls must not refund twice */
public record RefundRequest(String idempotencyKey, String providerPaymentReference, long amountMinor,
        String currency) {
}
