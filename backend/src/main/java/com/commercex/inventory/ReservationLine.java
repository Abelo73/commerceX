package com.commercex.inventory;

public record ReservationLine(long variantId, int quantity) {

    public ReservationLine {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
