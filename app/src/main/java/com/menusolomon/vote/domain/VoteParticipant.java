package com.menusolomon.vote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import lombok.Getter;

@Entity
@Table(name = "vote_participants", uniqueConstraints = @UniqueConstraint(columnNames = {"lunch_vote_session_id", "team_member_id"}))
@Getter
public class VoteParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lunch_vote_session_id", nullable = false, updatable = false)
    private Long lunchVoteSessionId;

    @Column(name = "team_member_id", nullable = false, updatable = false)
    private Long teamMemberId;

    @Column(name = "participating", nullable = false)
    private boolean participating;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VoteParticipant() {}

    private VoteParticipant(Long lunchVoteSessionId, Long teamMemberId, boolean participating, Instant now) {
        this.lunchVoteSessionId = lunchVoteSessionId;
        this.teamMemberId = teamMemberId;
        this.participating = participating;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static VoteParticipant create(Long lunchVoteSessionId, Long teamMemberId, boolean participating, Instant now) {
        return new VoteParticipant(lunchVoteSessionId, teamMemberId, participating, now);
    }

    public void changeParticipation(boolean participating, Instant now) {
        this.participating = participating;
        this.updatedAt = now;
    }

    public void requireParticipation() {
        if (!participating) throw new BusinessException(
                ErrorCode.VOTE_PARTICIPATION_REQUIRED);
    }
}
