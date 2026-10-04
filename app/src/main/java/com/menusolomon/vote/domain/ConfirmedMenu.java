package com.menusolomon.vote.domain;

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
@Table(name = "confirmed_menus", uniqueConstraints = @UniqueConstraint(columnNames = {"lunch_vote_session_id"}))
@Getter
public class ConfirmedMenu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lunch_vote_session_id", nullable = false, updatable = false)
    private Long lunchVoteSessionId;

    @Column(name = "vote_candidate_id", nullable = false)
    private Long voteCandidateId;

    @Column(name = "confirmed_by_team_member_id", nullable = false, updatable = false)
    private Long confirmedByTeamMemberId;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant confirmedAt;

    protected ConfirmedMenu() {}

    private ConfirmedMenu(Long lunchVoteSessionId, Long voteCandidateId, Long confirmedByTeamMemberId, Instant now) {
        this.lunchVoteSessionId = lunchVoteSessionId;
        this.voteCandidateId = voteCandidateId;
        this.confirmedByTeamMemberId = confirmedByTeamMemberId;
        this.confirmedAt = now;
    }

    public static ConfirmedMenu create(Long lunchVoteSessionId, Long voteCandidateId, Long confirmedByTeamMemberId, Instant now) {
        return new ConfirmedMenu(lunchVoteSessionId, voteCandidateId, confirmedByTeamMemberId, now);
    }
}
