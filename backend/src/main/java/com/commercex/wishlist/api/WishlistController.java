package com.commercex.wishlist.api;

import com.commercex.common.security.CurrentUser;
import com.commercex.wishlist.application.WishlistService;
import com.commercex.wishlist.application.WishlistService.WishlistItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wishlist")
@Tag(name = "Wishlist")
@SecurityRequirement(name = "bearer")
class WishlistController {

    private final WishlistService wishlistService;

    WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    record MoveToCartRequest(@NotNull Long variantId, @Min(1) @Max(99) int quantity) {
    }

    @GetMapping
    List<WishlistItem> list(@AuthenticationPrincipal Jwt jwt) {
        return wishlistService.list(CurrentUser.id(jwt));
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Save a product (idempotent)")
    List<WishlistItem> add(@AuthenticationPrincipal Jwt jwt, @PathVariable long productId) {
        return wishlistService.add(CurrentUser.id(jwt), productId);
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Remove a product (idempotent)")
    List<WishlistItem> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long productId) {
        return wishlistService.remove(CurrentUser.id(jwt), productId);
    }

    @PostMapping("/{productId}/move-to-cart")
    List<WishlistItem> moveToCart(@AuthenticationPrincipal Jwt jwt, @PathVariable long productId,
            @Valid @RequestBody MoveToCartRequest body) {
        return wishlistService.moveToCart(CurrentUser.id(jwt), productId, body.variantId(), body.quantity());
    }
}
