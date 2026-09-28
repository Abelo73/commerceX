package com.commercex.payment;

import static com.commercex.auth.AuthApi.uniqueEmail;
import static com.commercex.payment.PaymentSupport.key;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.auth.AuthApi;
import com.commercex.payment.application.PaymentService;
import com.commercex.payment.provider.MockPaymentProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class RefundIT extends AbstractIntegrationTest {

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
    AuthApi.Session admin;
    JsonNode order;
    long paymentId;
    long total;

    @BeforeEach
    void setUp() throws Exception {
        support = new PaymentSupport(mvc, json, jdbc);
        admin = support.shop.auth.registerWithRoles(uniqueEmail("admin"), jdbc, "ADMIN");
        order = support.order(1);
        paymentId = support.payJson(order.get("orderNumber").asText(), "tok_success").get("id").asLong();
        total = order.at("/total/amount").asLong();
    }

    @Test
    void fullRefundMarksOrderRefundedAndIsIdempotent() throws Exception {
        String key = key();

        refund(admin, key, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.amount.amount").value(total));
        refund(admin, key, null).andExpect(jsonPath("$.status").value("SUCCEEDED"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM refunds WHERE payment_id = ?", Integer.class, paymentId))
                .isEqualTo(1);
        assertThat(support.orderStatus(order)).isEqualTo("REFUNDED");
        mvc.perform(get("/api/v1/admin/orders/{n}/payments", order.get("orderNumber").asText())
                        .header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$[0].payment.refunded.amount").value(total))
                .andExpect(jsonPath("$[0].refunds[0].status").value("SUCCEEDED"));
    }

    @Test
    void partialRefundsNeverExceedTheCapturedAmount() throws Exception {
        refund(admin, key(), 1_000L).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(support.orderStatus(order)).as("partial refund keeps the order").isEqualTo("PAID");

        refund(admin, key(), total).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
        refund(admin, key(), total - 1_000).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(support.orderStatus(order)).isEqualTo("REFUNDED");
        refund(admin, key(), 1L).andExpect(status().isBadRequest());
    }

    @Test
    void refundTimeoutIsRetriedByReconciliationWithoutDoubleRefund() throws Exception {
        provider.failNextRefund();

        refund(admin, key(), null).andExpect(jsonPath("$.status").value("PENDING"));
        paymentService.reconcile(Duration.ZERO);

        assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE payment_id = ?", String.class, paymentId))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT refunded_minor FROM payments WHERE id = ?", Long.class, paymentId))
                .isEqualTo(total);
    }

    @Test
    void shippedOrdersCannotBeRefundedUntilDelivered() throws Exception {
        String number = order.get("orderNumber").asText();
        for (String next : new String[] {"PROCESSING", "PACKED", "SHIPPED"}) {
            mvc.perform(post("/api/v1/admin/orders/{n}/transitions", number).header("Authorization", admin.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"toStatus\":\"%s\"}".formatted(next)))
                    .andExpect(status().isOk());
        }

        refund(admin, key(), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_NOT_ALLOWED"));
    }

    @Test
    void onlyAdminsRefund() throws Exception {
        refund(support.buyer.customer, key(), null).andExpect(status().isForbidden());
        refund(support.shop.operations, key(), null).andExpect(status().isForbidden());
        refund(admin, null, null).andExpect(status().isBadRequest());
    }

    private ResultActions refund(AuthApi.Session session, String key, Long amount) throws Exception {
        var request = post("/api/v1/admin/payments/{id}/refunds", paymentId)
                .header("Authorization", session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(amount == null ? "{\"reason\":\"customer request\"}"
                        : "{\"reason\":\"customer request\",\"amount\":%d}".formatted(amount));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request);
    }
}
