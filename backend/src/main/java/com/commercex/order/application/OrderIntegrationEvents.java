package com.commercex.order.application;

import com.commercex.common.messaging.Outbox;
import com.commercex.common.messaging.Topics;
import com.commercex.order.OrderPlaced;
import com.commercex.order.OrderStatusChanged;
import com.commercex.order.domain.Order;
import com.commercex.order.infrastructure.OrderRepository;
import java.time.Instant;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns order domain events into integration events on {@code commercex.orders}, written to the outbox in the
 * same transaction as the order change. Payloads carry what downstream consumers need (e.g. the customer email)
 * so they never have to call back into this module.
 */
@Component
class OrderIntegrationEvents {

    record OrderPlacedV1(long orderId, String orderNumber, long userId, String customerEmail, long totalMinor,
            String currency, int itemCount, Instant placedAt, Instant paymentDueAt) {
    }

    record OrderStatusChangedV1(long orderId, String orderNumber, long userId, String customerEmail, String from,
            String to, String reason, long totalMinor, String currency) {
    }

    private final OrderRepository orders;
    private final Outbox outbox;

    OrderIntegrationEvents(OrderRepository orders, Outbox outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    @EventListener
    void on(OrderPlaced event) {
        Order o = load(event.orderId());
        outbox.append(Topics.ORDERS, o.getId(), "OrderPlaced", new OrderPlacedV1(o.getId(), o.getOrderNumber(),
                o.getUserId(), o.getCustomerEmail(), o.getTotalMinor(), o.getCurrency(),
                o.getItems().stream().mapToInt(i -> i.getQuantity()).sum(), o.getPlacedAt(), o.getPaymentDueAt()));
    }

    @EventListener
    void on(OrderStatusChanged event) {
        Order o = load(event.orderId());
        outbox.append(Topics.ORDERS, o.getId(), "OrderStatusChanged", new OrderStatusChangedV1(o.getId(),
                o.getOrderNumber(), o.getUserId(), o.getCustomerEmail(), event.from(), event.to(), event.reason(),
                o.getTotalMinor(), o.getCurrency()));
    }

    private Order load(long orderId) {
        return orders.findById(orderId).orElseThrow();
    }
}
