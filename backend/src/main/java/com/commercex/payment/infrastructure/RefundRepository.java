package com.commercex.payment.infrastructure;

import com.commercex.payment.domain.Refund;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByPaymentIdAndIdempotencyKey(long paymentId, String idempotencyKey);

    List<Refund> findByPaymentIdOrderByCreatedAtAsc(long paymentId);

    @Query("select coalesce(sum(r.amountMinor), 0) from Refund r where r.paymentId = :paymentId "
            + "and r.status = com.commercex.payment.domain.Refund.Status.PENDING")
    long pendingAmount(long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :id")
    Optional<Refund> findByIdForUpdate(long id);

    @Query("select r.id from Refund r where r.status = com.commercex.payment.domain.Refund.Status.PENDING "
            + "and r.updatedAt < :before order by r.updatedAt")
    List<Long> findPendingIds(Instant before, Limit limit);
}
