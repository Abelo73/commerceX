package com.commercex.notification.application;

import com.commercex.common.messaging.IntegrationEvent;
import com.commercex.common.messaging.ProcessedEvents;
import com.commercex.common.messaging.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends customer and operations emails from integration events. Each event is processed once per consumer
 * ({@link ProcessedEvents}); a failure rolls back the marker so the redelivery is processed again.
 * (If the email was sent but the commit then fails, the customer may get it twice: at-least-once.)
 */
@Component
class EventNotificationConsumer {

    static final String CONSUMER = "notification-email";
    private static final Logger log = LoggerFactory.getLogger(EventNotificationConsumer.class);

    private final ProcessedEvents processed;
    private final EmailSender emailSender;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;
    private final String operationsEmail;

    EventNotificationConsumer(ProcessedEvents processed, EmailSender emailSender, ObjectMapper objectMapper,
            MeterRegistry meters, @Value("${commercex.notification.operations-email}") String operationsEmail) {
        this.processed = processed;
        this.emailSender = emailSender;
        this.objectMapper = objectMapper;
        this.meters = meters;
        this.operationsEmail = operationsEmail;
    }

    @KafkaListener(topics = {Topics.ORDERS, Topics.USERS, Topics.INVENTORY}, groupId = "commercex-notifications")
    @Transactional
    public void on(ConsumerRecord<String, String> record) throws JsonProcessingException {
        IntegrationEvent event = objectMapper.readValue(record.value(), IntegrationEvent.class);
        if (!processed.markIfNew(CONSUMER, event.eventId())) {
            meters.counter("commercex.notifications", "outcome", "duplicate").increment();
            return;
        }
        var message = switch (event.type()) {
            case "OrderPlaced" -> NotificationTemplates.orderPlaced(event.data());
            case "OrderStatusChanged" -> NotificationTemplates.orderStatusChanged(event.data());
            case "UserRegistered" -> Optional.of(NotificationTemplates.welcome(event.data()));
            case "LowStockReached" -> Optional.of(NotificationTemplates.lowStock(operationsEmail, event.data()));
            default -> Optional.<EmailMessage>empty();
        };
        message.ifPresent(m -> {
            emailSender.send(m);
            meters.counter("commercex.notifications", "outcome", "sent", "type", event.type()).increment();
            log.debug("Sent {} notification for event {}", event.type(), event.eventId());
        });
    }
}
