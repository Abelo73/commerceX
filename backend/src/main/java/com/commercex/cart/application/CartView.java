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
        boolean checkoutReady) {

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
