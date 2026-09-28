package com.commercex.wishlist.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WishlistRepository {

    public record Entry(long productId, Instant addedAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    WishlistRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Entry> entries(long userId) {
        return jdbc.query("""
                SELECT product_id, created_at FROM wishlist_items WHERE user_id = :userId
                ORDER BY created_at DESC, product_id DESC""",
                new MapSqlParameterSource("userId", userId), (rs, n) -> {
                    Timestamp ts = rs.getTimestamp("created_at");
                    return new Entry(rs.getLong("product_id"), ts.toInstant());
                });
    }

    public int count(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM wishlist_items WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId), Integer.class);
    }

    /** Idempotent: adding an existing product is a no-op. */
    public void add(long userId, long productId) {
        jdbc.update("""
                INSERT INTO wishlist_items (user_id, product_id) VALUES (:userId, :productId)
                ON CONFLICT (user_id, product_id) DO NOTHING""", params(userId, productId));
    }

    public boolean contains(long userId, long productId) {
        return !jdbc.queryForList("SELECT 1 FROM wishlist_items WHERE user_id = :userId AND product_id = :productId",
                params(userId, productId), Integer.class).isEmpty();
    }

    public boolean remove(long userId, long productId) {
        return jdbc.update("DELETE FROM wishlist_items WHERE user_id = :userId AND product_id = :productId",
                params(userId, productId)) > 0;
    }

    private static MapSqlParameterSource params(long userId, long productId) {
        return new MapSqlParameterSource("userId", userId).addValue("productId", productId);
    }
}
