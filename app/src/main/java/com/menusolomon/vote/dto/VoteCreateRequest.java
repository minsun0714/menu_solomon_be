package com.menusolomon.vote.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record VoteCreateRequest(
        @JsonAlias("title") @Size(max = 40) @Pattern(regexp = "(?s).*\\S.*") String name,
        @NotNull Instant closesAt
) {}
