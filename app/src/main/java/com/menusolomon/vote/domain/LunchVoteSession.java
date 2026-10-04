package com.menusolomon.vote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.time.Instant;
import lombok.Getter;
import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;

@Entity
@Table(name = "lunch_vote_sessions", indexes = @Index(name = "idx_vote_session_team_status", columnList = "team_id,status,created_at"))
@Getter
public class LunchVoteSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false, updatable = false)
    private Long teamId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VoteStatus status;

    @Column(name = "created_by_team_member_id", nullable = false, updatable = false)
    private Long createdByTeamMemberId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at", nullable = true)
    private Instant closedAt;

    protected LunchVoteSession() {}

    private LunchVoteSession(Long teamId, String title, Long createdByTeamMemberId, Instant now) {
        if (title == null || title.isBlank() || title.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        this.teamId = teamId;
        this.title = title;
        this.createdByTeamMemberId = createdByTeamMemberId;
        this.status = VoteStatus.OPEN;
        this.createdAt = now;
    }

    public static LunchVoteSession create(Long teamId, String title, Long createdByTeamMemberId, Instant now) {
        return new LunchVoteSession(teamId, title, createdByTeamMemberId, now);
    }

    public boolean isOpen() { return status == VoteStatus.OPEN; }

    public void requireOpen() {
        if (!isOpen()) throw new BusinessException(ErrorCode.VOTE_ALREADY_CONFIRMED);
    }

    public void confirm(Instant now) {
        requireOpen();
        status = VoteStatus.CONFIRMED;
        closedAt = now;
    }
}
