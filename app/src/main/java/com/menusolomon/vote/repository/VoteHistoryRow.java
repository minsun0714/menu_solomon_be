package com.menusolomon.vote.repository;

import java.time.Instant;

public record VoteHistoryRow(Long voteId, String title, Instant confirmedAt, Long restaurantId,
        String restaurantName, Long voteCount, Long participantCount) {}
