package com.commercex.payment.application;

import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ErrorCode;
import com.commercex.common.error.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Provider webhooks: verify signature → dedupe by event ID → apply (one transaction) → send any
 * automatic refund. Delivery is at-least-once; processing is exactly-once.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final PaymentLedger ledger;
    private final PaymentService paymentService;
    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;
    private final Clock clock;

    WebhookService(PaymentLedger ledger, PaymentService paymentService, PaymentProperties properties,
            ObjectMapper objectMapper, MeterRegistry meters, Clock clock) {
        this.ledger = ledger;
        this.paymentService = paymentService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meters = meters;
        this.clock = clock;
    }

    /** Returns the processing outcome, e.g. SUCCEEDED, DUPLICATE, IGNORED_UNKNOWN_PAYMENT. */
    public String handle(String provider, String rawBody, String signatureHeader) {
        if (!WebhookSignature.verify(properties.webhookSecret(), signatureHeader, rawBody, clock.instant(),
                properties.signatureTolerance())) {
            meters.counter("commercex.payments.webhooks", "outcome", "invalid_signature").increment();
            log.warn("Rejected {} webhook with invalid or expired signature", provider);
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE, "Invalid or expired webhook signature");
        }
        WebhookEvent event;
        try {
            event = objectMapper.readValue(rawBody, WebhookEvent.class);
        } catch (JsonProcessingException e) {
            throw new ValidationException("body", "is not a valid webhook event");
        }
        if (event.id() == null || event.id().isBlank() || event.id().length() > 128 || event.type() == null) {
            throw new ValidationException("id", "event id and type are required");
        }
        PaymentLedger.Applied applied = ledger.processWebhook(provider, event, rawBody);
        paymentService.executeRefund(applied);
        return applied.outcome();
    }
}
