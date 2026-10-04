package com.menusolomon.place.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.place.dto.PlaceDetail;
import com.menusolomon.place.dto.PlaceSearchResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class KakaoPlaceSearchClient implements PlaceSearchClient {
    private final RestClient restClient;
    private final String restApiKey;

    public KakaoPlaceSearchClient(RestClient restClient, String restApiKey) {
        this.restClient = restClient;
        this.restApiKey = restApiKey;
    }

    @Override
    public PlaceSearchResponse search(String query, int page, int size) {
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
        }
        try {
            KakaoResponse response = restClient.get()
                    .uri(builder -> builder.path("/v2/local/search/keyword.json")
                            .queryParam("query", "{query}").queryParam("page", page)
                            .queryParam("size", size).build(query))
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve().body(KakaoResponse.class);
            if (response == null || response.meta() == null || response.documents() == null) {
                throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
            }
            Meta meta = response.meta();
            if (meta.totalCount() == null || meta.pageableCount() == null || meta.isEnd() == null) {
                throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
            }
            List<PlaceDetail> items = response.documents().stream().map(Document::toPlace).toList();
            int totalPages = (meta.pageableCount() + size - 1) / size;
            return new PlaceSearchResponse(items, page, size, meta.totalCount(), totalPages, !meta.isEnd());
        } catch (RestClientException | IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoResponse(Meta meta, List<Document> documents) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Meta(@JsonProperty("total_count") Integer totalCount,
            @JsonProperty("pageable_count") Integer pageableCount,
            @JsonProperty("is_end") Boolean isEnd) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Document(String id, @JsonProperty("place_name") String name,
            @JsonProperty("address_name") String address,
            @JsonProperty("road_address_name") String roadAddress,
            String x, String y, @JsonProperty("category_name") String category,
            @JsonProperty("place_url") String placeUrl) {
        PlaceDetail toPlace() {
            if (id == null || id.isBlank() || name == null || name.isBlank()
                    || address == null || category == null || placeUrl == null) {
                throw new BusinessException(ErrorCode.KAKAO_API_ERROR);
            }
            String preferredAddress = roadAddress == null || roadAddress.isBlank() ? address : roadAddress;
            String[] categories = category.split("\\s*>\\s*");
            String primaryCategory = categories.length > 1 && categories[0].equals("음식점")
                    ? categories[1] : category;
            return new PlaceDetail(id, name, preferredAddress, new BigDecimal(y), new BigDecimal(x),
                    primaryCategory, placeUrl);
        }
    }
}
