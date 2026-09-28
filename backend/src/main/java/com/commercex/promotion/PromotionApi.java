package com.commercex.promotion;

import java.util.List;

/** Public API of the promotion module. */
public interface PromotionApi {

    /**
     * Checks a code against a basket without consuming it. {@code userId} may be null (guest): the
     * per-customer limit is then enforced only at checkout.
     */
    CouponEvaluation evaluate(String code, Long userId, List<DiscountLine> lines, long subtotalMinor);

    /**
     * Consumes one use of the coupon for the order and returns the discount. Must run inside the order's
     * transaction: usage limits are enforced atomically and rolled back with the order.
     *
     * @throws CouponInvalidException if the coupon can no longer be applied (e.g. its last use was just taken)
     */
    long redeem(String code, long userId, long orderId, List<DiscountLine> lines, long subtotalMinor);
}
