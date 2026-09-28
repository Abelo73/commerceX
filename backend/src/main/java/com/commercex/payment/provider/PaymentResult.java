package com.commercex.payment.provider;

public record PaymentResult(String providerReference, ProviderStatus status, long amountMinor, String currency,
        String failureCode, String failureMessage, String nextActionUrl) {
}
