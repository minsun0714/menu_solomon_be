package com.menusolomon.vote.dto;

import com.menusolomon.vote.domain.VoteRecord;
import java.time.Instant;

public record BallotResponse(String id, String sessionId, String candidateId, String teamMemberId, Instant createdAt, Instant updatedAt) {
    public static BallotResponse from(VoteRecord ballot) {
        return new BallotResponse("ballot_"+ballot.getId(), "vote_"+ballot.getSessionId(), "candidate_"+ballot.getCandidateId(),
                "member_"+ballot.getTeamMemberId(), ballot.getCreatedAt(), ballot.getUpdatedAt());
    }
}
