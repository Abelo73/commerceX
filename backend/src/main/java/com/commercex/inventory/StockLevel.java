package com.commercex.inventory;

/** Customer-safe view of a variant's stock. */
public record StockLevel(long variantId, int available, int lowStockThreshold) {

    public enum Availability { IN_STOCK, LOW_STOCK, OUT_OF_STOCK }

    public Availability availability() {
        if (available <= 0) {
            return Availability.OUT_OF_STOCK;
        }
        return available <= lowStockThreshold ? Availability.LOW_STOCK : Availability.IN_STOCK;
    }

    public boolean canSatisfy(int quantity) {
        return available >= quantity;
    }
}
