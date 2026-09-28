package com.commercex.payment;

import static com.commercex.payment.PaymentSupport.key;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.checkout.CheckoutSupport;
import com.commercex.payment.application.PaymentService;
import com.commercex.payment.provider.MockPaymentProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class PaymentFlowIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockPaymentProvider provider;

    @Autowired
    PaymentService paymentService;

    PaymentSupport support;

    @BeforeEach
    void setUp() throws Exception {
        support = new PaymentSupport(mvc, json, jdbc);
    }

    @Test
    void successfulPaymentMarksOrderPaidAndCommitsStock() throws Exception {
        JsonNode order = support.order(2);

        support.pay(number(order), "tok_success", key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.amount.amount").value(order.at("/total/amount").asLong()));

        assertThat(support.orderStatus(order)).isEqualTo("PAID");
        assertThat(support.reserved()).isZero();
        assertThat(support.onHand()).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = ? AND provider_reference "
                + "IS NOT NULL", Integer.class, order.get("id").asLong())).isEqualTo(1);
    }

    @Test
    void declinedPaymentCanBeRetriedWithNewKey() throws Exception {
        JsonNode order = support.order(1);

        support.pay(number(order), "tok_decline", key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("card_declined"));
        assertThat(support.orderStatus(order)).isEqualTo("PAYMENT_FAILED");
        assertThat(support.reserved()).as("stock held for the retry").isEqualTo(1);

        support.pay(number(order), "tok_success", key()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(support.orderStatus(order)).isEqualTo("PAID");
    }

    @Test
    void sameKeyReturnsSamePaymentWithoutChargingAgain() throws Exception {
        JsonNode order = support.order(1);
        String key = key();
        int callsBefore = provider.createCalls();

        JsonNode first = json.readTree(support.pay(number(order), "tok_success", key)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        JsonNode retry = json.readTree(support.pay(number(order), "tok_success", key)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(retry.get("id")).isEqualTo(first.get("id"));
        assertThat(provider.createCalls() - callsBefore).isEqualTo(1);
    }

    @Test
    void secondConcurrentPaymentForSameOrderIsRefused() throws Exception {
        JsonNode order = support.order(1);
        support.pay(number(order), "tok_3ds", key()).andExpect(jsonPath("$.status").value("REQUIRES_ACTION"));

        support.pay(number(order), "tok_success", key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));
    }

    @Test
    void paidCancelledAndForeignOrdersCannotBePaid() throws Exception {
        JsonNode order = support.order(1);
        support.pay(number(order), "tok_success", key()).andExpect(status().isCreated());
        support.pay(number(order), "tok_success", key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));

        CheckoutSupport stranger = new CheckoutSupport(mvc, json, support.shop);
        JsonNode other = support.order(1);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/orders/{n}/payments", number(other))
                        .header("Authorization", stranger.customer.bearer())
                        .header("Idempotency-Key", key())
                        .contentType("application/json").content("{\"paymentToken\":\"tok_success\"}"))
                .andExpect(status().isNotFound());

        support.pay(number(other), "tok_success", "short").andExpect(status().isBadRequest());
    }

    @Test
    void timeoutAfterChargingIsSettledByReconciliation() throws Exception {
        JsonNode order = support.order(1);

        support.pay(number(order), "tok_timeout", key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(support.orderStatus(order)).isEqualTo("PENDING_PAYMENT");

        paymentService.reconcile(Duration.ZERO);

        assertThat(support.orderStatus(order)).as("TRD §20: payment succeeded but our request timed out")
                .isEqualTo("PAID");
    }

    @Test
    void providerOutageLeavesNothingChargedAndReconciliationFailsThePayment() throws Exception {
        JsonNode order = support.order(1);

        JsonNode payment = support.payJson(number(order), "tok_unavailable");
        assertThat(payment.get("status").asText()).isEqualTo("PENDING");

        paymentService.reconcile(Duration.ZERO);

        mvc.perform(get("/api/v1/orders/{n}/payments", number(order)).header("Authorization",
                        support.buyer.customer.bearer()))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].failureCode").value("not_received"));
        support.pay(number(order), "tok_success", key()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void threeDSecureCompletesThroughReconciliation() throws Exception {
        JsonNode order = support.order(1);
        JsonNode payment = support.payJson(number(order), "tok_3ds");
        assertThat(payment.get("nextActionUrl").asText()).startsWith("https://mock-pay.local/3ds/");

        String reference = jdbc.queryForObject("SELECT provider_reference FROM payments WHERE id = ?", String.class,
                payment.get("id").asLong());
        provider.completeAction(reference, true);
        paymentService.reconcile(Duration.ZERO);

        assertThat(support.orderStatus(order)).isEqualTo("PAID");
    }

    @Test
    void latePaymentOnCancelledOrderIsRefundedAutomatically() throws Exception {
        JsonNode order = support.order(1);
        JsonNode payment = support.payJson(number(order), "tok_3ds");
        String reference = jdbc.queryForObject("SELECT provider_reference FROM payments WHERE id = ?", String.class,
                payment.get("id").asLong());
        // The order is cancelled while the customer is still in the 3-D Secure challenge.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/orders/{n}/cancel", number(order))
                        .header("Authorization", support.buyer.customer.bearer()))
                .andExpect(status().isOk());

        provider.completeAction(reference, true);
        paymentService.reconcile(Duration.ZERO);

        assertThat(support.orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE payment_id = ?", String.class,
                payment.get("id").asLong())).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT refunded_minor FROM payments WHERE id = ?", Long.class,
                payment.get("id").asLong())).isEqualTo(order.at("/total/amount").asLong());
    }

    private static String number(JsonNode order) {
        return order.get("orderNumber").asText();
    }
}
