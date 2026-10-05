package com.menusolomon.vote.dto;

import com.menusolomon.vote.domain.ConfirmationType;
import java.time.Instant;

public record LunchHistoryResponse(String decisionId, String sessionId, Instant confirmedAt, RestaurantResponse restaurant, ConfirmationType confirmationType, String confirmedByNickname) {}
