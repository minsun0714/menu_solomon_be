package com.menusolomon.place.service;

import com.menusolomon.place.client.PlaceSearchClient;
import com.menusolomon.place.dto.PlaceDetail;
import com.menusolomon.place.dto.PlaceSearchResponse;
import com.menusolomon.restaurant.domain.Restaurant;
import com.menusolomon.restaurant.repository.RestaurantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PlaceService {
    private final PlaceSearchClient client;
    private final RestaurantRepository restaurants;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PlaceService(PlaceSearchClient client, RestaurantRepository restaurants, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.client = client;
        this.restaurants = restaurants;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PlaceSearchResponse search(String query, int page, int size) {
        PlaceSearchResponse response = client.search(query, page, size);
        if (!response.items().isEmpty()) {
            try {
                transactionTemplate.executeWithoutResult(status -> cacheResults(response));
            } catch (DataIntegrityViolationException exception) {
                transactionTemplate.executeWithoutResult(status -> cacheResults(response));
            }
        }
        return response;
    }

    private void cacheResults(PlaceSearchResponse response) {
        Set<String> existing = new HashSet<>(restaurants.findExistingPlaceIds(
                response.items().stream().map(PlaceDetail::kakaoPlaceId).toList()));
        Instant now = Instant.now(clock);
        restaurants.saveAll(response.items().stream()
                .filter(place -> existing.add(place.kakaoPlaceId()))
                .map(place -> Restaurant.create(place.kakaoPlaceId(), place.name(), place.address(),
                        place.latitude(), place.longitude(), place.category(), place.kakaoPlaceUrl(), now))
                .toList());
    }
}
