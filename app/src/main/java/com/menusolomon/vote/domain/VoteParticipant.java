package com.menusolomon.vote.domain;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
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
@Table(name = "lunch_participants", uniqueConstraints = @UniqueConstraint(name = "uk_lunch_participants", columnNames = {"session_id", "team_member_id"}))
@Getter
public class VoteParticipant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id", nullable = false, updatable = false) private Long sessionId;
    @Column(name = "team_member_id", nullable = false, updatable = false) private Long teamMemberId;
    @Column(nullable = false) private boolean participating;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected VoteParticipant() {}
    public static VoteParticipant create(Long sessionId, Long memberId, boolean participating, Instant now) {
        var participant = new VoteParticipant(); participant.sessionId = sessionId; participant.teamMemberId = memberId;
        participant.participating = participating; participant.createdAt = now; participant.updatedAt = now; return participant;
    }
    public void changeParticipation(boolean participating, Instant now) { this.participating = participating; updatedAt = now; }
    public void requireParticipation() {
        if (!participating) throw new BusinessException(ErrorCode.VOTE_PARTICIPATION_REQUIRED);
    }
}
