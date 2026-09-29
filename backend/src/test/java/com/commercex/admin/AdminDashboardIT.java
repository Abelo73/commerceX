package com.commercex.admin;

import static com.commercex.auth.AuthApi.uniqueEmail;
import static com.commercex.catalog.CatalogFixtures.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.auth.AuthApi;
import com.commercex.catalog.CatalogFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** The database is shared with other tests, so figures are checked as deltas. */
class AdminDashboardIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    AuthApi auth;
    AuthApi.Session operations;

    @BeforeEach
    void setUp() throws Exception {
        auth = new AuthApi(mvc, json);
        operations = auth.registerWithRoles(uniqueEmail("ops"), jdbc, "OPERATIONS");
    }

    @Test
    void dashboardReflectsOrdersCustomersAndPayments() throws Exception {
        JsonNode before = dashboard(7);

        auth.register(uniqueEmail("newcustomer"));
        long paid = order("PAID", 12_000);
        order("SHIPPED", 8_000);
        order("PENDING_PAYMENT", 5_000);
        order("CANCELLED", 3_000);
        order("REFUNDED", 4_000);
        jdbc.update("""
                INSERT INTO payments (id, order_id, user_id, provider, status, amount_minor, currency, idempotency_key,
                                      created_at, updated_at)
                VALUES (nextval('payments_seq'), ?, ?, 'mock', 'FAILED', 12000, 'USD', gen_random_uuid()::text,
                        now(), now())""", paid, operations.userId());

        JsonNode after = dashboard(7);

        assertThat(delta(before, after, "revenueMinor")).isEqualTo(20_000);
        assertThat(delta(before, after, "ordersPlaced")).isEqualTo(5);
        assertThat(delta(before, after, "paidOrders")).isEqualTo(2);
        assertThat(delta(before, after, "awaitingFulfilment")).isEqualTo(1);
        assertThat(delta(before, after, "unpaidOrders")).isEqualTo(1);
        assertThat(delta(before, after, "newCustomers")).isEqualTo(1);
        assertThat(delta(before, after, "totalCustomers")).isEqualTo(1);
        assertThat(delta(before, after, "failedPayments")).isEqualTo(1);
        assertThat(after.get("currency").asText()).isEqualTo("USD");
        assertThat(after.get("averageOrderValueMinor").asLong())
                .isEqualTo(after.get("revenueMinor").asLong() / after.get("paidOrders").asLong());

        JsonNode days = after.get("revenueByDay");
        assertThat(days).hasSize(7);
        JsonNode today = days.get(6);
        assertThat(today.get("date").asText()).isEqualTo(LocalDate.now(ZoneOffset.UTC).toString());
        assertThat(today.get("revenueMinor").asLong() - before.at("/revenueByDay/6/revenueMinor").asLong())
                .isEqualTo(20_000);
        assertThat(today.get("orders").asLong() - before.at("/revenueByDay/6/orders").asLong()).isEqualTo(5);
    }

    @Test
    void recentActivityShowsStaffActionsWithActorEmail() throws Exception {
        long variantId = variant();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/inventory/{id}/adjustments", variantId)
                        .header("Authorization", operations.bearer())
                        .contentType("application/json")
                        .content("{\"type\":\"RESTOCK\",\"quantity\":3,\"reason\":\"count\"}"))
                .andExpect(status().isOk());

        JsonNode latest = dashboard(1).at("/recentActivity/0");
        assertThat(latest.get("action").asText()).isEqualTo("INVENTORY_ADJUSTED");
        assertThat(latest.get("resourceId").asText()).isEqualTo(String.valueOf(variantId));
        assertThat(latest.get("actorEmail").asText()).startsWith("ops");
    }

    @Test
    void stockRowsCarryProductNameAndSku() throws Exception {
        long variantId = variant();
        jdbc.update("INSERT INTO inventory_items (variant_id, on_hand, low_stock_threshold) VALUES (?, 2, 5)",
                variantId);
        long position = jdbc.queryForObject("""
                SELECT count(*) FROM inventory_items
                WHERE (on_hand - reserved, variant_id) < (SELECT on_hand - reserved, variant_id FROM inventory_items
                                                          WHERE variant_id = ?)""", Long.class, variantId);

        mvc.perform(get("/api/v1/admin/stock").param("size", "1").param("page", String.valueOf(position))
                        .header("Authorization", operations.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].variantId").value(variantId))
                .andExpect(jsonPath("$.content[0].productName").isNotEmpty())
                .andExpect(jsonPath("$.content[0].sku").isNotEmpty())
                .andExpect(jsonPath("$.content[0].available").value(2))
                .andExpect(jsonPath("$.content[0].lowStock").value(true));
    }

    @Test
    void onlyAdminAndOperationsSeeTheDashboard() throws Exception {
        AuthApi.Session customer = auth.register(uniqueEmail("shopper"));
        AuthApi.Session catalogManager = auth.registerWithRoles(uniqueEmail("catmgr"), jdbc, "CATALOG_MANAGER");

        mvc.perform(get("/api/v1/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", customer.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/stock").header("Authorization", catalogManager.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/dashboard").param("days", "91").header("Authorization", operations.bearer()))
                .andExpect(status().isBadRequest());
    }

    private JsonNode dashboard(int days) throws Exception {
        String body = mvc.perform(get("/api/v1/admin/dashboard").param("days", String.valueOf(days))
                        .header("Authorization", operations.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private static long delta(JsonNode before, JsonNode after, String field) {
        return after.get(field).asLong() - before.get(field).asLong();
    }

    private long order(String status, long totalMinor) {
        return jdbc.queryForObject("""
                INSERT INTO orders (id, order_number, user_id, customer_email, status, currency, subtotal_minor,
                                    shipping_minor, total_minor, shipping_method, shipping_address, payment_due_at,
                                    placed_at, created_at, updated_at)
                VALUES (nextval('orders_seq'), 'T-' || currval('orders_seq'), ?, 'test@example.com', ?, 'USD', ?,
                        0, ?, 'STANDARD', '{}', now() + interval '1 day', now(), now(), now())
                RETURNING id""", Long.class, operations.userId(), status, totalMinor, totalMinor);
    }

    private long variant() throws Exception {
        AuthApi.Session manager = auth.registerWithRoles(uniqueEmail("catmgr"), jdbc, "CATALOG_MANAGER");
        CatalogFixtures catalog = new CatalogFixtures(mvc, json, manager);
        long categoryId = catalog.category(null, unique("dash")).get("id").asLong();
        return catalog.publishedProduct(unique("Dash"), categoryId, null, "x", "Black", 1_000)
                .at("/variants/0/id").asLong();
    }
}
