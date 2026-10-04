package com.menusolomon.vote.dto;

import jakarta.validation.constraints.NotNull;
public record VoteParticipantUpdateRequest(@NotNull Boolean participating) {}
