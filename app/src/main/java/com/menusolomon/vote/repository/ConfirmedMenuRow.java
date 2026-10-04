package com.menusolomon.vote.repository;

import java.time.Instant;

public record ConfirmedMenuRow(Long candidateId, Long restaurantId, String name, Long voteCount, Instant confirmedAt) {}
