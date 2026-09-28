package com.commercex.inventory;

import static com.commercex.auth.AuthApi.uniqueEmail;
import static com.commercex.catalog.CatalogFixtures.unique;

import com.commercex.auth.AuthApi;
import com.commercex.catalog.CatalogFixtures;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates real variants through the catalog API and drives InventoryApi inside transactions. */
final class InventoryTestSupport {

    final CatalogFixtures catalog;
    final AuthApi.Session manager;
    final AuthApi.Session operations;
    final MockMvc mvc;
    final JdbcTemplate jdbc;
    final InventoryApi inventory;
    final TransactionTemplate tx;
    final long categoryId;

    InventoryTestSupport(MockMvc mvc, ObjectMapper json, JdbcTemplate jdbc, InventoryApi inventory,
            TransactionTemplate tx) throws Exception {
        this.manager = new AuthApi(mvc, json).registerWithRoles(uniqueEmail("catmgr"), jdbc,
                "CATALOG_MANAGER");
        this.catalog = new CatalogFixtures(mvc, json, manager);
        this.operations = new AuthApi(mvc, json).registerWithRoles(uniqueEmail("ops"), jdbc, "OPERATIONS");
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.inventory = inventory;
        this.tx = tx;
        this.categoryId = catalog.category(null, unique("inv")).get("id").asLong();
    }

    String catalogManagerBearer() {
        return manager.bearer();
    }

    /**
     * A bare PENDING_PAYMENT order row to hang reservations on, bypassing checkout. Taking a raw value from
     * {@code orders_seq} is safe alongside Hibernate's pooled optimizer.
     */
    long newOrderId() {
        return jdbc.queryForObject("""
                INSERT INTO orders (id, order_number, user_id, customer_email, status, currency, subtotal_minor,
                                    shipping_minor, total_minor, shipping_method, shipping_address, payment_due_at,
                                    placed_at, created_at, updated_at)
                VALUES (nextval('orders_seq'), 'T-' || currval('orders_seq'), ?, 'test@example.com',
                        'PENDING_PAYMENT', 'USD', 0, 0, 0, 'STANDARD', '{}', now() + interval '1 day',
                        now(), now(), now())
                RETURNING id""", Long.class, manager.userId());
    }

    JsonNode publishedProduct(Object... colorPrice) throws Exception {
        return catalog.publishedProduct(unique("Stocked"), categoryId, null, "x", colorPrice);
    }

    long variantWithStock(int onHand) throws Exception {
        long variantId = publishedProduct("Black", 1_000).at("/variants/0/id").asLong();
        if (onHand > 0) {
            jdbc.update("INSERT INTO inventory_items (variant_id, on_hand) VALUES (?, ?)", variantId, onHand);
        }
        return variantId;
    }

    /** Stock change through the admin API (the real path, including stock-status events). */
    ResultActions adjust(long variantId, String type, int quantity) throws Exception {
        return mvc.perform(post("/api/v1/admin/inventory/{id}/adjustments", variantId)
                .header("Authorization", operations.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"%s\",\"quantity\":%d,\"reason\":\"test\"}".formatted(type, quantity)));
    }

    void reserve(long orderId, ReservationLine... lines) {
        tx.executeWithoutResult(s -> inventory.reserve(orderId, List.of(lines),
                Instant.now().plus(30, ChronoUnit.MINUTES)));
    }

    int reserved(long variantId) {
        return jdbc.queryForObject("SELECT reserved FROM inventory_items WHERE variant_id = ?", Integer.class,
                variantId);
    }

    int onHand(long variantId) {
        return jdbc.queryForObject("SELECT on_hand FROM inventory_items WHERE variant_id = ?", Integer.class,
                variantId);
    }

    int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
