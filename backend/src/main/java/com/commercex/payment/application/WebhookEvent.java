package com.commercex.payment.application;

import com.commercex.payment.provider.PaymentResult;
import com.commercex.payment.provider.ProviderStatus;
import com.commercex.payment.provider.RefundResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Provider-neutral webhook payload (the mock provider's format; real adapters translate into it).
 * {@code paymentId} / {@code refundId} are CommerceX IDs echoed back from request metadata, so events can
 * be matched even before our own call to the provider has returned.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record WebhookEvent(String id, String type, Data data) {

    enum Kind { PAYMENT, REFUND, OTHER }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(Long paymentId, Long refundId, String reference, Long amount, String currency, String failureCode,
            String failureMessage, String nextActionUrl) {
    }

    Kind kind() {
        if (type == null) {
            return Kind.OTHER;
        }
        return switch (type) {
            case "payment.succeeded", "payment.failed", "payment.requires_action" -> Kind.PAYMENT;
            case "refund.succeeded", "refund.failed" -> Kind.REFUND;
            default -> Kind.OTHER;
        };
    }

    Long paymentId() {
        return data == null ? null : data.paymentId();
    }

    Long refundId() {
        return data == null ? null : data.refundId();
    }

    PaymentResult toPaymentResult() {
        ProviderStatus status = switch (type) {
            case "payment.succeeded" -> ProviderStatus.SUCCEEDED;
            case "payment.failed" -> ProviderStatus.FAILED;
            default -> ProviderStatus.REQUIRES_ACTION;
        };
        return new PaymentResult(data.reference(), status, data.amount() == null ? -1 : data.amount(),
                data.currency(), data.failureCode(), data.failureMessage(), data.nextActionUrl());
    }

    RefundResult toRefundResult() {
        return new RefundResult(data.reference(), "refund.succeeded".equals(type)
                ? RefundResult.Status.SUCCEEDED : RefundResult.Status.FAILED, data.failureMessage());
    }
}
