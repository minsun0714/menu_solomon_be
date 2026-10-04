package com.menusolomon.vote.dto;

import com.menusolomon.vote.domain.CandidateSource;

public record VoteCandidateResponse(String id, String sessionId, String restaurantId, CandidateSource source, RestaurantResponse restaurant, double averageRating) {}
