package com.commercex.cart.api;

import com.commercex.cart.application.CartOwner;
import com.commercex.cart.application.CartService;
import com.commercex.cart.application.CartService.CartResult;
import com.commercex.cart.application.CartView;
import com.commercex.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cart for signed-in users (bearer token) and guests ({@code X-Cart-Token}). When both are present the
 * user cart wins; call {@code POST /cart/merge} after sign-in to fold the guest cart in.
 */
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart")
class CartController {

    static final String CART_TOKEN = "X-Cart-Token";

    private final CartService cartService;

    CartController(CartService cartService) {
        this.cartService = cartService;
    }

    record AddItemRequest(@NotNull Long variantId, @Min(1) @Max(99) int quantity) {
    }

    record QuantityRequest(@Min(1) @Max(99) int quantity) {
    }

    record CouponRequest(@NotBlank @Size(max = 32) String code) {
    }

    /** {@code cartToken} is only present when this response created a new guest cart. */
    record CartResponse(CartView cart, String cartToken) {

        @Override
        public String toString() {
            return "CartResponse[]";
        }
    }

    @GetMapping
    CartView get(@AuthenticationPrincipal Jwt jwt, @RequestHeader(value = CART_TOKEN, required = false) String token) {
        return cartService.view(owner(jwt, token));
    }

    @PostMapping("/items")
    @Operation(summary = "Add to cart; creates a guest cart (and returns its token) when needed")
    ResponseEntity<CartResponse> add(@AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Guest cart token") @RequestHeader(value = CART_TOKEN, required = false)
            String token,
            @Valid @RequestBody AddItemRequest body, HttpServletRequest request) {
        CartResult result = cartService.addItem(owner(jwt, token), body.variantId(), body.quantity(),
                request.getRemoteAddr());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (result.issuedGuestToken() != null) {
            response.header(CART_TOKEN, result.issuedGuestToken());
        }
        return response.body(new CartResponse(result.cart(), result.issuedGuestToken()));
    }

    @PatchMapping("/items/{variantId}")
    CartView update(@AuthenticationPrincipal Jwt jwt, @RequestHeader(value = CART_TOKEN, required = false) String token,
            @PathVariable long variantId, @Valid @RequestBody QuantityRequest body) {
        return cartService.updateQuantity(owner(jwt, token), variantId, body.quantity());
    }

    @DeleteMapping("/items/{variantId}")
    CartView remove(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = CART_TOKEN, required = false) String token, @PathVariable long variantId) {
        return cartService.removeItem(owner(jwt, token), variantId);
    }

    @DeleteMapping
    CartView clear(@AuthenticationPrincipal Jwt jwt, @RequestHeader(value = CART_TOKEN, required = false) String token) {
        return cartService.clear(owner(jwt, token));
    }

    @PutMapping("/coupon")
    @Operation(summary = "Apply a coupon code; 422 COUPON_INVALID with the reason when it does not qualify")
    CartView applyCoupon(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = CART_TOKEN, required = false) String token,
            @Valid @RequestBody CouponRequest body) {
        return cartService.applyCoupon(owner(jwt, token), body.code());
    }

    @DeleteMapping("/coupon")
    CartView removeCoupon(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = CART_TOKEN, required = false) String token) {
        return cartService.removeCoupon(owner(jwt, token));
    }

    @PostMapping("/merge")
    @Operation(summary = "Merge the guest cart (X-Cart-Token) into the signed-in user's cart")
    ResponseEntity<CartView> merge(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = CART_TOKEN, required = false) String token) {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(cartService.merge(CurrentUser.id(jwt), token));
    }

    private static CartOwner owner(Jwt jwt, String token) {
        if (jwt != null) {
            return new CartOwner.User(CurrentUser.id(jwt));
        }
        if (token != null && !token.isBlank() && token.length() <= 128) {
            return new CartOwner.Guest(token);
        }
        return new CartOwner.Anonymous();
    }
}
