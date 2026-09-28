package com.commercex.order.infrastructure;

import com.commercex.order.domain.Order;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @Query(value = "SELECT 'CX-' || nextval('order_number_seq')", nativeQuery = true)
    String nextOrderNumber();

    Optional<Order> findByOrderNumberAndUserId(String orderNumber, long userId);

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByUserId(long userId, Pageable pageable);

    /**
     * Row lock for every status change: staff actions, customer cancellation, payment callbacks and the
     * expiry job serialize on the order, and each sees the status left by the previous one.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(long id);

    /**
     * One overdue unpaid order, locked. SKIP LOCKED lets several job instances (and concurrent payment
     * callbacks) proceed without waiting on each other or processing the same order twice.
     */
    @Query(value = """
            SELECT id FROM orders
            WHERE status IN ('PENDING_PAYMENT', 'PAYMENT_FAILED') AND payment_due_at < now()
            ORDER BY payment_due_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    Optional<Long> lockNextOverdueUnpaid();
}
