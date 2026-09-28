package com.commercex.checkout.application;

import com.commercex.common.web.MoneyResponse;
import java.util.List;

public final class CheckoutViews {

    private CheckoutViews() {
    }

    public record ShippingOption(String code, String name, MoneyResponse price, int minDays, int maxDays) {
    }

    public record QuoteLine(long variantId, String productName, String variantLabel, int quantity,
            MoneyResponse unitPrice, MoneyResponse lineTotal, String issue) {
    }

    /**
     * Server-computed totals. The client shows {@code total} and sends it back as {@code expectedTotal}
     * when placing the order, so the customer never pays an amount they did not see.
     */
    public record Quote(
            List<QuoteLine> lines,
            MoneyResponse subtotal,
            MoneyResponse discount,
            MoneyResponse shipping,
            MoneyResponse tax,
            MoneyResponse total,
            String shippingMethod,
            List<ShippingOption> shippingOptions,
            boolean ready,
            List<String> problems) {
    }
}
