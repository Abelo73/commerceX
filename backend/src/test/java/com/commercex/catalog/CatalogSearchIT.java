package com.commercex.catalog;

import static com.commercex.auth.AuthApi.uniqueEmail;
import static com.commercex.catalog.CatalogFixtures.unique;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.auth.AuthApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Each test builds its own category subtree and unique words, so results are isolated. */
class CatalogSearchIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    CatalogFixtures catalog;
    String rootSlug;
    long rootId;
    long childId;

    @BeforeEach
    void setUp() throws Exception {
        AuthApi.Session manager = new AuthApi(mvc, json).registerWithRoles(uniqueEmail("catmgr"), jdbc,
                "CATALOG_MANAGER");
        catalog = new CatalogFixtures(mvc, json, manager);
        rootSlug = unique("root");
        rootId = catalog.category(null, rootSlug).get("id").asLong();
        childId = catalog.category(rootId, unique("child")).get("id").asLong();
    }

    @Test
    void categoryFilterIncludesSubcategoriesAndHidesDrafts() throws Exception {
        catalog.publishedProduct("In Root", rootId, null, "x", "Black", 3_000);
        catalog.publishedProduct("In Child", childId, null, "x", "Black", 2_000);
        catalog.product("Draft In Child", childId, null, "x", "Black", 1_000);

        mvc.perform(get("/api/v1/products").param("category", rootSlug).param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=60")))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].name").value(contains("In Child", "In Root")))
                .andExpect(jsonPath("$.content[0].price.amount").value(2_000))
                .andExpect(jsonPath("$.content[0].category.id").value(childId))
                .andExpect(jsonPath("$.content[0].image.url").value(containsString("https://cdn.example.com/")));
    }

    @Test
    void fullTextSearchRanksNameAboveDescription() throws Exception {
        String word = unique("quokka").replace("-", "");
        catalog.publishedProduct("Plain thing", rootId, null, "mentions " + word + " in passing", "Black", 1_000);
        catalog.publishedProduct(word + " Deluxe", rootId, null, "top tier", "Black", 1_000);
        catalog.publishedProduct("Unrelated", rootId, null, "nothing", "Black", 1_000);

        mvc.perform(get("/api/v1/products").param("q", word))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value(word + " Deluxe"));
    }

    @Test
    void searchHandlesStemmingAndHostileInput() throws Exception {
        String word = unique("wombat").replace("-", "");
        catalog.publishedProduct(word + " running shoes", rootId, null, "x", "Black", 1_000);

        mvc.perform(get("/api/v1/products").param("q", word + " run"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/products").param("q", "'); DROP TABLE products; -- \" & | !"))
                .andExpect(status().isOk());
    }

    @Test
    void priceBrandAndSortFilters() throws Exception {
        String brandSlug = unique("brandx");
        long brandId = catalog.brand(brandSlug).get("id").asLong();
        catalog.publishedProduct("Cheap", rootId, brandId, "x", "Black", 1_000);
        catalog.publishedProduct("Mid", rootId, brandId, "x", "Black", 5_000, "Red", 4_000);
        catalog.publishedProduct("Pricey", rootId, null, "x", "Black", 9_000);

        mvc.perform(get("/api/v1/products").param("category", rootSlug)
                        .param("minPrice", "2000").param("maxPrice", "9000").param("sort", "price_desc"))
                .andExpect(jsonPath("$.content[*].name").value(contains("Pricey", "Mid")))
                .andExpect(jsonPath("$.content[1].price.amount").value(4_000));

        mvc.perform(get("/api/v1/products").param("category", rootSlug).param("brand", brandSlug))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].brand.slug").value(brandSlug));
    }

    @Test
    void paginationIsStableAndBounded() throws Exception {
        for (int i = 0; i < 5; i++) {
            catalog.publishedProduct("Paged " + i, rootId, null, "x", "Black", 1_000);
        }

        mvc.perform(get("/api/v1/products").param("category", rootSlug).param("size", "2").param("page", "2"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/api/v1/products").param("size", "500"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products").param("sort", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    @Test
    void unknownCategoryReturnsEmptyPage() throws Exception {
        mvc.perform(get("/api/v1/products").param("category", unique("nope")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void productDetailShowsActiveVariantsAndOptions() throws Exception {
        JsonNode product = catalog.publishedProduct("Detail Shoe", childId, null, "Great", "Black", 12_000,
                "White", 11_000);

        mvc.perform(get("/api/v1/products/{slug}", product.get("slug").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.amount").value(11_000))
                .andExpect(jsonPath("$.options[0].name").value("Color"))
                .andExpect(jsonPath("$.options[0].values").value(contains("Black", "White")))
                .andExpect(jsonPath("$.variants", hasSize(2)))
                .andExpect(jsonPath("$.variants[0].label").value("Black"))
                .andExpect(jsonPath("$.category.id").value(childId))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }
}
