package com.menusolomon.restaurant.repository;

import java.math.BigDecimal;
import java.time.Instant;

public record TeamRestaurantRow(Long id, Long restaurantId, String kakaoPlaceId, String name,
        String address, BigDecimal latitude, BigDecimal longitude, String category, String kakaoPlaceUrl,
        String registeredByNickname, Instant createdAt, Double averageRating, Long reviewCount,
        String latestNickname, Integer latestRating, String latestContent, Instant latestUpdatedAt) {}
