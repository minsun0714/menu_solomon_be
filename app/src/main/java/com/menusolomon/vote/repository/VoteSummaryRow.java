package com.menusolomon.vote.repository;

import com.menusolomon.vote.domain.VoteStatus;
import java.time.Instant;

public record VoteSummaryRow(Long id, Long teamId, String name, Long creatorId, String creatorNickname, VoteStatus status, Instant closesAt, Instant createdAt, long participantCount, long candidateCount, long ballotCount) {}
