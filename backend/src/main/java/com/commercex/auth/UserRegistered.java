package com.commercex.auth;

/** Published after a new customer account was created. */
public record UserRegistered(long userId, String email, String firstName) {
}
