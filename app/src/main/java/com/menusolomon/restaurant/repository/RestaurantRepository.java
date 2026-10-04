package com.menusolomon.restaurant.repository;

import com.menusolomon.restaurant.domain.Restaurant;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    @Query("select r.kakaoPlaceId from Restaurant r where r.kakaoPlaceId in :ids")
    List<String> findExistingPlaceIds(@Param("ids") List<String> ids);

    Optional<Restaurant> findByKakaoPlaceId(String kakaoPlaceId);
}
