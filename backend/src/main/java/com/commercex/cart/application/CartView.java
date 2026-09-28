package com.commercex.cart.application;

import com.commercex.common.web.MoneyResponse;
import java.util.List;

/**
 * Cart as the customer sees it, priced from the live catalog. Lines with an {@code issue} are excluded
 * from the subtotal; {@code checkoutReady} is true only when there are lines and none has an issue.
 */
public record CartView(
        List<Line> lines,
        int itemCount,
        MoneyResponse subtotal,
        AppliedCoupon coupon,
        MoneyResponse discount,
        MoneyResponse total,
        boolean checkoutReady) {

    /**
     * The applied code is kept even when it stops qualifying (e.g. items removed below the minimum), so the
     * customer sees why: {@code valid=false}, zero discount, and a message. Shipping is added at checkout.
     */
    public record AppliedCoupon(String code, boolean valid, MoneyResponse discount, String reason, String message) {
    }

    /** UNAVAILABLE: no longer sold. OUT_OF_STOCK: none left. INSUFFICIENT_STOCK: fewer left than requested. */
    public enum Issue { UNAVAILABLE, OUT_OF_STOCK, INSUFFICIENT_STOCK }

    public record Line(
            long variantId,
            Long productId,
            String productSlug,
            String productName,
            String variantLabel,
            String sku,
            String imageUrl,
            int quantity,
            MoneyResponse unitPrice,
            MoneyResponse compareAtPrice,
            MoneyResponse lineTotal,
            Issue issue,
            Integer availableQuantity) {
    }
}
