package com.menusolomon.team.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminTransferRequest(@NotNull @Positive Long targetTeamMemberId) {}
