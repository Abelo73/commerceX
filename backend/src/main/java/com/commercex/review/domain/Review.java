package com.commercex.review.domain;

import com.commercex.common.error.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reviews_seq")
    @SequenceGenerator(name = "reviews_seq", sequenceName = "reviews_seq", allocationSize = 50)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private long productId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private long orderId;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(nullable = false)
    private short rating;

    @Column(nullable = false)
    private String title;

    private String comment;

    @Column(name = "verified_purchase", nullable = false, updatable = false)
    private boolean verifiedPurchase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "moderated_by")
    private Long moderatedBy;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Review() {
    }

    /** Only verified purchasers can review, so {@code verifiedPurchase} is always true today. */
    public static Review submit(long productId, long userId, long orderId, String authorName, int rating,
            String title, String comment, Instant now) {
        Review review = new Review();
        review.productId = productId;
        review.userId = userId;
        review.orderId = orderId;
        review.authorName = authorName;
        review.verifiedPurchase = true;
        review.createdAt = now;
        review.edit(rating, title, comment, now);
        return review;
    }

    /** Any edit goes back to moderation. */
    public void edit(int rating, String title, String comment, Instant now) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be 1..5");
        }
        this.rating = (short) rating;
        this.title = title;
        this.comment = comment;
        this.status = ReviewStatus.PENDING;
        this.rejectionReason = null;
        this.moderatedBy = null;
        this.moderatedAt = null;
        this.updatedAt = now;
    }

    public void approve(long moderatorId, Instant now) {
        moderate(ReviewStatus.APPROVED, null, moderatorId, now);
    }

    public void reject(String reason, long moderatorId, Instant now) {
        moderate(ReviewStatus.REJECTED, reason, moderatorId, now);
    }

    private void moderate(ReviewStatus target, String reason, long moderatorId, Instant now) {
        if (status != ReviewStatus.PENDING) {
            throw new ConflictException("Review " + id + " was already " + status);
        }
        this.status = target;
        this.rejectionReason = reason;
        this.moderatedBy = moderatorId;
        this.moderatedAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public long getProductId() {
        return productId;
    }

    public long getUserId() {
        return userId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public int getRating() {
        return rating;
    }

    public String getTitle() {
        return title;
    }

    public String getComment() {
        return comment;
    }

    public boolean isVerifiedPurchase() {
        return verifiedPurchase;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
