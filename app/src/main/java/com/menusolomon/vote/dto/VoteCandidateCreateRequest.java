package com.menusolomon.vote.dto;

import com.menusolomon.vote.domain.CandidateSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VoteCandidateCreateRequest(@NotBlank String kakaoPlaceId, @NotNull CandidateSource source) {}
