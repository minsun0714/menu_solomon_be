package com.menusolomon.vote.fixture;

import com.menusolomon.vote.domain.*;
import com.menusolomon.vote.repository.*;
import com.menusolomon.vote.dto.*;
import java.time.*;
import java.math.BigDecimal;
import org.springframework.test.util.ReflectionTestUtils;

public final class VoteFixture {
    public static final Instant NOW = Instant.parse("2026-10-04T04:00:00Z");
    public static final Instant DEADLINE = NOW.plusSeconds(10800);
    public static LunchVoteSession session() {
        var vote = LunchVoteSession.create(1L, 1L, DEADLINE, NOW); id(vote, 5L); return vote;
    }
    public static <T> T id(T entity, Long id) { ReflectionTestUtils.setField(entity, "id", id); return entity; }
    public static VoteCandidateRow candidateRow(Long id) {
        return new VoteCandidateRow(id, 5L, CandidateSource.MANUAL, 3L, "123", "식당", "서울", BigDecimal.ONE,
                BigDecimal.TEN, "한식", "https://place.map.kakao.com/123", 4.5);
    }
    public static VoteSessionResponse response() { return VoteSessionResponse.from(session()); }
    public static BallotResponse ballot() { return new BallotResponse("ballot_1", "vote_5", "candidate_100", "member_1", NOW, NOW); }
    private VoteFixture() {}
}
