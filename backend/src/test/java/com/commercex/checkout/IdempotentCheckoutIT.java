package com.commercex.checkout;

import static com.commercex.checkout.CheckoutSupport.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** TRD §10 and §20: "What happens if the request is retried?" — exactly one order. */
class IdempotentCheckoutIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    ShopFixtures shop;
    CheckoutSupport buyer;
    long variant;
    long total;

    @BeforeEach
    void setUp() throws Exception {
        shop = new ShopFixtures(mvc, json, jdbc);
        buyer = new CheckoutSupport(mvc, json, shop);
        variant = shop.product("Black", 2_500, 10).at("/variants/0/id").asLong();
        buyer.addToCart(variant, 2);
        total = buyer.quote("STANDARD").at("/total/amount").asLong();
    }

    @Test
    void retryWithSameKeyReturnsTheOriginalOrder() throws Exception {
        String key = newKey();

        JsonNode first = json.readTree(buyer.placeOrder(key, "STANDARD", total)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andReturn().getResponse().getContentAsString());
        JsonNode replay = json.readTree(buyer.placeOrder(key, "STANDARD", total)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString());

        assertThat(replay).isEqualTo(first);
        assertThat(orders()).isEqualTo(1);
        assertThat(reserved()).as("stock reserved once").isEqualTo(2);
    }

    @Test
    void reusingKeyWithDifferentRequestIsRejected() throws Exception {
        String key = newKey();
        buyer.placeOrder(key, "STANDARD", total).andExpect(status().isCreated());

        buyer.placeOrder(key, "EXPRESS", total)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_MISMATCH"));
    }

    @Test
    void keyIsRequired() throws Exception {
        buyer.placeOrder(null, "STANDARD", total)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
        buyer.placeOrder("short", "STANDARD", total).andExpect(status().isBadRequest());
    }

    @Test
    void failedAttemptFreesTheKeyForARetry() throws Exception {
        String key = newKey();
        jdbc.update("UPDATE inventory_items SET reserved = 9 WHERE variant_id = ?", variant);

        buyer.placeOrder(key, "STANDARD", total).andExpect(status().isConflict());

        jdbc.update("UPDATE inventory_items SET reserved = 0 WHERE variant_id = ?", variant);
        buyer.placeOrder(key, "STANDARD", total)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
        assertThat(orders()).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateSubmissionsCreateExactlyOneOrder() throws Exception {
        String key = newKey();
        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return buyer.placeOrder(key, "STANDARD", total).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> r : results) {
            statuses.add(r.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        // The winner gets 201; the others either replay (201) or see it in progress (409). Never a second order.
        assertThat(statuses).allMatch(s -> s == 201 || s == 409);
        assertThat(statuses).contains(201);
        assertThat(orders()).isEqualTo(1);
        assertThat(reserved()).isEqualTo(2);
    }

    @Test
    void differentKeysAreDifferentOrders() throws Exception {
        buyer.placeOrder(newKey(), "STANDARD", total).andExpect(status().isCreated());
        buyer.addToCart(variant, 2);
        buyer.placeOrder(newKey(), "STANDARD", total).andExpect(status().isCreated());

        assertThat(orders()).isEqualTo(2);
    }

    private int orders() {
        return jdbc.queryForObject("SELECT count(*) FROM orders WHERE user_id = ?", Integer.class,
                buyer.customer.userId());
    }

    private int reserved() {
        return jdbc.queryForObject("SELECT reserved FROM inventory_items WHERE variant_id = ?", Integer.class,
                variant);
    }
}
