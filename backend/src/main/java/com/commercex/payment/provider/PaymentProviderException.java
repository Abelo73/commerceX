package com.commercex.payment.provider;

/**
 * Transport-level failure (timeout, 5xx, network). The outcome is UNKNOWN: the provider may or may not have
 * acted. Callers must leave the payment/refund pending and resolve it later, never assume failure.
 */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message) {
        super(message);
    }

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
