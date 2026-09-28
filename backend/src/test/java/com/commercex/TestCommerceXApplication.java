package com.commercex;

import org.springframework.boot.SpringApplication;

/**
 * Runs the app against throwaway Testcontainers dependencies: {@code ./mvnw spring-boot:test-run}.
 */
public class TestCommerceXApplication {

    public static void main(String[] args) {
        SpringApplication.from(CommerceXApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
