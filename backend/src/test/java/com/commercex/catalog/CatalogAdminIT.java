package com.commercex.catalog;

import static com.commercex.auth.AuthApi.uniqueEmail;
import static com.commercex.catalog.CatalogFixtures.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.auth.AuthApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class CatalogAdminIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    AuthApi.Session manager;
    CatalogFixtures catalog;
    long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        manager = new AuthApi(mvc, json).registerWithRoles(uniqueEmail("catmgr"), jdbc, "CATALOG_MANAGER");
        catalog = new CatalogFixtures(mvc, json, manager);
        categoryId = catalog.category(null, unique("admin-cat")).get("id").asLong();
    }

    @Test
    void createsDraftProductWithVariantsAndDerivedPrice() throws Exception {
        JsonNode product = catalog.product("Trail Runner", categoryId, null, "Grippy", "Black", 12_000, "Red", 9_900);

        assertThat(product.get("status").asText()).isEqualTo("DRAFT");
        assertThat(product.at("/price/amount").asLong()).isEqualTo(9_900);
        assertThat(product.at("/price/currency").asText()).isEqualTo("USD");
        assertThat(product.get("variants")).hasSize(2);
        assertThat(product.at("/variants/0/sku").asText()).isUpperCase();
    }

    @Test
    void updateMatchesVariantsBySkuAndDeactivatesMissingOnes() throws Exception {
        JsonNode product = catalog.product("Court Classic", categoryId, null, "x", "Black", 5_000, "White", 6_000);
        long id = product.get("id").asLong();
        JsonNode black = product.at("/variants/0");

        String body = """
                {"name":"Court Classic 2","categoryId":%d,"options":["Color"],"version":%d,
                 "variants":[{"sku":"%s","options":{"Color":"Black"},"price":4500,"compareAtPrice":5000}]}
                """.formatted(categoryId, product.get("version").asLong(), black.get("sku").asText());
        JsonNode updated = json.readTree(update(id, body).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(updated.at("/variants/0/id").asLong()).as("variant id is stable").isEqualTo(black.get("id").asLong());
        assertThat(updated.at("/variants/0/compareAtPrice/amount").asLong()).isEqualTo(5_000);
        assertThat(updated.at("/variants/1/active").asBoolean()).isFalse();
        assertThat(updated.at("/price/amount").asLong()).isEqualTo(4_500);
        assertThat(updated.get("slug").asText()).isEqualTo("court-classic-2");

        // same version again -> someone else's change would be overwritten
        update(id, body).andExpect(status().isConflict());
    }

    @Test
    void skuMustBeGloballyUnique() throws Exception {
        JsonNode first = catalog.product("First", categoryId, null, "x", "Black", 1_000);
        String takenSku = first.at("/variants/0/sku").asText();

        mvc.perform(post("/api/v1/admin/products").header("Authorization", manager.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Second","categoryId":%d,"options":["Color"],
                                 "variants":[{"sku":"%s","options":{"Color":"Black"},"price":1000}]}
                                """.formatted(categoryId, takenSku.toLowerCase())))
                .andExpect(status().isConflict());
    }

    @Test
    void validatesProductDocument() throws Exception {
        mvc.perform(post("/api/v1/admin/products").header("Authorization", manager.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","categoryId":%d,"options":["Color"],
                                 "images":[{"url":"javascript:alert(1)"}],
                                 "variants":[{"sku":"bad sku!","options":{"Color":"Black"},"price":0}]}
                                """.formatted(categoryId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'images[0].url')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'variants[0].sku')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'variants[0].price')]").exists());

        mvc.perform(post("/api/v1/admin/products").header("Authorization", manager.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mismatch","categoryId":%d,"options":["Color"],
                                 "variants":[{"sku":"MISMATCH-1","options":{"Size":"42"},"price":100}]}
                                """.formatted(categoryId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("variants[0].options"));

        mvc.perform(post("/api/v1/admin/products").header("Authorization", manager.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"No category","categoryId":%d,"options":[],
                                 "variants":[{"sku":"NOCAT-1","options":{},"price":100}]}
                                """.formatted(Long.MAX_VALUE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
    }

    @Test
    void lifecycleControlsPublicVisibility() throws Exception {
        JsonNode product = catalog.product("Lifecycle Shoe", categoryId, null, "x", "Black", 7_000);
        long id = product.get("id").asLong();
        String slug = product.get("slug").asText();

        mvc.perform(get("/api/v1/products/{slug}", slug)).andExpect(status().isNotFound());
        catalog.publish(id);
        mvc.perform(get("/api/v1/products/{slug}", slug)).andExpect(status().isOk());
        lifecycle(id, "unpublish").andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(get("/api/v1/products/{slug}", slug)).andExpect(status().isNotFound());
        lifecycle(id, "archive").andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get("/api/v1/products/{slug}", slug)).andExpect(status().isNotFound());
    }

    @Test
    void adminListFiltersByStatusAndName() throws Exception {
        String marker = unique("zebra");
        JsonNode draft = catalog.product(marker + " draft", categoryId, null, "x", "Black", 1_000);
        catalog.publishedProduct(marker + " live", categoryId, null, "x", "Black", 1_000);

        mvc.perform(get("/api/v1/admin/products").param("q", marker.toUpperCase()).param("status", "DRAFT")
                        .header("Authorization", manager.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(draft.get("id").asLong()));
    }

    @Test
    void brandRenameUpdatesSearchKeywords() throws Exception {
        String brandSlug = unique("acme");
        JsonNode brand = catalog.brand(brandSlug);
        long brandId = brand.get("id").asLong();
        catalog.publishedProduct("Plain Widget", categoryId, brandId, "nothing special", "Black", 1_000);

        String newName = unique("zorblax");
        mvc.perform(put("/api/v1/admin/brands/{id}", brandId).header("Authorization", manager.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"slug\":\"%s\",\"version\":%d}"
                                .formatted(newName, brandSlug, brand.get("version").asLong())))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/products").param("q", newName))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void customerCannotManageProducts() throws Exception {
        AuthApi.Session customer = new AuthApi(mvc, json).register(uniqueEmail("shopper"));
        mvc.perform(get("/api/v1/admin/products").header("Authorization", customer.bearer()))
                .andExpect(status().isForbidden());
    }

    private ResultActions update(long id, String body) throws Exception {
        return mvc.perform(put("/api/v1/admin/products/{id}", id).header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions lifecycle(long id, String action) throws Exception {
        return mvc.perform(post("/api/v1/admin/products/{id}/{action}", id, action)
                .header("Authorization", manager.bearer()));
    }
}
