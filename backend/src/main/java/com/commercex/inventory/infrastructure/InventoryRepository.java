package com.commercex.inventory.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Stock mutations as single atomic SQL statements. Each conditional UPDATE both checks and changes
 * the row under the row lock PostgreSQL takes for the update, so concurrent requests serialize on the
 * row and can never oversell; {@code RETURNING} gives the post-change levels without a second read.
 */
@Repository
public class InventoryRepository {

    /** Levels after a change. */
    public record Levels(long variantId, int onHand, int reserved, int lowStockThreshold) {

        public int available() {
            return onHand - reserved;
        }
    }

    public record Item(long variantId, int onHand, int reserved, long sold, int lowStockThreshold, Instant updatedAt) {

        public int available() {
            return onHand - reserved;
        }
    }

    public record Reservation(long variantId, int quantity) {
    }

    public record Movement(long id, long variantId, String type, int onHandDelta, int reservedDelta, int onHandAfter,
            int reservedAfter, String reason, String reference, Long actorUserId, Instant createdAt) {
    }

    private static final String RETURNING = " RETURNING variant_id, on_hand, reserved, low_stock_threshold";

    private static final RowMapper<Levels> LEVELS = (rs, n) -> new Levels(
            rs.getLong("variant_id"), rs.getInt("on_hand"), rs.getInt("reserved"), rs.getInt("low_stock_threshold"));

    private static final RowMapper<Item> ITEM = (rs, n) -> new Item(
            rs.getLong("variant_id"), rs.getInt("on_hand"), rs.getInt("reserved"), rs.getLong("sold"),
            rs.getInt("low_stock_threshold"), instant(rs, "updated_at"));

    private final NamedParameterJdbcTemplate jdbc;

    InventoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- stock mutations -------------------------------------------------------------------------

    /** Moves {@code qty} from available to reserved, only if enough is available. Empty = insufficient. */
    public Optional<Levels> reserve(long variantId, int qty) {
        return single("""
                UPDATE inventory_items SET reserved = reserved + :qty, updated_at = now()
                WHERE variant_id = :variantId AND on_hand - reserved >= :qty""" + RETURNING, variantId, qty);
    }

    public Optional<Levels> release(long variantId, int qty) {
        return single("""
                UPDATE inventory_items SET reserved = reserved - :qty, updated_at = now()
                WHERE variant_id = :variantId""" + RETURNING, variantId, qty);
    }

    /** Reserved units leave the building: on_hand and reserved both drop, sold rises. */
    public Optional<Levels> commit(long variantId, int qty) {
        return single("""
                UPDATE inventory_items
                SET on_hand = on_hand - :qty, reserved = reserved - :qty, sold = sold + :qty, updated_at = now()
                WHERE variant_id = :variantId""" + RETURNING, variantId, qty);
    }

    /** Adds stock, creating the inventory record on first use. */
    public Levels increaseOnHand(long variantId, int qty) {
        return jdbc.queryForObject("""
                INSERT INTO inventory_items (variant_id, on_hand) VALUES (:variantId, :qty)
                ON CONFLICT (variant_id) DO UPDATE
                SET on_hand = inventory_items.on_hand + EXCLUDED.on_hand, updated_at = now()""" + RETURNING,
                params(variantId, qty), LEVELS);
    }

    /** Removes stock, never below what is already reserved for customers. Empty = not possible. */
    public Optional<Levels> decreaseOnHand(long variantId, int qty) {
        return single("""
                UPDATE inventory_items SET on_hand = on_hand - :qty, updated_at = now()
                WHERE variant_id = :variantId AND on_hand - :qty >= reserved""" + RETURNING, variantId, qty);
    }

    public Levels setThreshold(long variantId, int threshold) {
        return jdbc.queryForObject("""
                INSERT INTO inventory_items (variant_id, low_stock_threshold) VALUES (:variantId, :qty)
                ON CONFLICT (variant_id) DO UPDATE
                SET low_stock_threshold = EXCLUDED.low_stock_threshold, updated_at = now()""" + RETURNING,
                params(variantId, threshold), LEVELS);
    }

    // ---- reservations ----------------------------------------------------------------------------

    /** Returns false when this order already holds a reservation for the variant (idempotent retry). */
    public boolean insertReservation(long orderId, long variantId, int qty, Instant expiresAt) {
        List<Long> ids = jdbc.queryForList("""
                INSERT INTO inventory_reservations (order_id, variant_id, quantity, status, expires_at)
                VALUES (:orderId, :variantId, :qty, 'ACTIVE', :expiresAt)
                ON CONFLICT (order_id, variant_id) DO NOTHING
                RETURNING id""",
                params(variantId, qty).addValue("orderId", orderId).addValue("expiresAt", Timestamp.from(expiresAt)),
                Long.class);
        return !ids.isEmpty();
    }

    /** Locks the order's active reservations, in variant order (same lock order as reserve). */
    public List<Reservation> lockActiveReservations(long orderId) {
        return jdbc.query("""
                SELECT variant_id, quantity FROM inventory_reservations
                WHERE order_id = :orderId AND status = 'ACTIVE'
                ORDER BY variant_id
                FOR UPDATE""",
                new MapSqlParameterSource("orderId", orderId),
                (rs, n) -> new Reservation(rs.getLong("variant_id"), rs.getInt("quantity")));
    }

    public void markReservation(long orderId, long variantId, String status) {
        jdbc.update("""
                UPDATE inventory_reservations SET status = :status, updated_at = now()
                WHERE order_id = :orderId AND variant_id = :variantId""",
                new MapSqlParameterSource("orderId", orderId).addValue("variantId", variantId)
                        .addValue("status", status));
    }

    // ---- ledger ----------------------------------------------------------------------------------

    public void recordMovement(Levels after, String type, int onHandDelta, int reservedDelta, String reason,
            String reference, Long actorUserId) {
        jdbc.update("""
                INSERT INTO inventory_movements (variant_id, type, on_hand_delta, reserved_delta, on_hand_after,
                                                 reserved_after, reason, reference, actor_user_id)
                VALUES (:variantId, :type, :onHandDelta, :reservedDelta, :onHandAfter, :reservedAfter,
                        :reason, :reference, :actor)""",
                new MapSqlParameterSource("variantId", after.variantId())
                        .addValue("type", type)
                        .addValue("onHandDelta", onHandDelta)
                        .addValue("reservedDelta", reservedDelta)
                        .addValue("onHandAfter", after.onHand())
                        .addValue("reservedAfter", after.reserved())
                        .addValue("reason", reason)
                        .addValue("reference", reference)
                        .addValue("actor", actorUserId));
    }

    // ---- reads -----------------------------------------------------------------------------------

    public List<Levels> levels(Collection<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT variant_id, on_hand, reserved, low_stock_threshold FROM inventory_items
                WHERE variant_id IN (:ids)""", new MapSqlParameterSource("ids", variantIds), LEVELS);
    }

    public Optional<Item> item(long variantId) {
        return jdbc.query("""
                SELECT variant_id, on_hand, reserved, sold, low_stock_threshold, updated_at
                FROM inventory_items WHERE variant_id = :variantId""",
                new MapSqlParameterSource("variantId", variantId), ITEM).stream().findFirst();
    }

    public List<Item> items(boolean lowStockOnly, int limit, long offset) {
        return jdbc.query("""
                SELECT variant_id, on_hand, reserved, sold, low_stock_threshold, updated_at
                FROM inventory_items
                WHERE NOT :lowStockOnly OR on_hand - reserved - low_stock_threshold <= 0
                ORDER BY on_hand - reserved, variant_id
                LIMIT :limit OFFSET :offset""",
                new MapSqlParameterSource("lowStockOnly", lowStockOnly).addValue("limit", limit)
                        .addValue("offset", offset), ITEM);
    }

    public long countItems(boolean lowStockOnly) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM inventory_items
                WHERE NOT :lowStockOnly OR on_hand - reserved - low_stock_threshold <= 0""",
                new MapSqlParameterSource("lowStockOnly", lowStockOnly), Long.class);
    }

    public List<Movement> movements(long variantId, int limit, long offset) {
        return jdbc.query("""
                SELECT id, variant_id, type, on_hand_delta, reserved_delta, on_hand_after, reserved_after, reason,
                       reference, actor_user_id, created_at
                FROM inventory_movements WHERE variant_id = :variantId
                ORDER BY created_at DESC, id DESC
                LIMIT :limit OFFSET :offset""",
                new MapSqlParameterSource("variantId", variantId).addValue("limit", limit)
                        .addValue("offset", offset),
                (rs, n) -> new Movement(rs.getLong("id"), rs.getLong("variant_id"), rs.getString("type"),
                        rs.getInt("on_hand_delta"), rs.getInt("reserved_delta"), rs.getInt("on_hand_after"),
                        rs.getInt("reserved_after"), rs.getString("reason"), rs.getString("reference"),
                        rs.getObject("actor_user_id", Long.class), instant(rs, "created_at")));
    }

    public long countMovements(long variantId) {
        return jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE variant_id = :variantId",
                new MapSqlParameterSource("variantId", variantId), Long.class);
    }

    private Optional<Levels> single(String sql, long variantId, int qty) {
        return jdbc.query(sql, params(variantId, qty), LEVELS).stream().findFirst();
    }

    private static MapSqlParameterSource params(long variantId, int qty) {
        return new MapSqlParameterSource("variantId", variantId).addValue("qty", qty);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }
}
