package com.commercex.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commercex.payment.provider.PaymentResult;
import com.commercex.payment.provider.ProviderStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private static Payment pending() {
        return Payment.start(1, 2, "mock", "key-12345678", 5_000, "USD", NOW);
    }

    private static PaymentResult result(ProviderStatus status) {
        return new PaymentResult("ref_1", status, 5_000, "USD", status == ProviderStatus.FAILED ? "card_declined" : null,
                null, status == ProviderStatus.REQUIRES_ACTION ? "https://3ds" : null);
    }

    @Test
    void settledPaymentsNeverChange() {
        Payment p = pending();
        assertThat(p.apply(result(ProviderStatus.SUCCEEDED), NOW)).isEqualTo(Payment.Change.SUCCEEDED);
        assertThat(p.apply(result(ProviderStatus.SUCCEEDED), NOW)).isEqualTo(Payment.Change.NONE);
        assertThat(p.apply(result(ProviderStatus.FAILED), NOW)).isEqualTo(Payment.Change.NONE);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void requiresActionThenSucceeds() {
        Payment p = pending();
        assertThat(p.apply(result(ProviderStatus.REQUIRES_ACTION), NOW)).isEqualTo(Payment.Change.REQUIRES_ACTION);
        assertThat(p.getNextActionUrl()).isEqualTo("https://3ds");
        assertThat(p.apply(result(ProviderStatus.REQUIRES_ACTION), NOW)).isEqualTo(Payment.Change.NONE);
        assertThat(p.apply(result(ProviderStatus.SUCCEEDED), NOW)).isEqualTo(Payment.Change.SUCCEEDED);
        assertThat(p.getNextActionUrl()).isNull();
    }

    @Test
    void pendingResultOnlyRecordsReference() {
        Payment p = pending();
        assertThat(p.apply(result(ProviderStatus.PENDING), NOW)).isEqualTo(Payment.Change.NONE);
        assertThat(p.getProviderReference()).isEqualTo("ref_1");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void refundsCannotExceedCapturedAmount() {
        Payment p = pending();
        assertThat(p.refundableMinor()).isZero();
        p.apply(result(ProviderStatus.SUCCEEDED), NOW);
        p.recordRefund(3_000, NOW);
        assertThat(p.refundableMinor()).isEqualTo(2_000);
        assertThatThrownBy(() -> p.recordRefund(2_001, NOW)).isInstanceOf(IllegalStateException.class);
        p.recordRefund(2_000, NOW);
        assertThat(p.isFullyRefunded()).isTrue();
    }
}
