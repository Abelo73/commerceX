package com.commercex.payment.provider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * In-memory provider for development and tests. The outcome is chosen by the payment token:
 * <ul>
 *   <li>{@code tok_success} → SUCCEEDED</li>
 *   <li>{@code tok_decline} → FAILED (card_declined)</li>
 *   <li>{@code tok_3ds} → REQUIRES_ACTION (complete via {@link #completeAction})</li>
 *   <li>{@code tok_pending} → PENDING</li>
 *   <li>{@code tok_timeout} → charges the card, then throws: "payment succeeded but our request timed out"</li>
 *   <li>{@code tok_unavailable} → throws without charging (provider down)</li>
 * </ul>
 * Repeated idempotency keys return the original payment, like real providers.
 */
@Component
public class MockPaymentProvider implements PaymentProvider {

    private static final class MockPayment {
        final String reference;
        final long amount;
        final String currency;
        ProviderStatus status;
        String failureCode;
        long refunded;

        MockPayment(String reference, long amount, String currency, ProviderStatus status, String failureCode) {
            this.reference = reference;
            this.amount = amount;
            this.currency = currency;
            this.status = status;
            this.failureCode = failureCode;
        }
    }

    private final Map<String, MockPayment> byKey = new ConcurrentHashMap<>();
    private final Map<String, MockPayment> byReference = new ConcurrentHashMap<>();
    private final Map<String, RefundResult> refunds = new ConcurrentHashMap<>();
    private final AtomicInteger createCalls = new AtomicInteger();
    private volatile boolean failNextRefund;

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public synchronized PaymentResult createPayment(PaymentRequest request) {
        createCalls.incrementAndGet();
        MockPayment existing = byKey.get(request.idempotencyKey());
        if (existing != null) {
            return result(existing);
        }
        String token = request.paymentToken();
        if (token == null) {
            // Re-query of an attempt we never received: nothing was charged.
            return new PaymentResult(null, ProviderStatus.FAILED, request.amountMinor(), request.currency(),
                    "not_received", "The provider never received this payment", null);
        }
        if ("tok_unavailable".equals(token)) {
            throw new PaymentProviderException("mock provider unavailable");
        }
        ProviderStatus status = switch (token) {
            case "tok_success", "tok_timeout" -> ProviderStatus.SUCCEEDED;
            case "tok_decline" -> ProviderStatus.FAILED;
            case "tok_3ds" -> ProviderStatus.REQUIRES_ACTION;
            case "tok_pending" -> ProviderStatus.PENDING;
            default -> ProviderStatus.FAILED;
        };
        String failure = switch (token) {
            case "tok_decline" -> "card_declined";
            case "tok_success", "tok_timeout", "tok_3ds", "tok_pending" -> null;
            default -> "invalid_token";
        };
        MockPayment payment = new MockPayment("mock_pay_" + UUID.randomUUID(), request.amountMinor(),
                request.currency(), status, failure);
        byKey.put(request.idempotencyKey(), payment);
        byReference.put(payment.reference, payment);
        if ("tok_timeout".equals(token)) {
            throw new PaymentProviderException("mock timeout after charging");
        }
        return result(payment);
    }

    @Override
    public synchronized PaymentResult getPayment(String providerReference) {
        MockPayment payment = byReference.get(providerReference);
        if (payment == null) {
            throw new PaymentProviderException("unknown payment " + providerReference);
        }
        return result(payment);
    }

    @Override
    public synchronized RefundResult refund(RefundRequest request) {
        RefundResult previous = refunds.get(request.idempotencyKey());
        if (previous != null) {
            return previous;
        }
        if (failNextRefund) {
            failNextRefund = false;
            throw new PaymentProviderException("mock refund timeout");
        }
        MockPayment payment = byReference.get(request.providerPaymentReference());
        RefundResult result;
        if (payment == null || payment.status != ProviderStatus.SUCCEEDED) {
            result = new RefundResult(null, RefundResult.Status.FAILED, "payment not captured");
        } else if (payment.refunded + request.amountMinor() > payment.amount) {
            result = new RefundResult(null, RefundResult.Status.FAILED, "amount exceeds captured amount");
        } else {
            payment.refunded += request.amountMinor();
            result = new RefundResult("mock_re_" + UUID.randomUUID(), RefundResult.Status.SUCCEEDED, null);
        }
        refunds.put(request.idempotencyKey(), result);
        return result;
    }

    // ---- test / demo controls ----------------------------------------------------------------------

    /** Simulates the customer finishing (or abandoning) a 3-D Secure challenge. */
    public synchronized void completeAction(String providerReference, boolean succeeded) {
        MockPayment payment = byReference.get(providerReference);
        payment.status = succeeded ? ProviderStatus.SUCCEEDED : ProviderStatus.FAILED;
        payment.failureCode = succeeded ? null : "authentication_failed";
    }

    public synchronized String referenceForKey(String idempotencyKey) {
        MockPayment payment = byKey.get(idempotencyKey);
        return payment == null ? null : payment.reference;
    }

    public int createCalls() {
        return createCalls.get();
    }

    public void failNextRefund() {
        failNextRefund = true;
    }

    private static PaymentResult result(MockPayment p) {
        return new PaymentResult(p.reference, p.status, p.amount, p.currency, p.failureCode,
                p.failureCode == null ? null : "Mock failure: " + p.failureCode,
                p.status == ProviderStatus.REQUIRES_ACTION ? "https://mock-pay.local/3ds/" + p.reference : null);
    }
}
