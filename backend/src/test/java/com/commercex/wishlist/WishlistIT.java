package com.commercex.wishlist;

import static com.commercex.auth.AuthApi.uniqueEmail;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.auth.AuthApi;
import com.commercex.testsupport.ShopFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class WishlistIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    ShopFixtures shop;
    AuthApi.Session user;

    @BeforeEach
    void setUp() throws Exception {
        shop = new ShopFixtures(mvc, json, jdbc);
        user = shop.auth.register(uniqueEmail("wisher"));
    }

    @Test
    void addIsIdempotentAndListShowsProductCards() throws Exception {
        JsonNode product = shop.product("Black", 4_200, 3);
        long productId = product.get("id").asLong();

        save(productId).andExpect(status().isOk());
        save(productId)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value(product.get("name").asText()))
                .andExpect(jsonPath("$[0].price.amount").value(4_200))
                .andExpect(jsonPath("$[0].inStock").value(true))
                .andExpect(jsonPath("$[0].available").value(true));

        mvc.perform(delete("/api/v1/wishlist/{id}", productId).header("Authorization", user.bearer()))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/api/v1/wishlist/{id}", productId).header("Authorization", user.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void unpublishedProductsCannotBeAddedButStayListedAsUnavailable() throws Exception {
        JsonNode product = shop.product("Black", 1_000, 1);
        long productId = product.get("id").asLong();
        save(productId).andExpect(status().isOk());

        shop.unpublish(productId);

        mvc.perform(get("/api/v1/wishlist").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$[0].available").value(false))
                .andExpect(jsonPath("$[0].inStock").value(false));
        JsonNode other = shop.product("Black", 1_000, 1);
        shop.unpublish(other.get("id").asLong());
        save(other.get("id").asLong()).andExpect(status().isNotFound());
    }

    @Test
    void moveToCartTransfersTheChosenVariant() throws Exception {
        JsonNode product = shop.product("Black", 1_000, 5, "White", 1_100, 5);
        long productId = product.get("id").asLong();
        long white = product.at("/variants/1/id").asLong();
        save(productId);

        move(productId, white, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/cart").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.lines[0].variantId").value(white))
                .andExpect(jsonPath("$.lines[0].quantity").value(2));
    }

    @Test
    void failedMoveLeavesWishlistUntouched() throws Exception {
        JsonNode product = shop.product("Black", 1_000, 1);
        long productId = product.get("id").asLong();
        long variant = product.at("/variants/0/id").asLong();
        JsonNode otherProduct = shop.product("Black", 1_000, 1);
        save(productId);

        move(productId, variant, 5)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        move(productId, otherProduct.at("/variants/0/id").asLong(), 1)
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/wishlist").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void wishlistRequiresSignIn() throws Exception {
        mvc.perform(get("/api/v1/wishlist")).andExpect(status().isUnauthorized());
    }

    private ResultActions save(long productId) throws Exception {
        return mvc.perform(put("/api/v1/wishlist/{id}", productId).header("Authorization", user.bearer()));
    }

    private ResultActions move(long productId, long variantId, int quantity) throws Exception {
        return mvc.perform(post("/api/v1/wishlist/{id}/move-to-cart", productId)
                .header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\":%d,\"quantity\":%d}".formatted(variantId, quantity)));
    }
}
