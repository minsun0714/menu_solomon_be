package com.menusolomon.place.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoPlaceSearchClientTest {
    private MockRestServiceServer server;
    private KakaoPlaceSearchClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dapi.kakao.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoPlaceSearchClient(builder.build(), "test-key");
    }

    @Test
    void search_sendsEncodedKeywordAndApiKey_andMapsCoordinatesAndPagination() {
        server.expect(requestTo("https://dapi.kakao.com/v2/local/search/keyword.json?query=%EC%97%AD%EC%82%BC%20%ED%95%9C%EC%8B%9D&page=2&size=5"))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andRespond(withSuccess(response("서울 강남구 도로명", false), MediaType.APPLICATION_JSON));
        var result = client.search("역삼 한식", 2, 5);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(5);
        assertThat(result.totalCount()).isEqualTo(100);
        assertThat(result.totalPages()).isEqualTo(9);
        assertThat(result.hasNextPage()).isTrue();
        assertThat(result.items()).singleElement().satisfies(place -> {
            assertThat(place.kakaoPlaceId()).isEqualTo("123");
            assertThat(place.name()).isEqualTo("맛있는 식당");
            assertThat(place.address()).isEqualTo("서울 강남구 도로명");
            assertThat(place.latitude()).isEqualByComparingTo(new BigDecimal("37.501"));
            assertThat(place.longitude()).isEqualByComparingTo(new BigDecimal("127.039"));
            assertThat(place.category()).isEqualTo("한식");
            assertThat(place.kakaoPlaceUrl()).isEqualTo("https://place.map.kakao.com/123");
        });
        server.verify();
    }

    @Test
    void search_withoutRoadAddress_usesLotAddressAndLastPage() {
        server.expect(anything()).andRespond(withSuccess(response("", true), MediaType.APPLICATION_JSON));
        var result = client.search("식당", 1, 5);
        assertThat(result.items().getFirst().address()).isEqualTo("서울 지번 주소");
        assertThat(result.hasNextPage()).isFalse();
    }

    @Test
    void search_noMatches_returnsEmptyList() {
        server.expect(anything()).andRespond(withSuccess("""
                {"meta":{"total_count":0,"pageable_count":0,"is_end":true},"documents":[]}
                """, MediaType.APPLICATION_JSON));
        var result = client.search("없는식당", 1, 5);
        assertThat(result.items()).isEmpty();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void search_kakaoError_throwsKakaoApiError() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertApiError(() -> client.search("식당", 1, 5));
    }

    @Test
    void search_malformedResponse_throwsKakaoApiError() {
        server.expect(anything()).andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));
        assertApiError(() -> client.search("식당", 1, 5));
    }

    @Test
    void search_invalidCoordinates_throwsKakaoApiError() {
        server.expect(anything()).andRespond(withSuccess(response("", true).replace("37.501", "invalid"), MediaType.APPLICATION_JSON));
        assertApiError(() -> client.search("식당", 1, 5));
    }

    @Test
    void search_connectionFailure_throwsKakaoApiError() {
        server.expect(anything()).andRespond(withException(new java.io.IOException("connection failed")));
        assertApiError(() -> client.search("식당", 1, 5));
    }

    @Test
    void search_missingKey_doesNotCallKakao() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer noRequests = MockRestServiceServer.bindTo(builder).build();
        var unconfigured = new KakaoPlaceSearchClient(builder.build(), "");
        assertApiError(() -> unconfigured.search("식당", 1, 5));
        noRequests.verify();
    }

    private void assertApiError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.KAKAO_API_ERROR));
    }

    private String response(String roadAddress, boolean isEnd) {
        return """
                {"meta":{"total_count":100,"pageable_count":45,"is_end":%s},
                "documents":[{"id":"123","place_name":"맛있는 식당","address_name":"서울 지번 주소",
                "road_address_name":"%s","x":"127.039","y":"37.501",
                "category_name":"음식점 > 한식 > 한정식","place_url":"https://place.map.kakao.com/123","phone":"ignored"}]}
                """.formatted(isEnd, roadAddress);
    }
}
