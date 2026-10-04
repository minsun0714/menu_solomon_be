package com.menusolomon.vote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "vote_records", indexes = @Index(name = "idx_vote_record_candidate", columnList = "vote_candidate_id"), uniqueConstraints = @UniqueConstraint(columnNames = {"lunch_vote_session_id", "team_member_id"}))
@Getter
public class VoteRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lunch_vote_session_id", nullable = false, updatable = false)
    private Long lunchVoteSessionId;

    @Column(name = "team_member_id", nullable = false, updatable = false)
    private Long teamMemberId;

    @Column(name = "vote_candidate_id", nullable = false)
    private Long voteCandidateId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VoteRecord() {}

    private VoteRecord(Long lunchVoteSessionId, Long teamMemberId, Long voteCandidateId, Instant now) {
        this.lunchVoteSessionId = lunchVoteSessionId;
        this.teamMemberId = teamMemberId;
        this.voteCandidateId = voteCandidateId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static VoteRecord create(Long lunchVoteSessionId, Long teamMemberId, Long voteCandidateId, Instant now) {
        return new VoteRecord(lunchVoteSessionId, teamMemberId, voteCandidateId, now);
    }

    public void changeCandidate(Long candidateId, Instant now) {
        this.voteCandidateId = candidateId;
        this.updatedAt = now;
    }
}
