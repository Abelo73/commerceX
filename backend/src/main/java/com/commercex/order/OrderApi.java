package com.commercex.order;

import java.util.Optional;

/**
 * Public API of the order module. The order module owns every stock interaction of an order:
 * placing reserves, paying commits, cancelling an unpaid order releases.
 */
public interface OrderApi {

    /** Creates a PENDING_PAYMENT order and reserves its stock. Must run inside the caller's transaction. */
    OrderDetails place(PlaceOrderCommand command);

    /**
     * Payment captured: PENDING_PAYMENT / PAYMENT_FAILED → PAID and stock committed. Idempotent: an order that
     * is already paid (or further along) is left unchanged.
     *
     * @throws com.commercex.common.error.ConflictException if the order was cancelled in the meantime
     */
    void markPaid(long orderId, String paymentReference);

    /** Payment attempt failed; the reservation is kept until the payment window closes. Idempotent. */
    void markPaymentFailed(long orderId, String reason);

    Optional<OrderDetails> find(long orderId);
}
