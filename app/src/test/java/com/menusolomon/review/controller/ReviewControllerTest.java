package com.menusolomon.review.controller;

import static com.menusolomon.review.fixture.ReviewFixture.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.review.dto.ReviewListItem;
import com.menusolomon.review.dto.ReviewResponse;
import com.menusolomon.review.dto.ReviewSaveResult;
import com.menusolomon.review.service.ReviewService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {
    private static final String URL = "/api/teams/team_1/restaurants/teamRestaurant_5/reviews";
    @Autowired MockMvc mvc;
    @MockitoBean ReviewService service;
    private Cookie cookie() { return new Cookie(WebConstants.SESSION_COOKIE_NAME, "token"); }

    @Test
    void saveNewReview_returns201() throws Exception {
        when(service.saveMyReview(1L, 5L, "token", 5, "맛있어요"))
                .thenReturn(new ReviewSaveResult(ReviewResponse.from(review(), "익명 사용자"), true));
        mvc.perform(put(URL + "/me").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"content\":\"맛있어요\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("review_7"))
                .andExpect(jsonPath("$.data.teamRestaurantId").value("teamRestaurant_5"))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.content").value("맛있어요"))
                .andExpect(jsonPath("$.data.authorNickname").value("익명 사용자"))
                .andExpect(jsonPath("$.data.createdAt").value(CREATED.toString()))
                .andExpect(jsonPath("$.data.updatedAt").value(CREATED.toString()))
                .andExpect(header().doesNotExist("Set-Cookie"));
        verify(service).saveMyReview(1L, 5L, "token", 5, "맛있어요");
    }

    @Test
    void updateExistingReview_returns200() throws Exception {
        var review = review();
        review.update(4, "수정", NOW);
        when(service.saveMyReview(1L, 5L, "token", 4, "수정"))
                .thenReturn(new ReviewSaveResult(ReviewResponse.from(review, "익명 사용자"), false));
        mvc.perform(put(URL + "/me").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"content\":\"수정\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("review_7"))
                .andExpect(jsonPath("$.data.rating").value(4))
                .andExpect(jsonPath("$.data.updatedAt").value(NOW.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"rating\":0,\"content\":\"리뷰\"}", "{\"rating\":6,\"content\":\"리뷰\"}",
            "{\"rating\":5,\"content\":\" \"}", "{\"content\":\"리뷰\"}", "{\"rating\":5}"})
    void invalidRatingOrContent_returns400ProblemDetail(String body) throws Exception {
        mvc.perform(put(URL + "/me").cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void contentOver200_returns400ProblemDetail() throws Exception {
        mvc.perform(put(URL + "/me").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"content\":\"" + "가".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void getReviews_returns200() throws Exception {
        when(service.getReviews(1L, 5L, "token")).thenReturn(List.of(ReviewListItem.from(row(1L), 1L)));
        mvc.perform(get(URL).cookie(cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("review_7"))
                .andExpect(jsonPath("$.data[0].isMine").value(true))
                .andExpect(jsonPath("$.data[0].authorNickname").value("익명 사용자"));
    }

    @Test
    void getReviews_nonMember_returns403ProblemDetail() throws Exception {
        when(service.getReviews(1L, 5L, "token")).thenThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        mvc.perform(get(URL).cookie(cookie())).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_TEAM_MEMBER"));
    }

    @Test
    void deleteMyReview_returns204() throws Exception {
        mvc.perform(delete(URL + "/me").cookie(cookie())).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).deleteMyReview(1L, 5L, "token");
    }

    @Test
    void deleteMyReview_notFound_returns404ProblemDetail() throws Exception {
        doThrow(new BusinessException(ErrorCode.REVIEW_NOT_FOUND)).when(service).deleteMyReview(1L, 5L, "token");
        mvc.perform(delete(URL + "/me").cookie(cookie())).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value(ErrorCode.REVIEW_NOT_FOUND.getDetail()));
    }

    @Test
    void missingSession_returns403WithoutCreatingSession() throws Exception {
        when(service.getReviews(1L, 5L, null)).thenThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        mvc.perform(get(URL)).andExpect(status().isForbidden()).andExpect(header().doesNotExist("Set-Cookie"));
        verify(service).getReviews(1L, 5L, null);
    }
}
