package com.commercex.payment.provider;

/**
 * @param idempotencyKey stable per CommerceX payment; providers must return the same payment for a repeated key
 * @param paymentId      our payment ID, sent as metadata and echoed in webhooks
 * @param paymentToken   opaque token from the provider's client SDK (never card data); may be null when
 *                       re-querying an attempt by its idempotency key
 */
public record PaymentRequest(String idempotencyKey, long paymentId, String orderNumber, long amountMinor,
        String currency, String paymentToken) {

    @Override
    public String toString() {
        return "PaymentRequest[paymentId=" + paymentId + ", order=" + orderNumber + "]";
    }
}
