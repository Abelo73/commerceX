package com.commercex.payment.application;

import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ErrorCode;
import com.commercex.payment.application.PaymentViews.RefundView;
import com.commercex.payment.provider.PaymentProviderException;
import com.commercex.payment.provider.RefundResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Refund orchestration: record (tx) → provider call (no tx) → apply result (tx). */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final PaymentLedger ledger;
    private final PaymentProviders providers;

    RefundService(PaymentLedger ledger, PaymentProviders providers) {
        this.ledger = ledger;
        this.providers = providers;
    }

    /** Idempotent per (payment, key). {@code amount} null = everything still refundable. */
    public RefundView refund(long actorId, long paymentId, Long amount, String reason, String idempotencyKey) {
        PaymentService.requireKey(idempotencyKey);
        long refundId;
        try {
            refundId = ledger.startRefund(actorId, paymentId, amount, reason, idempotencyKey);
        } catch (DataIntegrityViolationException concurrentSameKey) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS, "This refund is already being created");
        }
        execute(refundId);
        return ledger.refundView(refundId);
    }

    /** Sends a pending refund to the provider. Unknown outcomes stay pending for reconciliation. */
    void execute(long refundId) {
        PaymentLedger.RefundCall call = ledger.refundCall(refundId);
        if (call == null) {
            return;
        }
        RefundResult result;
        try {
            result = providers.byName(call.provider()).refund(call.request());
        } catch (PaymentProviderException e) {
            log.warn("Refund {} outcome unknown ({}); will reconcile", refundId, e.getMessage());
            return;
        }
        ledger.applyRefund(refundId, result);
    }
}
