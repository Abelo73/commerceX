package com.commercex.inventory.application;

import com.commercex.common.messaging.Outbox;
import com.commercex.common.messaging.Topics;
import com.commercex.inventory.LowStockReached;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class InventoryIntegrationEvents {

    record LowStockReachedV1(long variantId, int available, int threshold) {
    }

    private final Outbox outbox;

    InventoryIntegrationEvents(Outbox outbox) {
        this.outbox = outbox;
    }

    @EventListener
    void on(LowStockReached event) {
        outbox.append(Topics.INVENTORY, event.variantId(), "LowStockReached",
                new LowStockReachedV1(event.variantId(), event.available(), event.threshold()));
    }
}
