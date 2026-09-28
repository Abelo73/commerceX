package com.commercex.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.auth.AuthApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Builds catalog data through the admin API, as a catalog manager would. */
public final class CatalogFixtures {

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final AuthApi.Session manager;

    public CatalogFixtures(MockMvc mvc, ObjectMapper json, AuthApi.Session manager) {
        this.mvc = mvc;
        this.json = json;
        this.manager = manager;
    }

    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public JsonNode category(Long parentId, String slug) throws Exception {
        String body = """
                {"parentId":%s,"name":"%s","slug":"%s"}
                """.formatted(parentId, slug, slug);
        return postJson("/api/v1/admin/categories", body);
    }

    public JsonNode brand(String slug) throws Exception {
        return postJson("/api/v1/admin/brands", "{\"name\":\"%s\",\"slug\":\"%s\"}".formatted(slug, slug));
    }

    /** One-option product with one variant per (color, price) pair. */
    public JsonNode product(String name, long categoryId, Long brandId, String description, Object... colorPrice)
            throws Exception {
        StringBuilder variants = new StringBuilder();
        for (int i = 0; i < colorPrice.length; i += 2) {
            if (i > 0) {
                variants.append(',');
            }
            variants.append("{\"sku\":\"%s\",\"options\":{\"Color\":\"%s\"},\"price\":%d}"
                    .formatted(unique("SKU"), colorPrice[i], ((Number) colorPrice[i + 1]).longValue()));
        }
        String body = """
                {"name":"%s","slug":"%s","description":"%s","categoryId":%d,"brandId":%s,
                 "options":["Color"],"tags":["test"],
                 "images":[{"url":"https://cdn.example.com/%s.jpg","altText":"%s"}],
                 "variants":[%s]}
                """.formatted(name, unique("p"), description, categoryId, brandId, unique("img"), name, variants);
        return postJson("/api/v1/admin/products", body);
    }

    public JsonNode publish(long productId) throws Exception {
        return postJson("/api/v1/admin/products/" + productId + "/publish", "");
    }

    public JsonNode publishedProduct(String name, long categoryId, Long brandId, String description,
            Object... colorPrice) throws Exception {
        return publish(product(name, categoryId, brandId, description, colorPrice).get("id").asLong());
    }

    private JsonNode postJson(String path, String body) throws Exception {
        var request = post(path).header("Authorization", manager.bearer());
        if (!body.isEmpty()) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        String response = mvc.perform(request)
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }
}
