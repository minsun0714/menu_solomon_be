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
@Table(name = "vote_candidates", uniqueConstraints = @UniqueConstraint(columnNames = {"lunch_vote_session_id", "restaurant_id"}))
@Getter
public class VoteCandidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lunch_vote_session_id", nullable = false, updatable = false)
    private Long lunchVoteSessionId;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private Long restaurantId;

    @Column(name = "created_by_team_member_id", nullable = false, updatable = false)
    private Long createdByTeamMemberId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VoteCandidate() {}

    private VoteCandidate(Long lunchVoteSessionId, Long restaurantId, Long createdByTeamMemberId, Instant now) {
        this.lunchVoteSessionId = lunchVoteSessionId;
        this.restaurantId = restaurantId;
        this.createdByTeamMemberId = createdByTeamMemberId;
        this.createdAt = now;
    }

    public static VoteCandidate create(Long lunchVoteSessionId, Long restaurantId, Long createdByTeamMemberId, Instant now) {
        return new VoteCandidate(lunchVoteSessionId, restaurantId, createdByTeamMemberId, now);
    }
}
