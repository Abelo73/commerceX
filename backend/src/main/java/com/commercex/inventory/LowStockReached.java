package com.commercex.inventory;

/** Published when available stock drops to or below the variant's low-stock threshold. */
public record LowStockReached(long variantId, int available, int threshold) {
}
