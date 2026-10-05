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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "lunch_candidates", uniqueConstraints = @UniqueConstraint(name = "uk_lunch_candidates", columnNames = {"session_id", "restaurant_id"}))
@Getter
public class VoteCandidate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "session_id", nullable = false, updatable = false) private Long sessionId;
    @Column(name = "restaurant_id", nullable = false, updatable = false) private Long restaurantId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CandidateSource source;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected VoteCandidate() {}
    public static VoteCandidate create(Long sessionId, Long restaurantId, CandidateSource source, Instant now) {
        if (source == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        var candidate = new VoteCandidate(); candidate.sessionId = sessionId; candidate.restaurantId = restaurantId;
        candidate.source = source; candidate.createdAt = now; return candidate;
    }
}
