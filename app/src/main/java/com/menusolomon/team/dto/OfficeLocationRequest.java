package com.menusolomon.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record OfficeLocationRequest(@NotBlank String kakaoPlaceId, @NotBlank String name,
        @NotBlank String address, @NotNull BigDecimal latitude, @NotNull BigDecimal longitude) {}
