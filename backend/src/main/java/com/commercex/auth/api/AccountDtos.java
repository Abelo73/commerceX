package com.commercex.auth.api;

import com.commercex.auth.domain.AddressDetails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

final class AccountDtos {

    private AccountDtos() {
    }

    record UpdateProfileRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName) {
    }

    record ChangePasswordRequest(
            @NotBlank @Size(max = 200) String currentPassword,
            @NotBlank @Size(min = 10, max = 72) String newPassword) {

        @Override
        public String toString() {
            return "ChangePasswordRequest[]";
        }
    }

    record AddressRequest(
            @Size(max = 50) String label,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @NotBlank @Size(max = 100) String city,
            @Size(max = 100) String region,
            @NotBlank @Size(max = 20) String postalCode,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "must be an ISO 3166-1 alpha-2 code") String country,
            @Pattern(regexp = "\\+?[0-9 ()-]{5,32}", message = "must be a valid phone number") String phone,
            boolean isDefault) {

        AddressDetails details() {
            return new AddressDetails(label, fullName, line1, line2, city, region, postalCode, country, phone);
        }
    }

    record AddressResponse(
            long id,
            String label,
            String fullName,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String country,
            String phone,
            boolean isDefault) {

        static AddressResponse from(com.commercex.auth.application.AddressView view) {
            AddressDetails d = view.details();
            return new AddressResponse(view.id(), d.label(), d.fullName(), d.line1(), d.line2(), d.city(),
                    d.region(), d.postalCode(), d.country(), d.phone(), view.isDefault());
        }
    }
}
