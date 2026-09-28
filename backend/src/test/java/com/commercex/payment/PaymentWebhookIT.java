package com.commercex.payment;

import static com.commercex.payment.PaymentSupport.paymentEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.payment.application.WebhookSignature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** TRD §20: "What happens if a webhook arrives twice?" and forged / replayed webhooks. */
class PaymentWebhookIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    PaymentSupport support;
    JsonNode order;
    long paymentId;
    long amount;

    @BeforeEach
    void setUp() throws Exception {
        support = new PaymentSupport(mvc, json, jdbc);
        order = support.order(1);
        // tok_pending: the provider accepted the payment; the outcome will arrive by webhook.
        paymentId = support.payJson(order.get("orderNumber").asText(), "tok_pending").get("id").asLong();
        amount = order.at("/total/amount").asLong();
    }

    @Test
    void succeededWebhookMarksOrderPaid() throws Exception {
        support.webhook(paymentEvent(eventId(), "payment.succeeded", paymentId, "ref", amount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SUCCEEDED"));

        assertThat(support.orderStatus(order)).isEqualTo("PAID");
    }

    @Test
    void duplicateDeliveryIsProcessedOnce() throws Exception {
        String body = paymentEvent(eventId(), "payment.succeeded", paymentId, "ref", amount);

        support.webhook(body).andExpect(jsonPath("$.outcome").value("SUCCEEDED"));
        support.webhook(body).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("DUPLICATE"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_status_history WHERE order_id = ? "
                + "AND to_status = 'PAID'", Integer.class, order.get("id").asLong())).isEqualTo(1);
        assertThat(support.onHand()).as("stock committed once").isEqualTo(9);
    }

    @Test
    void differentEventsForAnAlreadySettledPaymentChangeNothing() throws Exception {
        support.webhook(paymentEvent(eventId(), "payment.succeeded", paymentId, "ref", amount));
        support.webhook(paymentEvent(eventId(), "payment.failed", paymentId, "ref", amount))
                .andExpect(jsonPath("$.outcome").value("NONE"));

        assertThat(support.orderStatus(order)).isEqualTo("PAID");
    }

    @Test
    void forgedOrReplayedWebhooksAreRejected() throws Exception {
        String body = paymentEvent(eventId(), "payment.succeeded", paymentId, "ref", amount);

        support.webhook(body, "t=1,v1=deadbeef")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        support.webhook(body, WebhookSignature.sign("attacker-secret-0123456789-abcdefghijkl",
                Instant.now().getEpochSecond(), body)).andExpect(status().isUnauthorized());
        support.webhook(body, WebhookSignature.sign(PaymentSupport.SECRET,
                Instant.now().minusSeconds(3600).getEpochSecond(), body)).andExpect(status().isUnauthorized());
        support.webhook(body, null).andExpect(status().isUnauthorized());

        assertThat(support.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void amountMismatchIsNotTreatedAsPayment() throws Exception {
        support.webhook(paymentEvent(eventId(), "payment.succeeded", paymentId, "ref", amount - 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REJECTED_AMOUNT_MISMATCH"));

        assertThat(support.orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'PAYMENT_AMOUNT_MISMATCH' "
                + "AND resource_id = ?", Integer.class, String.valueOf(paymentId))).isEqualTo(1);
    }

    @Test
    void failedWebhookMarksPaymentFailedButKeepsStock() throws Exception {
        support.webhook("""
                {"id":"%s","type":"payment.failed","data":{"paymentId":%d,"reference":"ref","amount":%d,
                 "currency":"USD","failureCode":"insufficient_funds"}}""".formatted(eventId(), paymentId, amount))
                .andExpect(jsonPath("$.outcome").value("FAILED"));

        assertThat(support.orderStatus(order)).isEqualTo("PAYMENT_FAILED");
        assertThat(support.reserved()).isEqualTo(1);
    }

    @Test
    void unknownPaymentsAndTypesAreAcknowledged() throws Exception {
        support.webhook(paymentEvent(eventId(), "payment.succeeded", Long.MAX_VALUE, "ref", amount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED_UNKNOWN_PAYMENT"));
        support.webhook("{\"id\":\"%s\",\"type\":\"customer.created\",\"data\":{}}".formatted(eventId()))
                .andExpect(jsonPath("$.outcome").value("IGNORED_TYPE"));
        support.webhook("not json").andExpect(status().isBadRequest());
    }

    private static String eventId() {
        return "evt_" + UUID.randomUUID();
    }
}
