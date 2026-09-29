package com.commercex.auth;

import java.time.Instant;
import java.util.Optional;

/** Public API of the auth module for checkout and orders. */
public interface AccountApi {

    Optional<CustomerInfo> customer(long userId);

    /** The user's own address; another user's address is reported as absent. */
    Optional<ShippingAddress> address(long userId, long addressId);

    /** Customers in total and registered since {@code from} (dashboard). */
    CustomerStats customerStats(Instant from);
}
