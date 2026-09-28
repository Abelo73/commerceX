package com.commercex.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class InventoryLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InventoryApi inventory;

    @Autowired
    TransactionTemplate tx;

    InventoryTestSupport support;

    @BeforeEach
    void setUp() throws Exception {
        support = new InventoryTestSupport(mvc, json, jdbc, inventory, tx);
    }

    @Test
    void reservationIsAllOrNothing() throws Exception {
        long plenty = support.variantWithStock(5);
        long scarce = support.variantWithStock(1);

        assertThatThrownBy(() -> support.reserve(InventoryTestSupport.nextOrderId(),
                new ReservationLine(plenty, 2), new ReservationLine(scarce, 2)))
                .isInstanceOfSatisfying(InsufficientStockException.class, e -> {
                    assertThat(e.variantId()).isEqualTo(scarce);
                    assertThat(e.available()).isEqualTo(1);
                });

        assertThat(support.reserved(plenty)).as("first line rolled back").isZero();
    }

    @Test
    void retriedReservationForSameOrderIsNotDoubleCounted() throws Exception {
        long variantId = support.variantWithStock(5);
        long orderId = InventoryTestSupport.nextOrderId();

        support.reserve(orderId, new ReservationLine(variantId, 2));
        support.reserve(orderId, new ReservationLine(variantId, 2));

        assertThat(support.reserved(variantId)).isEqualTo(2);
    }

    @Test
    void duplicateLinesAreMerged() throws Exception {
        long variantId = support.variantWithStock(5);

        support.reserve(InventoryTestSupport.nextOrderId(), new ReservationLine(variantId, 1),
                new ReservationLine(variantId, 2));

        assertThat(support.reserved(variantId)).isEqualTo(3);
    }

    @Test
    void commitTurnsReservationIntoSaleExactlyOnce() throws Exception {
        long variantId = support.variantWithStock(5);
        long orderId = InventoryTestSupport.nextOrderId();
        support.reserve(orderId, new ReservationLine(variantId, 2));

        assertThat(commit(orderId)).isEqualTo(1);
        assertThat(commit(orderId)).isZero();

        assertThat(support.onHand(variantId)).isEqualTo(3);
        assertThat(support.reserved(variantId)).isZero();
        assertThat(support.count("SELECT sold FROM inventory_items WHERE variant_id = ?", variantId)).isEqualTo(2);
    }

    @Test
    void releaseReturnsStockExactlyOnceAndBlocksLaterCommit() throws Exception {
        long variantId = support.variantWithStock(5);
        long orderId = InventoryTestSupport.nextOrderId();
        support.reserve(orderId, new ReservationLine(variantId, 2));

        assertThat(release(orderId, "payment failed")).isEqualTo(1);
        assertThat(release(orderId, "payment failed")).isZero();
        assertThat(commit(orderId)).isZero();

        assertThat(support.reserved(variantId)).isZero();
        assertThat(support.onHand(variantId)).isEqualTo(5);
        assertThat(support.count("SELECT count(*) FROM inventory_movements WHERE variant_id = ? AND type = 'RELEASE'"
                + " AND reason = 'payment failed'", variantId)).isEqualTo(1);
    }

    @Test
    void reservationMustJoinCallersTransaction() throws Exception {
        long variantId = support.variantWithStock(5);

        assertThatThrownBy(() -> inventory.reserve(1L, List.of(new ReservationLine(variantId, 1)), Instant.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void stockLevelsDefaultToZeroForUnstockedVariants() throws Exception {
        long variantId = support.variantWithStock(0);

        StockLevel level = inventory.stockLevels(List.of(variantId)).get(variantId);

        assertThat(level.available()).isZero();
        assertThat(level.availability()).isEqualTo(StockLevel.Availability.OUT_OF_STOCK);
    }

    @Test
    void catalogStockFlagsFollowAvailability() throws Exception {
        JsonNode product = support.publishedProduct("Black", 1_000, "White", 1_200);
        String slug = product.get("slug").asText();
        long black = product.at("/variants/0/id").asLong();
        String categorySlug = jdbc.queryForObject("SELECT slug FROM categories WHERE id = ?", String.class,
                support.categoryId);

        inStockResults(categorySlug, 0);
        mvc.perform(get("/api/v1/products/{slug}", slug))
                .andExpect(jsonPath("$.inStock").value(false))
                .andExpect(jsonPath("$.variants[0].availability").value("OUT_OF_STOCK"));

        support.adjust(black, "RESTOCK", 8).andExpect(status().isOk());
        inStockResults(categorySlug, 1);
        mvc.perform(get("/api/v1/products").param("category", categorySlug))
                .andExpect(jsonPath("$.content[0].inStock").value(true));
        mvc.perform(get("/api/v1/products/{slug}", slug))
                .andExpect(jsonPath("$.inStock").value(true))
                .andExpect(jsonPath("$.variants[0].availability").value("IN_STOCK"))
                .andExpect(jsonPath("$.variants[1].availability").value("OUT_OF_STOCK"));

        long orderId = InventoryTestSupport.nextOrderId();
        support.reserve(orderId, new ReservationLine(black, 4));
        mvc.perform(get("/api/v1/products/{slug}", slug))
                .andExpect(jsonPath("$.variants[0].availability").value("LOW_STOCK")); // 4 left, threshold 5

        support.reserve(InventoryTestSupport.nextOrderId(), new ReservationLine(black, 4));
        inStockResults(categorySlug, 0);

        tx.executeWithoutResult(s -> inventory.release(orderId, "cancelled"));
        inStockResults(categorySlug, 1);
    }

    @Test
    void deactivatingTheOnlyStockedVariantClearsProductFlag() throws Exception {
        JsonNode product = support.publishedProduct("Black", 1_000, "White", 1_200);
        long productId = product.get("id").asLong();
        support.adjust(product.at("/variants/0/id").asLong(), "RESTOCK", 3).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT in_stock FROM products WHERE id = ?", Boolean.class, productId))
                .isTrue();

        // Keep only the unstocked White variant active.
        String body = """
                {"name":"%s","categoryId":%d,"options":["Color"],"version":%d,
                 "variants":[{"sku":"%s","options":{"Color":"White"},"price":1200}]}
                """.formatted(product.get("name").asText(), support.categoryId,
                jdbc.queryForObject("SELECT version FROM products WHERE id = ?", Long.class, productId),
                product.at("/variants/1/sku").asText());
        mvc.perform(put("/api/v1/admin/products/{id}", productId)
                        .header("Authorization", support.catalogManagerBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT in_stock FROM products WHERE id = ?", Boolean.class, productId))
                .isFalse();
    }

    private int commit(long orderId) {
        Integer lines = tx.execute(s -> inventory.commit(orderId));
        return lines == null ? -1 : lines;
    }

    private int release(long orderId, String reason) {
        Integer lines = tx.execute(s -> inventory.release(orderId, reason));
        return lines == null ? -1 : lines;
    }

    private void inStockResults(String categorySlug, int expected) throws Exception {
        mvc.perform(get("/api/v1/products").param("category", categorySlug).param("inStock", "true"))
                .andExpect(jsonPath("$.totalElements").value(expected));
    }
}
