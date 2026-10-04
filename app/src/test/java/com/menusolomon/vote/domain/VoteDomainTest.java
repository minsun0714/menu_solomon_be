package com.menusolomon.vote.domain;

import static org.assertj.core.api.Assertions.*;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VoteDomainTest {
    private final Instant now = Instant.parse("2026-10-04T04:00:00Z");

    @Test
    void newVote_isOpen() {
        assertThat(LunchVoteSession.create(1L, "점심", 2L, now).isOpen()).isTrue();
    }

    @Test
    void confirm_openVote_becomesConfirmed() {
        var vote = LunchVoteSession.create(1L, "점심", 2L, now);
        vote.confirm(now.plusSeconds(60));
        assertThat(vote.getStatus()).isEqualTo(VoteStatus.CONFIRMED);
        assertThat(vote.getClosedAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void confirm_confirmedVote_isRejected_withoutChangingClosedAt() {
        var vote = LunchVoteSession.create(1L, "점심", 2L, now);
        vote.confirm(now);
        assertThatThrownBy(() -> vote.confirm(now.plusSeconds(60))).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VOTE_ALREADY_CONFIRMED));
        assertThat(vote.getClosedAt()).isEqualTo(now);
    }

    @Test
    void participation_canChange_andAbsentMemberCannotVote() {
        var participant = VoteParticipant.create(1L, 2L, true, now);
        participant.changeParticipation(false, now.plusSeconds(60));
        assertThat(participant.isParticipating()).isFalse();
        assertThat(participant.getUpdatedAt()).isEqualTo(now.plusSeconds(60));
        assertThatThrownBy(participant::requireParticipation).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VOTE_PARTICIPATION_REQUIRED));
        participant.changeParticipation(true, now.plusSeconds(120));
        assertThatCode(participant::requireParticipation).doesNotThrowAnyException();
    }

    @Test
    void changeCandidate_updatesVote_andPreservesCreatedAt() {
        var record = VoteRecord.create(1L, 2L, 3L, now);
        record.changeCandidate(4L, now.plusSeconds(60));
        assertThat(record.getVoteCandidateId()).isEqualTo(4L);
        assertThat(record.getUpdatedAt()).isEqualTo(now.plusSeconds(60));
        assertThat(record.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void createVote_invalidTitle_isRejectedByDomain() {
        for (String title : new String[]{null, "", " ", "가".repeat(101)}) {
            assertThatThrownBy(() -> LunchVoteSession.create(1L, title, 2L, now)).isInstanceOf(BusinessException.class);
        }
    }
}
