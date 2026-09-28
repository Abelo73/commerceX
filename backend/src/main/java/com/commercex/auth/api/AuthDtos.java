package com.commercex.auth.api;

import com.commercex.auth.application.AuthResult;
import com.commercex.auth.application.UserView;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request/response bodies for {@link AuthController}. Records holding passwords override
 * {@code toString()} so secrets never end up in logs.
 */
final class AuthDtos {

    private AuthDtos() {
    }

    record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName) {

        @Override
        public String toString() {
            return "RegisterRequest[email=" + email + "]";
        }
    }

    record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 200) String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + "]";
        }
    }

    record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    record PasswordResetConfirmRequest(
            @NotBlank @Size(max = 128) String token,
            @NotBlank @Size(min = 10, max = 72) String newPassword) {

        @Override
        public String toString() {
            return "PasswordResetConfirmRequest[]";
        }
    }

    /** The refresh token travels only in the httpOnly cookie, never in this body. */
    record AuthResponse(String accessToken, String tokenType, long expiresIn, UserView user) {

        static AuthResponse from(AuthResult result) {
            return new AuthResponse(result.accessToken(), "Bearer", result.expiresInSeconds(), result.user());
        }

        @Override
        public String toString() {
            return "AuthResponse[user=" + user.id() + "]";
        }
    }
}
