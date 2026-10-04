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

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Team() {
    }

    private Team(String name, String description, String inviteToken, Instant now) {
        this.name = name;
        this.description = description;
        this.inviteToken = inviteToken;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Team create(String name, String description, String inviteToken, Instant now) {
        return new Team(name, description, inviteToken, now);
    }
}
