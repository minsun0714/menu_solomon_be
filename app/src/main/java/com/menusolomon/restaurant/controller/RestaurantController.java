package com.menusolomon.restaurant.controller;

import com.menusolomon.common.response.ApiResponse;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.restaurant.dto.RestaurantDetailResponse;
import com.menusolomon.restaurant.dto.RestaurantListResponse;
import com.menusolomon.restaurant.dto.RestaurantRegisterRequest;
import com.menusolomon.restaurant.dto.RestaurantRegisterResponse;
import com.menusolomon.restaurant.dto.RestaurantSort;
import com.menusolomon.restaurant.service.RestaurantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams/{teamId}/restaurants")
public class RestaurantController {
    private final RestaurantService service;

    public RestaurantController(RestaurantService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RestaurantRegisterResponse>> register(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token,
            @Valid @RequestBody RestaurantRegisterRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.of(service.registerRestaurant(teamId, token, request.kakaoPlaceId())));
    }

    @GetMapping
    public ApiResponse<RestaurantListResponse> list(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "LATEST") RestaurantSort sort) {
        return ApiResponse.of(service.getRestaurants(teamId, token, keyword, category, sort));
    }

    @GetMapping("/{teamRestaurantId}")
    public ApiResponse<RestaurantDetailResponse> detail(@PathVariable Long teamId, @PathVariable Long teamRestaurantId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(service.getRestaurant(teamId, teamRestaurantId, token));
    }

    @DeleteMapping("/{teamRestaurantId}")
    public ResponseEntity<Void> delete(@PathVariable Long teamId, @PathVariable Long teamRestaurantId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        service.deleteRestaurant(teamId, teamRestaurantId, token);
        return ResponseEntity.noContent().build();
    }
}
