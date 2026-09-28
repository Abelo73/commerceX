package com.commercex.notification;

import static com.commercex.auth.AuthApi.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import com.commercex.AbstractIntegrationTest;
import com.commercex.CapturingEmailSender;
import com.commercex.checkout.CheckoutSupport;
import com.commercex.common.messaging.IntegrationEvent;
import com.commercex.common.messaging.Topics;
import com.commercex.order.OrderApi;
import com.commercex.testsupport.ShopFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

class NotificationIT extends AbstractIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CapturingEmailSender emails;

    @Autowired
    OrderApi orderApi;

    @Autowired
    KafkaTemplate<String, String> kafka;

    ShopFixtures shop;

    @BeforeEach
    void setUp() throws Exception {
        shop = new ShopFixtures(mvc, json, jdbc);
    }

    @Test
    void customerIsEmailedThroughTheOrderLifecycle() throws Exception {
        CheckoutSupport buyer = new CheckoutSupport(mvc, json, shop);
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class,
                buyer.customer.userId());
        buyer.addToCart(shop.product("Black", 2_500, 5).at("/variants/0/id").asLong(), 1);
        JsonNode order = buyer.checkout();
        String number = order.get("orderNumber").asText();

        assertThat(emails.awaitMessage(email, "We received your order " + number, WAIT)).isPresent();

        orderApi.markPaid(order.get("id").asLong(), "pay");
        var paid = emails.awaitMessage(email, "Payment received for order " + number, WAIT);
        assertThat(paid).isPresent();
        assertThat(paid.get().textBody()).contains("USD 30.99"); // 25.00 + 5.99 shipping

        assertThat(emails.awaitMessage(email, "Welcome to CommerceX", WAIT)).isPresent();
    }

    @Test
    void duplicateDeliveryOfAnEventSendsOneEmail() throws Exception {
        String email = uniqueEmail("dupe");
        IntegrationEvent event = new IntegrationEvent(UUID.randomUUID(), "UserRegistered", Instant.now(),
                json.valueToTree(Map.of("userId", 1, "email", email, "firstName", "Dup")));
        String payload = json.writeValueAsString(event);

        kafka.send(Topics.USERS, "1", payload).get();
        kafka.send(Topics.USERS, "1", payload).get(); // at-least-once delivery → same event twice

        assertThat(emails.awaitMessage(email, "Welcome", WAIT)).isPresent();
        Thread.sleep(2_000); // give the duplicate time to be (not) processed
        assertThat(emails.sentTo(email, "Welcome")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class,
                event.eventId())).isEqualTo(1);
    }

    @Test
    void lowStockAlertsOperations() throws Exception {
        long variant = shop.product("Black", 1_000, 6).at("/variants/0/id").asLong(); // threshold 5
        CheckoutSupport buyer = new CheckoutSupport(mvc, json, shop);
        buyer.addToCart(variant, 2);
        buyer.checkout(); // available 6 → 4

        assertThat(emails.awaitMessage("ops@commercex.local", "Low stock: variant " + variant, WAIT)).isPresent();
    }
}
