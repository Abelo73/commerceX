package com.commercex.payment.provider;

/**
 * Port to a payment service provider (TRD §9). Order logic never depends on a concrete provider.
 * Implementations must be idempotent on the request idempotency keys and must never receive or return
 * raw card data.
 */
public interface PaymentProvider {

    /** Stable identifier stored with each payment, e.g. {@code "mock"} or {@code "stripe"}. */
    String name();

    /** @throws PaymentProviderException when the outcome is unknown (timeout, network, 5xx) */
    PaymentResult createPayment(PaymentRequest request);

    PaymentResult getPayment(String providerReference);

    RefundResult refund(RefundRequest request);
}
