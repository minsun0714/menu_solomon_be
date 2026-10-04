package com.menusolomon.restaurant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "restaurants")
@Getter
public class Restaurant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "kakao_place_id", nullable = false, unique = true)
    private String kakaoPlaceId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String address;
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;
    @Column(nullable = false)
    private String category;
    @Column(name = "kakao_place_url", nullable = false)
    private String kakaoPlaceUrl;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Restaurant() {}

    private Restaurant(String kakaoPlaceId, String name, String address, BigDecimal latitude,
            BigDecimal longitude, String category, String kakaoPlaceUrl, Instant now) {
        this.kakaoPlaceId = kakaoPlaceId;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category;
        this.kakaoPlaceUrl = kakaoPlaceUrl;
        createdAt = now;
        updatedAt = now;
    }

    public static Restaurant create(String kakaoPlaceId, String name, String address, BigDecimal latitude,
            BigDecimal longitude, String category, String kakaoPlaceUrl, Instant now) {
        return new Restaurant(kakaoPlaceId, name, address, latitude, longitude, category, kakaoPlaceUrl, now);
    }
}
