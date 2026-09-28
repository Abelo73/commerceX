package com.commercex.promotion;

import static com.commercex.auth.AuthApi.uniqueEmail;
import static com.commercex.checkout.CheckoutSupport.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.auth.AuthApi;
import com.commercex.checkout.CheckoutSupport;
import com.commercex.testsupport.ShopFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class PromotionIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    ShopFixtures shop;
    AuthApi.Session admin;
    CheckoutSupport buyer;
    long variant;

    @BeforeEach
    void setUp() throws Exception {
        shop = new ShopFixtures(mvc, json, jdbc);
        admin = shop.auth.registerWithRoles(uniqueEmail("admin"), jdbc, "ADMIN");
        buyer = new CheckoutSupport(mvc, json, shop);
        variant = shop.product("Black", 5_000, 100).at("/variants/0/id").asLong();
    }

    @Test
    void couponDiscountsCartQuoteAndOrder() throws Exception {
        String code = coupon("{\"type\":\"PERCENTAGE\",\"value\":1000}");
        buyer.addToCart(variant, 2); // 10_000

        apply(buyer.customer, code.toLowerCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupon.code").value(code))
                .andExpect(jsonPath("$.coupon.valid").value(true))
                .andExpect(jsonPath("$.discount.amount").value(1_000))
                .andExpect(jsonPath("$.total.amount").value(9_000));

        JsonNode quote = buyer.quote("STANDARD");
        assertThat(quote.at("/discount/amount").asLong()).isEqualTo(1_000);
        assertThat(quote.at("/total/amount").asLong()).isEqualTo(9_000); // free shipping from 10_000 subtotal
        JsonNode order = buyer.checkout();

        assertThat(order.get("couponCode").asText()).isEqualTo(code);
        assertThat(order.at("/discount/amount").asLong()).isEqualTo(1_000);
        assertThat(order.at("/total/amount").asLong()).isEqualTo(9_000);
        assertThat(usedCount(code)).isEqualTo(1);
    }

    @Test
    void invalidCodesExplainWhy() throws Exception {
        buyer.addToCart(variant, 1);
        String minimum = coupon("{\"type\":\"FIXED\",\"value\":500,\"minOrderMinor\":20000}");
        String future = coupon("{\"type\":\"FIXED\",\"value\":500,\"startsAt\":\"%s\"}"
                .formatted(Instant.now().plusSeconds(3600)));

        apply(buyer.customer, "NOPE-" + UUID.randomUUID().toString().substring(0, 8))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COUPON_INVALID"))
                .andExpect(jsonPath("$.details.reason").value("NOT_FOUND"));
        apply(buyer.customer, minimum).andExpect(jsonPath("$.details.reason").value("MIN_ORDER_NOT_MET"));
        apply(buyer.customer, future).andExpect(jsonPath("$.details.reason").value("NOT_STARTED"));
    }

    @Test
    void appliedCouponThatStopsQualifyingIsShownAndBlocksCheckout() throws Exception {
        String code = coupon("{\"type\":\"FIXED\",\"value\":500,\"minOrderMinor\":10000}");
        buyer.addToCart(variant, 2);
        apply(buyer.customer, code).andExpect(status().isOk());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/cart/items/{id}", variant).header("Authorization", buyer.customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(jsonPath("$.coupon.valid").value(false))
                .andExpect(jsonPath("$.coupon.reason").value("MIN_ORDER_NOT_MET"))
                .andExpect(jsonPath("$.discount.amount").value(0));

        JsonNode quote = buyer.quote("STANDARD");
        assertThat(quote.get("ready").asBoolean()).isFalse();
        buyer.placeOrder(newKey(), "STANDARD", quote.at("/total/amount").asLong())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COUPON_INVALID"));

        mvc.perform(delete("/api/v1/cart/coupon").header("Authorization", buyer.customer.bearer()))
                .andExpect(jsonPath("$.coupon").doesNotExist());
    }

    @Test
    void categoryRestrictionIncludesSubcategories() throws Exception {
        long parent = shop.catalog.category(null, "promo-" + UUID.randomUUID().toString().substring(0, 8))
                .get("id").asLong();
        long child = shop.catalog.category(parent, "promo-" + UUID.randomUUID().toString().substring(0, 8))
                .get("id").asLong();
        JsonNode inChild = shop.catalog.publishedProduct("Child item", child, null, "x", "Black", 3_000);
        shop.restock(inChild.at("/variants/0/id").asLong(), 5);
        String code = coupon("{\"type\":\"PERCENTAGE\",\"value\":5000,\"categoryIds\":[%d]}".formatted(parent));

        buyer.addToCart(variant, 1);                                  // 5_000, not eligible
        buyer.addToCart(inChild.at("/variants/0/id").asLong(), 1);    // 3_000, eligible via subcategory
        apply(buyer.customer, code).andExpect(jsonPath("$.discount.amount").value(1_500));
    }

    @Test
    void singleUseCouponCannotBeRedeemedTwiceUnderConcurrency() throws Exception {
        String code = coupon("{\"type\":\"FIXED\",\"value\":1000,\"usageLimit\":1}");
        int buyers = 5;
        List<CheckoutSupport> customers = new ArrayList<>();
        List<Long> totals = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            CheckoutSupport c = new CheckoutSupport(mvc, json, shop);
            c.addToCart(variant, 1);
            apply(c.customer, code).andExpect(status().isOk());
            customers.add(c);
            totals.add(c.quote("STANDARD").at("/total/amount").asLong());
        }

        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            CheckoutSupport c = customers.get(i);
            long total = totals.get(i);
            results.add(pool.submit(() -> {
                start.await();
                return c.placeOrder(newKey(), "STANDARD", total).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int created = 0;
        for (Future<Integer> r : results) {
            int s = r.get(60, TimeUnit.SECONDS);
            assertThat(s).isIn(201, 422);
            created += s == 201 ? 1 : 0;
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(usedCount(code)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM coupon_redemptions r JOIN coupons c ON c.id = r.coupon_id "
                + "WHERE c.code = ?", Integer.class, code)).isEqualTo(1);
    }

    @Test
    void perCustomerLimitAndCancellationReturnsTheUse() throws Exception {
        String code = coupon("{\"type\":\"FIXED\",\"value\":1000,\"perCustomerLimit\":1,\"usageLimit\":10}");
        buyer.addToCart(variant, 1);
        apply(buyer.customer, code);
        JsonNode order = buyer.checkout();
        assertThat(usedCount(code)).isEqualTo(1);

        buyer.addToCart(variant, 1);
        apply(buyer.customer, code).andExpect(jsonPath("$.details.reason").value("CUSTOMER_LIMIT_REACHED"));

        mvc.perform(post("/api/v1/orders/{n}/cancel", order.get("orderNumber").asText())
                        .header("Authorization", buyer.customer.bearer()))
                .andExpect(status().isOk());
        assertThat(usedCount(code)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM coupon_redemptions WHERE order_id = ?", String.class,
                order.get("id").asLong())).isEqualTo("RELEASED");
        apply(buyer.customer, code).andExpect(status().isOk());
    }

    @Test
    void couponAdministration() throws Exception {
        String code = coupon("{\"type\":\"FIXED\",\"value\":1000}");
        long id = jdbc.queryForObject("SELECT id FROM coupons WHERE code = ?", Long.class, code);

        mvc.perform(post("/api/v1/admin/coupons").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\",\"type\":\"FIXED\",\"value\":1,\"startsAt\":\"%s\"}"
                                .formatted(code, Instant.now())))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/coupons").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BAD PCT\",\"type\":\"PERCENTAGE\",\"value\":20000,\"startsAt\":\"%s\"}"
                                .formatted(Instant.now())))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/coupons/{id}", id).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FIXED\",\"value\":1000,\"startsAt\":\"%s\",\"active\":false,\"version\":0}"
                                .formatted(Instant.now().minusSeconds(60))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        buyer.addToCart(variant, 1);
        apply(buyer.customer, code).andExpect(jsonPath("$.details.reason").value("INACTIVE"));

        mvc.perform(get("/api/v1/admin/coupons").header("Authorization", shop.manager.bearer()))
                .andExpect(status().isForbidden());
    }

    private String coupon(String fields) throws Exception {
        String code = "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String body = fields.substring(0, fields.length() - 1) + ",\"code\":\"" + code + "\""
                + (fields.contains("startsAt") ? "" : ",\"startsAt\":\"" + Instant.now().minusSeconds(60) + "\"") + "}";
        mvc.perform(post("/api/v1/admin/coupons").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return code;
    }

    private ResultActions apply(AuthApi.Session session, String code) throws Exception {
        return mvc.perform(put("/api/v1/cart/coupon").header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"%s\"}".formatted(code)));
    }

    private int usedCount(String code) {
        return jdbc.queryForObject("SELECT used_count FROM coupons WHERE code = ?", Integer.class, code);
    }
}
