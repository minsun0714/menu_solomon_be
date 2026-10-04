package com.menusolomon.vote.dto;

import java.math.BigDecimal;

public record RestaurantResponse(String id, String kakaoPlaceId, String name, String address,
        BigDecimal latitude, BigDecimal longitude, String category, String kakaoPlaceUrl) {
    public static RestaurantResponse from(com.menusolomon.restaurant.domain.Restaurant restaurant) {
        return new RestaurantResponse("restaurant_"+restaurant.getId(), restaurant.getKakaoPlaceId(), restaurant.getName(),
                restaurant.getAddress(), restaurant.getLatitude(), restaurant.getLongitude(), restaurant.getCategory(), restaurant.getKakaoPlaceUrl());
    }
}
