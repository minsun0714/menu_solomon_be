package com.menusolomon.restaurant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "team_restaurants", uniqueConstraints = @UniqueConstraint(
        name = "uk_team_restaurant", columnNames = {"team_id", "restaurant_id"}))
@Getter
public class TeamRestaurant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "team_id", nullable = false)
    private Long teamId;
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;
    @Column(name = "registered_by_team_member_id", nullable = false)
    private Long registeredByTeamMemberId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TeamRestaurant() {}

    private TeamRestaurant(Long teamId, Long restaurantId, Long registeredByTeamMemberId, Instant now) {
        this.teamId = teamId;
        this.restaurantId = restaurantId;
        this.registeredByTeamMemberId = registeredByTeamMemberId;
        createdAt = now;
    }

    public static TeamRestaurant create(Long teamId, Long restaurantId, Long registeredByTeamMemberId, Instant now) {
        return new TeamRestaurant(teamId, restaurantId, registeredByTeamMemberId, now);
    }
}
