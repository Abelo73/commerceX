package com.commercex.promotion.infrastructure;

import com.commercex.promotion.domain.Coupon;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    Page<Coupon> findByCodeContaining(String fragment, Pageable pageable);

    /**
     * Takes one use atomically. The UPDATE also row-locks the coupon until commit, which serializes
     * concurrent redemptions of the same code (so the per-customer count read afterwards is exact).
     */
    @Modifying
    @Query(value = """
            UPDATE coupons SET used_count = used_count + 1
            WHERE id = :id AND (usage_limit IS NULL OR used_count < usage_limit)""", nativeQuery = true)
    int takeUse(long id);

    @Modifying
    @Query(value = "UPDATE coupons SET used_count = used_count - 1 WHERE id = :id AND used_count > 0",
            nativeQuery = true)
    int returnUse(long id);
}
