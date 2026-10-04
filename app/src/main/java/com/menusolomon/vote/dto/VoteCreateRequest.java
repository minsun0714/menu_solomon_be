package com.menusolomon.vote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record VoteCreateRequest(@NotBlank @Size(max = 100) String title) {}
