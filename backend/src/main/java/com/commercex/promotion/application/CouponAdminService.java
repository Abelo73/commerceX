package com.commercex.promotion.application;

import com.commercex.common.audit.AuditLog;
import com.commercex.common.error.ConflictException;
import com.commercex.common.error.NotFoundException;
import com.commercex.common.error.ValidationException;
import com.commercex.common.persistence.Versions;
import com.commercex.promotion.domain.Coupon;
import com.commercex.promotion.domain.CouponType;
import com.commercex.promotion.infrastructure.CouponRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coupon management (ADMIN). Coupons are never deleted once created; deactivate them instead. */
@Service
public class CouponAdminService {

    public record CouponView(long id, String code, String description, String type, long value,
            Long maxDiscountMinor, long minOrderMinor, Instant startsAt, Instant endsAt, Integer usageLimit,
            int usedCount, Integer perCustomerLimit, boolean active, List<Long> productIds, List<Long> categoryIds,
            long version) {

        static CouponView from(Coupon c) {
            return new CouponView(c.getId(), c.getCode(), c.getDescription(), c.getType().name(), c.getValue(),
                    c.getMaxDiscountMinor(), c.getMinOrderMinor(), c.getStartsAt(), c.getEndsAt(), c.getUsageLimit(),
                    c.getUsedCount(), c.getPerCustomerLimit(), c.isActive(),
                    c.getProductIds().stream().sorted().toList(), c.getCategoryIds().stream().sorted().toList(),
                    c.getVersion());
        }
    }

    private final CouponRepository coupons;
    private final AuditLog auditLog;
    private final Clock clock;

    CouponAdminService(CouponRepository coupons, AuditLog auditLog, Clock clock) {
        this.coupons = coupons;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<CouponView> list(String codeQuery, int page, int size) {
        PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Coupon> result = codeQuery == null || codeQuery.isBlank()
                ? coupons.findAll(request)
                : coupons.findByCodeContaining(PromotionService.normalize(codeQuery), request);
        return result.map(CouponView::from);
    }

    @Transactional(readOnly = true)
    public CouponView get(long id) {
        return CouponView.from(load(id));
    }

    @Transactional
    public CouponView create(long actorId, String code, Coupon.Definition definition) {
        String normalized = PromotionService.normalize(code);
        if (coupons.existsByCode(normalized)) {
            throw new ConflictException("Coupon code '" + normalized + "' already exists");
        }
        validate(definition);
        Coupon coupon = coupons.saveAndFlush(Coupon.create(normalized, definition, clock.instant()));
        auditLog.record(actorId, "COUPON_CREATED", "coupon", coupon.getId(), Map.of("code", normalized));
        return CouponView.from(coupon);
    }

    @Transactional
    public CouponView update(long actorId, long id, Coupon.Definition definition, Long version) {
        Coupon coupon = load(id);
        Versions.require(coupon.getVersion(), version);
        validate(definition);
        if (definition.usageLimit() != null && definition.usageLimit() < coupon.getUsedCount()) {
            throw new ConflictException("Usage limit cannot be below the " + coupon.getUsedCount() + " uses taken");
        }
        coupon.update(definition, clock.instant());
        coupons.flush();
        auditLog.record(actorId, "COUPON_UPDATED", "coupon", id, Map.of("code", coupon.getCode()));
        return CouponView.from(coupon);
    }

    private static void validate(Coupon.Definition d) {
        if (d.type() == CouponType.PERCENTAGE && d.value() > 10_000) {
            throw new ValidationException("value", "percentage is at most 10000 (100%)");
        }
        if (d.endsAt() != null && !d.endsAt().isAfter(d.startsAt())) {
            throw new ValidationException("endsAt", "must be after startsAt");
        }
    }

    private Coupon load(long id) {
        return coupons.findById(id).orElseThrow(() -> new NotFoundException("Coupon", id));
    }
}
