package com.menusolomon.vote.dto;

import com.menusolomon.vote.domain.LunchVoteSession;
import java.time.Instant;
public record VoteCreateResponse(String id, String teamId, String title, String status, Instant createdAt) {
    public static VoteCreateResponse from(LunchVoteSession vote) {
        return new VoteCreateResponse("vote_" + vote.getId(), "team_" + vote.getTeamId(), vote.getTitle(), vote.getStatus().name(), vote.getCreatedAt());
    }
}
