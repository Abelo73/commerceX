package com.commercex.payment.application;

import com.commercex.common.web.MoneyResponse;
import com.commercex.payment.domain.Payment;
import com.commercex.payment.domain.Refund;
import java.time.Instant;
import java.util.List;

public final class PaymentViews {

    private PaymentViews() {
    }

    /** Customer view: {@code nextActionUrl} is set when status is REQUIRES_ACTION (e.g. 3-D Secure). */
    public record PaymentView(long id, String status, MoneyResponse amount, MoneyResponse refunded,
            String failureCode, String failureMessage, String nextActionUrl, Instant createdAt) {

        static PaymentView from(Payment p) {
            return new PaymentView(p.getId(), p.getStatus().name(), MoneyResponse.of(p.getAmountMinor(), p.getCurrency()),
                    MoneyResponse.of(p.getRefundedMinor(), p.getCurrency()), p.getFailureCode(), p.getFailureMessage(),
                    p.getNextActionUrl(), p.getCreatedAt());
        }
    }

    public record RefundView(long id, long paymentId, String status, MoneyResponse amount, String reason,
            String failureMessage, Instant createdAt) {

        static RefundView from(Refund r, String currency) {
            return new RefundView(r.getId(), r.getPaymentId(), r.getStatus().name(),
                    MoneyResponse.of(r.getAmountMinor(), currency), r.getReason(), r.getFailureMessage(),
                    r.getCreatedAt());
        }
    }

    public record AdminPaymentView(PaymentView payment, String provider, String providerReference,
            List<RefundView> refunds) {
    }
}
