package com.menusolomon.restaurant.service;

import com.menusolomon.restaurant.dto.RestaurantDetailResponse;
import com.menusolomon.restaurant.dto.RestaurantListResponse;
import com.menusolomon.restaurant.dto.RestaurantRegisterResponse;
import com.menusolomon.restaurant.dto.RestaurantSort;

public interface RestaurantService {
    RestaurantRegisterResponse registerRestaurant(Long teamId, String rawSessionToken, String kakaoPlaceId);
    RestaurantListResponse getRestaurants(Long teamId, String rawSessionToken, String keyword, String category, RestaurantSort sort);
    RestaurantDetailResponse getRestaurant(Long teamId, Long teamRestaurantId, String rawSessionToken);
    void deleteRestaurant(Long teamId, Long teamRestaurantId, String rawSessionToken);
}
