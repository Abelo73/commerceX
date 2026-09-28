package com.commercex.auth;

public record CustomerInfo(long userId, String email, String firstName, String lastName, boolean active) {
}
