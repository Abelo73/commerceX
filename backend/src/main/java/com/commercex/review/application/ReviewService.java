package com.commercex.review.application;

import com.commercex.auth.AccountApi;
import com.commercex.auth.CustomerInfo;
import com.commercex.catalog.CatalogApi;
import com.commercex.common.audit.AuditLog;
import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ConflictException;
import com.commercex.common.error.ErrorCode;
import com.commercex.common.error.NotFoundException;
import com.commercex.order.OrderApi;
import com.commercex.review.application.ReviewViews.ModerationItem;
import com.commercex.review.application.ReviewViews.OwnReview;
import com.commercex.review.application.ReviewViews.PublicReview;
import com.commercex.review.application.ReviewViews.RatingSummary;
import com.commercex.review.domain.Review;
import com.commercex.review.domain.ReviewStatus;
import com.commercex.review.infrastructure.ReviewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    public enum ReviewSort { NEWEST, HIGHEST, LOWEST }

    private final ReviewRepository reviews;
    private final OrderApi orders;
    private final CatalogApi catalog;
    private final AccountApi accounts;
    private final AuditLog auditLog;
    private final Clock clock;

    ReviewService(ReviewRepository reviews, OrderApi orders, CatalogApi catalog, AccountApi accounts,
            AuditLog auditLog, Clock clock) {
        this.reviews = reviews;
        this.orders = orders;
        this.catalog = catalog;
        this.accounts = accounts;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    // ---- customer ------------------------------------------------------------------------------------

    /** Only customers with a delivered order containing the product may review it, once. */
    @Transactional
    public OwnReview submit(long userId, long productId, int rating, String title, String comment) {
        if (reviews.existsByProductIdAndUserId(productId, userId)) {
            throw new ConflictException("You have already reviewed this product; edit your review instead");
        }
        long orderId = orders.deliveredOrderContaining(userId, productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN,
                        "Only customers who received this product can review it"));
        CustomerInfo customer = accounts.customer(userId).orElseThrow(() -> new NotFoundException("User", userId));
        Review review = reviews.saveAndFlush(Review.submit(productId, userId, orderId, displayName(customer), rating,
                title.trim(), trimToNull(comment), clock.instant()));
        return OwnReview.from(review);
    }

    @Transactional
    public OwnReview edit(long userId, long reviewId, int rating, String title, String comment) {
        Review review = own(userId, reviewId);
        boolean wasApproved = review.getStatus() == ReviewStatus.APPROVED;
        review.edit(rating, title.trim(), trimToNull(comment), clock.instant());
        reviews.flush();
        if (wasApproved) {
            refreshStats(review.getProductId()); // it leaves the public average until re-approved
        }
        return OwnReview.from(review);
    }

    @Transactional
    public void delete(long userId, long reviewId) {
        Review review = own(userId, reviewId);
        boolean wasApproved = review.getStatus() == ReviewStatus.APPROVED;
        reviews.delete(review);
        reviews.flush();
        if (wasApproved) {
            refreshStats(review.getProductId());
        }
    }

    @Transactional(readOnly = true)
    public List<OwnReview> mine(long userId) {
        return reviews.findByUserIdOrderByCreatedAtDesc(userId).stream().map(OwnReview::from).toList();
    }

    // ---- public --------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<PublicReview> approved(String productSlug, ReviewSort sort, int page, int size) {
        long productId = catalog.publishedProductId(productSlug)
                .orElseThrow(() -> new NotFoundException("Product", productSlug));
        Sort order = switch (sort) {
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt", "id");
            case HIGHEST -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case LOWEST -> Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };
        return reviews.findByProductIdAndStatus(productId, ReviewStatus.APPROVED, PageRequest.of(page, size, order))
                .map(PublicReview::from);
    }

    @Transactional(readOnly = true)
    public RatingSummary summary(String productSlug) {
        long productId = catalog.publishedProductId(productSlug)
                .orElseThrow(() -> new NotFoundException("Product", productSlug));
        return summaryOf(productId);
    }

    // ---- moderation ----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<ModerationItem> moderationQueue(ReviewStatus status, int page, int size) {
        return reviews.findByStatus(status, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt", "id")))
                .map(ModerationItem::from);
    }

    @Transactional
    public ModerationItem approve(long moderatorId, long reviewId) {
        Review review = load(reviewId);
        review.approve(moderatorId, clock.instant());
        reviews.flush();
        refreshStats(review.getProductId());
        auditLog.record(moderatorId, "REVIEW_APPROVED", "review", reviewId, null);
        return ModerationItem.from(review);
    }

    @Transactional
    public ModerationItem reject(long moderatorId, long reviewId, String reason) {
        Review review = load(reviewId);
        review.reject(reason, moderatorId, clock.instant());
        auditLog.record(moderatorId, "REVIEW_REJECTED", "review", reviewId, Map.of("reason", reason));
        return ModerationItem.from(review);
    }

    // ---- internals -----------------------------------------------------------------------------------

    /** Recomputes approved-review stats under a per-product lock and stores them on the catalog product. */
    private void refreshStats(long productId) {
        reviews.lockProductStats(productId);
        RatingSummary summary = summaryOf(productId);
        catalog.updateRating(productId, summary.average(), summary.count());
    }

    private RatingSummary summaryOf(long productId) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int stars = 5; stars >= 1; stars--) {
            distribution.put(stars, 0L);
        }
        long count = 0;
        long sum = 0;
        for (Object[] row : reviews.approvedDistribution(productId)) {
            int stars = ((Number) row[0]).intValue();
            long n = ((Number) row[1]).longValue();
            distribution.put(stars, n);
            count += n;
            sum += stars * n;
        }
        BigDecimal average = count == 0 ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        return new RatingSummary(average, (int) count, distribution);
    }

    private Review own(long userId, long reviewId) {
        return reviews.findByIdAndUserId(reviewId, userId).orElseThrow(() -> new NotFoundException("Review", reviewId));
    }

    private Review load(long reviewId) {
        return reviews.findById(reviewId).orElseThrow(() -> new NotFoundException("Review", reviewId));
    }

    private static String displayName(CustomerInfo customer) {
        String last = customer.lastName();
        return customer.firstName() + (last == null || last.isBlank() ? "" : " " + last.charAt(0) + ".");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
