package com.menusolomon.vote.domain;

import static org.assertj.core.api.Assertions.*;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.vote.domain.VoteClosingTimeException;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

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
    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    @DisplayName("생성과 수정은 과거 또는 현재와 같은 마감 시간을 식별 가능한 예외로 거절한다")
    void closingTime_notFuture_isRejectedWithoutChangingSession(long seconds) {
        Instant closesAt = NOW.plusSeconds(seconds);
        assertThatThrownBy(() -> LunchVoteSession.create(1L, 2L, closesAt, NOW))
                .isInstanceOf(VoteClosingTimeException.class);
        var vote = session();
        Instant originalDeadline = vote.getClosesAt();
        assertThatThrownBy(() -> vote.update(null, closesAt, NOW))
                .isInstanceOf(VoteClosingTimeException.class);
        assertThat(vote.getClosesAt()).isEqualTo(originalDeadline);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 60, 120})
    @DisplayName("서버는 생성과 수정에서 미래 마감 시간을 허용하며 최소 1분을 강제하지 않는다")
    void closingTime_future_isAcceptedForCreateAndUpdate(long seconds) {
        Instant closesAt = NOW.plusSeconds(seconds);
        assertThat(LunchVoteSession.create(1L, 2L, closesAt, NOW).getClosesAt()).isEqualTo(closesAt);
        var vote = session(); vote.update(null, closesAt, NOW);
        assertThat(vote.getClosesAt()).isEqualTo(closesAt);
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
    @ParameterizedTest
    @EnumSource(VoteStatus.class)
    @DisplayName("마감 시간이 지난 투표는 상태와 관계없이 이름만 변경할 수 있다")
    void expiredVote_canChangeNameWithoutChangingDeadlineOrStatus(VoteStatus status) {
        var vote = session();
        Instant deadline = vote.getClosesAt();
        if (status != VoteStatus.OPEN) vote.close(deadline);
        if (status == VoteStatus.CONFIRMED) vote.confirm();
        vote.update("변경된 이름", null, deadline.plusSeconds(1));
        assertThat(vote.getName()).isEqualTo("변경된 이름");
        assertThat(vote.getClosesAt()).isEqualTo(deadline);
        assertThat(vote.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(value = VoteStatus.class, names = {"CLOSED", "CONFIRMED"})
    @DisplayName("마감된 투표의 이름과 종료 시간을 함께 변경하면 전체 변경을 거절한다")
    void closedVote_cannotChangeDeadlineEvenWithNewName(VoteStatus status) {
        var vote = session();
        Instant deadline = vote.getClosesAt();
        vote.close(deadline);
        if (status == VoteStatus.CONFIRMED) vote.confirm();
        ErrorCode expected = status == VoteStatus.CONFIRMED ? ErrorCode.VOTE_ALREADY_CONFIRMED : ErrorCode.VOTE_NOT_OPEN;
        assertThatThrownBy(() -> vote.update("새 이름", deadline.plusSeconds(60), deadline))
                .hasFieldOrPropertyWithValue("errorCode", expected);
        assertThat(vote.getName()).isNull();
        assertThat(vote.getClosesAt()).isEqualTo(deadline);
    }

    @Test
    @DisplayName("즉시 마감 요청은 종료 시간을 서버 현재 시각으로 바꾸고 정산 가능한 상태로 만든다")
    void expireNow_openVote_becomesDue() {
        var vote = session();
        vote.expireNow(NOW);
        assertThat(vote.getClosesAt()).isEqualTo(NOW);
        assertThat(vote.isDue(NOW)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = VoteStatus.class, names = {"CLOSED", "CONFIRMED"})
    @DisplayName("이미 마감 또는 확정된 투표의 즉시 마감은 VOTE_NOT_OPEN으로 거절한다")
    void expireNow_closedOrConfirmed_isRejected(VoteStatus status) {
        var vote = session(); Instant deadline = vote.getClosesAt();
        vote.close(deadline);
        if (status == VoteStatus.CONFIRMED) vote.confirm();
        assertThatThrownBy(() -> vote.expireNow(deadline.plusSeconds(1)))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VOTE_NOT_OPEN);
        assertThat(vote.getClosesAt()).isEqualTo(deadline);
    }

    @Test
    @DisplayName("종료 시간이 이미 지난 OPEN 투표도 즉시 마감 요청을 거절한다")
    void expireNow_alreadyExpired_isRejected() {
        var vote = session();
        assertThatThrownBy(() -> vote.expireNow(vote.getClosesAt()))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VOTE_NOT_OPEN);
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
    @Test void confirmedVote_cannotRestartOrChangeDeadline() {
        var vote = session(); vote.close(NOW.plusSeconds(10800)); vote.confirm();
        assertThatThrownBy(() -> vote.restart(2L, NOW)).hasFieldOrPropertyWithValue("errorCode", ErrorCode.VOTE_ALREADY_CONFIRMED);
        assertThatThrownBy(() -> vote.update(null, NOW.plusSeconds(14400), NOW)).hasFieldOrPropertyWithValue("errorCode", ErrorCode.VOTE_ALREADY_CONFIRMED);
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
    @Test
    @DisplayName("후보가 하나이고 0표이면 자동 확정하지 않지만 해당 후보의 수동 확정은 허용한다")
    void singleCandidateWithoutBallots_allowsManualDecisionOnly() {
        var tally = new VoteTally(List.of(new VoteTally.Entry(1L, 10L, 0)), 0);
        assertThat(tally.automaticWinner()).isEmpty(); assertThat(tally.percentage(0)).isZero();
        assertThatCode(() -> tally.requireManualCandidate(10L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> tally.requireManualCandidate(20L))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DECISION_CANDIDATE);
    }
    @Test
    @DisplayName("후보가 없으면 수동 확정할 수 없다")
    void noCandidates_rejectsManualDecision() {
        var tally = new VoteTally(List.of(), 0);
        assertThatThrownBy(() -> tally.requireManualCandidate(10L))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DECISION_CANDIDATE);
    }
    @Test
    @DisplayName("득표한 단독 후보는 기존 자동 확정 정책을 따른다")
    void singlePositiveCandidate_remainsAutomaticWinner() {
        var tally = new VoteTally(List.of(new VoteTally.Entry(1L, 10L, 1)), 1);
        assertThat(tally.automaticWinner()).isPresent();
        assertThatThrownBy(() -> tally.requireManualCandidate(10L))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DECISION_CANDIDATE);
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
