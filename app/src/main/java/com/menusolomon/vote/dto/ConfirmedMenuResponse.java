package com.menusolomon.vote.dto;

import com.menusolomon.vote.repository.ConfirmedMenuRow;
import java.time.Instant;
public record ConfirmedMenuResponse(String candidateId, String restaurantId, String name, long voteCount, Instant confirmedAt) {
    public static ConfirmedMenuResponse from(ConfirmedMenuRow row) {
        return new ConfirmedMenuResponse("candidate_" + row.candidateId(), "restaurant_" + row.restaurantId(),
                row.name(), row.voteCount(), row.confirmedAt());
    }
}
