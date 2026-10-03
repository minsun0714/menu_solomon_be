package com.menusolomon.team.domain;

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
@Table(name = "team_members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_team_member_team_user", columnNames = {"team_id", "user_id"})
})
@Getter
public class TeamMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeamRole role;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    private Instant leftAt;

    protected TeamMember() {
    }

    public TeamMember(Long teamId, Long userId, TeamRole role, Instant joinedAt) {
        this.teamId = teamId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public boolean isActive() {
        return leftAt == null;
    }

    public boolean isAdmin() {
        return role == TeamRole.ADMIN;
    }

    public void leave(Instant now) {
        if (isActive()) {
            leftAt = now;
        }
    }

    public void rejoin(Instant now) {
        if (isActive()) {
            throw new IllegalStateException("Active member cannot rejoin");
        }
        leftAt = null;
        joinedAt = now;
        role = TeamRole.MEMBER;
    }

    public void promoteToAdmin() {
        role = TeamRole.ADMIN;
    }

    public void demoteToMember() {
        role = TeamRole.MEMBER;
    }
}
