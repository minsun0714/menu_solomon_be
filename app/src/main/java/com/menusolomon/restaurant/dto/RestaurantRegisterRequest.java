package com.menusolomon.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

public record RestaurantRegisterRequest(@NotBlank String kakaoPlaceId) {}
