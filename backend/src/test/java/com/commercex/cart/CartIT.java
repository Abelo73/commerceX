package com.commercex.cart;

import static com.commercex.auth.AuthApi.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.auth.AuthApi;
import com.commercex.cart.infrastructure.CartRepository;
import com.commercex.common.security.SecureTokens;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class CartIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CartRepository cartRepository;

    ShopFixtures shop;

    @BeforeEach
    void setUp() throws Exception {
        shop = new ShopFixtures(mvc, json, jdbc);
    }

    @Test
    void guestGetsTokenAndServerPricedCart() throws Exception {
        JsonNode product = shop.product("Black", 2_500, 10);
        long variantId = product.at("/variants/0/id").asLong();

        MvcResult result = add(null, null, variantId, 2)
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Cart-Token"))
                .andExpect(jsonPath("$.cart.lines[0].productName").value(product.get("name").asText()))
                .andExpect(jsonPath("$.cart.lines[0].unitPrice.amount").value(2_500))
                .andExpect(jsonPath("$.cart.lines[0].lineTotal.amount").value(5_000))
                .andExpect(jsonPath("$.cart.subtotal.amount").value(5_000))
                .andExpect(jsonPath("$.cart.itemCount").value(2))
                .andExpect(jsonPath("$.cart.checkoutReady").value(true))
                .andReturn();
        String token = json.readTree(result.getResponse().getContentAsString()).get("cartToken").asText();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM carts WHERE guest_token_hash = ?", Integer.class,
                SecureTokens.sha256(token))).as("only the token hash is stored").isEqualTo(1);
        mvc.perform(get("/api/v1/cart").header("X-Cart-Token", token))
                .andExpect(jsonPath("$.lines", hasSize(1)));
        mvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(0)))
                .andExpect(jsonPath("$.checkoutReady").value(false));

        // A second add with the token reuses the cart and does not issue a new token.
        add(null, token, variantId, 1)
                .andExpect(header().doesNotExist("X-Cart-Token"))
                .andExpect(jsonPath("$.cart.lines[0].quantity").value(3));
    }

    @Test
    void pricesAlwaysComeFromTheCatalog() throws Exception {
        JsonNode product = shop.product("Black", 2_500, 10);
        long variantId = product.at("/variants/0/id").asLong();
        String token = guestToken(add(null, null, variantId, 1));

        mvc.perform(put("/api/v1/admin/products/{id}", product.get("id").asLong())
                        .header("Authorization", shop.manager.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","categoryId":%d,"options":["Color"],"version":%d,
                                 "variants":[{"sku":"%s","options":{"Color":"Black"},"price":1999}]}
                                """.formatted(product.get("name").asText(), shop.categoryId,
                                jdbc.queryForObject("SELECT version FROM products WHERE id = ?", Long.class,
                                        product.get("id").asLong()),
                                product.at("/variants/0/sku").asText())))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/cart").header("X-Cart-Token", token))
                .andExpect(jsonPath("$.subtotal.amount").value(1_999));
    }

    @Test
    void addingRespectsStockAndLineLimits() throws Exception {
        long variantId = shop.product("Black", 1_000, 3).at("/variants/0/id").asLong();
        String token = guestToken(add(null, null, variantId, 2));

        add(null, token, variantId, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.details.available").value(3))
                .andExpect(jsonPath("$.details.requested").value(4));
        add(null, token, variantId, 100).andExpect(status().isBadRequest());
        add(null, token, variantId, 0).andExpect(status().isBadRequest());
        add(null, token, Long.MAX_VALUE, 1).andExpect(status().isNotFound());
    }

    @Test
    void cartFlagsProblemsInsteadOfSilentlyDroppingLines() throws Exception {
        JsonNode gone = shop.product("Black", 1_000, 5);
        JsonNode scarce = shop.product("Black", 2_000, 5);
        long goneVariant = gone.at("/variants/0/id").asLong();
        long scarceVariant = scarce.at("/variants/0/id").asLong();
        String token = guestToken(add(null, null, goneVariant, 1));
        add(null, token, scarceVariant, 4).andExpect(status().isOk());

        shop.unpublish(gone.get("id").asLong());
        jdbc.update("UPDATE inventory_items SET reserved = 3 WHERE variant_id = ?", scarceVariant); // 2 left

        mvc.perform(get("/api/v1/cart").header("X-Cart-Token", token))
                .andExpect(jsonPath("$.lines[0].issue").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.lines[1].issue").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.lines[1].availableQuantity").value(2))
                .andExpect(jsonPath("$.subtotal.amount").value(0))
                .andExpect(jsonPath("$.checkoutReady").value(false));

        add(null, token, goneVariant, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VARIANT_UNAVAILABLE"));

        mvc.perform(withToken(patch("/api/v1/cart/items/{id}", scarceVariant), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(jsonPath("$.lines[1].issue").doesNotExist());
        mvc.perform(withToken(delete("/api/v1/cart/items/{id}", goneVariant), token))
                .andExpect(jsonPath("$.checkoutReady").value(true))
                .andExpect(jsonPath("$.subtotal.amount").value(4_000));
    }

    @Test
    void updateAndRemoveRequireAnExistingLine() throws Exception {
        long variantId = shop.product("Black", 1_000, 5).at("/variants/0/id").asLong();
        String token = guestToken(add(null, null, variantId, 1));

        mvc.perform(withToken(patch("/api/v1/cart/items/{id}", 999_999_999L), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(withToken(delete("/api/v1/cart/items/{id}", 999_999_999L), token))
                .andExpect(status().isNotFound());
        mvc.perform(withToken(delete("/api/v1/cart"), token))
                .andExpect(jsonPath("$.lines", hasSize(0)));
    }

    @Test
    void mergeFoldsGuestCartIntoUserCart() throws Exception {
        long a = shop.product("Black", 1_000, 20).at("/variants/0/id").asLong();
        long b = shop.product("Black", 2_000, 20).at("/variants/0/id").asLong();
        String token = guestToken(add(null, null, a, 2));

        AuthApi.Session user = shop.auth.register(uniqueEmail("merger"));
        add(user, null, a, 1).andExpect(status().isOk());
        add(user, null, b, 1).andExpect(status().isOk());

        mvc.perform(post("/api/v1/cart/merge").header("X-Cart-Token", token)
                        .header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.lines[?(@.variantId == %d)].quantity".formatted(a)).value(3))
                .andExpect(jsonPath("$.subtotal.amount").value(5_000));

        mvc.perform(get("/api/v1/cart").header("X-Cart-Token", token))
                .andExpect(jsonPath("$.lines", hasSize(0)));
        mvc.perform(post("/api/v1/cart/merge").header("X-Cart-Token", token)
                        .header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.subtotal.amount").value(5_000)); // repeat is harmless
        mvc.perform(post("/api/v1/cart/merge").header("X-Cart-Token", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedInUserCartIgnoresGuestToken() throws Exception {
        long variantId = shop.product("Black", 1_000, 20).at("/variants/0/id").asLong();
        String token = guestToken(add(null, null, variantId, 5));
        AuthApi.Session user = shop.auth.register(uniqueEmail("user"));

        mvc.perform(get("/api/v1/cart").header("X-Cart-Token", token).header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.lines", hasSize(0)));
    }

    @Test
    void concurrentAddsFromTwoTabsAreNotLost() throws Exception {
        long variantId = shop.product("Black", 1_000, 50).at("/variants/0/id").asLong();
        AuthApi.Session user = shop.auth.register(uniqueEmail("tabs"));

        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return add(user, null, variantId, 1).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> r : results) {
            assertThat(r.get(60, TimeUnit.SECONDS)).isEqualTo(200);
        }
        pool.shutdown();

        mvc.perform(get("/api/v1/cart").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].quantity").value(10));
    }

    @Test
    void expiredGuestCartsDisappear() throws Exception {
        long variantId = shop.product("Black", 1_000, 5).at("/variants/0/id").asLong();
        String token = guestToken(add(null, null, variantId, 1));
        jdbc.update("UPDATE carts SET expires_at = now() - interval '1 minute' WHERE guest_token_hash = ?",
                SecureTokens.sha256(token));

        mvc.perform(get("/api/v1/cart").header("X-Cart-Token", token))
                .andExpect(jsonPath("$.lines", hasSize(0)));
        assertThat(cartRepository.deleteExpiredGuestCarts()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM carts WHERE guest_token_hash = ?", Integer.class,
                SecureTokens.sha256(token))).isZero();
    }

    private ResultActions add(AuthApi.Session user, String token, long variantId, int quantity) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\":%d,\"quantity\":%d}".formatted(variantId, quantity));
        if (user != null) {
            request.header("Authorization", user.bearer());
        }
        return mvc.perform(withToken(request, token));
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header("X-Cart-Token", token);
    }

    private String guestToken(ResultActions actions) throws Exception {
        return json.readTree(actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("cartToken").asText();
    }
}
