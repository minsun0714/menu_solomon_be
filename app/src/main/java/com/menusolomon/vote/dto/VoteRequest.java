package com.menusolomon.vote.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public record VoteRequest(@NotNull @Positive Long voteCandidateId) {}
