package com.menusolomon.vote.service;

import static com.menusolomon.vote.fixture.VoteFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.restaurant.fixture.RestaurantFixture;
import com.menusolomon.restaurant.repository.TeamRestaurantRepository;
import com.menusolomon.team.fixture.TeamFixture;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.user.fixture.UserFixture;
import com.menusolomon.user.service.UserService;
import com.menusolomon.vote.domain.*;
import com.menusolomon.vote.repository.*;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VoteServiceImplTest {
    @Mock UserService users;
    @Mock TeamMemberRepository members;
    @Mock TeamRestaurantRepository restaurants;
    @Mock LunchVoteSessionRepository sessions;
    @Mock VoteParticipantRepository participants;
    @Mock VoteCandidateRepository candidates;
    @Mock VoteRecordRepository records;
    @Mock ConfirmedMenuRepository menus;
    VoteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VoteServiceImpl(users, members, restaurants, sessions, participants, candidates, records,
                menus, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void activeMember() {
        when(users.findBySessionToken("token")).thenReturn(Optional.of(UserFixture.user(10L, "hash", "익명")));
        when(members.findByTeamIdAndUserId(1L, 10L)).thenReturn(Optional.of(TeamFixture.admin()));
    }

    private LunchVoteSession openVote() {
        var vote = session();
        when(sessions.findByIdAndTeamIdForUpdate(5L, 1L)).thenReturn(Optional.of(vote));
        return vote;
    }

    private void validCandidate() {
        when(candidates.findByIdAndLunchVoteSessionId(100L, 5L)).thenReturn(Optional.of(candidate()));
    }

    private void candidateResponse() {
        when(candidates.findCandidate(100L, 5L, 1L, 1L)).thenReturn(Optional.of(candidateRow(100L)));
    }

    @Test
    void createVote_activeMember_createsOpenVoteAndInitialParticipants() {
        activeMember();
        when(sessions.save(any())).thenReturn(session());
        when(members.findAllByTeamIdAndLeftAtIsNull(1L)).thenReturn(List.of(TeamFixture.admin(), TeamFixture.member()));
        var result = service.createVote(1L, "token", "점심");
        assertThat(result.id()).isEqualTo("vote_5");
        assertThat(result.status()).isEqualTo("OPEN");
        verify(participants).saveAll(argThat(items -> {
            var list = java.util.stream.StreamSupport.stream(items.spliterator(), false).toList();
            assertThat(list).hasSize(2).allSatisfy(p -> {
                assertThat(p.getLunchVoteSessionId()).isEqualTo(5L);
                assertThat(p.isParticipating()).isTrue();
            });
            return true;
        }));
    }

    @Test
    void createVote_ordinaryMember_isAllowed() {
        when(users.findBySessionToken("token")).thenReturn(Optional.of(UserFixture.user(20L, "hash", "익명")));
        when(members.findByTeamIdAndUserId(1L, 20L)).thenReturn(Optional.of(TeamFixture.member()));
        when(members.findAllByTeamIdAndLeftAtIsNull(1L)).thenReturn(List.of(TeamFixture.member()));
        when(sessions.save(any())).thenReturn(session());
        assertThat(service.createVote(1L, "token", "점심").status()).isEqualTo("OPEN");
        var capture = ArgumentCaptor.forClass(LunchVoteSession.class);
        verify(sessions).save(capture.capture());
        assertThat(capture.getValue().getCreatedByTeamMemberId()).isEqualTo(2L);
    }

    @Test
    void createVote_nonMember_throwsNotTeamMember() {
        assertError(() -> service.createVote(1L, "token", "점심"), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(sessions, participants);
    }

    @Test
    void multipleOpenVotes_areAllowed() {
        activeMember();
        when(members.findAllByTeamIdAndLeftAtIsNull(1L)).thenReturn(List.of(TeamFixture.admin()));
        var ids = new java.util.concurrent.atomic.AtomicLong(4);
        when(sessions.save(any())).thenAnswer(invocation -> {
            LunchVoteSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", ids.incrementAndGet());
            return session;
        });
        var first = service.createVote(1L, "token", "첫 점심");
        var next = service.createVote(1L, "token", "다음 점심");
        assertThat(first.id()).isNotEqualTo(next.id());
        assertThat(first.status()).isEqualTo("OPEN");
        assertThat(next.status()).isEqualTo("OPEN");
        verify(sessions, times(2)).save(any());
    }

    @Test
    void updateParticipation_toFalse_marksAbsentAndDeletesExistingVoteRecord() {
        activeMember();
        openVote();
        var participant = participant();
        when(participants.findByLunchVoteSessionIdAndTeamMemberId(5L, 1L)).thenReturn(Optional.of(participant));
        assertThat(service.updateParticipation(1L, 5L, "token", false).participating()).isFalse();
        assertThat(participant.isParticipating()).isFalse();
        verify(records).deleteMyVote(5L, 1L);
    }

    @Test
    void updateParticipation_newMember_createsOwnParticipation() {
        activeMember();
        openVote();
        when(participants.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.updateParticipation(1L, 5L, "token", true).participating()).isTrue();
        verify(participants).save(any());
        verifyNoInteractions(records);
    }

    @Test
    void updateParticipation_confirmedVote_throwsVoteAlreadyConfirmed() {
        activeMember();
        openVote().confirm(NOW);
        assertError(() -> service.updateParticipation(1L, 5L, "token", false), ErrorCode.VOTE_ALREADY_CONFIRMED);
        verifyNoInteractions(participants, records);
    }

    @Test
    void updateParticipation_nonMember_throwsNotTeamMember() {
        assertError(() -> service.updateParticipation(1L, 5L, "token", false), ErrorCode.NOT_TEAM_MEMBER);
    }

    @Test
    void addCandidate_activeMember_succeeds() {
        activeMember();
        openVote();
        when(restaurants.findByIdAndTeamId(5L, 1L)).thenReturn(Optional.of(RestaurantFixture.savedLink()));
        when(candidates.saveAndFlush(any())).thenReturn(candidate());
        candidateResponse();
        assertThat(service.addCandidate(1L, 5L, "token", 5L).candidateId()).isEqualTo("candidate_100");
        var capture = ArgumentCaptor.forClass(VoteCandidate.class);
        verify(candidates).saveAndFlush(capture.capture());
        assertThat(capture.getValue().getRestaurantId()).isEqualTo(3L);
        verifyNoInteractions(participants);
    }

    @Test
    void addCandidate_duplicate_throwsCandidateAlreadyExists() {
        activeMember();
        openVote();
        when(restaurants.findByIdAndTeamId(5L, 1L)).thenReturn(Optional.of(RestaurantFixture.savedLink()));
        when(candidates.existsByLunchVoteSessionIdAndRestaurantId(5L, 3L)).thenReturn(true);
        assertError(() -> service.addCandidate(1L, 5L, "token", 5L), ErrorCode.VOTE_CANDIDATE_ALREADY_EXISTS);
        verify(candidates, never()).saveAndFlush(any());
    }

    @Test
    void addCandidate_databaseDuplicate_mapsToCandidateAlreadyExists() {
        activeMember();
        openVote();
        when(restaurants.findByIdAndTeamId(5L, 1L)).thenReturn(Optional.of(RestaurantFixture.savedLink()));
        when(candidates.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));
        assertError(() -> service.addCandidate(1L, 5L, "token", 5L), ErrorCode.VOTE_CANDIDATE_ALREADY_EXISTS);
    }

    @Test
    void addCandidate_restaurantFromOtherTeam_throwsTeamRestaurantNotFound() {
        activeMember();
        openVote();
        assertError(() -> service.addCandidate(1L, 5L, "token", 5L), ErrorCode.TEAM_RESTAURANT_NOT_FOUND);
        verifyNoInteractions(candidates);
    }

    @Test
    void addCandidate_confirmedVote_throwsVoteAlreadyConfirmed() {
        activeMember();
        openVote().confirm(NOW);
        assertError(() -> service.addCandidate(1L, 5L, "token", 5L), ErrorCode.VOTE_ALREADY_CONFIRMED);
    }

    @Test
    void vote_firstTime_createsVoteRecord() {
        activeMember();
        openVote();
        validCandidate();
        candidateResponse();
        when(participants.findByLunchVoteSessionIdAndTeamMemberId(5L, 1L)).thenReturn(Optional.of(participant()));
        service.vote(1L, 5L, "token", 100L);
        var capture = ArgumentCaptor.forClass(VoteRecord.class);
        verify(records).save(capture.capture());
        assertThat(capture.getValue().getTeamMemberId()).isEqualTo(1L);
        assertThat(capture.getValue().getVoteCandidateId()).isEqualTo(100L);
        assertThat(capture.getValue().getCreatedAt()).isEqualTo(NOW);
        var order = inOrder(sessions, records);
        order.verify(sessions).findByIdAndTeamIdForUpdate(5L, 1L);
        order.verify(records).findByLunchVoteSessionIdAndTeamMemberId(5L, 1L);
    }

    @Test
    void vote_again_changesExistingCandidateWithoutNewRow() {
        activeMember();
        openVote();
        validCandidate();
        candidateResponse();
        when(participants.findByLunchVoteSessionIdAndTeamMemberId(5L, 1L)).thenReturn(Optional.of(participant()));
        var existing = VoteRecord.create(5L, 1L, 99L, NOW.minusSeconds(60));
        when(records.findByLunchVoteSessionIdAndTeamMemberId(5L, 1L)).thenReturn(Optional.of(existing));
        service.vote(1L, 5L, "token", 100L);
        assertThat(existing.getVoteCandidateId()).isEqualTo(100L);
        assertThat(existing.getCreatedAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(existing.getUpdatedAt()).isEqualTo(NOW);
        verify(records, never()).save(any());
    }

    @Test
    void vote_nonParticipant_throwsVoteParticipationRequired() {
        activeMember();
        openVote();
        var participant = participant();
        participant.changeParticipation(false, NOW);
        when(participants.findByLunchVoteSessionIdAndTeamMemberId(5L, 1L)).thenReturn(Optional.of(participant));
        assertError(() -> service.vote(1L, 5L, "token", 100L), ErrorCode.VOTE_PARTICIPATION_REQUIRED);
        verifyNoInteractions(records, candidates);
    }

    @Test
    void vote_candidateFromOtherVote_throwsCandidateNotFound() {
        activeMember();
        openVote();
        when(participants.findByLunchVoteSessionIdAndTeamMemberId(5L, 1L)).thenReturn(Optional.of(participant()));
        assertError(() -> service.vote(1L, 5L, "token", 100L), ErrorCode.VOTE_CANDIDATE_NOT_FOUND);
        verifyNoInteractions(records);
    }

    @Test
    void vote_confirmedSession_throwsVoteAlreadyConfirmed() {
        activeMember();
        openVote().confirm(NOW);
        assertError(() -> service.vote(1L, 5L, "token", 100L), ErrorCode.VOTE_ALREADY_CONFIRMED);
        verifyNoInteractions(participants, records);
    }

    @Test
    void confirm_openVote_createsConfirmedMenuAndChangesStatus_evenForCandidateWithNoVotes() {
        activeMember();
        var session = openVote();
        validCandidate();
        when(menus.findResult(5L)).thenReturn(Optional.of(result()));
        var result = service.confirm(1L, 5L, "token", 100L);
        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.confirmedMenu().voteCount()).isZero();
        assertThat(session.getStatus()).isEqualTo(VoteStatus.CONFIRMED);
        assertThat(session.getClosedAt()).isEqualTo(NOW);
        var capture = ArgumentCaptor.forClass(ConfirmedMenu.class);
        verify(menus).saveAndFlush(capture.capture());
        assertThat(capture.getValue().getVoteCandidateId()).isEqualTo(100L);
        assertThat(capture.getValue().getConfirmedByTeamMemberId()).isEqualTo(1L);
        verifyNoInteractions(records);
    }

    @Test
    void confirm_candidateFromOtherVote_throwsCandidateNotFound() {
        activeMember();
        openVote();
        assertError(() -> service.confirm(1L, 5L, "token", 100L), ErrorCode.VOTE_CANDIDATE_NOT_FOUND);
        verifyNoInteractions(menus);
    }

    @Test
    void confirm_alreadyConfirmedVote_throwsVoteAlreadyConfirmed() {
        activeMember();
        openVote().confirm(NOW);
        assertError(() -> service.confirm(1L, 5L, "token", 100L), ErrorCode.VOTE_ALREADY_CONFIRMED);
        verifyNoInteractions(menus);
    }

    @Test
    void getHistory_returnsDatabaseConfirmedHistoryInProvidedOrder() {
        activeMember();
        when(menus.findHistory(1L)).thenReturn(List.of(
                new VoteHistoryRow(6L, "새 투표", NOW, 3L, "식당", 2L, 3L),
                new VoteHistoryRow(5L, "예전 투표", NOW.minusSeconds(60), 3L, "식당", 1L, 2L)));
        assertThat(service.getHistory(1L, "token")).extracting(item -> item.voteId()).containsExactly("vote_6", "vote_5");
        verify(menus).findHistory(1L);
    }

    @Test
    void getHistory_nonMember_throwsNotTeamMember() {
        assertError(() -> service.getHistory(1L, "token"), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(menus);
    }

    @Test
    void getVotes_passesStatusAndCurrentMemberToProjection() {
        activeMember();
        when(sessions.findSummaries(1L, 1L, VoteStatus.OPEN)).thenReturn(List.of(summary()));
        assertThat(service.getVotes(1L, "token", VoteStatus.OPEN).getFirst().myVoteCandidateId()).isEqualTo("candidate_100");
    }

    @Test
    void getVoteDetail_returnsParticipantCandidateAndConfirmedMenuProjections() {
        activeMember();
        when(sessions.findSummary(5L, 1L, 1L)).thenReturn(Optional.of(summary()));
        when(participants.findParticipants(5L)).thenReturn(List.of(new VoteParticipantRow(1L, "익명", true)));
        when(candidates.findCandidates(5L, 1L, 1L)).thenReturn(List.of(candidateRow(100L)));
        var result = service.getVoteDetail(1L, 5L, "token");
        assertThat(result.participants().getFirst().nickname()).isEqualTo("익명");
        assertThat(result.candidates().getFirst().voteCount()).isEqualTo(2);
        assertThat(result.confirmedMenu()).isNull();
    }

    @Test
    void getVoteDetail_otherTeamVote_throwsVoteNotFound() {
        activeMember();
        assertError(() -> service.getVoteDetail(1L, 5L, "token"), ErrorCode.VOTE_NOT_FOUND);
        verifyNoInteractions(participants, candidates, menus);
    }

    @Test
    void vote_inactiveMember_throwsNotTeamMember() {
        activeMember();
        var inactive = TeamFixture.admin();
        inactive.leave(NOW);
        when(members.findByTeamIdAndUserId(1L, 10L)).thenReturn(Optional.of(inactive));
        assertError(() -> service.vote(1L, 5L, "token", 100L), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(sessions);
    }

    @Test
    void getVotes_ordinaryMember_hasSameAccessAsAdmin() {
        when(users.findBySessionToken("token")).thenReturn(Optional.of(UserFixture.user(20L, "hash", "익명")));
        when(members.findByTeamIdAndUserId(1L, 20L)).thenReturn(Optional.of(TeamFixture.member()));
        assertThat(service.getVotes(1L, "token", null)).isEmpty();
        verify(sessions).findSummaries(1L, 2L, null);
    }

    @Test
    void deleteTeamData_bulkDeletesVotesInDependencyOrder() {
        service.deleteTeamData(1L);
        var order = inOrder(menus, records, participants, candidates, sessions);
        order.verify(menus).deleteAllByTeamId(1L);
        order.verify(records).deleteAllByTeamId(1L);
        order.verify(participants).deleteAllByTeamId(1L);
        order.verify(candidates).deleteAllByTeamId(1L);
        order.verify(sessions).deleteAllByTeamId(1L);
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(code));
    }
}
