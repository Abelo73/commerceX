package com.commercex.cart;

import java.util.List;
import java.util.Optional;

/** Public API of the cart module (wishlist "move to cart", checkout). */
public interface CartApi {

    /** Adds to the user's cart with the same validation as the storefront (availability, stock, limits). */
    void addForUser(long userId, long variantId, int quantity);

    /** Raw lines of the user's cart, oldest first. Prices must be taken from the catalog, never the cart. */
    List<CartLine> userCartLines(long userId);

    /** Clears lines and the applied coupon. */
    void clearUserCart(long userId);

    /** Coupon code the user applied to their cart, if any (re-validate before use). */
    Optional<String> userCartCoupon(long userId);
}
