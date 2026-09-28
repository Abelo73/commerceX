package com.commercex.promotion.application;

import com.commercex.category.CategoryApi;
import com.commercex.promotion.CouponEvaluation;
import com.commercex.promotion.CouponInvalidException;
import com.commercex.promotion.DiscountLine;
import com.commercex.promotion.PromotionApi;
import com.commercex.promotion.domain.Coupon;
import com.commercex.promotion.domain.CouponRules;
import com.commercex.promotion.domain.CouponRules.Outcome;
import com.commercex.promotion.domain.CouponRules.Reason;
import com.commercex.promotion.infrastructure.CouponRepository;
import com.commercex.promotion.infrastructure.RedemptionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionService implements PromotionApi {

    private final CouponRepository coupons;
    private final RedemptionRepository redemptions;
    private final CategoryApi categories;
    private final MeterRegistry meters;
    private final Clock clock;

    PromotionService(CouponRepository coupons, RedemptionRepository redemptions, CategoryApi categories,
            MeterRegistry meters, Clock clock) {
        this.coupons = coupons;
        this.redemptions = redemptions;
        this.categories = categories;
        this.meters = meters;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public CouponEvaluation evaluate(String code, Long userId, List<DiscountLine> lines, long subtotalMinor) {
        String normalized = normalize(code);
        Optional<Coupon> coupon = coupons.findByCode(normalized);
        if (coupon.isEmpty()) {
            return evaluation(normalized, new Outcome(Reason.NOT_FOUND, 0));
        }
        int customerUses = userId == null ? 0 : redemptions.activeUses(coupon.get().getId(), userId);
        return evaluation(normalized, rules(coupon.get()).evaluate(lines, subtotalMinor, clock.instant(),
                customerUses, true));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long redeem(String code, long userId, long orderId, List<DiscountLine> lines, long subtotalMinor) {
        String normalized = normalize(code);
        Coupon coupon = coupons.findByCode(normalized)
                .orElseThrow(() -> invalid(normalized, Reason.NOT_FOUND));
        if (coupons.takeUse(coupon.getId()) == 0) {
            meters.counter("commercex.coupons.redemptions", "outcome", "limit_reached").increment();
            throw invalid(normalized, Reason.USAGE_LIMIT_REACHED);
        }
        // The coupon row is locked by takeUse, so this count cannot race with the same customer's other order.
        int customerUses = redemptions.activeUses(coupon.getId(), userId);
        Outcome outcome = rules(coupon).evaluate(lines, subtotalMinor, clock.instant(), customerUses, false);
        if (!outcome.valid()) {
            throw new CouponInvalidException(evaluation(normalized, outcome)); // rolls back takeUse
        }
        redemptions.insert(coupon.getId(), userId, orderId, outcome.discountMinor());
        meters.counter("commercex.coupons.redemptions", "outcome", "redeemed").increment();
        return outcome.discountMinor();
    }

    /** Gives the use back when an order is cancelled. Idempotent. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseForOrder(long orderId) {
        redemptions.release(orderId).ifPresent(coupons::returnUse);
    }

    private CouponRules rules(Coupon coupon) {
        Set<Long> expanded = new HashSet<>();
        for (long categoryId : coupon.getCategoryIds()) {
            expanded.addAll(categories.subtreeIds(categoryId));
        }
        return coupon.rules(expanded);
    }

    static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static CouponEvaluation evaluation(String code, Outcome outcome) {
        return new CouponEvaluation(code, outcome.valid(), outcome.discountMinor(), outcome.reason().name(),
                outcome.reason().message);
    }

    private static CouponInvalidException invalid(String code, Reason reason) {
        return new CouponInvalidException(evaluation(code, new Outcome(reason, 0)));
    }
}
