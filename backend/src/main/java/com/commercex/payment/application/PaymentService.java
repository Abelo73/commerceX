package com.commercex.payment.application;

import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ErrorCode;
import com.commercex.common.error.ValidationException;
import com.commercex.common.idempotency.Idempotency;
import com.commercex.payment.application.PaymentViews.AdminPaymentView;
import com.commercex.payment.application.PaymentViews.PaymentView;
import com.commercex.payment.provider.PaymentProviderException;
import com.commercex.payment.provider.PaymentResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Payment orchestration. The provider is never called inside a database transaction:
 * <pre>
 * 1. record PENDING payment (tx)  →  2. provider.createPayment (no tx)  →  3. apply result (tx, row lock)
 * </pre>
 * If step 2 times out, the payment stays PENDING and is settled by a webhook or {@link #reconcile}.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_.:-]{8,255}");
    private static final int RECONCILE_BATCH = 100;

    public record PayResult(PaymentView payment, boolean created) {
    }

    private final PaymentLedger ledger;
    private final PaymentProviders providers;
    private final RefundService refundService;
    private final PaymentProperties properties;
    private final MeterRegistry meters;
    private final Clock clock;

    PaymentService(PaymentLedger ledger, PaymentProviders providers, RefundService refundService,
            PaymentProperties properties, MeterRegistry meters, Clock clock) {
        this.ledger = ledger;
        this.providers = providers;
        this.refundService = refundService;
        this.properties = properties;
        this.meters = meters;
        this.clock = clock;
    }

    /**
     * Pays an unpaid order. Idempotent per (order, key): a retry returns the same payment and, if the first
     * attempt never reached the provider, re-sends it with the same provider idempotency key.
     */
    public PayResult pay(long userId, String orderNumber, String idempotencyKey, String paymentToken) {
        requireKey(idempotencyKey);
        PaymentLedger.Attempt attempt;
        try {
            attempt = ledger.start(userId, orderNumber, idempotencyKey, providers.active().name());
        } catch (DataIntegrityViolationException concurrentAttempt) {
            throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS, "Another payment for this order is in progress");
        }
        if (attempt.callProvider()) {
            callProviderAndApply(attempt.paymentId(), paymentToken);
        }
        return new PayResult(ledger.view(attempt.paymentId()), attempt.created());
    }

    public List<PaymentView> customerPayments(long userId, String orderNumber) {
        return ledger.customerPayments(userId, orderNumber);
    }

    public List<AdminPaymentView> staffPayments(String orderNumber) {
        return ledger.staffPayments(orderNumber);
    }

    /**
     * Settles payments stuck in PENDING / REQUIRES_ACTION (lost responses, missed webhooks) and retries
     * pending refunds. Safe to run concurrently: every result is applied idempotently under a row lock.
     */
    public int reconcile(Duration minAge) {
        int settled = 0;
        for (long paymentId : ledger.unsettledPaymentIds(clock.instant().minus(minAge), RECONCILE_BATCH)) {
            PaymentLedger.ProviderCall call = ledger.providerCall(paymentId, null);
            PaymentResult result;
            try {
                result = call.providerReference() == null
                        ? providers.byName(call.provider()).createPayment(call.request()) // same key: no double charge
                        : providers.byName(call.provider()).getPayment(call.providerReference());
            } catch (PaymentProviderException e) {
                log.warn("Reconciliation of payment {} failed: {}", paymentId, e.getMessage());
                continue;
            }
            PaymentLedger.Applied applied = ledger.apply(paymentId, result);
            if (!"NONE".equals(applied.outcome())) {
                settled++;
            }
            executeRefund(applied);
        }
        for (long refundId : ledger.pendingRefundIds(clock.instant().minus(minAge), RECONCILE_BATCH)) {
            refundService.execute(refundId);
        }
        return settled;
    }

    @Scheduled(fixedDelayString = "${commercex.payments.reconcile-interval-ms:60000}")
    void scheduledReconcile() {
        int settled = reconcile(properties.reconcileAfter());
        if (settled > 0) {
            log.info("Reconciliation settled {} payments", settled);
        }
    }

    void executeRefund(PaymentLedger.Applied applied) {
        if (applied.refundToExecute() != null) {
            refundService.execute(applied.refundToExecute());
        }
    }

    private void callProviderAndApply(long paymentId, String paymentToken) {
        PaymentLedger.ProviderCall call = ledger.providerCall(paymentId, paymentToken);
        PaymentResult result;
        try {
            result = providers.byName(call.provider()).createPayment(call.request());
        } catch (PaymentProviderException e) {
            meters.counter("commercex.payments", "outcome", "unknown").increment();
            log.warn("Payment {} outcome unknown ({}); left PENDING for webhook/reconciliation", paymentId,
                    e.getMessage());
            return;
        }
        executeRefund(ledger.apply(paymentId, result));
    }

    static void requireKey(String key) {
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw new ValidationException(Idempotency.HEADER, "header is required: 8-255 characters of [A-Za-z0-9_.:-]");
        }
    }
}
