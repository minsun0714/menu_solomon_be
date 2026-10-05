package com.menusolomon.place.controller;

import com.menusolomon.common.response.ApiResponse;
import com.menusolomon.place.service.PlaceService;
import com.menusolomon.place.dto.PlaceSearchResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/places")
public class PlaceController {
    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping("/search")
    public ApiResponse<PlaceSearchResponse> search(
            @RequestParam @NotBlank String query,
            @RequestParam(defaultValue = "1") @Min(1) @Max(45) int page,
            @RequestParam(defaultValue = "5") @Min(1) @Max(15) int size) {
        return ApiResponse.of(placeService.search(query, page, size));
    }
}
