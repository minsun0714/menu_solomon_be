package com.menusolomon.restaurant.dto;

import com.menusolomon.restaurant.domain.Restaurant;
import com.menusolomon.restaurant.domain.TeamRestaurant;
import java.math.BigDecimal;

public record RestaurantRegisterResponse(String id, String restaurantId, String kakaoPlaceId,
        String name, String address, BigDecimal latitude, BigDecimal longitude,
        String category, String kakaoPlaceUrl) {
    public static RestaurantRegisterResponse from(TeamRestaurant link, Restaurant restaurant) {
        return new RestaurantRegisterResponse("teamRestaurant_" + link.getId(), "restaurant_" + restaurant.getId(),
                restaurant.getKakaoPlaceId(), restaurant.getName(), restaurant.getAddress(),
                restaurant.getLatitude(), restaurant.getLongitude(), restaurant.getCategory(), restaurant.getKakaoPlaceUrl());
    }
}
