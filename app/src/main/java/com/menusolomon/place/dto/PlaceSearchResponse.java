package com.menusolomon.place.dto;

import java.util.List;

public record PlaceSearchResponse(List<PlaceDetail> items, int page, int pageSize,
        int totalCount, int totalPages, boolean hasNextPage) {
}
