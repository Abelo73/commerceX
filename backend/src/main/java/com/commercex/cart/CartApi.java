package com.commercex.cart;

import java.util.List;

/** Public API of the cart module (wishlist "move to cart", checkout). */
public interface CartApi {

    /** Adds to the user's cart with the same validation as the storefront (availability, stock, limits). */
    void addForUser(long userId, long variantId, int quantity);

    /** Raw lines of the user's cart, oldest first. Prices must be taken from the catalog, never the cart. */
    List<CartLine> userCartLines(long userId);

    void clearUserCart(long userId);
}
