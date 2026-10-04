package com.menusolomon.vote.domain;

import static org.assertj.core.api.Assertions.*;
import com.menusolomon.common.exception.ErrorCode;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VoteDomainTest {
    private static final Instant NOW = Instant.parse("2026-10-04T04:00:00Z");
    private LunchVoteSession session() { return LunchVoteSession.create(1L, 2L, NOW.plusSeconds(10800), NOW); }

    @Test void create_hasNullableNameAndFutureDeadline() {
        var vote = session();
        assertThat(vote.getName()).isNull();
        assertThat(vote.getStatus()).isEqualTo(VoteStatus.OPEN);
        assertThat(vote.getClosesAt()).isEqualTo(NOW.plusSeconds(10800));
    }
    @Test void create_withNameStoresNameAndProtectsInvariant() {
        assertThat(LunchVoteSession.create(1L,2L,"asf",NOW.plusSeconds(1),NOW).getName()).isEqualTo("asf");
        assertThatThrownBy(() -> LunchVoteSession.create(1L,2L," ",NOW.plusSeconds(1),NOW))
                .hasFieldOrPropertyWithValue("errorCode",ErrorCode.VALIDATION_ERROR);
    }
    @Test void create_nonFutureDeadline_isRejected() {
        assertThatThrownBy(() -> LunchVoteSession.create(1L, 2L, NOW, NOW)).hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }
    @Test void patch_preservesUnsentFields() {
        var vote = session(); vote.update("점심", null, NOW);
        assertThat(vote.getClosesAt()).isEqualTo(NOW.plusSeconds(10800));
        vote.update(null, NOW.plusSeconds(14400), NOW);
        assertThat(vote.getName()).isEqualTo("점심");
    }
    @ParameterizedTest @ValueSource(strings = {"", " ", "12345678901234567890123456789012345678901"})
    void patch_invalidName_isRejected(String name) {
        assertThatThrownBy(() -> session().update(name, null, NOW)).hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }
    @Test void deadlineBoundary_closesAtIsNotOpen() {
        var vote = session();
        assertThat(vote.isDue(vote.getClosesAt())).isTrue();
        assertThatThrownBy(() -> vote.requireOpen(vote.getClosesAt())).hasFieldOrPropertyWithValue("errorCode", ErrorCode.VOTE_NOT_OPEN);
        vote.close(vote.getClosesAt()); assertThat(vote.getStatus()).isEqualTo(VoteStatus.CLOSED);
    }
    @Test void restart_keepsNameAndMovesDeadlineThreeHours() {
        var vote = session(); vote.update("점심", null, NOW); vote.close(NOW.plusSeconds(10800));
        vote.restart(2L, NOW.plusSeconds(20000));
        assertThat(vote.getStatus()).isEqualTo(VoteStatus.OPEN);
        assertThat(vote.getClosesAt()).isEqualTo(NOW.plusSeconds(30800));
        assertThat(vote.getName()).isEqualTo("점심");
    }
    @Test void creatorOnlyBehavior_rejectsOtherMember() {
        assertThatThrownBy(() -> session().restart(3L, NOW)).hasFieldOrPropertyWithValue("errorCode", ErrorCode.VOTE_CREATOR_REQUIRED);
    }
    @Test void confirmedVote_cannotRestartOrChange() {
        var vote = session(); vote.close(NOW.plusSeconds(10800)); vote.confirm();
        assertThatThrownBy(() -> vote.restart(2L, NOW)).hasFieldOrPropertyWithValue("errorCode", ErrorCode.VOTE_ALREADY_CONFIRMED);
        assertThatThrownBy(() -> vote.update("새 이름", null, NOW)).hasFieldOrPropertyWithValue("errorCode", ErrorCode.VOTE_ALREADY_CONFIRMED);
        vote.removeDecision(2L); assertThat(vote.getStatus()).isEqualTo(VoteStatus.CLOSED);
    }
    @Test void singlePositiveLeader_isAutomaticWinner() {
        var tally = new VoteTally(List.of(new VoteTally.Entry(1L, 10L, 2), new VoteTally.Entry(2L, 20L, 1)), 2);
        assertThat(tally.automaticWinner().orElseThrow().restaurantId()).isEqualTo(10L);
        assertThatThrownBy(() -> tally.requireManualCandidate(10L)).hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DECISION_CANDIDATE);
    }
    @Test void tiedLeaders_allowOnlyTheirRestaurantsForManualDecision() {
        var tally = new VoteTally(List.of(new VoteTally.Entry(1L, 10L, 2), new VoteTally.Entry(2L, 20L, 2), new VoteTally.Entry(3L, 30L, 1)), 2);
        assertThat(tally.automaticWinner()).isEmpty(); tally.requireManualCandidate(10L);
        assertThatThrownBy(() -> tally.requireManualCandidate(30L)).hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DECISION_CANDIDATE);
        assertThat(tally.percentage(2)).isEqualTo(100);
    }
    @Test void noBallots_doesNotAutomaticallyOrManuallyConfirm() {
        var tally = new VoteTally(List.of(new VoteTally.Entry(1L, 10L, 0)), 0);
        assertThat(tally.automaticWinner()).isEmpty(); assertThat(tally.percentage(0)).isZero();
        assertThatThrownBy(() -> tally.requireManualCandidate(10L)).hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DECISION_CANDIDATE);
    }
    @Test void noBallots_withMultipleTiedCandidates_allowsCreatorManualSelection() {
        var tally = new VoteTally(List.of(new VoteTally.Entry(1L, 10L, 0), new VoteTally.Entry(2L, 20L, 0)), 0);
        assertThat(tally.automaticWinner()).isEmpty();
        tally.requireManualCandidate(10L);
    }
    @Test void historyWeek_startsMondayInSeoulAndExcludesNextMonday() {
        var period = HistoryPeriod.of("WEEK", LocalDate.parse("2026-10-04"), null);
        assertThat(period.from()).isEqualTo(Instant.parse("2026-09-27T15:00:00Z"));
        assertThat(period.until()).isEqualTo(Instant.parse("2026-10-04T15:00:00Z"));
    }
    @Test void historyMonth_handlesYearBoundary() {
        var period = HistoryPeriod.of("MONTH", null, YearMonth.of(2026, 12));
        assertThat(period.from()).isEqualTo(Instant.parse("2026-11-30T15:00:00Z"));
        assertThat(period.until()).isEqualTo(Instant.parse("2026-12-31T15:00:00Z"));
    }
    @Test void participant_canBecomeAbsent() {
        var participant = VoteParticipant.create(1L, 2L, true, NOW); participant.changeParticipation(false, NOW.plusSeconds(1));
        assertThatThrownBy(participant::requireParticipation).hasFieldOrPropertyWithValue("errorCode", ErrorCode.VOTE_PARTICIPATION_REQUIRED);
    }
    @Test void automaticDecision_hasNoConfirmer_andEditBecomesManual() {
        var decision = ConfirmedMenu.automatic(1L, 10L, NOW);
        assertThat(decision.getConfirmedByTeamMemberId()).isNull();
        assertThat(decision.getConfirmationType()).isEqualTo(ConfirmationType.AUTO);
        decision.changeRestaurant(20L, 2L, NOW.plusSeconds(1));
        assertThat(decision.getConfirmationType()).isEqualTo(ConfirmationType.MANUAL);
        assertThat(decision.getRestaurantId()).isEqualTo(20L);
        assertThat(decision.getConfirmedAt()).isEqualTo(NOW.plusSeconds(1));
    }
}
