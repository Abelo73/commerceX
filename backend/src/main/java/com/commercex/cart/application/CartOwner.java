package com.commercex.cart.application;

/** Who a cart request acts for: a signed-in user, a guest with a token, or an anonymous visitor. */
public sealed interface CartOwner {

    record User(long userId) implements CartOwner {
    }

    /** {@code token} is the raw token from the {@code X-Cart-Token} header. */
    record Guest(String token) implements CartOwner {

        @Override
        public String toString() {
            return "Guest[]";
        }
    }

    record Anonymous() implements CartOwner {
    }
}
