package com.commercex.order.infrastructure;

import com.commercex.order.OrderStats;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only aggregates over the order module's own tables (dashboard). All dates are UTC. */
@Repository
public class OrderStatsQuery {

    private static final String EARNED = "('PAID','PROCESSING','PACKED','SHIPPED','DELIVERED')";

    private final NamedParameterJdbcTemplate jdbc;

    OrderStatsQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OrderStats stats(Instant from, String currency) {
        MapSqlParameterSource params = new MapSqlParameterSource("from", Timestamp.from(from));
        return jdbc.queryForObject("""
                SELECT
                  coalesce(sum(total_minor) FILTER (WHERE placed_at >= :from AND status IN %1$s), 0) AS revenue,
                  count(*) FILTER (WHERE placed_at >= :from) AS placed,
                  count(*) FILTER (WHERE placed_at >= :from AND status IN %1$s) AS paid,
                  count(*) FILTER (WHERE status IN ('PAID','PROCESSING','PACKED')) AS awaiting,
                  count(*) FILTER (WHERE status IN ('PENDING_PAYMENT','PAYMENT_FAILED')) AS unpaid
                FROM orders""".formatted(EARNED), params,
                (rs, n) -> new OrderStats(rs.getLong("revenue"), currency, rs.getLong("placed"), rs.getLong("paid"),
                        rs.getLong("awaiting"), rs.getLong("unpaid"), byDay(params)));
    }

    private List<OrderStats.Day> byDay(MapSqlParameterSource params) {
        return jdbc.query("""
                SELECT d.day::date AS day,
                       coalesce(sum(o.total_minor) FILTER (WHERE o.status IN %s), 0) AS revenue,
                       count(o.id) AS orders
                FROM generate_series((CAST(:from AS timestamptz) AT TIME ZONE 'UTC')::date,
                                     (now() AT TIME ZONE 'UTC')::date, interval '1 day') AS d(day)
                LEFT JOIN orders o ON (o.placed_at AT TIME ZONE 'UTC')::date = d.day::date
                GROUP BY d.day
                ORDER BY d.day""".formatted(EARNED), params,
                (rs, n) -> new OrderStats.Day(rs.getDate("day").toLocalDate(), rs.getLong("revenue"),
                        rs.getLong("orders")));
    }
}
