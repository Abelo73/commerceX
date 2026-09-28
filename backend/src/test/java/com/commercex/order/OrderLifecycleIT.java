package com.commercex.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.auth.AuthApi;
import com.commercex.checkout.CheckoutSupport;
import com.commercex.order.application.OrderService;
import com.commercex.testsupport.ShopFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class OrderLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OrderApi orderApi;

    @Autowired
    OrderService orderService;

    ShopFixtures shop;
    CheckoutSupport buyer;
    long variant;

    @BeforeEach
    void setUp() throws Exception {
        shop = new ShopFixtures(mvc, json, jdbc);
        buyer = new CheckoutSupport(mvc, json, shop);
        variant = shop.product("Black", 2_500, 10).at("/variants/0/id").asLong();
    }

    @Test
    void customerSeesOnlyOwnOrders() throws Exception {
        JsonNode order = placeOrder(2);
        CheckoutSupport other = new CheckoutSupport(mvc, json, shop);

        mvc.perform(get("/api/v1/orders").header("Authorization", buyer.customer.bearer()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].itemCount").value(2));
        mvc.perform(get("/api/v1/orders/{n}", number(order)).header("Authorization", other.customer.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/orders/{n}/cancel", number(order)).header("Authorization", other.customer.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerCancellationReleasesStock() throws Exception {
        JsonNode order = placeOrder(3);
        assertThat(reserved()).isEqualTo(3);

        mvc.perform(post("/api/v1/orders/{n}/cancel", number(order)).header("Authorization", buyer.customer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.history[*].to").value(contains("PENDING_PAYMENT", "CANCELLED")));
        assertThat(reserved()).isZero();

        mvc.perform(post("/api/v1/orders/{n}/cancel", number(order)).header("Authorization", buyer.customer.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void paymentCommitsStockAndIsIdempotent() throws Exception {
        JsonNode order = placeOrder(2);
        long id = order.get("id").asLong();

        assertThat(orderApi.markPaid(id, "pay_123")).isTrue();
        assertThat(orderApi.markPaid(id, "pay_123")).as("duplicate notification").isTrue();

        assertThat(orderStatus(id)).isEqualTo("PAID");
        assertThat(reserved()).isZero();
        assertThat(onHand()).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_status_history WHERE order_id = ? "
                + "AND to_status = 'PAID'", Integer.class, id)).isEqualTo(1);

        mvc.perform(post("/api/v1/orders/{n}/cancel", number(order)).header("Authorization", buyer.customer.bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void failedPaymentKeepsReservationUntilExpiryThenCanStillBePaid() throws Exception {
        long id = placeOrder(1).get("id").asLong();

        orderApi.markPaymentFailed(id, "card declined");
        assertThat(orderStatus(id)).isEqualTo("PAYMENT_FAILED");
        assertThat(reserved()).isEqualTo(1);

        orderApi.markPaid(id, "pay_retry");
        assertThat(orderStatus(id)).isEqualTo("PAID");
    }

    @Test
    void staffMoveOrdersThroughFulfilment() throws Exception {
        JsonNode order = placeOrder(1);
        AuthApi.Session ops = shop.operations;
        orderApi.markPaid(order.get("id").asLong(), "pay_1");

        transition(ops, number(order), "SHIPPED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.from").value("PAID"));
        for (String next : new String[] {"PROCESSING", "PACKED", "SHIPPED", "DELIVERED"}) {
            transition(ops, number(order), next).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(next));
        }
        transition(ops, number(order), "PAID")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("toStatus"));

        mvc.perform(get("/api/v1/admin/orders").param("q", number(order).toLowerCase())
                        .header("Authorization", ops.bearer()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("DELIVERED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_status_history WHERE order_id = ?", Integer.class,
                order.get("id").asLong())).isEqualTo(6);
    }

    @Test
    void customersCannotUseStaffEndpoints() throws Exception {
        JsonNode order = placeOrder(1);

        transition(buyer.customer, number(order), "CANCELLED").andExpect(status().isForbidden());
        AuthApi.Session catalogManager = shop.manager;
        mvc.perform(get("/api/v1/admin/orders").header("Authorization", catalogManager.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void overdueUnpaidOrdersAreCancelledAndReleaseStock() throws Exception {
        long overdue = placeOrder(2).get("id").asLong();
        long fresh = placeOrder(1).get("id").asLong();
        jdbc.update("UPDATE orders SET payment_due_at = now() - interval '1 second' WHERE id = ?", overdue);

        while (orderService.cancelNextOverdueOrder()) {
            // drain everything overdue, including leftovers from other tests
        }

        assertThat(orderStatus(overdue)).isEqualTo("CANCELLED");
        assertThat(orderStatus(fresh)).isEqualTo("PENDING_PAYMENT");
        assertThat(reserved()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reason FROM order_status_history WHERE order_id = ? "
                + "AND to_status = 'CANCELLED'", String.class, overdue)).isEqualTo("Payment window expired");
    }

    @Test
    void expiryAndLatePaymentRaceHasExactlyOneWinner() throws Exception {
        long id = placeOrder(2).get("id").asLong();
        jdbc.update("UPDATE orders SET payment_due_at = now() - interval '1 second' WHERE id = ?", id);

        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Boolean> payment = CompletableFuture.supplyAsync(() -> {
            await(start);
            return orderApi.markPaid(id, "pay_late");
        });
        CompletableFuture<Void> expiry = CompletableFuture.runAsync(() -> {
            await(start);
            while (orderService.cancelNextOverdueOrder()) {
                // drain
            }
        });
        start.countDown();
        boolean paid = payment.get(30, TimeUnit.SECONDS);
        expiry.get(30, TimeUnit.SECONDS);

        String finalStatus = orderStatus(id);
        if (finalStatus.equals("PAID")) {
            assertThat(paid).isTrue();
            assertThat(onHand()).as("stock sold").isEqualTo(8);
        } else {
            assertThat(finalStatus).isEqualTo("CANCELLED");
            assertThat(paid).as("late payment rejected → caller refunds").isFalse();
            assertThat(onHand()).as("stock returned").isEqualTo(10);
        }
        assertThat(reserved()).isZero();
    }

    @Test
    void markPaidOnCancelledOrderIsRejected() throws Exception {
        JsonNode order = placeOrder(1);
        mvc.perform(post("/api/v1/orders/{n}/cancel", number(order)).header("Authorization", buyer.customer.bearer()))
                .andExpect(status().isOk());

        assertThat(orderApi.markPaid(order.get("id").asLong(), "pay_late")).isFalse();
        assertThat(orderStatus(order.get("id").asLong())).isEqualTo("CANCELLED");
    }

    private JsonNode placeOrder(int quantity) throws Exception {
        buyer.addToCart(variant, quantity);
        return buyer.checkout();
    }

    private ResultActions transition(AuthApi.Session session, String orderNumber, String to) throws Exception {
        return mvc.perform(post("/api/v1/admin/orders/{n}/transitions", orderNumber)
                .header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toStatus\":\"%s\",\"reason\":\"test\"}".formatted(to)));
    }

    private static String number(JsonNode order) {
        return order.get("orderNumber").asText();
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private int reserved() {
        return jdbc.queryForObject("SELECT reserved FROM inventory_items WHERE variant_id = ?", Integer.class, variant);
    }

    private int onHand() {
        return jdbc.queryForObject("SELECT on_hand FROM inventory_items WHERE variant_id = ?", Integer.class, variant);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
