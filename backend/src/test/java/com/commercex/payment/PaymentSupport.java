package com.commercex.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.commercex.checkout.CheckoutSupport;
import com.commercex.payment.application.WebhookSignature;
import com.commercex.testsupport.ShopFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** A customer with an unpaid order, plus helpers to pay it and to send signed provider webhooks. */
final class PaymentSupport {

    static final String SECRET = "test-webhook-secret-0123456789-abcdefghijkl";

    final ShopFixtures shop;
    final CheckoutSupport buyer;
    final long variantId;
    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;

    PaymentSupport(MockMvc mvc, ObjectMapper json, JdbcTemplate jdbc) throws Exception {
        this.mvc = mvc;
        this.json = json;
        this.jdbc = jdbc;
        this.shop = new ShopFixtures(mvc, json, jdbc);
        this.buyer = new CheckoutSupport(mvc, json, shop);
        this.variantId = shop.product("Black", 4_000, 10).at("/variants/0/id").asLong();
    }

    static String key() {
        return "pay-" + UUID.randomUUID();
    }

    JsonNode order(int quantity) throws Exception {
        buyer.addToCart(variantId, quantity);
        return buyer.checkout();
    }

    ResultActions pay(String orderNumber, String token, String key) throws Exception {
        return mvc.perform(post("/api/v1/orders/{n}/payments", orderNumber)
                .header("Authorization", buyer.customer.bearer())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentToken\":\"%s\"}".formatted(token)));
    }

    JsonNode payJson(String orderNumber, String token) throws Exception {
        return json.readTree(pay(orderNumber, token, key()).andReturn().getResponse().getContentAsString());
    }

    ResultActions webhook(String body) throws Exception {
        return webhook(body, WebhookSignature.sign(SECRET, Instant.now().getEpochSecond(), body));
    }

    ResultActions webhook(String body, String signature) throws Exception {
        var request = post("/api/v1/payments/webhooks/mock").contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) {
            request.header(WebhookSignature.HEADER, signature);
        }
        return mvc.perform(request);
    }

    static String paymentEvent(String eventId, String type, long paymentId, String reference, long amount) {
        return """
                {"id":"%s","type":"%s","data":{"paymentId":%d,"reference":"%s","amount":%d,"currency":"USD"}}"""
                .formatted(eventId, type, paymentId, reference, amount);
    }

    String orderStatus(JsonNode order) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order.get("id").asLong());
    }

    int reserved() {
        return jdbc.queryForObject("SELECT reserved FROM inventory_items WHERE variant_id = ?", Integer.class,
                variantId);
    }

    int onHand() {
        return jdbc.queryForObject("SELECT on_hand FROM inventory_items WHERE variant_id = ?", Integer.class,
                variantId);
    }
}
