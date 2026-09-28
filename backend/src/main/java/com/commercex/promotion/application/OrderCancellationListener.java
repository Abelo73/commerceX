package com.commercex.promotion.application;

import com.commercex.order.OrderStatusChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** A cancelled order gives its coupon use back, in the same transaction as the cancellation. */
@Component
class OrderCancellationListener {

    private final PromotionService promotions;

    OrderCancellationListener(PromotionService promotions) {
        this.promotions = promotions;
    }

    @EventListener
    void on(OrderStatusChanged event) {
        if ("CANCELLED".equals(event.to())) {
            promotions.releaseForOrder(event.orderId());
        }
    }
}
