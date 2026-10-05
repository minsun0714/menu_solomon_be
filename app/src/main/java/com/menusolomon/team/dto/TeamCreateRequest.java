package com.menusolomon.team.dto;

import jakarta.validation.constraints.NotBlank;

public record TeamCreateRequest(
        @NotBlank String name,
        String description
) {
    public TeamCreateRequest {
        description = description == null ? "" : description;
    }
}
