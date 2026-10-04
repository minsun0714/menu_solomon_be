package com.menusolomon.vote.dto;

import com.menusolomon.vote.repository.VoteHistoryRow;
import java.time.Instant;
public record VoteHistoryResponse(String voteId, String title, Instant confirmedAt, String restaurantId,
        String restaurantName, long voteCount, long participantCount) {
    public static VoteHistoryResponse from(VoteHistoryRow row) {
        return new VoteHistoryResponse("vote_" + row.voteId(), row.title(), row.confirmedAt(), "restaurant_" + row.restaurantId(),
                row.restaurantName(), row.voteCount(), row.participantCount());
    }
}
