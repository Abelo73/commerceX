package com.commercex.admin.application;

import java.time.Instant;

/** Inventory row with the product data an operator needs to recognise it. */
public record StockRow(
        long variantId,
        Long productId,
        String productName,
        String sku,
        String label,
        String imageUrl,
        int onHand,
        int reserved,
        int available,
        long sold,
        int lowStockThreshold,
        boolean lowStock,
        Instant updatedAt) {
}
