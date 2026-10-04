package com.menusolomon.vote.dto;

import com.menusolomon.vote.domain.ConfirmationType;
import com.menusolomon.vote.domain.ConfirmedMenu;
import java.time.Instant;

public record DecisionResponse(String id, String sessionId, String restaurantId, String confirmedByTeamMemberId,
        ConfirmationType confirmationType, Instant confirmedAt) {
    public static DecisionResponse from(ConfirmedMenu decision) {
        return new DecisionResponse("decision_"+decision.getId(), "vote_"+decision.getSessionId(), "restaurant_"+decision.getRestaurantId(),
                decision.getConfirmedByTeamMemberId()==null ? null : "member_"+decision.getConfirmedByTeamMemberId(),
                decision.getConfirmationType(), decision.getConfirmedAt());
    }
}
