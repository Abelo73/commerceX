package com.commercex.review.api;

import com.commercex.common.security.CurrentUser;
import com.commercex.common.web.PageResponse;
import com.commercex.review.application.ReviewService;
import com.commercex.review.application.ReviewViews.ModerationItem;
import com.commercex.review.domain.ReviewStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@Tag(name = "Admin: Reviews")
@SecurityRequirement(name = "bearer")
class AdminReviewController {

    private final ReviewService reviewService;

    AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }

    @GetMapping
    PageResponse<ModerationItem> queue(@RequestParam(defaultValue = "PENDING") ReviewStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(reviewService.moderationQueue(status, page, size), r -> r);
    }

    @PostMapping("/{id}/approve")
    ModerationItem approve(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return reviewService.approve(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/reject")
    ModerationItem reject(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody RejectRequest body) {
        return reviewService.reject(CurrentUser.id(jwt), id, body.reason());
    }
}
