package com.commercex.order;

/** Published inside the placing transaction; async consumers must use after-commit listeners. */
public record OrderPlaced(long orderId, String orderNumber, long userId, long totalMinor, String currency) {
}
