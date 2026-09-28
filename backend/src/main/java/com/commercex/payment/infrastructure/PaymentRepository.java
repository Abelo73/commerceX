package com.commercex.payment.infrastructure;

import com.commercex.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderIdAndIdempotencyKey(long orderId, String idempotencyKey);

    @Query("select p from Payment p where p.orderId = :orderId and p.status in "
            + "(com.commercex.payment.domain.PaymentStatus.PENDING, "
            + "com.commercex.payment.domain.PaymentStatus.REQUIRES_ACTION, "
            + "com.commercex.payment.domain.PaymentStatus.SUCCEEDED)")
    Optional<Payment> findActiveByOrderId(long orderId);

    List<Payment> findByOrderIdOrderByCreatedAtAsc(long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(long id);

    @Query("select p.id from Payment p where p.status in (com.commercex.payment.domain.PaymentStatus.PENDING, "
            + "com.commercex.payment.domain.PaymentStatus.REQUIRES_ACTION) and p.updatedAt < :before "
            + "order by p.updatedAt")
    List<Long> findUnsettledIds(Instant before, Limit limit);
}
