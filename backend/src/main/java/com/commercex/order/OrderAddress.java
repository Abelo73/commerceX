package com.commercex.order;

/** Shipping address copied onto the order at placement. */
public record OrderAddress(String fullName, String line1, String line2, String city, String region,
        String postalCode, String country, String phone) {
}
