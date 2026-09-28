package com.commercex.promotion;

/**
 * Outcome of checking a code against a basket. {@code reason} is a stable code for the UI
 * (e.g. MIN_ORDER_NOT_MET), {@code message} a customer-facing explanation.
 */
public record CouponEvaluation(String code, boolean valid, long discountMinor, String reason, String message) {
}
