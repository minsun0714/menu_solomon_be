package com.menusolomon.vote.dto;

import com.menusolomon.vote.repository.VoteSummaryRow;
import java.time.Instant;
import java.util.List;
public record VoteDetailResponse(String id, String title, String status, Instant createdAt, long participantCount,
        List<VoteParticipantResponse> participants, List<VoteCandidateResponse> candidates, boolean myParticipation,
        String myVoteCandidateId, ConfirmedMenuResponse confirmedMenu) {
    public static VoteDetailResponse from(VoteSummaryRow row, List<VoteParticipantResponse> participants,
            List<VoteCandidateResponse> candidates, ConfirmedMenuResponse confirmedMenu) {
        return new VoteDetailResponse("vote_" + row.id(), row.title(), row.status().name(), row.createdAt(), row.participantCount(),
                participants, candidates, row.myParticipation(), row.myVoteCandidateId() == null ? null : "candidate_" + row.myVoteCandidateId(), confirmedMenu);
    }
}
