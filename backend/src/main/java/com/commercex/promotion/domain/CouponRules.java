package com.commercex.promotion.domain;

import com.commercex.promotion.DiscountLine;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Pure coupon evaluation (no I/O), so every rule is unit-testable.
 *
 * @param categoryIds restriction categories already expanded to include their subcategories
 */
public record CouponRules(
        boolean active,
        Instant startsAt,
        Instant endsAt,
        CouponType type,
        long value,
        Long maxDiscountMinor,
        long minOrderMinor,
        Integer usageLimit,
        int usedCount,
        Integer perCustomerLimit,
        Set<Long> productIds,
        Set<Long> categoryIds) {

    public enum Reason {
        OK("Coupon applied"),
        NOT_FOUND("This code does not exist"),
        INACTIVE("This code is no longer active"),
        NOT_STARTED("This code is not valid yet"),
        EXPIRED("This code has expired"),
        USAGE_LIMIT_REACHED("This code has been fully redeemed"),
        CUSTOMER_LIMIT_REACHED("You have already used this code"),
        MIN_ORDER_NOT_MET("Your order does not reach the minimum amount for this code"),
        NOT_APPLICABLE("This code does not apply to the items in your cart");

        public final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    public record Outcome(Reason reason, long discountMinor) {

        public boolean valid() {
            return reason == Reason.OK;
        }
    }

    /**
     * @param customerUses       active redemptions by this customer, or 0 when unknown (guest)
     * @param checkGlobalLimit   false when the caller has already reserved a use atomically
     */
    public Outcome evaluate(List<DiscountLine> lines, long subtotalMinor, Instant now, int customerUses,
            boolean checkGlobalLimit) {
        if (!active) {
            return fail(Reason.INACTIVE);
        }
        if (now.isBefore(startsAt)) {
            return fail(Reason.NOT_STARTED);
        }
        if (endsAt != null && !now.isBefore(endsAt)) {
            return fail(Reason.EXPIRED);
        }
        if (checkGlobalLimit && usageLimit != null && usedCount >= usageLimit) {
            return fail(Reason.USAGE_LIMIT_REACHED);
        }
        if (perCustomerLimit != null && customerUses >= perCustomerLimit) {
            return fail(Reason.CUSTOMER_LIMIT_REACHED);
        }
        if (subtotalMinor < minOrderMinor) {
            return fail(Reason.MIN_ORDER_NOT_MET);
        }
        long eligible = lines.stream().filter(this::eligible).mapToLong(DiscountLine::lineTotalMinor).sum();
        if (eligible <= 0) {
            return fail(Reason.NOT_APPLICABLE);
        }
        long discount = switch (type) {
            // Half-up rounding to the minor unit.
            case PERCENTAGE -> Math.floorDiv(Math.multiplyExact(eligible, value) + 5_000, 10_000);
            case FIXED -> value;
        };
        if (maxDiscountMinor != null) {
            discount = Math.min(discount, maxDiscountMinor);
        }
        discount = Math.min(discount, eligible); // never discount more than the eligible items cost
        return discount > 0 ? new Outcome(Reason.OK, discount) : fail(Reason.NOT_APPLICABLE);
    }

    private boolean eligible(DiscountLine line) {
        boolean restricted = !productIds.isEmpty() || !categoryIds.isEmpty();
        return !restricted || productIds.contains(line.productId()) || categoryIds.contains(line.categoryId());
    }

    private static Outcome fail(Reason reason) {
        return new Outcome(reason, 0);
    }
}
