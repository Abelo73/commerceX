package com.commercex.review.infrastructure;

import com.commercex.review.domain.Review;
import com.commercex.review.domain.ReviewStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByProductIdAndUserId(long productId, long userId);

    Optional<Review> findByIdAndUserId(long id, long userId);

    List<Review> findByUserIdOrderByCreatedAtDesc(long userId);

    Page<Review> findByProductIdAndStatus(long productId, ReviewStatus status, Pageable pageable);

    Page<Review> findByStatus(ReviewStatus status, Pageable pageable);

    /** rating → count of approved reviews. */
    @Query("select r.rating, count(r) from Review r where r.productId = :productId "
            + "and r.status = com.commercex.review.domain.ReviewStatus.APPROVED group by r.rating")
    List<Object[]> approvedDistribution(long productId);

    /**
     * Serializes rating recomputation per product for the rest of the transaction, so two concurrent
     * moderations cannot each publish a stale average.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtextextended('review-stats:' || CAST(:productId AS TEXT), 0))",
            nativeQuery = true)
    Object lockProductStats(long productId);
}
