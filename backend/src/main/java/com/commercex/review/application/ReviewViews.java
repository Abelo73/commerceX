package com.commercex.review.application;

import com.commercex.review.domain.Review;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public final class ReviewViews {

    private ReviewViews() {
    }

    /** Public: the author is shown as first name + last initial only. */
    public record PublicReview(long id, String authorName, int rating, String title, String comment,
            boolean verifiedPurchase, Instant createdAt) {

        static PublicReview from(Review r) {
            return new PublicReview(r.getId(), r.getAuthorName(), r.getRating(), r.getTitle(), r.getComment(),
                    r.isVerifiedPurchase(), r.getCreatedAt());
        }
    }

    public record OwnReview(long id, long productId, int rating, String title, String comment, String status,
            String rejectionReason, Instant createdAt, Instant updatedAt) {

        static OwnReview from(Review r) {
            return new OwnReview(r.getId(), r.getProductId(), r.getRating(), r.getTitle(), r.getComment(),
                    r.getStatus().name(), r.getRejectionReason(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    public record ModerationItem(long id, long productId, long userId, String authorName, int rating, String title,
            String comment, String status, Instant createdAt) {

        static ModerationItem from(Review r) {
            return new ModerationItem(r.getId(), r.getProductId(), r.getUserId(), r.getAuthorName(), r.getRating(),
                    r.getTitle(), r.getComment(), r.getStatus().name(), r.getCreatedAt());
        }
    }

    /** {@code distribution}: stars (1..5) → number of approved reviews. */
    public record RatingSummary(BigDecimal average, int count, Map<Integer, Long> distribution) {
    }
}
