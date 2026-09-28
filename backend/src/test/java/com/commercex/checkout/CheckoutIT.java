package com.commercex.checkout;

import static com.commercex.checkout.CheckoutSupport.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.testsupport.ShopFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class CheckoutIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    ShopFixtures shop;
    CheckoutSupport buyer;

    @BeforeEach
    void setUp() throws Exception {
        shop = new ShopFixtures(mvc, json, jdbc);
        buyer = new CheckoutSupport(mvc, json, shop);
    }

    @Test
    void quoteAppliesShippingRulesServerSide() throws Exception {
        long cheap = shop.product("Black", 2_000, 10).at("/variants/0/id").asLong();
        buyer.addToCart(cheap, 2);

        JsonNode standard = buyer.quote("STANDARD");
        assertThat(standard.at("/subtotal/amount").asLong()).isEqualTo(4_000);
        assertThat(standard.at("/shipping/amount").asLong()).isEqualTo(599);
        assertThat(standard.at("/total/amount").asLong()).isEqualTo(4_599);
        assertThat(standard.get("ready").asBoolean()).isTrue();

        buyer.addToCart(cheap, 3); // subtotal 10_000 → free standard shipping
        assertThat(buyer.quote("STANDARD").at("/shipping/amount").asLong()).isZero();
        assertThat(buyer.quote("EXPRESS").at("/shipping/amount").asLong()).isEqualTo(1_499);
    }

    @Test
    void placingAnOrderSnapshotsLinesReservesStockAndClearsCart() throws Exception {
        JsonNode product = shop.product("Black", 2_500, 5, "White", 3_000, 5);
        long black = product.at("/variants/0/id").asLong();
        long white = product.at("/variants/1/id").asLong();
        buyer.addToCart(black, 2);
        buyer.addToCart(white, 1);

        JsonNode order = buyer.checkout();

        assertThat(order.get("orderNumber").asText()).startsWith("CX-");
        assertThat(order.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.at("/subtotal/amount").asLong()).isEqualTo(8_000);
        assertThat(order.at("/total/amount").asLong()).isEqualTo(8_599);
        assertThat(order.get("items")).hasSize(2);
        assertThat(order.at("/items/0/productName").asText()).isEqualTo(product.get("name").asText());
        assertThat(order.at("/shippingAddress/city").asText()).isEqualTo("Springfield");
        assertThat(order.at("/history/0/to").asText()).isEqualTo("PENDING_PAYMENT");

        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_items WHERE variant_id = ?", Integer.class,
                black)).isEqualTo(2);
        mvc.perform(get("/api/v1/cart").header("Authorization", buyer.customer.bearer()))
                .andExpect(jsonPath("$.lines", hasSize(0)));
    }

    @Test
    void orderLinesDoNotChangeWhenTheCatalogDoes() throws Exception {
        JsonNode product = shop.product("Black", 2_500, 5);
        buyer.addToCart(product.at("/variants/0/id").asLong(), 1);
        JsonNode order = buyer.checkout();

        jdbc.update("UPDATE product_variants SET price_minor = 9999 WHERE id = ?",
                product.at("/variants/0/id").asLong());
        jdbc.update("UPDATE products SET name = 'Renamed' WHERE id = ?", product.get("id").asLong());

        mvc.perform(get("/api/v1/orders/{n}", order.get("orderNumber").asText())
                        .header("Authorization", buyer.customer.bearer()))
                .andExpect(jsonPath("$.items[0].unitPrice.amount").value(2_500))
                .andExpect(jsonPath("$.items[0].productName").value(product.get("name").asText()));
    }

    @Test
    void totalMismatchIsRejectedSoCustomersNeverPayAnUnseenAmount() throws Exception {
        long variant = shop.product("Black", 2_500, 5).at("/variants/0/id").asLong();
        buyer.addToCart(variant, 1);
        long quoted = buyer.quote("STANDARD").at("/total/amount").asLong();

        buyer.placeOrder(newKey(), "STANDARD", quoted - 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.details.actualTotal").value(quoted));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE user_id = ?", Integer.class,
                buyer.customer.userId())).isZero();
    }

    @Test
    void cartProblemsBlockCheckout() throws Exception {
        buyer.placeOrder(newKey(), "STANDARD", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECKOUT_NOT_READY"));

        JsonNode product = shop.product("Black", 2_500, 5);
        buyer.addToCart(product.at("/variants/0/id").asLong(), 1);
        shop.unpublish(product.get("id").asLong());

        buyer.placeOrder(newKey(), "STANDARD", 3_099)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECKOUT_NOT_READY"))
                .andExpect(jsonPath("$.details.problems", hasSize(1)));
    }

    @Test
    void insufficientStockAtCheckoutRollsBackEverything() throws Exception {
        long variant = shop.product("Black", 2_500, 3).at("/variants/0/id").asLong();
        buyer.addToCart(variant, 3);
        long total = buyer.quote("STANDARD").at("/total/amount").asLong();
        jdbc.update("UPDATE inventory_items SET reserved = 1 WHERE variant_id = ?", variant); // someone else won

        buyer.placeOrder(newKey(), "STANDARD", total)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE user_id = ?", Integer.class,
                buyer.customer.userId())).as("order rolled back").isZero();
        mvc.perform(get("/api/v1/cart").header("Authorization", buyer.customer.bearer()))
                .andExpect(jsonPath("$.lines", hasSize(1))); // cart kept so the customer can adjust it
    }

    @Test
    void addressMustBelongToTheCustomer() throws Exception {
        long variant = shop.product("Black", 2_500, 3).at("/variants/0/id").asLong();
        buyer.addToCart(variant, 1);
        CheckoutSupport stranger = new CheckoutSupport(mvc, json, shop);

        buyer.placeOrder(newKey(), stranger.addressId, "STANDARD", 3_099)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("addressId"));
        buyer.placeOrder(newKey(), "TELEPORT", 3_099)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("shippingMethod"));
    }

    @Test
    void checkoutRequiresSignIn() throws Exception {
        mvc.perform(get("/api/v1/checkout/shipping-methods")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/checkout/shipping-methods").header("Authorization", buyer.customer.bearer()))
                .andExpect(jsonPath("$[0].code").value("STANDARD"))
                .andExpect(jsonPath("$[0].price.amount").value(599));
        mvc.perform(get("/api/v1/orders").header("Authorization", buyer.customer.bearer()))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }
}
