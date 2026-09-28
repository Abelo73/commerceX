package com.commercex.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

/** Plain-text customer emails built from integration-event payloads. */
final class NotificationTemplates {

    private NotificationTemplates() {
    }

    static Optional<EmailMessage> orderPlaced(JsonNode d) {
        return Optional.of(new EmailMessage(d.path("customerEmail").asText(),
                "We received your order " + d.path("orderNumber").asText(),
                """
                Thank you for your order %s.

                Total: %s (%d item(s)).
                Please complete payment to confirm it; unpaid orders are released after the payment window.
                """.formatted(d.path("orderNumber").asText(), money(d), d.path("itemCount").asInt())));
    }

    /** One email per customer-relevant status; internal steps (PROCESSING, PACKED) send nothing. */
    static Optional<EmailMessage> orderStatusChanged(JsonNode d) {
        String number = d.path("orderNumber").asText();
        String to = d.path("customerEmail").asText();
        return switch (d.path("to").asText()) {
            case "PAID" -> Optional.of(new EmailMessage(to, "Payment received for order " + number,
                    "We received your payment of " + money(d) + ". We are preparing order " + number + ".\n"));
            case "PAYMENT_FAILED" -> Optional.of(new EmailMessage(to, "Payment failed for order " + number,
                    "Your payment for order " + number + " did not go through. You can try again from your account"
                            + " before the payment window closes.\n"));
            case "SHIPPED" -> Optional.of(new EmailMessage(to, "Order " + number + " has shipped",
                    "Good news: order " + number + " is on its way.\n"));
            case "DELIVERED" -> Optional.of(new EmailMessage(to, "Order " + number + " was delivered",
                    "Order " + number + " was delivered. We would love to hear what you think: leave a review from"
                            + " your account.\n"));
            case "CANCELLED" -> Optional.of(new EmailMessage(to, "Order " + number + " was cancelled",
                    "Order " + number + " was cancelled (" + d.path("reason").asText("") + ").\n"));
            case "REFUNDED" -> Optional.of(new EmailMessage(to, "Refund for order " + number,
                    "We refunded " + money(d) + " for order " + number + ". It may take a few days to appear.\n"));
            default -> Optional.empty();
        };
    }

    static EmailMessage welcome(JsonNode d) {
        return new EmailMessage(d.path("email").asText(), "Welcome to CommerceX",
                "Hi " + d.path("firstName").asText() + ",\n\nYour CommerceX account is ready.\n");
    }

    static EmailMessage lowStock(String operationsEmail, JsonNode d) {
        return new EmailMessage(operationsEmail, "Low stock: variant " + d.path("variantId").asLong(),
                "Variant " + d.path("variantId").asLong() + " has " + d.path("available").asInt()
                        + " unit(s) available (threshold " + d.path("threshold").asInt() + ").\n");
    }

    static String money(JsonNode d) {
        Currency currency = Currency.getInstance(d.path("currency").asText("USD"));
        BigDecimal amount = BigDecimal.valueOf(d.path("totalMinor").asLong(), currency.getDefaultFractionDigits());
        return currency.getCurrencyCode() + " " + amount.toPlainString();
    }
}
