package com.menusolomon.place.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.menusolomon.place.client.PlaceSearchClient;
import com.menusolomon.place.dto.PlaceDetail;
import com.menusolomon.place.dto.PlaceSearchResponse;
import com.menusolomon.restaurant.domain.Restaurant;
import com.menusolomon.restaurant.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {
    @Mock PlaceSearchClient client;
    @Mock RestaurantRepository restaurants;
    @Mock PlatformTransactionManager transactions;
    @Mock TransactionStatus transactionStatus;
    PlaceService service;
    Instant now = Instant.parse("2026-10-04T03:00:00Z");

    @BeforeEach
    void setUp() {
        service = new PlaceService(client, restaurants, Clock.fixed(now, ZoneOffset.UTC), transactions);
    }

    @Test
    void search_cachesOnlyTrustedNewPlaces_afterKakaoCallBeforeTransaction() {
        var response = new PlaceSearchResponse(List.of(place("123"), place("456")), 1, 5, 2, 1, false);
        when(client.search("한식", 1, 5)).thenReturn(response);
        when(transactions.getTransaction(any())).thenReturn(transactionStatus);
        when(restaurants.findExistingPlaceIds(List.of("123", "456"))).thenReturn(List.of("123"));
        assertThat(service.search("한식", 1, 5)).isSameAs(response);
        var order = inOrder(client, transactions, restaurants);
        order.verify(client).search("한식", 1, 5);
        order.verify(transactions).getTransaction(any());
        order.verify(restaurants).findExistingPlaceIds(List.of("123", "456"));
        verify(restaurants).saveAll(argThat(items -> {
            List<Restaurant> saved = java.util.stream.StreamSupport.stream(items.spliterator(), false).toList();
            assertThat(saved).singleElement().satisfies(restaurant -> {
                assertThat(restaurant.getKakaoPlaceId()).isEqualTo("456");
                assertThat(restaurant.getCreatedAt()).isEqualTo(now);
            });
            return true;
        }));
        verify(transactions).commit(transactionStatus);
    }

    @Test
    void search_concurrentCacheInsert_rechecksExistingPlacesInNewTransaction() {
        var response = new PlaceSearchResponse(List.of(place("123")), 1, 5, 1, 1, false);
        when(client.search("한식", 1, 5)).thenReturn(response);
        when(transactions.getTransaction(any())).thenReturn(transactionStatus);
        when(restaurants.findExistingPlaceIds(List.of("123")))
                .thenReturn(List.of(), List.of("123"));
        when(restaurants.saveAll(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"))
                .thenReturn(List.of());
        assertThat(service.search("한식", 1, 5)).isSameAs(response);
        verify(restaurants, times(2)).findExistingPlaceIds(List.of("123"));
        verify(transactions).rollback(transactionStatus);
        verify(transactions).commit(transactionStatus);
        verify(client, times(1)).search("한식", 1, 5);
    }

    @Test
    void search_noResults_doesNotStartDatabaseTransaction() {
        when(client.search("없는식당", 1, 5)).thenReturn(new PlaceSearchResponse(List.of(), 1, 5, 0, 0, false));
        assertThat(service.search("없는식당", 1, 5).items()).isEmpty();
        verifyNoInteractions(restaurants, transactions);
    }

    @Test
    void search_upstreamFailure_doesNotSaveAnything() {
        when(client.search("한식", 1, 5)).thenThrow(new com.menusolomon.common.exception.BusinessException(
                com.menusolomon.common.exception.ErrorCode.KAKAO_API_ERROR));
        assertThatThrownBy(() -> service.search("한식", 1, 5)).isInstanceOf(com.menusolomon.common.exception.BusinessException.class);
        verifyNoInteractions(restaurants, transactions);
    }

    private PlaceDetail place(String id) {
        return new PlaceDetail(id, "식당", "서울", new BigDecimal("37.5"), new BigDecimal("127.0"),
                "한식", "https://place.map.kakao.com/" + id);
    }
}
