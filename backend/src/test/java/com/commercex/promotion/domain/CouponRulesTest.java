package com.commercex.promotion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.commercex.promotion.DiscountLine;
import com.commercex.promotion.domain.CouponRules.Reason;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CouponRulesTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private static final List<DiscountLine> LINES = List.of(
            new DiscountLine(1, 10, 6_000),   // product 1 in category 10
            new DiscountLine(2, 20, 4_000));  // product 2 in category 20

    private static CouponRules rules(CouponType type, long value, Long cap, long minOrder, Integer limit, int used,
            Integer perCustomer, Set<Long> products, Set<Long> categories) {
        return new CouponRules(true, NOW.minusSeconds(60), NOW.plusSeconds(3600), type, value, cap, minOrder, limit,
                used, perCustomer, products, categories);
    }

    private static CouponRules percent(long bp) {
        return rules(CouponType.PERCENTAGE, bp, null, 0, null, 0, null, Set.of(), Set.of());
    }

    @Test
    void percentageRoundsHalfUpAndRespectsCap() {
        assertThat(percent(1_000).evaluate(LINES, 10_000, NOW, 0, true).discountMinor()).isEqualTo(1_000);
        assertThat(percent(1_250).evaluate(List.of(new DiscountLine(1, 1, 12_345)), 12_345, NOW, 0, true)
                .discountMinor()).isEqualTo(1_543); // 1543.125 → 1543
        CouponRules capped = rules(CouponType.PERCENTAGE, 5_000, 2_000L, 0, null, 0, null, Set.of(), Set.of());
        assertThat(capped.evaluate(LINES, 10_000, NOW, 0, true).discountMinor()).isEqualTo(2_000);
    }

    @Test
    void fixedDiscountNeverExceedsEligibleAmount() {
        CouponRules fixed = rules(CouponType.FIXED, 50_000, null, 0, null, 0, null, Set.of(2L), Set.of());
        assertThat(fixed.evaluate(LINES, 10_000, NOW, 0, true).discountMinor()).isEqualTo(4_000);
    }

    @Test
    void restrictionsLimitDiscountToEligibleLines() {
        CouponRules byProduct = rules(CouponType.PERCENTAGE, 1_000, null, 0, null, 0, null, Set.of(1L), Set.of());
        CouponRules byCategory = rules(CouponType.PERCENTAGE, 1_000, null, 0, null, 0, null, Set.of(), Set.of(20L));
        CouponRules none = rules(CouponType.PERCENTAGE, 1_000, null, 0, null, 0, null, Set.of(99L), Set.of());

        assertThat(byProduct.evaluate(LINES, 10_000, NOW, 0, true).discountMinor()).isEqualTo(600);
        assertThat(byCategory.evaluate(LINES, 10_000, NOW, 0, true).discountMinor()).isEqualTo(400);
        assertThat(none.evaluate(LINES, 10_000, NOW, 0, true).reason()).isEqualTo(Reason.NOT_APPLICABLE);
    }

    @Test
    void windowActivityAndMinimumOrder() {
        CouponRules inactive = new CouponRules(false, NOW.minusSeconds(1), null, CouponType.FIXED, 100, null, 0, null,
                0, null, Set.of(), Set.of());
        CouponRules future = new CouponRules(true, NOW.plusSeconds(1), null, CouponType.FIXED, 100, null, 0, null, 0,
                null, Set.of(), Set.of());
        CouponRules expired = new CouponRules(true, NOW.minusSeconds(10), NOW, CouponType.FIXED, 100, null, 0, null, 0,
                null, Set.of(), Set.of());
        CouponRules minimum = rules(CouponType.FIXED, 100, null, 10_001, null, 0, null, Set.of(), Set.of());

        assertThat(inactive.evaluate(LINES, 10_000, NOW, 0, true).reason()).isEqualTo(Reason.INACTIVE);
        assertThat(future.evaluate(LINES, 10_000, NOW, 0, true).reason()).isEqualTo(Reason.NOT_STARTED);
        assertThat(expired.evaluate(LINES, 10_000, NOW, 0, true).reason()).as("ends_at is exclusive")
                .isEqualTo(Reason.EXPIRED);
        assertThat(minimum.evaluate(LINES, 10_000, NOW, 0, true).reason()).isEqualTo(Reason.MIN_ORDER_NOT_MET);
    }

    @Test
    void usageLimits() {
        CouponRules full = rules(CouponType.FIXED, 100, null, 0, 5, 5, null, Set.of(), Set.of());
        CouponRules perCustomer = rules(CouponType.FIXED, 100, null, 0, null, 0, 1, Set.of(), Set.of());

        assertThat(full.evaluate(LINES, 10_000, NOW, 0, true).reason()).isEqualTo(Reason.USAGE_LIMIT_REACHED);
        assertThat(full.evaluate(LINES, 10_000, NOW, 0, false).valid())
                .as("caller already took the use atomically").isTrue();
        assertThat(perCustomer.evaluate(LINES, 10_000, NOW, 1, true).reason())
                .isEqualTo(Reason.CUSTOMER_LIMIT_REACHED);
        assertThat(perCustomer.evaluate(LINES, 10_000, NOW, 0, true).valid()).isTrue();
    }
}
