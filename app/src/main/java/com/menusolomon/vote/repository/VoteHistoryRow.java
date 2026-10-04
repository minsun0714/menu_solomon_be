package com.menusolomon.vote.repository;

import com.menusolomon.vote.domain.ConfirmationType;
import com.menusolomon.vote.dto.LunchHistoryResponse;
import com.menusolomon.vote.dto.RestaurantResponse;
import java.math.BigDecimal;
import java.time.Instant;

public record VoteHistoryRow(Long decisionId, Long sessionId, Instant confirmedAt, ConfirmationType type, String nickname,
        Long restaurantId, String kakaoPlaceId, String name, String address, BigDecimal latitude, BigDecimal longitude, String category, String kakaoPlaceUrl) {
    public LunchHistoryResponse response() { return new LunchHistoryResponse("decision_"+decisionId, "vote_"+sessionId, confirmedAt,
        new RestaurantResponse("restaurant_"+restaurantId, kakaoPlaceId, name, address, latitude, longitude, category, kakaoPlaceUrl), type, nickname); }
}
