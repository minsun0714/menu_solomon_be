package com.menusolomon.vote.dto;

import com.menusolomon.vote.domain.VoteStatus;
import java.time.Instant;
import java.util.List;

public record VoteSummaryResponse(String id, String teamId, String name, String createdByTeamMemberId,
        String creatorNickname, VoteStatus status, Instant closesAt, Instant createdAt,
        long participantCount, long candidateCount, long ballotCount, List<String> myBallotCandidateIds) {}
