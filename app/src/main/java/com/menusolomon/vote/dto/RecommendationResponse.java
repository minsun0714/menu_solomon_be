package com.menusolomon.vote.dto;

import java.util.List;

public record RecommendationResponse(List<RecommendationItem> items, Integer nextCursor) {}
