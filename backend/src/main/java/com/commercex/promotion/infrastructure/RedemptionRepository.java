package com.commercex.promotion.infrastructure;

import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedemptionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    RedemptionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int activeUses(long couponId, long userId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM coupon_redemptions
                WHERE coupon_id = :couponId AND user_id = :userId AND status = 'ACTIVE'""",
                new MapSqlParameterSource("couponId", couponId).addValue("userId", userId), Integer.class);
    }

    public void insert(long couponId, long userId, long orderId, long discountMinor) {
        jdbc.update("""
                INSERT INTO coupon_redemptions (coupon_id, user_id, order_id, discount_minor, status)
                VALUES (:couponId, :userId, :orderId, :discount, 'ACTIVE')""",
                new MapSqlParameterSource("couponId", couponId).addValue("userId", userId)
                        .addValue("orderId", orderId).addValue("discount", discountMinor));
    }

    /** Releases the order's redemption; returns its coupon so the use can be given back. Idempotent. */
    public Optional<Long> release(long orderId) {
        return jdbc.queryForList("""
                UPDATE coupon_redemptions SET status = 'RELEASED', released_at = now()
                WHERE order_id = :orderId AND status = 'ACTIVE'
                RETURNING coupon_id""", new MapSqlParameterSource("orderId", orderId), Long.class)
                .stream().findFirst();
    }
}
