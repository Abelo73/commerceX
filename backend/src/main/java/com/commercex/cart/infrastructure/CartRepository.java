package com.commercex.cart.infrastructure;

import com.commercex.cart.CartLine;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Cart persistence as single-statement upserts, so concurrent requests (two tabs, double clicks)
 * cannot lose updates or collide on cart creation.
 */
@Repository
public class CartRepository {

    public static final int MAX_QUANTITY_PER_LINE = 99;

    private final NamedParameterJdbcTemplate jdbc;

    CartRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findUserCart(long userId) {
        return jdbc.queryForList("SELECT id FROM carts WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId), Long.class).stream().findFirst();
    }

    /** Only unexpired guest carts are found. */
    public Optional<Long> findGuestCart(String tokenHash) {
        return jdbc.queryForList("""
                SELECT id FROM carts WHERE guest_token_hash = :hash AND expires_at > now()""",
                new MapSqlParameterSource("hash", tokenHash), Long.class).stream().findFirst();
    }

    public long getOrCreateUserCart(long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        jdbc.update("INSERT INTO carts (user_id) VALUES (:userId) ON CONFLICT (user_id) DO NOTHING", params);
        return jdbc.queryForObject("SELECT id FROM carts WHERE user_id = :userId", params, Long.class);
    }

    public long createGuestCart(String tokenHash, Instant expiresAt) {
        return jdbc.queryForObject("""
                INSERT INTO carts (guest_token_hash, expires_at) VALUES (:hash, :expiresAt) RETURNING id""",
                new MapSqlParameterSource("hash", tokenHash).addValue("expiresAt", Timestamp.from(expiresAt)),
                Long.class);
    }

    /** Marks activity; guest carts get a sliding expiry, user carts keep none. */
    public void touch(long cartId, Instant guestExpiresAt) {
        jdbc.update("""
                UPDATE carts SET updated_at = now(),
                       expires_at = CASE WHEN user_id IS NULL THEN CAST(:expiresAt AS TIMESTAMPTZ) ELSE NULL END
                WHERE id = :cartId""",
                new MapSqlParameterSource("cartId", cartId).addValue("expiresAt", Timestamp.from(guestExpiresAt)));
    }

    public List<CartLine> lines(long cartId) {
        return jdbc.query("""
                SELECT variant_id, quantity FROM cart_items WHERE cart_id = :cartId ORDER BY added_at, variant_id""",
                new MapSqlParameterSource("cartId", cartId),
                (rs, n) -> new CartLine(rs.getLong("variant_id"), rs.getInt("quantity")));
    }

    public int lineCount(long cartId) {
        return jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE cart_id = :cartId",
                new MapSqlParameterSource("cartId", cartId), Integer.class);
    }

    /** Adds to a line atomically. Empty when the line would exceed the per-line maximum. */
    public Optional<Integer> addQuantity(long cartId, long variantId, int quantity) {
        return jdbc.queryForList("""
                INSERT INTO cart_items (cart_id, variant_id, quantity) VALUES (:cartId, :variantId, :qty)
                ON CONFLICT (cart_id, variant_id) DO UPDATE SET quantity = cart_items.quantity + EXCLUDED.quantity
                WHERE cart_items.quantity + EXCLUDED.quantity <= :max
                RETURNING quantity""",
                line(cartId, variantId).addValue("qty", quantity).addValue("max", MAX_QUANTITY_PER_LINE),
                Integer.class).stream().findFirst();
    }

    public boolean setQuantity(long cartId, long variantId, int quantity) {
        return jdbc.update("""
                UPDATE cart_items SET quantity = :qty WHERE cart_id = :cartId AND variant_id = :variantId""",
                line(cartId, variantId).addValue("qty", quantity)) > 0;
    }

    public boolean remove(long cartId, long variantId) {
        return jdbc.update("DELETE FROM cart_items WHERE cart_id = :cartId AND variant_id = :variantId",
                line(cartId, variantId)) > 0;
    }

    public void clear(long cartId) {
        jdbc.update("DELETE FROM cart_items WHERE cart_id = :cartId", new MapSqlParameterSource("cartId", cartId));
    }

    /** Moves every guest line into the user cart (summing, capped per line) and deletes the guest cart. */
    public void mergeInto(long guestCartId, long userCartId) {
        MapSqlParameterSource params = new MapSqlParameterSource("guest", guestCartId).addValue("user", userCartId)
                .addValue("max", MAX_QUANTITY_PER_LINE);
        jdbc.update("""
                INSERT INTO cart_items (cart_id, variant_id, quantity, added_at)
                SELECT :user, variant_id, quantity, added_at FROM cart_items WHERE cart_id = :guest
                ON CONFLICT (cart_id, variant_id)
                DO UPDATE SET quantity = LEAST(cart_items.quantity + EXCLUDED.quantity, :max)""", params);
        jdbc.update("DELETE FROM carts WHERE id = :guest", params);
    }

    public int deleteExpiredGuestCarts() {
        return jdbc.update("DELETE FROM carts WHERE user_id IS NULL AND expires_at < now()",
                new MapSqlParameterSource());
    }

    private static MapSqlParameterSource line(long cartId, long variantId) {
        return new MapSqlParameterSource("cartId", cartId).addValue("variantId", variantId);
    }
}
