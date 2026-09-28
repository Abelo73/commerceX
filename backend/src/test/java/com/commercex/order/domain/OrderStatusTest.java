package com.commercex.order.domain;

import static com.commercex.order.domain.OrderStatus.CANCELLED;
import static com.commercex.order.domain.OrderStatus.DELIVERED;
import static com.commercex.order.domain.OrderStatus.PACKED;
import static com.commercex.order.domain.OrderStatus.PAID;
import static com.commercex.order.domain.OrderStatus.PAYMENT_FAILED;
import static com.commercex.order.domain.OrderStatus.PENDING_PAYMENT;
import static com.commercex.order.domain.OrderStatus.PROCESSING;
import static com.commercex.order.domain.OrderStatus.REFUNDED;
import static com.commercex.order.domain.OrderStatus.SHIPPED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderStatusTest {

    /** The full transition table from docs/domain-model.md; every other pair must be rejected. */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PENDING_PAYMENT, EnumSet.of(PAID, PAYMENT_FAILED, CANCELLED),
            PAYMENT_FAILED, EnumSet.of(PAID, CANCELLED),
            PAID, EnumSet.of(PROCESSING, REFUNDED),
            PROCESSING, EnumSet.of(PACKED, REFUNDED),
            PACKED, EnumSet.of(SHIPPED),
            SHIPPED, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.of(REFUNDED),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            REFUNDED, EnumSet.noneOf(OrderStatus.class));

    @Test
    void everyPairMatchesTheDocumentedTable() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                assertThat(from.canTransitionTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(ALLOWED.get(from).contains(to));
            }
        }
    }

    @Test
    void cannotSkipPaymentOrLeaveTerminalStates() {
        assertThat(PENDING_PAYMENT.canTransitionTo(SHIPPED)).isFalse();
        assertThat(CANCELLED.isTerminal()).isTrue();
        assertThat(REFUNDED.isTerminal()).isTrue();
        assertThat(DELIVERED.isTerminal()).isFalse();
    }

    @Test
    void staffCannotSetPaymentDrivenStatuses() {
        assertThat(OrderStatus.STAFF_TARGETS).doesNotContain(PAID, PAYMENT_FAILED, REFUNDED, PENDING_PAYMENT);
    }

    @Test
    void paymentClassification() {
        assertThat(PENDING_PAYMENT.isUnpaid()).isTrue();
        assertThat(PAYMENT_FAILED.isUnpaid()).isTrue();
        assertThat(PAID.isUnpaid()).isFalse();
        assertThat(SHIPPED.isPaidOrLater()).isTrue();
        assertThat(CANCELLED.isPaidOrLater()).isFalse();
    }
}
