package com.menusolomon.review.dto;

import com.menusolomon.review.domain.Review;
import java.time.Instant;

public record ReviewResponse(String id, String teamRestaurantId, int rating, String content,
        String authorNickname, Instant createdAt, Instant updatedAt) {
    public static ReviewResponse from(Review review, String authorNickname) {
        return new ReviewResponse("review_" + review.getId(), "teamRestaurant_" + review.getTeamRestaurantId(),
                review.getRating(), review.getContent(), authorNickname, review.getCreatedAt(), review.getUpdatedAt());
    }
}
