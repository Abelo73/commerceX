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
     * Payment captured: PENDING_PAYMENT / PAYMENT_FAILED → PAID and stock committed. Idempotent.
     * Returns {@code false} (and changes nothing) when the order can no longer be paid, e.g. it was
     * cancelled because the payment window closed: the caller must then refund the money. Never throws for
     * that case, so it can run inside the caller's transaction without poisoning it.
     */
    boolean markPaid(long orderId, String paymentReference);

    /** Payment attempt failed; the reservation is kept until the payment window closes. Idempotent. */
    void markPaymentFailed(long orderId, String reason);

    /**
     * Fully refunded: → REFUNDED if the lifecycle allows it. Returns {@code false} when it does not
     * (e.g. already refunded or cancelled). Stock is not returned automatically; returns are inspected
     * and restocked by staff.
     */
    boolean markRefunded(long orderId, String reason);

    Optional<OrderDetails> find(long orderId);

    Optional<OrderRef> ref(long orderId);

    /** The customer's own order by number; another customer's order is reported as absent. */
    Optional<OrderRef> refForCustomer(long userId, String orderNumber);

    Optional<OrderRef> refByNumber(String orderNumber);

    /** A DELIVERED order of this customer that contains the product (review eligibility). */
    Optional<Long> deliveredOrderContaining(long userId, long productId);
}
