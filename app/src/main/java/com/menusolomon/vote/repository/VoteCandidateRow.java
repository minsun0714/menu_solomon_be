package com.menusolomon.vote.repository;

import com.menusolomon.vote.domain.CandidateSource;
import com.menusolomon.vote.dto.RestaurantResponse;
import com.menusolomon.vote.dto.VoteCandidateResponse;
import java.math.BigDecimal;

public record VoteCandidateRow(Long id, Long sessionId, CandidateSource source, Long restaurantId, String kakaoPlaceId, String name, String address,
        BigDecimal latitude, BigDecimal longitude, String category, String kakaoPlaceUrl, Double averageRating) {
    public VoteCandidateResponse response() { return new VoteCandidateResponse("candidate_"+id, "vote_"+sessionId, "restaurant_"+restaurantId, source,
        new RestaurantResponse("restaurant_"+restaurantId, kakaoPlaceId, name, address, latitude, longitude, category, kakaoPlaceUrl), averageRating == null ? 0 : averageRating); }
}
