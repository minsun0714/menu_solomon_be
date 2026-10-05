package com.menusolomon.vote.domain;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Getter;

@Entity
@Table(name = "lunch_vote_sessions", indexes = @Index(name = "idx_vote_expiry", columnList = "status,closes_at"))
@Getter
public class LunchVoteSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "team_id", nullable = false, updatable = false) private Long teamId;
    @Column(length = 40) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private VoteStatus status;
    @Column(name = "created_by_team_member_id", nullable = false, updatable = false) private Long createdByTeamMemberId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "closes_at", nullable = false) private Instant closesAt;
    protected LunchVoteSession() {}
    public static LunchVoteSession create(Long teamId, Long creatorId, Instant closesAt, Instant now) {
        return create(teamId, creatorId, null, closesAt, now);
    }
    public static LunchVoteSession create(Long teamId, Long creatorId, String name, Instant closesAt, Instant now) {
        validateTime(closesAt, now);
        validateName(name);
        var session = new LunchVoteSession();
        session.name = name;
        session.teamId = teamId; session.createdByTeamMemberId = creatorId;
        session.closesAt = closesAt; session.createdAt = now; session.status = VoteStatus.OPEN;
        return session;
    }
    public boolean isDue(Instant now) { return status == VoteStatus.OPEN && !closesAt.isAfter(now); }
    public void requireOpen(Instant now) {
        if (status == VoteStatus.CONFIRMED) throw new BusinessException(ErrorCode.VOTE_ALREADY_CONFIRMED);
        if (status != VoteStatus.OPEN || !closesAt.isAfter(now)) throw new BusinessException(ErrorCode.VOTE_NOT_OPEN);
    }
    public void requireCreator(Long memberId) {
        if (!createdByTeamMemberId.equals(memberId)) throw new BusinessException(ErrorCode.VOTE_CREATOR_REQUIRED);
    }
    public void update(String name, Instant closesAt, Instant now) {
        if (name == null && closesAt == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        validateName(name);
        if (closesAt != null) {
            requireOpen(now);
            validateTime(closesAt, now);
        }
        if (name != null) this.name = name;
        if (closesAt != null) this.closesAt = closesAt;
    }
    public void close(Instant now) {
        if (!isDue(now)) throw new BusinessException(ErrorCode.VOTE_NOT_CLOSED);
        status = VoteStatus.CLOSED;
    }
    public void requireClosed() {
        if (status == VoteStatus.CONFIRMED) throw new BusinessException(ErrorCode.VOTE_ALREADY_CONFIRMED);
        if (status != VoteStatus.CLOSED) throw new BusinessException(ErrorCode.VOTE_NOT_CLOSED);
    }
    public void confirm() { requireClosed(); status = VoteStatus.CONFIRMED; }
    public void restart(Long memberId, Instant now) {
        requireCreator(memberId);
        if (status == VoteStatus.CONFIRMED) throw new BusinessException(ErrorCode.VOTE_ALREADY_CONFIRMED);
        status = VoteStatus.OPEN; closesAt = now.plus(3, ChronoUnit.HOURS);
    }
    public void removeDecision(Long memberId) {
        requireCreator(memberId);
        if (status != VoteStatus.CONFIRMED) throw new BusinessException(ErrorCode.DECISION_NOT_FOUND);
        status = VoteStatus.CLOSED;
    }
    private static void validateName(String name) {
        if (name != null && (name.isBlank() || name.length() > 40)) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    private static void validateTime(Instant time, Instant now) {
        if (time == null || !time.isAfter(now)) throw new VoteClosingTimeException();
    }
}
