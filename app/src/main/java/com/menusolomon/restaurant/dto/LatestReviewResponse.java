package com.menusolomon.restaurant.dto;

import java.time.Instant;

public record LatestReviewResponse(String nickname, int rating, String content, Instant updatedAt) {}
