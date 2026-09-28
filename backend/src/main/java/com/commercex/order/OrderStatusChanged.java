package com.commercex.order;

/** Published inside the transaction that changed the status. */
public record OrderStatusChanged(long orderId, String orderNumber, long userId, String from, String to,
        String reason) {
}
