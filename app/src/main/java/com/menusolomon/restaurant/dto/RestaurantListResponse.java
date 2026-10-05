package com.menusolomon.restaurant.dto;

import java.util.List;
import java.util.Map;

public record RestaurantListResponse(List<RestaurantDetailResponse> restaurants, long totalCount,
        long totalReviewCount, Map<String, Long> categoryCounts) {}
