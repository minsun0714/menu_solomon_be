package com.menusolomon.team.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamCreateRequest(
        @NotBlank @Size(max = 30) String name,
        @Size(max = 100) String description
) {
}
