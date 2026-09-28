package com.commercex.auth.application;

import com.commercex.auth.domain.User;
import java.time.Instant;
import java.util.List;

public record UserView(
        long id,
        String email,
        String firstName,
        String lastName,
        String status,
        List<String> roles,
        boolean emailVerified,
        Instant createdAt) {

    static UserView from(User user) {
        return new UserView(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus().name(),
                user.getRoles().stream().map(Enum::name).sorted().toList(),
                user.isEmailVerified(),
                user.getCreatedAt());
    }
}
