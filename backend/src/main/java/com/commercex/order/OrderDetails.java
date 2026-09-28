package com.commercex.order;

import com.commercex.common.web.MoneyResponse;
import java.time.Instant;
import java.util.List;

/** Order as shown to its customer and to operations staff. */
public record OrderDetails(
        long id,
        String orderNumber,
        String status,
        List<Item> items,
        MoneyResponse subtotal,
        MoneyResponse discount,
        MoneyResponse shipping,
        MoneyResponse tax,
        MoneyResponse total,
        String couponCode,
        String shippingMethod,
        OrderAddress shippingAddress,
        String customerEmail,
        Instant placedAt,
        Instant paymentDueAt,
        List<StatusChange> history) {

    public record Item(long variantId, long productId, String productName, String variantLabel, String sku,
            String imageUrl, MoneyResponse unitPrice, int quantity, MoneyResponse lineTotal) {
    }

    public record StatusChange(String from, String to, String reason, Instant at) {
    }
}
