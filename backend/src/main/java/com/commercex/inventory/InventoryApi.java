package com.commercex.inventory;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Public API of the inventory module.
 *
 * <p>Reservation lifecycle for an order: {@code reserve} (checkout) → {@code commit} (payment
 * captured) or {@code release} (cancelled / payment failed / expired). All three must run inside the
 * caller's transaction so that order state and stock change atomically, and all three are idempotent
 * per order, so retries are safe. Timeouts are driven by the order module, which owns the order state.
 */
public interface InventoryApi {

    /** Stock levels; variants without an inventory record report zero available. */
    Map<Long, StockLevel> stockLevels(Collection<Long> variantIds);

    /**
     * Reserves every line or nothing. Lines are processed in ascending variant order to avoid deadlocks.
     * Lines already reserved for this order are skipped (idempotent retry).
     *
     * @throws InsufficientStockException if any line cannot be satisfied (nothing is reserved)
     */
    void reserve(long orderId, List<ReservationLine> lines, Instant expiresAt);

    /** Converts the order's active reservations into sales. Returns the number of lines committed. */
    int commit(long orderId);

    /** Returns the order's active reservations to available stock. Returns the number of lines released. */
    int release(long orderId, String reason);

    /** Stock rows, lowest availability first (admin overview). */
    StockPage stockItems(boolean lowStockOnly, int page, int size);

    long lowStockCount();
}
