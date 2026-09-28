package com.commercex.auth.domain;

/** Editable address fields. {@code country} is an upper-case ISO 3166-1 alpha-2 code. */
public record AddressDetails(
        String label,
        String fullName,
        String line1,
        String line2,
        String city,
        String region,
        String postalCode,
        String country,
        String phone) {
}
