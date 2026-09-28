package com.commercex.review.api;

import com.commercex.common.security.CurrentUser;
import com.commercex.common.web.PageResponse;
import com.commercex.review.application.ReviewService;
import com.commercex.review.application.ReviewService.ReviewSort;
import com.commercex.review.application.ReviewViews.OwnReview;
import com.commercex.review.application.ReviewViews.PublicReview;
import com.commercex.review.application.ReviewViews.RatingSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Reviews")
class ReviewController {

    private final ReviewService reviewService;

    ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    record ReviewRequest(
            @NotNull Long productId,
            @Min(1) @Max(5) int rating,
            @NotBlank @Size(max = 150) String title,
            @Size(max = 5000) String comment) {
    }

    record EditRequest(@Min(1) @Max(5) int rating, @NotBlank @Size(max = 150) String title,
            @Size(max = 5000) String comment) {
    }

    /** Approved reviews plus the rating summary for a product page. */
    record ProductReviews(RatingSummary summary, PageResponse<PublicReview> reviews) {
    }

    @GetMapping("/api/v1/products/{slug}/reviews")
    ResponseEntity<ProductReviews> productReviews(@PathVariable @Size(max = 160) String slug,
            @RequestParam(defaultValue = "NEWEST") ReviewSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .body(new ProductReviews(reviewService.summary(slug),
                        PageResponse.from(reviewService.approved(slug, sort, page, size), r -> r)));
    }

    @PostMapping("/api/v1/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Review a product you received; published after moderation")
    OwnReview submit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReviewRequest body) {
        return reviewService.submit(CurrentUser.id(jwt), body.productId(), body.rating(), body.title(),
                body.comment());
    }

    @PutMapping("/api/v1/reviews/{id}")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Edit your review; it returns to moderation")
    OwnReview edit(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody EditRequest body) {
        return reviewService.edit(CurrentUser.id(jwt), id, body.rating(), body.title(), body.comment());
    }

    @DeleteMapping("/api/v1/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearer")
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        reviewService.delete(CurrentUser.id(jwt), id);
    }

    @GetMapping("/api/v1/me/reviews")
    @SecurityRequirement(name = "bearer")
    List<OwnReview> mine(@AuthenticationPrincipal Jwt jwt) {
        return reviewService.mine(CurrentUser.id(jwt));
    }
}
