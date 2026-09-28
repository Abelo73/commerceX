package com.commercex.auth;

/** Address snapshot suitable for copying onto an order. */
public record ShippingAddress(String fullName, String line1, String line2, String city, String region,
        String postalCode, String country, String phone) {
}
