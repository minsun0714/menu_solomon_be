package com.menusolomon.team.dto;

import jakarta.validation.constraints.NotBlank;

public record TeamUpdateRequest(@NotBlank String name, String description) {
    public TeamUpdateRequest {
        description = description == null ? "" : description;
    }
}
