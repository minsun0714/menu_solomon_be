package com.menusolomon.vote.dto;

import com.menusolomon.vote.domain.LunchVoteSession;
import com.menusolomon.vote.domain.VoteStatus;
import java.time.Instant;

public record VoteSessionResponse(String id, String teamId, String name, String createdByTeamMemberId,
        VoteStatus status, Instant closesAt, Instant createdAt) {
    public static VoteSessionResponse from(LunchVoteSession vote) {
        return new VoteSessionResponse("vote_"+vote.getId(), "team_"+vote.getTeamId(), vote.getName(),
                "member_"+vote.getCreatedByTeamMemberId(), vote.getStatus(), vote.getClosesAt(), vote.getCreatedAt());
    }
}
