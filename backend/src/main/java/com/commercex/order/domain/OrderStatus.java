package com.commercex.order.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Order lifecycle (docs/domain-model.md). All transitions are defined here and nowhere else. */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAYMENT_FAILED,
    PAID,
    PROCESSING,
    PACKED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            PENDING_PAYMENT, EnumSet.of(PAID, PAYMENT_FAILED, CANCELLED),
            PAYMENT_FAILED, EnumSet.of(PAID, CANCELLED),
            PAID, EnumSet.of(PROCESSING, REFUNDED),
            PROCESSING, EnumSet.of(PACKED, REFUNDED),
            PACKED, EnumSet.of(SHIPPED),
            SHIPPED, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.of(REFUNDED),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            REFUNDED, EnumSet.noneOf(OrderStatus.class));

    /** Statuses operations staff may set by hand; PAID / PAYMENT_FAILED / REFUNDED are payment-driven. */
    public static final Set<OrderStatus> STAFF_TARGETS = EnumSet.of(PROCESSING, PACKED, SHIPPED, DELIVERED, CANCELLED);

    public boolean canTransitionTo(OrderStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** Payment has not been captured, so stock is still only reserved. */
    public boolean isUnpaid() {
        return this == PENDING_PAYMENT || this == PAYMENT_FAILED;
    }

    /** Payment captured at some point (includes later fulfilment stages). */
    public boolean isPaidOrLater() {
        return this == PAID || this == PROCESSING || this == PACKED || this == SHIPPED || this == DELIVERED;
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
