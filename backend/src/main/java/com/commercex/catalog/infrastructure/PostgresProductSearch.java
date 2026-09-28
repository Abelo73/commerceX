package com.commercex.catalog.infrastructure;

import com.commercex.catalog.application.ProductSearch;
import com.commercex.catalog.application.ProductSearchCriteria;
import com.commercex.catalog.application.ProductSummaryRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Published-product search using the generated {@code search_vector} column and
 * {@code websearch_to_tsquery} (supports quotes, OR and -exclusions). All user input is bound as
 * parameters; ORDER BY comes from a fixed whitelist.
 */
@Component
class PostgresProductSearch implements ProductSearch {

    private static final String QUERY = "websearch_to_tsquery('english', :q)";

    private final NamedParameterJdbcTemplate jdbc;

    PostgresProductSearch(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryRow> search(ProductSearchCriteria c) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> where = new ArrayList<>();
        where.add("p.status = 'PUBLISHED'");

        boolean hasQuery = c.query() != null && !c.query().isBlank();
        if (hasQuery) {
            where.add("p.search_vector @@ " + QUERY);
            params.addValue("q", c.query().trim());
        }
        if (c.categoryIds() != null) {
            if (c.categoryIds().isEmpty()) {
                return Page.empty(PageRequest.of(c.page(), c.size()));
            }
            where.add("p.category_id IN (:categoryIds)");
            params.addValue("categoryIds", c.categoryIds());
        }
        if (c.brandSlugs() != null && !c.brandSlugs().isEmpty()) {
            where.add("b.slug IN (:brandSlugs)");
            params.addValue("brandSlugs", c.brandSlugs());
        }
        if (c.minPriceMinor() != null) {
            where.add("p.min_price_minor >= :minPrice");
            params.addValue("minPrice", c.minPriceMinor());
        }
        if (c.maxPriceMinor() != null) {
            where.add("p.min_price_minor <= :maxPrice");
            params.addValue("maxPrice", c.maxPriceMinor());
        }
        if (c.minRating() != null) {
            where.add("p.rating_avg >= :minRating");
            params.addValue("minRating", c.minRating());
        }
        String whereSql = " WHERE " + String.join(" AND ", where);
        String from = " FROM products p LEFT JOIN brands b ON b.id = p.brand_id";

        long total = jdbc.queryForObject("SELECT count(*)" + from + whereSql, params, Long.class);
        if (total == 0) {
            return Page.empty(PageRequest.of(c.page(), c.size()));
        }

        String orderBy = switch (c.sort()) {
            case RELEVANCE -> hasQuery
                    ? "ts_rank_cd(p.search_vector, " + QUERY + ") DESC, p.published_at DESC"
                    : "p.published_at DESC";
            case NEWEST -> "p.published_at DESC";
            case PRICE_ASC -> "p.min_price_minor ASC";
            case PRICE_DESC -> "p.min_price_minor DESC";
            case RATING -> "p.rating_avg DESC, p.review_count DESC";
        };
        params.addValue("limit", c.size());
        params.addValue("offset", (long) c.page() * c.size());

        String sql = """
                SELECT p.id, p.slug, p.name, p.short_description, p.currency, p.min_price_minor,
                       p.rating_avg, p.review_count, p.category_id, p.published_at,
                       b.name AS brand_name, b.slug AS brand_slug,
                       cheapest.compare_at_price_minor, img.url AS image_url, img.alt_text AS image_alt
                """ + from + """
                 LEFT JOIN LATERAL (
                     SELECT v.compare_at_price_minor FROM product_variants v
                     WHERE v.product_id = p.id AND v.is_active
                     ORDER BY v.price_minor, v.position LIMIT 1) cheapest ON TRUE
                 LEFT JOIN LATERAL (
                     SELECT i.url, i.alt_text FROM product_images i
                     WHERE i.product_id = p.id ORDER BY i.position LIMIT 1) img ON TRUE
                """ + whereSql + " ORDER BY " + orderBy + ", p.id DESC LIMIT :limit OFFSET :offset";

        List<ProductSummaryRow> rows = jdbc.query(sql, params, PostgresProductSearch::map);
        return new PageImpl<>(rows, PageRequest.of(c.page(), c.size()), total);
    }

    private static ProductSummaryRow map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp publishedAt = rs.getTimestamp("published_at");
        return new ProductSummaryRow(
                rs.getLong("id"),
                rs.getString("slug"),
                rs.getString("name"),
                rs.getString("short_description"),
                rs.getString("currency"),
                rs.getLong("min_price_minor"),
                rs.getObject("compare_at_price_minor", Long.class),
                rs.getBigDecimal("rating_avg"),
                rs.getInt("review_count"),
                rs.getLong("category_id"),
                rs.getString("brand_name"),
                rs.getString("brand_slug"),
                rs.getString("image_url"),
                rs.getString("image_alt"),
                publishedAt != null ? publishedAt.toInstant() : null);
    }
}
