package com.commercex.testsupport;

import static com.commercex.auth.AuthApi.uniqueEmail;
import static com.commercex.catalog.CatalogFixtures.unique;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.auth.AuthApi;
import com.commercex.catalog.CatalogFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Published, stocked products created through the real admin APIs. */
public final class ShopFixtures {

    public final AuthApi auth;
    public final CatalogFixtures catalog;
    public final AuthApi.Session manager;
    public final AuthApi.Session operations;
    public final long categoryId;
    private final MockMvc mvc;

    public ShopFixtures(MockMvc mvc, ObjectMapper json, JdbcTemplate jdbc) throws Exception {
        this.mvc = mvc;
        this.auth = new AuthApi(mvc, json);
        this.manager = auth.registerWithRoles(uniqueEmail("catmgr"), jdbc, "CATALOG_MANAGER");
        this.operations = auth.registerWithRoles(uniqueEmail("ops"), jdbc, "OPERATIONS");
        this.catalog = new CatalogFixtures(mvc, json, manager);
        this.categoryId = catalog.category(null, unique("shop")).get("id").asLong();
    }

    /** Published product; each (color, price, stock) triple becomes a variant with that much stock. */
    public JsonNode product(Object... colorPriceStock) throws Exception {
        Object[] colorPrice = new Object[colorPriceStock.length / 3 * 2];
        for (int i = 0, j = 0; i < colorPriceStock.length; i += 3, j += 2) {
            colorPrice[j] = colorPriceStock[i];
            colorPrice[j + 1] = colorPriceStock[i + 1];
        }
        JsonNode product = catalog.publishedProduct(unique("Item"), categoryId, null, "x", colorPrice);
        for (int i = 0; i < colorPriceStock.length / 3; i++) {
            int stock = ((Number) colorPriceStock[i * 3 + 2]).intValue();
            if (stock > 0) {
                restock(product.at("/variants/" + i + "/id").asLong(), stock);
            }
        }
        return product;
    }

    public void restock(long variantId, int quantity) throws Exception {
        mvc.perform(post("/api/v1/admin/inventory/{id}/adjustments", variantId)
                        .header("Authorization", operations.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RESTOCK\",\"quantity\":%d}".formatted(quantity)))
                .andExpect(status().isOk());
    }

    public void unpublish(long productId) throws Exception {
        mvc.perform(post("/api/v1/admin/products/{id}/unpublish", productId)
                        .header("Authorization", manager.bearer()))
                .andExpect(status().isOk());
    }
}
