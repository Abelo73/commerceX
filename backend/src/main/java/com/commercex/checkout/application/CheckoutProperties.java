package com.commercex.checkout.application;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("commercex.checkout")
public record CheckoutProperties(List<ShippingMethod> shippingMethods) {

    /**
     * @param freeOverMinor subtotal (minor units) at or above which shipping is free; null = never free
     */
    public record ShippingMethod(String code, String name, long priceMinor, Long freeOverMinor, int minDays,
            int maxDays) {

        long priceFor(long subtotalMinor) {
            return freeOverMinor != null && subtotalMinor >= freeOverMinor ? 0 : priceMinor;
        }
    }
}
