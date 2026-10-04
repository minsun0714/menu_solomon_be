package com.menusolomon.vote.dto;

import com.menusolomon.vote.repository.VoteSummaryRow;
import java.time.Instant;
public record VoteSummaryResponse(String id, String title, String status, Instant createdAt,
        long participantCount, long candidateCount, boolean myParticipation, String myVoteCandidateId) {
    public static VoteSummaryResponse from(VoteSummaryRow row) {
        return new VoteSummaryResponse("vote_" + row.id(), row.title(), row.status().name(), row.createdAt(),
                row.participantCount(), row.candidateCount(), row.myParticipation(),
                row.myVoteCandidateId() == null ? null : "candidate_" + row.myVoteCandidateId());
    }
}
