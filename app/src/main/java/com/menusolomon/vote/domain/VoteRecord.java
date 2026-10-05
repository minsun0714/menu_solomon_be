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
@Table(name = "lunch_ballots", uniqueConstraints = @UniqueConstraint(name = "uk_lunch_ballots", columnNames = {"session_id", "candidate_id", "team_member_id"}))
@Getter
public class VoteRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id", nullable = false, updatable = false) private Long sessionId;
    @Column(name = "candidate_id", nullable = false, updatable = false) private Long candidateId;
    @Column(name = "team_member_id", nullable = false, updatable = false) private Long teamMemberId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected VoteRecord() {}
    public static VoteRecord create(Long sessionId, Long memberId, Long candidateId, Instant now) {
        var ballot = new VoteRecord(); ballot.sessionId = sessionId; ballot.teamMemberId = memberId;
        ballot.candidateId = candidateId; ballot.createdAt = now; ballot.updatedAt = now; return ballot;
    }
}
