package com.commercex.review;

import static com.commercex.auth.AuthApi.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.auth.AuthApi;
import com.commercex.checkout.CheckoutSupport;
import com.commercex.order.OrderApi;
import com.commercex.testsupport.ShopFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
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

class ReviewIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OrderApi orderApi;

    ShopFixtures shop;
    AuthApi.Session admin;
    JsonNode product;
    long productId;
    String slug;

    @BeforeEach
    void setUp() throws Exception {
        shop = new ShopFixtures(mvc, json, jdbc);
        admin = shop.auth.registerWithRoles(uniqueEmail("admin"), jdbc, "ADMIN");
        product = shop.product("Black", 2_000, 50);
        productId = product.get("id").asLong();
        slug = product.get("slug").asText();
    }

    @Test
    void onlyCustomersWhoReceivedTheProductCanReview() throws Exception {
        CheckoutSupport notDelivered = new CheckoutSupport(mvc, json, shop);
        notDelivered.addToCart(product.at("/variants/0/id").asLong(), 1);
        orderApi.markPaid(notDelivered.checkout().get("id").asLong(), "pay");

        submit(notDelivered.customer, 5).andExpect(status().isForbidden());

        AuthApi.Session buyer = deliveredBuyer();
        submit(buyer, 5).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
        submit(buyer, 4).andExpect(status().isConflict());
    }

    @Test
    void onlyApprovedReviewsArePublicAndDriveTheRating() throws Exception {
        long five = id(submit(deliveredBuyer(), 5));
        long three = id(submit(deliveredBuyer(), 3));
        long rejected = id(submit(deliveredBuyer(), 1));

        publicReviews().andExpect(jsonPath("$.summary.count").value(0))
                .andExpect(jsonPath("$.reviews.content", hasSize(0)));

        moderate(five, "approve").andExpect(status().isOk());
        moderate(three, "approve").andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/reviews/{id}/reject", rejected).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"off-topic\"}"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        moderate(five, "approve").andExpect(status().isConflict());

        publicReviews()
                .andExpect(jsonPath("$.summary.count").value(2))
                .andExpect(jsonPath("$.summary.average").value(4.0))
                .andExpect(jsonPath("$.summary.distribution.5").value(1))
                .andExpect(jsonPath("$.reviews.content", hasSize(2)))
                .andExpect(jsonPath("$.reviews.content[0].authorName").value("Ada L."))
                .andExpect(jsonPath("$.reviews.content[0].verifiedPurchase").value(true));
        mvc.perform(get("/api/v1/products/{slug}", slug))
                .andExpect(jsonPath("$.rating").value(4.0))
                .andExpect(jsonPath("$.reviewCount").value(2));
    }

    @Test
    void editingSendsReviewBackToModerationAndDeletingRemovesIt() throws Exception {
        AuthApi.Session buyer = deliveredBuyer();
        long id = id(submit(buyer, 5));
        moderate(id, "approve");

        mvc.perform(put("/api/v1/reviews/{id}", id).header("Authorization", buyer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":2,\"title\":\"Changed my mind\"}"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        publicReviews().andExpect(jsonPath("$.summary.count").value(0));
        assertThat(rating()).isEqualTo(0.0);

        AuthApi.Session stranger = shop.auth.register(uniqueEmail("stranger"));
        mvc.perform(delete("/api/v1/reviews/{id}", id).header("Authorization", stranger.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/reviews").header("Authorization", buyer.bearer()))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(delete("/api/v1/reviews/{id}", id).header("Authorization", buyer.bearer()))
                .andExpect(status().isNoContent());
    }

    @Test
    void concurrentApprovalsKeepTheCountExact() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ids.add(id(submit(deliveredBuyer(), 4)));
        }
        ExecutorService pool = Executors.newFixedThreadPool(ids.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (long id : ids) {
            results.add(pool.submit(() -> {
                start.await();
                return moderate(id, "approve").andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> r : results) {
            assertThat(r.get(60, TimeUnit.SECONDS)).isEqualTo(200);
        }
        pool.shutdown();

        assertThat(jdbc.queryForObject("SELECT review_count FROM products WHERE id = ?", Integer.class, productId))
                .isEqualTo(6);
    }

    @Test
    void moderationIsAdminOnly() throws Exception {
        mvc.perform(get("/api/v1/admin/reviews").header("Authorization", shop.manager.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reviews").header("Authorization", admin.bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/{slug}/reviews", "no-such-product")).andExpect(status().isNotFound());
    }

    /** A new customer whose order for the product has gone all the way to DELIVERED. */
    private AuthApi.Session deliveredBuyer() throws Exception {
        CheckoutSupport buyer = new CheckoutSupport(mvc, json, shop);
        buyer.addToCart(product.at("/variants/0/id").asLong(), 1);
        JsonNode order = buyer.checkout();
        orderApi.markPaid(order.get("id").asLong(), "pay");
        for (String next : new String[] {"PROCESSING", "PACKED", "SHIPPED", "DELIVERED"}) {
            mvc.perform(post("/api/v1/admin/orders/{n}/transitions", order.get("orderNumber").asText())
                            .header("Authorization", shop.operations.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"toStatus\":\"%s\"}".formatted(next)))
                    .andExpect(status().isOk());
        }
        return buyer.customer;
    }

    private ResultActions submit(AuthApi.Session session, int rating) throws Exception {
        return mvc.perform(post("/api/v1/reviews").header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":%d,\"rating\":%d,\"title\":\"Great\",\"comment\":\"Would buy again\"}"
                        .formatted(productId, rating)));
    }

    private ResultActions moderate(long reviewId, String action) throws Exception {
        return mvc.perform(post("/api/v1/admin/reviews/{id}/{action}", reviewId, action)
                .header("Authorization", admin.bearer()));
    }

    private ResultActions publicReviews() throws Exception {
        return mvc.perform(get("/api/v1/products/{slug}/reviews", slug)).andExpect(status().isOk());
    }

    private long id(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private double rating() {
        return jdbc.queryForObject("SELECT rating_avg FROM products WHERE id = ?", Double.class, productId);
    }
}
