package com.commercex.order;

import java.util.List;

/**
 * Fully priced order, computed by checkout from live catalog data. Amounts are in minor units and must
 * satisfy {@code total = subtotal - discount + shipping + tax} (enforced by the database too).
 */
public record PlaceOrderCommand(
        long userId,
        String customerEmail,
        String currency,
        List<Line> lines,
        long subtotalMinor,
        long discountMinor,
        long shippingMinor,
        long taxMinor,
        long totalMinor,
        String shippingMethod,
        OrderAddress shippingAddress) {

    public record Line(long variantId, long productId, String productName, String variantLabel, String sku,
            String imageUrl, long unitPriceMinor, int quantity) {
    }
}
