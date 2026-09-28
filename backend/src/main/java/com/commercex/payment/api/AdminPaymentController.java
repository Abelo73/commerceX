package com.commercex.payment.api;

import com.commercex.common.idempotency.Idempotency;
import com.commercex.common.security.CurrentUser;
import com.commercex.payment.application.PaymentService;
import com.commercex.payment.application.PaymentViews.AdminPaymentView;
import com.commercex.payment.application.PaymentViews.RefundView;
import com.commercex.payment.application.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/admin")
@Validated
@Tag(name = "Admin: Payments")
@SecurityRequirement(name = "bearer")
class AdminPaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    AdminPaymentController(PaymentService paymentService, RefundService refundService) {
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    /** {@code amount} (minor units) omitted = refund everything still refundable. */
    record RefundRequest(@Positive Long amount, @NotBlank @Size(max = 500) String reason) {
    }

    @GetMapping("/orders/{orderNumber}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
    List<AdminPaymentView> payments(@PathVariable @Size(max = 20) String orderNumber) {
        return paymentService.staffPayments(orderNumber);
    }

    @PostMapping("/payments/{paymentId}/refunds")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Refund a payment (full or partial). Requires Idempotency-Key")
    RefundView refund(@AuthenticationPrincipal Jwt jwt, @PathVariable long paymentId,
            @RequestHeader(value = Idempotency.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody RefundRequest body) {
        return refundService.refund(CurrentUser.id(jwt), paymentId, body.amount(), body.reason(), idempotencyKey);
    }
}
