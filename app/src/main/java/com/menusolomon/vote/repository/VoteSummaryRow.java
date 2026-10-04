package com.menusolomon.vote.repository;

import com.menusolomon.vote.domain.VoteStatus;
import java.time.Instant;

public record VoteSummaryRow(Long id, Long teamId, String title, VoteStatus status, Instant createdAt,
        Long participantCount, Long candidateCount, Boolean myParticipation, Long myVoteCandidateId) {}
