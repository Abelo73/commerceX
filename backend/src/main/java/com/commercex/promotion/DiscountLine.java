package com.commercex.promotion;

/** A priced cart/order line as seen by coupon rules. */
public record DiscountLine(long productId, long categoryId, long lineTotalMinor) {
}
