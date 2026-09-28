package com.commercex.inventory;

/**
 * Published synchronously, inside the changing transaction, when a variant crosses between
 * available > 0 and available = 0. Consumers must be cheap and must not call back into inventory.
 */
public record StockStatusChanged(long variantId, boolean inStock) {
}
