package com.menusolomon.vote.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record BallotSaveRequest(@NotEmpty List<@NotNull @Positive Long> candidateIds) {}
