package com.commercex.promotion.api;

import com.commercex.common.error.ValidationException;
import com.commercex.common.security.CurrentUser;
import com.commercex.common.web.PageResponse;
import com.commercex.promotion.application.CouponAdminService;
import com.commercex.promotion.application.CouponAdminService.CouponView;
import com.commercex.promotion.domain.Coupon;
import com.commercex.promotion.domain.CouponType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/coupons")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@Tag(name = "Admin: Promotions")
@SecurityRequirement(name = "bearer")
class AdminCouponController {

    private final CouponAdminService couponAdminService;

    AdminCouponController(CouponAdminService couponAdminService) {
        this.couponAdminService = couponAdminService;
    }

    /** PERCENTAGE value in basis points (1250 = 12.5 %), FIXED value in minor units. */
    record CouponRequest(
            @Pattern(regexp = "[A-Za-z0-9_-]{3,32}") String code,
            @Size(max = 500) String description,
            @NotNull CouponType type,
            @Positive @Max(100_000_000_000L) long value,
            @Positive Long maxDiscountMinor,
            @PositiveOrZero Long minOrderMinor,
            @NotNull Instant startsAt,
            Instant endsAt,
            @Positive Integer usageLimit,
            @Positive Integer perCustomerLimit,
            Boolean active,
            @Size(max = 500) Set<@NotNull Long> productIds,
            @Size(max = 100) Set<@NotNull Long> categoryIds,
            Long version) {

        Coupon.Definition definition() {
            return new Coupon.Definition(description, type, value, maxDiscountMinor,
                    minOrderMinor == null ? 0 : minOrderMinor, startsAt, endsAt, usageLimit, perCustomerLimit,
                    active == null || active, productIds == null ? Set.of() : productIds,
                    categoryIds == null ? Set.of() : categoryIds);
        }
    }

    @GetMapping
    PageResponse<CouponView> list(@RequestParam(required = false) @Size(max = 32) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(couponAdminService.list(q, page, size), v -> v);
    }

    @GetMapping("/{id}")
    CouponView get(@PathVariable long id) {
        return couponAdminService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CouponView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CouponRequest body) {
        if (body.code() == null) {
            throw new ValidationException("code", "is required");
        }
        return couponAdminService.create(CurrentUser.id(jwt), body.code(), body.definition());
    }

    /** The code itself is immutable; deactivate with {@code "active": false}. */
    @PutMapping("/{id}")
    CouponView update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody CouponRequest body) {
        return couponAdminService.update(CurrentUser.id(jwt), id, body.definition(), body.version());
    }
}
