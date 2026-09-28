package com.commercex.common.persistence;

import com.commercex.common.error.ConflictException;
import com.commercex.common.error.ValidationException;

/**
 * Optimistic concurrency for admin edits: the client sends back the {@code version} it loaded,
 * and the update is rejected if someone else changed the resource in between (no lost updates).
 */
public final class Versions {

    private Versions() {
    }

    public static void require(long current, Long expected) {
        if (expected == null) {
            throw new ValidationException("version", "is required");
        }
        if (current != expected) {
            throw new ConflictException("The resource was modified by someone else; reload and retry");
        }
    }
}
