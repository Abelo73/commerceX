package com.commercex.category;

import static com.commercex.auth.AuthApi.uniqueEmail;
import static com.commercex.catalog.CatalogFixtures.unique;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.auth.AuthApi;
import com.commercex.catalog.CatalogFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class CategoryIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    AuthApi.Session manager;
    CatalogFixtures catalog;

    @BeforeEach
    void setUp() throws Exception {
        manager = new AuthApi(mvc, json).registerWithRoles(uniqueEmail("catmgr"), jdbc, "CATALOG_MANAGER");
        catalog = new CatalogFixtures(mvc, json, manager);
    }

    @Test
    void publicTreeShowsOnlyActiveBranches() throws Exception {
        JsonNode root = catalog.category(null, unique("shoes"));
        JsonNode child = catalog.category(root.get("id").asLong(), unique("running"));
        JsonNode hidden = catalog.category(root.get("id").asLong(), unique("hidden"));
        update(hidden, "{\"parentId\":%d,\"name\":\"Hidden\",\"slug\":\"%s\",\"active\":false,\"version\":%d}"
                .formatted(root.get("id").asLong(), hidden.get("slug").asText(), hidden.get("version").asLong()))
                .andExpect(status().isOk());

        String rootPath = "$[?(@.slug == '%s')]".formatted(root.get("slug").asText());
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", Matchers.containsString("public")))
                .andExpect(jsonPath(rootPath + ".children[*].slug").value(Matchers.contains(child.get("slug").asText())));
    }

    @Test
    void slugIsDerivedAndMustBeUnique() throws Exception {
        String name = "Summer Sale " + unique("x");
        mvc.perform(post("/api/v1/admin/categories").header("Authorization", manager.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(Matchers.startsWith("summer-sale-x-")));
        mvc.perform(post("/api/v1/admin/categories").header("Authorization", manager.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isConflict());
    }

    @Test
    void cannotCreateCyclesOrExceedMaxDepth() throws Exception {
        JsonNode a = catalog.category(null, unique("a"));
        JsonNode b = catalog.category(a.get("id").asLong(), unique("b"));
        JsonNode c = catalog.category(b.get("id").asLong(), unique("c"));
        JsonNode d = catalog.category(c.get("id").asLong(), unique("d"));

        // move A under its grandchild C -> cycle
        update(a, "{\"parentId\":%d,\"name\":\"a\",\"slug\":\"%s\",\"version\":%d}"
                .formatted(c.get("id").asLong(), a.get("slug").asText(), a.get("version").asLong()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("parentId"));

        // 5th level
        mvc.perform(post("/api/v1/admin/categories").header("Authorization", manager.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":%d,\"name\":\"%s\"}".formatted(d.get("id").asLong(), unique("e"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        JsonNode cat = catalog.category(null, unique("versioned"));
        String body = "{\"name\":\"Renamed\",\"slug\":\"%s\",\"version\":%d}"
                .formatted(cat.get("slug").asText(), cat.get("version").asLong());

        update(cat, body).andExpect(status().isOk());
        update(cat, body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void cannotDeleteCategoryWithChildrenOrProducts() throws Exception {
        JsonNode parent = catalog.category(null, unique("parent"));
        JsonNode child = catalog.category(parent.get("id").asLong(), unique("child"));
        catalog.product("Thing", child.get("id").asLong(), null, "x", "Red", 1000);

        mvc.perform(delete("/api/v1/admin/categories/{id}", parent.get("id").asLong())
                        .header("Authorization", manager.bearer()))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/admin/categories/{id}", child.get("id").asLong())
                        .header("Authorization", manager.bearer()))
                .andExpect(status().isConflict());

        JsonNode empty = catalog.category(null, unique("empty"));
        mvc.perform(delete("/api/v1/admin/categories/{id}", empty.get("id").asLong())
                        .header("Authorization", manager.bearer()))
                .andExpect(status().isNoContent());
    }

    @Test
    void customersCannotManageCategories() throws Exception {
        AuthApi.Session customer = new AuthApi(mvc, json).register(uniqueEmail("shopper"));
        mvc.perform(post("/api/v1/admin/categories").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions update(JsonNode category, String body) throws Exception {
        return mvc.perform(put("/api/v1/admin/categories/{id}", category.get("id").asLong())
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
