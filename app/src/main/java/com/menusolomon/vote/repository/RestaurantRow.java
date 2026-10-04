package com.menusolomon.vote.repository;

import com.menusolomon.vote.dto.RestaurantResponse;
import java.math.BigDecimal;

public record RestaurantRow(Long id, String kakaoPlaceId, String name, String address, BigDecimal latitude, BigDecimal longitude, String category, String kakaoPlaceUrl, Double averageRating) {
    public RestaurantResponse restaurant() { return new RestaurantResponse("restaurant_"+id, kakaoPlaceId, name, address, latitude, longitude, category, kakaoPlaceUrl); }
}
