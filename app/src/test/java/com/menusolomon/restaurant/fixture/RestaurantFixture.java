package com.menusolomon.restaurant.fixture;

import com.menusolomon.restaurant.domain.Restaurant;
import com.menusolomon.restaurant.domain.TeamRestaurant;
import com.menusolomon.restaurant.repository.TeamRestaurantRow;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

public final class RestaurantFixture {
    public static final Instant NOW = Instant.parse("2026-10-04T03:00:00Z");
    private RestaurantFixture() {}

    public static Restaurant restaurant(String placeId, String name, String category) {
        return Restaurant.create(placeId, name, "서울 강남구", new BigDecimal("37.5"),
                new BigDecimal("127.0"), category, "https://place.map.kakao.com/" + placeId, NOW);
    }

    public static Restaurant savedRestaurant() {
        var restaurant = restaurant("123", "을지다락", "양식");
        ReflectionTestUtils.setField(restaurant, "id", 3L);
        return restaurant;
    }

    public static TeamRestaurant savedLink() {
        var link = TeamRestaurant.create(1L, 3L, 1L, NOW);
        ReflectionTestUtils.setField(link, "id", 5L);
        return link;
    }

    public static TeamRestaurantRow rowWithoutReviews() {
        var row = row();
        return new TeamRestaurantRow(row.id(), row.restaurantId(), row.kakaoPlaceId(), row.name(),
                row.address(), row.latitude(), row.longitude(), row.category(), row.kakaoPlaceUrl(),
                row.registeredByNickname(), row.createdAt(), null, 0L, null, null, null, null);
    }

    public static TeamRestaurantRow row() {
        return new TeamRestaurantRow(5L, 3L, "123", "을지다락", "서울 강남구",
                new BigDecimal("37.5"), new BigDecimal("127.0"), "양식",
                "https://place.map.kakao.com/123", "익명 사용자", NOW, 4.5, 2L, "리뷰 작성자", 5, "맛있어요", NOW);
    }
}
