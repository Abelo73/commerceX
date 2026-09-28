package com.commercex.auth.api;

import com.commercex.auth.api.AuthDtos.AuthResponse;
import com.commercex.auth.api.AuthDtos.LoginRequest;
import com.commercex.auth.api.AuthDtos.PasswordResetConfirmRequest;
import com.commercex.auth.api.AuthDtos.PasswordResetRequest;
import com.commercex.auth.api.AuthDtos.RegisterRequest;
import com.commercex.auth.application.AuthResult;
import com.commercex.auth.application.AuthService;
import com.commercex.auth.application.PasswordResetService;
import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenCookies cookies;

    AuthController(AuthService authService, PasswordResetService passwordResetService, RefreshTokenCookies cookies) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @Operation(summary = "Create a customer account and sign in")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        AuthResult result = authService.register(body.email(), body.password(), body.firstName(), body.lastName(),
                request.getRemoteAddr());
        return withSession(HttpStatus.CREATED, result);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return withSession(HttpStatus.OK, authService.login(body.email(), body.password(), request.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh-token cookie for a new access token (rotates the cookie)")
    ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            return withSession(HttpStatus.OK, authService.refresh(readRefreshCookie(request)));
        } catch (BusinessException ex) {
            if (ex.code() == ErrorCode.INVALID_REFRESH_TOKEN || ex.code() == ErrorCode.ACCOUNT_DISABLED) {
                // The session is over: clear the dead cookie; the error body is rendered by the exception handler.
                response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
            }
            throw ex;
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current session and clear the refresh-token cookie")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(readRefreshCookie(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @PostMapping("/password-reset/request")
    @Operation(summary = "Email a password reset link (always 202, even for unknown emails)")
    ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest body,
            HttpServletRequest request) {
        passwordResetService.request(body.email(), request.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Set a new password using the emailed token; signs out all sessions")
    ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest body) {
        passwordResetService.confirm(body.token(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthResponse> withSession(HttpStatus status, AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE,
                        cookies.issue(result.refreshToken(), result.refreshTokenExpiresAt()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(AuthResponse.from(result));
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (cookie.getName().equals(cookies.name())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
