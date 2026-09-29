package com.commercex.inventory;

import java.time.Instant;
import java.util.List;

public record StockPage(List<Item> items, int page, int size, long totalElements) {

    public record Item(long variantId, int onHand, int reserved, int available, long sold, int lowStockThreshold,
            Instant updatedAt) {
    }
}
