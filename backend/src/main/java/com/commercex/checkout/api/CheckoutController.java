package com.commercex.checkout.api;

import com.commercex.checkout.application.CheckoutService;
import com.commercex.checkout.application.CheckoutService.PlaceOrderRequest;
import com.commercex.checkout.application.CheckoutViews.Quote;
import com.commercex.checkout.application.CheckoutViews.ShippingOption;
import com.commercex.common.idempotency.Idempotency;
import com.commercex.common.security.CurrentUser;
import com.commercex.order.OrderDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Checkout")
@SecurityRequirement(name = "bearer")
class CheckoutController {

    private final CheckoutService checkoutService;

    CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    record QuoteRequest(@Size(max = 32) String shippingMethod) {
    }

    record PlaceOrderBody(
            @NotNull Long addressId,
            @NotBlank @Size(max = 32) String shippingMethod,
            @NotNull @PositiveOrZero Long expectedTotal) {
    }

    @GetMapping("/checkout/shipping-methods")
    List<ShippingOption> shippingMethods() {
        return checkoutService.shippingOptions(0);
    }

    @PostMapping("/checkout/quote")
    @Operation(summary = "Server-side totals for the current cart (no side effects)")
    Quote quote(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody(required = false) QuoteRequest body) {
        return checkoutService.quote(CurrentUser.id(jwt), body == null ? null : body.shippingMethod());
    }

    @PostMapping("/orders")
    @Operation(summary = "Place an order from the cart; reserves stock. Requires Idempotency-Key.")
    ResponseEntity<?> placeOrder(@AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Unique per checkout attempt; retries must reuse it")
            @RequestHeader(value = Idempotency.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody PlaceOrderBody body) {
        Idempotency.Result<OrderDetails> result = checkoutService.placeOrder(CurrentUser.id(jwt),
                new PlaceOrderRequest(body.addressId(), body.shippingMethod(), body.expectedTotal()), idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status())
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (result.replayed()) {
            return response.header("Idempotent-Replayed", "true").body(result.replayedBody());
        }
        return response.body(result.body());
    }
}
