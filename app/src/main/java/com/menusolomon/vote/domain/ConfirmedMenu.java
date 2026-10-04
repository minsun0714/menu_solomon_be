package com.menusolomon.vote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "lunch_decisions", uniqueConstraints = @UniqueConstraint(name = "uk_lunch_decisions", columnNames = {"session_id"}))
@Getter
public class ConfirmedMenu {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id", nullable = false, updatable = false) private Long sessionId;
    @Column(name = "restaurant_id", nullable = false) private Long restaurantId;
    @Column(name = "confirmed_by_team_member_id") private Long confirmedByTeamMemberId;
    @Enumerated(EnumType.STRING) @Column(name = "confirmation_type", nullable = false) private ConfirmationType confirmationType;
    @Column(name = "confirmed_at", nullable = false) private Instant confirmedAt;
    protected ConfirmedMenu() {}
    public static ConfirmedMenu automatic(Long sessionId, Long restaurantId, Instant now) {
        return create(sessionId, restaurantId, null, ConfirmationType.AUTO, now);
    }
    public static ConfirmedMenu manual(Long sessionId, Long restaurantId, Long memberId, Instant now) {
        return create(sessionId, restaurantId, memberId, ConfirmationType.MANUAL, now);
    }
    private static ConfirmedMenu create(Long sessionId, Long restaurantId, Long memberId, ConfirmationType type, Instant now) {
        var decision = new ConfirmedMenu(); decision.sessionId = sessionId; decision.restaurantId = restaurantId;
        decision.confirmedByTeamMemberId = memberId; decision.confirmationType = type; decision.confirmedAt = now; return decision;
    }
    public void changeRestaurant(Long restaurantId, Long memberId, Instant now) {
        this.restaurantId = restaurantId; confirmedByTeamMemberId = memberId;
        confirmationType = ConfirmationType.MANUAL; confirmedAt = now;
    }
}
