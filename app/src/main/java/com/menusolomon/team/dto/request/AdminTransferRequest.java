package com.menusolomon.team.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminTransferRequest(@NotBlank String memberId) {
}
