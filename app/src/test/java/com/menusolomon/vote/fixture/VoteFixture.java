package com.menusolomon.vote.fixture;

import com.menusolomon.vote.domain.*;
import com.menusolomon.vote.repository.*;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

public final class VoteFixture {
    public static final Instant NOW = Instant.parse("2026-10-04T04:00:00Z");
    private VoteFixture() {}
    public static LunchVoteSession session() {
        var vote = LunchVoteSession.create(1L, "점심", 1L, NOW);
        ReflectionTestUtils.setField(vote, "id", 5L);
        return vote;
    }
    public static VoteCandidate candidate() {
        var candidate = VoteCandidate.create(5L, 3L, 1L, NOW);
        ReflectionTestUtils.setField(candidate, "id", 100L);
        return candidate;
    }
    public static VoteParticipant participant() { return VoteParticipant.create(5L, 1L, true, NOW); }
    public static VoteCandidateRow candidateRow(Long id) {
        return new VoteCandidateRow(id, 3L, "을지다락", "양식", "서울", 4.5, 2L, true);
    }
    public static VoteSummaryRow summary() {
        return new VoteSummaryRow(5L, 1L, "점심", VoteStatus.OPEN, NOW, 2L, 1L, true, 100L);
    }
    public static ConfirmedMenuRow result() { return new ConfirmedMenuRow(100L, 3L, "을지다락", 0L, NOW); }
}
