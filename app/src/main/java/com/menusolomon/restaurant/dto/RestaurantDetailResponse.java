package com.menusolomon.restaurant.dto;

import com.menusolomon.restaurant.repository.TeamRestaurantRow;
import java.math.BigDecimal;
import java.time.Instant;

public record RestaurantDetailResponse(String id, String restaurantId, String kakaoPlaceId,
        String name, String address, BigDecimal latitude, BigDecimal longitude,
        String category, String kakaoPlaceUrl, String registeredByNickname, Instant createdAt,
        Double averageRating, long reviewCount, LatestReviewResponse latestReview) {
    public static RestaurantDetailResponse from(TeamRestaurantRow row) {
        return new RestaurantDetailResponse("teamRestaurant_" + row.id(), "restaurant_" + row.restaurantId(),
                row.kakaoPlaceId(), row.name(), row.address(), row.latitude(), row.longitude(),
                row.category(), row.kakaoPlaceUrl(), row.registeredByNickname(), row.createdAt(),
                row.averageRating(), row.reviewCount(), row.latestUpdatedAt() == null ? null
                        : new LatestReviewResponse(row.latestNickname(), row.latestRating(), row.latestContent(), row.latestUpdatedAt()));
    }
}
