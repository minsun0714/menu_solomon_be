package com.menusolomon.restaurant.repository;

import static com.menusolomon.restaurant.fixture.RestaurantFixture.*;
import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class RestaurantRepositoryTest {
    @Autowired RestaurantRepository restaurants;

    @Test
    void findByKakaoPlaceId_returnsRestaurant() {
        var saved = restaurants.saveAndFlush(restaurant("123", "을지다락", "양식"));
        assertThat(restaurants.findByKakaoPlaceId("123")).get().extracting("id").isEqualTo(saved.getId());
    }

    @Test
    void duplicateKakaoPlaceId_isRejected() {
        restaurants.saveAndFlush(restaurant("123", "을지다락", "양식"));
        assertThatThrownBy(() -> restaurants.saveAndFlush(restaurant("123", "중복", "양식")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findExistingPlaceIds_returnsOnlyCachedMatches() {
        restaurants.saveAndFlush(restaurant("123", "을지다락", "양식"));
        assertThat(restaurants.findExistingPlaceIds(java.util.List.of("123", "456"))).containsExactly("123");
    }
}
