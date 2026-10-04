package com.menusolomon.review.controller;

import com.menusolomon.common.response.ApiResponse;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.review.dto.ReviewListItem;
import com.menusolomon.review.dto.ReviewResponse;
import com.menusolomon.review.dto.ReviewSaveRequest;
import com.menusolomon.review.service.ReviewService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams/{teamId}/restaurants/{teamRestaurantId}/reviews")
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<ReviewResponse>> save(@PathVariable Long teamId,
            @PathVariable Long teamRestaurantId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token,
            @Valid @RequestBody ReviewSaveRequest request) {
        var result = service.saveMyReview(teamId, teamRestaurantId, token, request.rating(), request.content());
        return ResponseEntity.status(result.created() ? 201 : 200).body(ApiResponse.of(result.review()));
    }

    @GetMapping
    public ApiResponse<List<ReviewListItem>> list(@PathVariable Long teamId,
            @PathVariable Long teamRestaurantId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(service.getReviews(teamId, teamRestaurantId, token));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> delete(@PathVariable Long teamId, @PathVariable Long teamRestaurantId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        service.deleteMyReview(teamId, teamRestaurantId, token);
        return ResponseEntity.noContent().build();
    }
}
