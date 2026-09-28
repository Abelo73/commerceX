package com.commercex.common.messaging;

/** Kafka topics for integration events. Messages are keyed by aggregate ID (per-aggregate ordering). */
public final class Topics {

    public static final String ORDERS = "commercex.orders";
    public static final String USERS = "commercex.users";
    public static final String INVENTORY = "commercex.inventory";

    private Topics() {
    }
}
