package com.menusolomon.vote.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record VoteCreateRequest(@NotNull Instant closesAt) {}
