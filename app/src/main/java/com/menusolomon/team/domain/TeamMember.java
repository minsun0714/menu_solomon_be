package com.menusolomon.team.domain;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
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
@Table(
        name = "team_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "user_id"})
)
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

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    protected TeamMember() {
    }

    private TeamMember(Long teamId, Long userId, TeamRole role, Instant joinedAt) {
        this.teamId = teamId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public static TeamMember newAdmin(Long teamId, Long userId, Instant joinedAt) {
        return new TeamMember(teamId, userId, TeamRole.ADMIN, joinedAt);
    }

    public static TeamMember newMember(Long teamId, Long userId, Instant joinedAt) {
        return new TeamMember(teamId, userId, TeamRole.MEMBER, joinedAt);
    }

    public boolean isActive() {
        return leftAt == null;
    }

    public boolean isAdmin() {
        return role == TeamRole.ADMIN;
    }

    public void leave(Instant now) {
        leftAt = now;
    }

    public void rejoin(Instant now) {
        if (isActive()) {
            throw new IllegalStateException("An active team member cannot rejoin");
        }
        joinedAt = now;
        leftAt = null;
        role = TeamRole.MEMBER;
    }

    public void joinIfInactive(Instant now) {
        if (!isActive()) {
            rejoin(now);
        }
    }
    public void requireAdmin() {
        requireActive();
        if (!isAdmin()) {
            throw new BusinessException(ErrorCode.ADMIN_REQUIRED);
        }
    }

    public void promoteToAdmin() {
        requireActive();
        role = TeamRole.ADMIN;
    }

    public void demoteToMember() {
        requireAdmin();
        role = TeamRole.MEMBER;
    }

    public void transferAdminTo(TeamMember target) {
        requireAdmin();
        if (!teamId.equals(target.teamId) || !target.isActive() || target.isAdmin()
                || (id != null && id.equals(target.id))) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        demoteToMember();
        target.promoteToAdmin();
    }

    public boolean shouldDeleteTeamOnLeave(long activeMemberCount) {
        requireActive();
        if (isAdmin() && activeMemberCount > 1) {
            throw new BusinessException(ErrorCode.ADMIN_TRANSFER_REQUIRED);
        }
        return isAdmin();
    }

    private void requireActive() {
        if (!isActive()) {
            throw new BusinessException(ErrorCode.NOT_TEAM_MEMBER);
        }
    }

}
