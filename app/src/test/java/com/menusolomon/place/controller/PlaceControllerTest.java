package com.menusolomon.place.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.place.service.PlaceService;
import com.menusolomon.place.dto.PlaceDetail;
import com.menusolomon.place.dto.PlaceSearchResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlaceController.class)
class PlaceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PlaceService client;

    @Test
    void search_keyword_usesDefaultPaginationAndReturnsPlaceData() throws Exception {
        when(client.search("역삼 한식", 1, 5)).thenReturn(new PlaceSearchResponse(List.of(
                new PlaceDetail("123", "맛있는 식당", "서울", new BigDecimal("37.5"),
                        new BigDecimal("127.0"), "한식", "https://place.map.kakao.com/123")), 1, 5, 20, 4, true));
        mvc.perform(get("/api/places/search").param("query", "역삼 한식"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].kakaoPlaceId").value("123"))
                .andExpect(jsonPath("$.data.items[0].name").value("맛있는 식당"))
                .andExpect(jsonPath("$.data.items[0].latitude").value(37.5))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(5))
                .andExpect(jsonPath("$.data.totalCount").value(20))
                .andExpect(jsonPath("$.data.totalPages").value(4))
                .andExpect(jsonPath("$.data.hasNextPage").value(true))
                .andExpect(header().doesNotExist("Set-Cookie"));
        verify(client).search("역삼 한식", 1, 5);
    }

    @Test
    void search_explicitPagination_isPassedToClient() throws Exception {
        when(client.search("식당", 2, 10)).thenReturn(new PlaceSearchResponse(List.of(), 2, 10, 0, 0, false));
        mvc.perform(get("/api/places/search").param("query", "식당").param("page", "2").param("size", "10"))
                .andExpect(status().isOk());
        verify(client).search("식당", 2, 10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "query=", "query=x&page=0", "query=x&page=46", "query=x&size=0", "query=x&size=16", "query=x&page=invalid"})
    void search_invalidRequest_returns400ProblemDetail(String params) throws Exception {
        mvc.perform(get("/api/places/search?" + params))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(client);
    }

    @Test
    void search_blankKeyword_returns400WithoutCallingKakao() throws Exception {
        mvc.perform(get("/api/places/search").param("query", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(client);
    }

    @Test
    void search_upstreamFailure_returns502ProblemDetail() throws Exception {
        when(client.search("식당", 1, 5)).thenThrow(new BusinessException(ErrorCode.KAKAO_API_ERROR));
        mvc.perform(get("/api/places/search").param("query", "식당"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("KAKAO_API_ERROR"));
    }
}
