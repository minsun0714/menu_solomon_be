package com.menusolomon.restaurant.controller;

import static com.menusolomon.restaurant.fixture.RestaurantFixture.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.restaurant.dto.*;
import com.menusolomon.restaurant.service.RestaurantService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RestaurantController.class)
class RestaurantControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean RestaurantService service;
    private Cookie cookie() { return new Cookie(WebConstants.SESSION_COOKIE_NAME, "token"); }

    @Test
    void registerTeamRestaurant_returns201() throws Exception {
        when(service.registerRestaurant(1L, "token", "123"))
                .thenReturn(RestaurantRegisterResponse.from(savedLink(), savedRestaurant()));
        mvc.perform(post("/api/teams/team_1/restaurants").cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kakaoPlaceId\":\"123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("teamRestaurant_5"))
                .andExpect(jsonPath("$.data.restaurantId").value("restaurant_3"))
                .andExpect(jsonPath("$.data.kakaoPlaceId").value("123"))
                .andExpect(jsonPath("$.data.name").value("을지다락"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        verify(service).registerRestaurant(1L, "token", "123");
    }

    @Test
    void registerTeamRestaurant_blankKakaoPlaceId_returns400ProblemDetail() throws Exception {
        mvc.perform(post("/api/teams/1/restaurants").cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kakaoPlaceId\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void registerTeamRestaurant_duplicate_returns409ProblemDetail() throws Exception {
        when(service.registerRestaurant(1L, "token", "123"))
                .thenThrow(new BusinessException(ErrorCode.RESTAURANT_ALREADY_REGISTERED));
        mvc.perform(post("/api/teams/1/restaurants").cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kakaoPlaceId\":\"123\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("RESTAURANT_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.detail").value(ErrorCode.RESTAURANT_ALREADY_REGISTERED.getDetail()));
    }

    @Test
    void getTeamRestaurants_returns200() throws Exception {
        when(service.getRestaurants(1L, "token", null, null, RestaurantSort.LATEST))
                .thenReturn(new RestaurantListResponse(List.of(RestaurantDetailResponse.from(row())), 1L, 2L, Map.of("양식", 1L)));
        mvc.perform(get("/api/teams/1/restaurants").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restaurants[0].registeredByNickname").value("익명 사용자"))
                .andExpect(jsonPath("$.data.restaurants[0].createdAt").value(NOW.toString()))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.categoryCounts.양식").value(1))
                .andExpect(jsonPath("$.data.restaurants[0].averageRating").value(4.5))
                .andExpect(jsonPath("$.data.restaurants[0].reviewCount").value(2))
                .andExpect(jsonPath("$.data.restaurants[0].latestReview.nickname").value("리뷰 작성자"))
                .andExpect(jsonPath("$.data.totalReviewCount").value(2));
    }

    @Test
    void getTeamRestaurants_bindsKeywordCategorySort() throws Exception {
        mvc.perform(get("/api/teams/1/restaurants").cookie(cookie())
                        .param("keyword", "강남").param("category", "한식").param("sort", "NAME"))
                .andExpect(status().isOk());
        verify(service).getRestaurants(1L, "token", "강남", "한식", RestaurantSort.NAME);
    }

    @Test
    void getTeamRestaurants_bindsRatingDesc() throws Exception {
        when(service.getRestaurants(1L, "token", null, null, RestaurantSort.RATING_DESC))
                .thenReturn(new RestaurantListResponse(List.of(RestaurantDetailResponse.from(row())), 1L, 2L, Map.of("양식", 1L)));
        mvc.perform(get("/api/teams/1/restaurants").cookie(cookie()).param("sort", "RATING_DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restaurants[0].averageRating").value(4.5));
        verify(service).getRestaurants(1L, "token", null, null, RestaurantSort.RATING_DESC);
    }

    @Test
    void getTeamRestaurants_unknownSort_returns400ProblemDetail() throws Exception {
        mvc.perform(get("/api/teams/1/restaurants").cookie(cookie()).param("sort", "INVALID"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void getTeamRestaurant_returns200() throws Exception {
        when(service.getRestaurant(1L, 5L, "token")).thenReturn(RestaurantDetailResponse.from(row()));
        mvc.perform(get("/api/teams/team_1/restaurants/teamRestaurant_5").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("teamRestaurant_5"))
                .andExpect(jsonPath("$.data.kakaoPlaceUrl").value("https://place.map.kakao.com/123"))
                .andExpect(jsonPath("$.data.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.reviewCount").value(2));
        verify(service).getRestaurant(1L, 5L, "token");
    }

    @Test
    void getTeamRestaurant_withoutReviews_returnsNullAverageAndLatestReview() throws Exception {
        when(service.getRestaurant(1L, 5L, "token"))
                .thenReturn(RestaurantDetailResponse.from(rowWithoutReviews()));
        mvc.perform(get("/api/teams/1/restaurants/5").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.reviewCount").value(0))
                .andExpect(jsonPath("$.data.latestReview").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void getTeamRestaurant_notFound_returns404ProblemDetail() throws Exception {
        when(service.getRestaurant(1L, 5L, "token")).thenThrow(new BusinessException(ErrorCode.TEAM_RESTAURANT_NOT_FOUND));
        mvc.perform(get("/api/teams/1/restaurants/5").cookie(cookie()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("TEAM_RESTAURANT_NOT_FOUND"));
    }

    @Test
    void deleteTeamRestaurant_returns204() throws Exception {
        mvc.perform(delete("/api/teams/1/restaurants/5").cookie(cookie()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).deleteRestaurant(1L, 5L, "token");
    }

    @Test
    void nonMemberRestaurantAccess_returns403ProblemDetail() throws Exception {
        when(service.getRestaurants(1L, "token", null, null, RestaurantSort.LATEST))
                .thenThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        mvc.perform(get("/api/teams/1/restaurants").cookie(cookie()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("NOT_TEAM_MEMBER"));
    }

    @Test
    void withoutSessionCookie_doesNotCreateUserAndReturns403() throws Exception {
        when(service.getRestaurant(1L, 5L, null)).thenThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        mvc.perform(get("/api/teams/1/restaurants/5"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Set-Cookie"));
        verify(service).getRestaurant(1L, 5L, null);
    }
}
