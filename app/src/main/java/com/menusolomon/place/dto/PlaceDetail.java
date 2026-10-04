package com.menusolomon.place.dto;

import java.math.BigDecimal;

public record PlaceDetail(String kakaoPlaceId, String name, String address,
        BigDecimal latitude, BigDecimal longitude, String category, String kakaoPlaceUrl) {
}
