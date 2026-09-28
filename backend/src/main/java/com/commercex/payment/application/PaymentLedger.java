package com.commercex.payment.application;

import com.commercex.common.audit.AuditLog;
import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ErrorCode;
import com.commercex.common.error.NotFoundException;
import com.commercex.common.error.ValidationException;
import com.commercex.order.OrderApi;
import com.commercex.order.OrderRef;
import com.commercex.payment.application.PaymentViews.AdminPaymentView;
import com.commercex.payment.application.PaymentViews.PaymentView;
import com.commercex.payment.application.PaymentViews.RefundView;
import com.commercex.payment.domain.Payment;
import com.commercex.payment.domain.PaymentStatus;
import com.commercex.payment.domain.Refund;
import com.commercex.payment.infrastructure.PaymentRepository;
import com.commercex.payment.infrastructure.RefundRepository;
import com.commercex.payment.infrastructure.WebhookEventRepository;
import com.commercex.payment.provider.PaymentRequest;
import com.commercex.payment.provider.PaymentResult;
import com.commercex.payment.provider.RefundRequest;
import com.commercex.payment.provider.RefundResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * All payment state changes, each in a short transaction with the payment row locked. Provider calls
 * never happen here: the orchestrating services call the provider between these transactions.
 */
@Component
class PaymentLedger {

    private static final Logger log = LoggerFactory.getLogger(PaymentLedger.class);

    /** Orders whose money may be (partly) returned. CANCELLED covers payments that arrived too late. */
    private static final Set<String> REFUNDABLE_ORDER_STATUSES = Set.of("PAID", "PROCESSING", "DELIVERED",
            "CANCELLED", "REFUNDED");

    record Attempt(long paymentId, boolean created, boolean callProvider) {
    }

    record ProviderCall(String provider, String providerReference, PaymentRequest request) {
    }

    record RefundCall(String provider, RefundRequest request) {
    }

    /** {@code refundToExecute}: a refund created by this change that must now be sent to the provider. */
    record Applied(String outcome, Long refundToExecute) {
    }

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final WebhookEventRepository webhookEvents;
    private final OrderApi orders;
    private final AuditLog auditLog;
    private final MeterRegistry meters;
    private final Clock clock;

    PaymentLedger(PaymentRepository payments, RefundRepository refunds, WebhookEventRepository webhookEvents,
            OrderApi orders, AuditLog auditLog, MeterRegistry meters, Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.webhookEvents = webhookEvents;
        this.orders = orders;
        this.auditLog = auditLog;
        this.meters = meters;
        this.clock = clock;
    }

    // ---- payments ------------------------------------------------------------------------------------

    @Transactional
    Attempt start(long userId, String orderNumber, String idempotencyKey, String provider) {
        OrderRef order = orders.refForCustomer(userId, orderNumber)
                .orElseThrow(() -> new NotFoundException("Order", orderNumber));
        Optional<Payment> existing = payments.findByOrderIdAndIdempotencyKey(order.id(), idempotencyKey);
        if (existing.isPresent()) {
            Payment p = existing.get();
            return new Attempt(p.getId(), false,
                    p.getStatus() == PaymentStatus.PENDING && p.getProviderReference() == null);
        }
        if (!order.isUnpaid()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_PAYABLE, "Order " + orderNumber + " is " + order.status(),
                    Map.of("status", order.status()));
        }
        if (clock.instant().isAfter(order.paymentDueAt())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_PAYABLE, "The payment window for this order has closed");
        }
        payments.findActiveByOrderId(order.id()).ifPresent(active -> {
            throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS, "A payment for this order is already "
                    + active.getStatus(), Map.of("paymentId", active.getId(), "status", active.getStatus().name()));
        });
        Payment payment = payments.saveAndFlush(Payment.start(order.id(), userId, provider, idempotencyKey,
                order.totalMinor(), order.currency(), clock.instant()));
        return new Attempt(payment.getId(), true, true);
    }

    @Transactional(readOnly = true)
    ProviderCall providerCall(long paymentId, String paymentToken) {
        Payment p = load(paymentId);
        return new ProviderCall(p.getProvider(), p.getProviderReference(), new PaymentRequest("commercex-pay-" + p.getId(),
                p.getId(), orders.ref(p.getOrderId()).map(OrderRef::orderNumber).orElse(null), p.getAmountMinor(),
                p.getCurrency(), paymentToken));
    }

    /**
     * Applies a provider result (from a direct call, a webhook or reconciliation). Idempotent. A success on
     * an order that can no longer be paid creates an automatic full refund.
     */
    @Transactional
    Applied apply(long paymentId, PaymentResult result) {
        Payment p = lockPayment(paymentId);
        if (result.status() == com.commercex.payment.provider.ProviderStatus.SUCCEEDED
                && (result.amountMinor() != p.getAmountMinor() || !p.getCurrency().equals(result.currency()))) {
            log.error("Payment {} amount mismatch: expected {} {}, provider reported {} {}", paymentId,
                    p.getAmountMinor(), p.getCurrency(), result.amountMinor(), result.currency());
            meters.counter("commercex.payments.anomalies", "type", "amount_mismatch").increment();
            auditLog.record(null, "PAYMENT_AMOUNT_MISMATCH", "payment", paymentId,
                    Map.of("expected", p.getAmountMinor(), "reported", result.amountMinor()));
            return new Applied("REJECTED_AMOUNT_MISMATCH", null);
        }
        Payment.Change change = p.apply(result, clock.instant());
        Long refundId = null;
        switch (change) {
            case SUCCEEDED -> {
                meters.counter("commercex.payments", "outcome", "succeeded").increment();
                if (!orders.markPaid(p.getOrderId(), p.getProviderReference())) {
                    meters.counter("commercex.payments.anomalies", "type", "late_payment").increment();
                    log.warn("Payment {} succeeded for an order that can no longer be paid; refunding", paymentId);
                    refundId = createRefund(p, "auto-late-payment", p.getAmountMinor(),
                            "Order was cancelled before the payment completed", null).getId();
                }
            }
            case FAILED -> {
                meters.counter("commercex.payments", "outcome", "failed").increment();
                orders.markPaymentFailed(p.getOrderId(), "Payment failed: " + p.getFailureCode());
            }
            case REQUIRES_ACTION, NONE -> {
            }
        }
        payments.flush();
        return new Applied(change.name(), refundId);
    }

    // ---- refunds -------------------------------------------------------------------------------------

    @Transactional
    long startRefund(Long actorId, long paymentId, Long requestedAmount, String reason, String idempotencyKey) {
        Payment p = lockPayment(paymentId);
        Optional<Refund> existing = refunds.findByPaymentIdAndIdempotencyKey(paymentId, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        if (p.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED, "Only succeeded payments can be refunded");
        }
        String orderStatus = orders.ref(p.getOrderId()).map(OrderRef::status).orElse("UNKNOWN");
        if (!REFUNDABLE_ORDER_STATUSES.contains(orderStatus)) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED,
                    "Orders that are " + orderStatus + " cannot be refunded; wait until delivered",
                    Map.of("orderStatus", orderStatus));
        }
        long refundable = p.refundableMinor() - refunds.pendingAmount(paymentId);
        long amount = requestedAmount == null ? refundable : requestedAmount;
        if (amount <= 0 || amount > refundable) {
            throw new ValidationException("amount", "must be between 1 and " + refundable);
        }
        Refund refund = createRefund(p, idempotencyKey, amount, reason, actorId);
        auditLog.record(actorId, "REFUND_REQUESTED", "payment", paymentId, Map.of("amount", amount));
        return refund.getId();
    }

    /** Null when the refund is no longer pending (nothing to send). */
    @Transactional(readOnly = true)
    RefundCall refundCall(long refundId) {
        Refund r = refunds.findById(refundId).orElseThrow(() -> new NotFoundException("Refund", refundId));
        if (r.getStatus() != Refund.Status.PENDING) {
            return null;
        }
        Payment p = load(r.getPaymentId());
        return new RefundCall(p.getProvider(), new RefundRequest("commercex-refund-" + r.getId(),
                p.getProviderReference(), r.getAmountMinor(), p.getCurrency()));
    }

    @Transactional
    String applyRefund(long refundId, RefundResult result) {
        long paymentId = refunds.findById(refundId).orElseThrow(() -> new NotFoundException("Refund", refundId))
                .getPaymentId();
        Payment p = lockPayment(paymentId); // payment before refund: same lock order as startRefund
        Refund r = refunds.findByIdForUpdate(refundId).orElseThrow();
        Instant now = clock.instant();
        switch (result.status()) {
            case SUCCEEDED -> {
                if (r.succeed(result.providerReference(), now)) {
                    p.recordRefund(r.getAmountMinor(), now);
                    meters.counter("commercex.refunds", "outcome", "succeeded").increment();
                    if (p.isFullyRefunded()) {
                        orders.markRefunded(p.getOrderId(), "Refunded: " + r.getReason());
                    }
                }
            }
            case FAILED -> {
                r.fail(result.failureMessage(), now);
                meters.counter("commercex.refunds", "outcome", "failed").increment();
            }
            case PENDING -> {
            }
        }
        payments.flush();
        return r.getStatus().name();
    }

    // ---- webhooks ------------------------------------------------------------------------------------

    /** Dedup record + state change in one transaction: a failure rolls both back so the provider retries. */
    @Transactional
    Applied processWebhook(String provider, WebhookEvent event, String rawPayload) {
        if (!webhookEvents.insertIfNew(provider, event.id(), event.type(), rawPayload)) {
            meters.counter("commercex.payments.webhooks", "outcome", "duplicate").increment();
            return new Applied("DUPLICATE", null);
        }
        Applied applied = switch (event.kind()) {
            case PAYMENT -> {
                Optional<Payment> payment = event.paymentId() == null ? Optional.empty()
                        : payments.findById(event.paymentId());
                if (payment.isEmpty()) {
                    yield new Applied("IGNORED_UNKNOWN_PAYMENT", null);
                }
                if (!payment.get().getProvider().equals(provider)) {
                    yield new Applied("REJECTED_PROVIDER_MISMATCH", null);
                }
                yield apply(payment.get().getId(), event.toPaymentResult());
            }
            case REFUND -> {
                if (event.refundId() == null || refunds.findById(event.refundId()).isEmpty()) {
                    yield new Applied("IGNORED_UNKNOWN_REFUND", null);
                }
                yield new Applied(applyRefund(event.refundId(), event.toRefundResult()), null);
            }
            case OTHER -> new Applied("IGNORED_TYPE", null);
        };
        webhookEvents.setOutcome(provider, event.id(), applied.outcome());
        meters.counter("commercex.payments.webhooks", "outcome", applied.outcome().toLowerCase()).increment();
        return applied;
    }

    // ---- reconciliation & views ----------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<Long> unsettledPaymentIds(Instant before, int limit) {
        return payments.findUnsettledIds(before, Limit.of(limit));
    }

    @Transactional(readOnly = true)
    List<Long> pendingRefundIds(Instant before, int limit) {
        return refunds.findPendingIds(before, Limit.of(limit));
    }

    @Transactional(readOnly = true)
    PaymentView view(long paymentId) {
        return PaymentView.from(load(paymentId));
    }

    @Transactional(readOnly = true)
    RefundView refundView(long refundId) {
        Refund r = refunds.findById(refundId).orElseThrow(() -> new NotFoundException("Refund", refundId));
        return RefundView.from(r, load(r.getPaymentId()).getCurrency());
    }

    @Transactional(readOnly = true)
    List<PaymentView> customerPayments(long userId, String orderNumber) {
        OrderRef order = orders.refForCustomer(userId, orderNumber)
                .orElseThrow(() -> new NotFoundException("Order", orderNumber));
        return payments.findByOrderIdOrderByCreatedAtAsc(order.id()).stream().map(PaymentView::from).toList();
    }

    @Transactional(readOnly = true)
    List<AdminPaymentView> staffPayments(String orderNumber) {
        OrderRef order = orders.refByNumber(orderNumber).orElseThrow(() -> new NotFoundException("Order", orderNumber));
        return payments.findByOrderIdOrderByCreatedAtAsc(order.id()).stream()
                .map(p -> new AdminPaymentView(PaymentView.from(p), p.getProvider(), p.getProviderReference(),
                        refunds.findByPaymentIdOrderByCreatedAtAsc(p.getId()).stream()
                                .map(r -> RefundView.from(r, p.getCurrency())).toList()))
                .toList();
    }

    private Refund createRefund(Payment p, String key, long amount, String reason, Long actorId) {
        return refunds.saveAndFlush(Refund.start(p.getId(), key, amount, reason, actorId, clock.instant()));
    }

    private Payment load(long paymentId) {
        return payments.findById(paymentId).orElseThrow(() -> new NotFoundException("Payment", paymentId));
    }

    private Payment lockPayment(long paymentId) {
        return payments.findByIdForUpdate(paymentId).orElseThrow(() -> new NotFoundException("Payment", paymentId));
    }
}
