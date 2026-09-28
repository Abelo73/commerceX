package com.commercex.payment.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param provider           name of the active {@code PaymentProvider}
 * @param webhookSecret      HMAC secret shared with the provider (required)
 * @param signatureTolerance max age of a webhook signature timestamp (replay protection)
 * @param reconcileAfter     unsettled payments/refunds older than this are re-checked with the provider
 */
@ConfigurationProperties("commercex.payments")
public record PaymentProperties(String provider, String webhookSecret, Duration signatureTolerance,
        Duration reconcileAfter) {

    public PaymentProperties {
        if (webhookSecret == null || webhookSecret.length() < 32) {
            throw new IllegalStateException("commercex.payments.webhook-secret must be at least 32 characters");
        }
    }

    @Override
    public String toString() {
        return "PaymentProperties[provider=" + provider + "]";
    }
}
