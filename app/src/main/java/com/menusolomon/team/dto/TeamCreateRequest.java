package com.menusolomon.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamCreateRequest(
        @NotBlank(message = "팀 이름은 필수입니다.")
        @Size(max = 30, message = "팀 이름은 30자 이하여야 합니다.")
        String name,
        @Size(max = 100, message = "팀 설명은 100자 이하여야 합니다.")
        String description
) {
}
