package com.commercex.payment.api;

import com.commercex.payment.application.WebhookService;
import com.commercex.payment.application.WebhookSignature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Public, authenticated by HMAC signature instead of a user token. The raw body is needed to verify it. */
@RestController
@Tag(name = "Payments")
class PaymentWebhookController {

    private final WebhookService webhookService;

    PaymentWebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(path = "/api/v1/payments/webhooks/{provider}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Provider webhook (signed). Duplicate deliveries are acknowledged and ignored")
    Map<String, Object> receive(@PathVariable String provider,
            @RequestHeader(value = WebhookSignature.HEADER, required = false) String signature,
            @RequestBody String rawBody) {
        return Map.of("received", true, "outcome", webhookService.handle(provider, rawBody, signature));
    }
}
