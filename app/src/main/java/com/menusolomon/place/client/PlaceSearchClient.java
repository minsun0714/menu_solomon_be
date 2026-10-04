package com.menusolomon.place.client;

import com.menusolomon.place.dto.PlaceSearchResponse;

public interface PlaceSearchClient {
    PlaceSearchResponse search(String query, int page, int size);
}
