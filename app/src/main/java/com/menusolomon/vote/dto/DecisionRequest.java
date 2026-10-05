package com.menusolomon.vote.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DecisionRequest(@NotNull @Positive Long restaurantId) {}
