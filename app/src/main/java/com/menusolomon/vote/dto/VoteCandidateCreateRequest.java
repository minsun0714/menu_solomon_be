package com.menusolomon.vote.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public record VoteCandidateCreateRequest(@NotNull @Positive Long teamRestaurantId) {}
