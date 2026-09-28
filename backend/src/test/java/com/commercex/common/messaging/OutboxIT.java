package com.commercex.common.messaging;

import static com.commercex.checkout.CheckoutSupport.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commercex.AbstractIntegrationTest;
import com.commercex.checkout.CheckoutSupport;
import com.commercex.testsupport.ShopFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** TRD §11: business data and its events commit together; the relay then delivers them to Kafka. */
class OutboxIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ConsumerFactory<String, String> consumerFactory;

    ShopFixtures shop;
    CheckoutSupport buyer;
    long variant;

    @BeforeEach
    void setUp() throws Exception {
        shop = new ShopFixtures(mvc, json, jdbc);
        buyer = new CheckoutSupport(mvc, json, shop);
        variant = shop.product("Black", 2_000, 10).at("/variants/0/id").asLong();
    }

    @Test
    void orderAndItsEventCommitTogetherAndReachKafka() throws Exception {
        buyer.addToCart(variant, 1);
        JsonNode order = buyer.checkout();
        String orderId = order.get("id").asText();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE message_key = ? "
                + "AND event_type = 'OrderPlaced'", Integer.class, orderId)).isEqualTo(1);

        ConsumerRecord<String, String> record = awaitRecord(Topics.ORDERS, orderId, "OrderPlaced");
        JsonNode event = json.readTree(record.value());
        assertThat(event.at("/data/orderNumber").asText()).isEqualTo(order.get("orderNumber").asText());
        assertThat(event.at("/data/customerEmail").asText()).contains("@example.com");
        assertThat(new String(record.headers().lastHeader("eventId").value())).isEqualTo(event.get("eventId").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE message_key = ? "
                + "AND published_at IS NOT NULL", Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void rolledBackOrderLeavesNoEvent() throws Exception {
        buyer.addToCart(variant, 1);
        long before = jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type = 'OrderPlaced'",
                Long.class);

        buyer.placeOrder(newKey(), "STANDARD", 1).andExpect(status().isConflict()); // wrong expectedTotal

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type = 'OrderPlaced'",
                Long.class)).isEqualTo(before);
    }

    @Test
    void eventsContainNoSecrets() throws Exception {
        String email = com.commercex.auth.AuthApi.uniqueEmail("secret");
        shop.auth.register(email);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/auth/password-reset/request")
                .contentType("application/json").content("{\"email\":\"%s\"}".formatted(email)));

        List<String> payloads = jdbc.queryForList("SELECT payload::text FROM outbox_events WHERE payload::text LIKE ?",
                String.class, "%" + email + "%");
        assertThat(payloads).hasSize(1); // UserRegistered only; the reset link never leaves the process
        assertThat(payloads.get(0)).doesNotContain("token").doesNotContain("password");
    }

    private ConsumerRecord<String, String> awaitRecord(String topic, String key, String type) {
        // The Spring-configured factory knows the Testcontainers broker address.
        try (Consumer<String, String> consumer = consumerFactory.createConsumer("outbox-it-" + UUID.randomUUID(),
                null)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key()) && type.equals(typeOf(record))) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("No " + type + " event for key " + key + " on " + topic);
    }

    private String typeOf(ConsumerRecord<String, String> record) {
        try {
            return json.readTree(record.value()).path("type").asText();
        } catch (Exception e) {
            return null;
        }
    }
}
