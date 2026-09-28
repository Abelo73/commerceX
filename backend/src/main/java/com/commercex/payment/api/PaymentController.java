package com.commercex.payment.api;

import com.commercex.common.idempotency.Idempotency;
import com.commercex.common.security.CurrentUser;
import com.commercex.payment.application.PaymentService;
import com.commercex.payment.application.PaymentService.PayResult;
import com.commercex.payment.application.PaymentViews.PaymentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/{orderNumber}/payments")
@Validated
@Tag(name = "Payments")
@SecurityRequirement(name = "bearer")
class PaymentController {

    private final PaymentService paymentService;

    PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** {@code paymentToken} comes from the provider's client SDK; card data never reaches CommerceX. */
    record PayRequest(@NotBlank @Size(max = 256) @Pattern(regexp = "[A-Za-z0-9_\\-]+") String paymentToken) {

        @Override
        public String toString() {
            return "PayRequest[]";
        }
    }

    @PostMapping
    @Operation(summary = "Pay an unpaid order. Requires Idempotency-Key; the status may be PENDING or "
            + "REQUIRES_ACTION (follow nextActionUrl) and is settled by webhook")
    ResponseEntity<PaymentView> pay(@AuthenticationPrincipal Jwt jwt, @PathVariable @Size(max = 20) String orderNumber,
            @RequestHeader(value = Idempotency.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody PayRequest body) {
        PayResult result = paymentService.pay(CurrentUser.id(jwt), orderNumber, idempotencyKey, body.paymentToken());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.payment());
    }

    @GetMapping
    List<PaymentView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable @Size(max = 20) String orderNumber) {
        return paymentService.customerPayments(CurrentUser.id(jwt), orderNumber);
    }
}
