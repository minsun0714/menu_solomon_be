package com.menusolomon.team.domain;

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
@Table(name = "teams")
@Getter
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false, length = 100)
    private String description;

    @Column(name = "invite_token", nullable = false, unique = true)
    private String inviteToken;

    @Column(name = "office_kakao_place_id")
    private String officeKakaoPlaceId;

    @Column(name = "office_name")
    private String officeName;

    @Column(name = "office_address")
    private String officeAddress;

    @Column(name = "office_latitude", precision = 10, scale = 7)
    private BigDecimal officeLatitude;

    @Column(name = "office_longitude", precision = 10, scale = 7)
    private BigDecimal officeLongitude;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Team() {
    }

    public Team(String name, String description, String inviteToken, Instant now) {
        validateName(name);
        if (inviteToken == null || inviteToken.isBlank()) {
            throw new IllegalArgumentException("inviteToken must not be blank");
        }
        this.name = name;
        this.description = normalize(description);
        this.inviteToken = inviteToken;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void changeInfo(String name, String description, Instant now) {
        validateName(name);
        this.name = name;
        this.description = normalize(description);
        this.updatedAt = now;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    private static String normalize(String description) {
        return description == null ? "" : description;
    }
}
