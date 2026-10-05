package com.menusolomon.vote.service;

import static com.menusolomon.vote.fixture.VoteFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.menusolomon.common.exception.*;
import com.menusolomon.restaurant.domain.Restaurant;
import com.menusolomon.restaurant.repository.RestaurantRepository;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.user.domain.User;
import com.menusolomon.user.service.UserService;
import com.menusolomon.vote.domain.*;
import com.menusolomon.vote.dto.*;
import com.menusolomon.vote.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoteServiceImplTest {
    @Mock UserService users;
    @Mock TeamMemberRepository members;
    @Mock RestaurantRepository restaurants;
    @Mock LunchVoteSessionRepository sessions;
    @Mock VoteParticipantRepository participants;
    @Mock VoteCandidateRepository candidates;
    @Mock VoteRecordRepository ballots;
    @Mock ConfirmedMenuRepository decisions;
    @Mock VoteRecommendationRepository recommendations;
    VoteServiceImpl service;
    TeamMember member;

    @BeforeEach void setUp() {
        service=new VoteServiceImpl(users,members,restaurants,sessions,participants,candidates,ballots,decisions,recommendations,Clock.fixed(NOW,ZoneOffset.UTC));
        member=id(TeamMember.newMember(1L,10L,NOW),1L);
    }
    private void identity() {
        var user=id(User.create("hash","익명",NOW),10L);
        when(users.findBySessionToken("token")).thenReturn(Optional.of(user));
        when(members.findByTeamIdAndUserId(1L,10L)).thenReturn(Optional.of(member));
    }
    private LunchVoteSession scoped() {
        identity(); var vote=session(); when(sessions.findScopedForUpdate(5L,1L)).thenReturn(Optional.of(vote)); return vote;
    }
    private void participating() {
        when(participants.findBySessionIdAndTeamMemberId(5L,1L)).thenReturn(Optional.of(VoteParticipant.create(5L,1L,true,NOW)));
    }
    private void error(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode",code);
    }
    @Test void createVote_initializesAllActiveParticipantsAndAllowsMultipleOpenSessions() {
        identity(); when(members.findAllByTeamIdAndLeftAtIsNull(1L)).thenReturn(List.of(member));
        when(sessions.save(any())).thenAnswer(invocation -> id(invocation.getArgument(0),5L));
        var first=service.createVote(1L,"token",null,DEADLINE); service.createVote(1L,"token",null,DEADLINE);
        assertThat(first.name()).isNull(); assertThat(first.status()).isEqualTo(VoteStatus.OPEN);
        verify(sessions,times(2)).save(any()); verify(participants,times(2)).saveAll(argThat(rows -> rows.iterator().next().isParticipating()));
    }
    @Test void createVote_namedRequestPersistsName() {
        identity(); when(members.findAllByTeamIdAndLeftAtIsNull(1L)).thenReturn(List.of(member));
        when(sessions.save(any())).thenAnswer(invocation -> id(invocation.getArgument(0),5L));
        assertThat(service.createVote(1L,"token","asf",DEADLINE).name()).isEqualTo("asf");
        verify(sessions).save(argThat(vote -> vote.getName().equals("asf")));
    }
    @Test void createVote_nonMember_isRejectedWithoutSaving() {
        error(() -> service.createVote(1L,"token",null,DEADLINE),ErrorCode.NOT_TEAM_MEMBER); verifyNoInteractions(sessions);
    }
    @Test
    @DisplayName("팀 투표 정산은 후보 ID 탐색 후 PK 잠금을 획득하고 이미 확정된 투표는 건너뛴다")
    void getVotes_rechecksLatestStateAfterPrimaryLock() {
        identity();
        var vote = id(LunchVoteSession.create(1L, 1L, NOW.minusSeconds(1), NOW.minusSeconds(10)), 5L);
        vote.close(NOW); vote.confirm();
        when(sessions.findDueIdsForTeam(1L, NOW)).thenReturn(List.of(5L));
        when(sessions.findAllForUpdate(List.of(5L))).thenReturn(List.of(vote));

        assertThat(service.getVotes(1L, "token")).isEmpty();

        var order = inOrder(sessions);
        order.verify(sessions).findDueIdsForTeam(1L, NOW);
        order.verify(sessions).findAllForUpdate(List.of(5L));
        order.verify(sessions).flush();
        verifyNoInteractions(candidates, decisions);
    }

    @Test
    @DisplayName("만료 후보 ID가 없으면 팀 투표 목록에서 잠금 조회를 하지 않는다")
    void getVotes_withoutDueIds_doesNotAcquireLocks() {
        identity();
        assertThat(service.getVotes(1L, "token")).isEmpty();
        verify(sessions, never()).findAllForUpdate(any());
    }

    @Test void getVotes_usesDistinctVoterCountAndAllMyCandidates() {
        identity(); when(sessions.findSummaries(1L)).thenReturn(List.of(new VoteSummaryRow(5L,1L,null,1L,"익명",VoteStatus.OPEN,DEADLINE,NOW,2,3,1)));
        when(ballots.findAllBySessionIdInAndTeamMemberIdOrderById(List.of(5L),1L)).thenReturn(List.of(VoteRecord.create(5L,1L,100L,NOW),VoteRecord.create(5L,1L,200L,NOW)));
        var result=service.getVotes(1L,"token").getFirst();
        assertThat(result.ballotCount()).isEqualTo(1); assertThat(result.myBallotCandidateIds()).containsExactly("candidate_100","candidate_200");
    }
    @Test void getDetail_wrongTeam_isNotFound() {
        identity(); error(() -> service.getVoteDetail(1L,5L,"token"),ErrorCode.VOTE_NOT_FOUND);
    }
    @Test void updateVote_anyMemberCanChangeNameAndKeepsDeadline() {
        var vote=scoped(); service.updateVote(1L,5L,"token",new VoteUpdateRequest("점심",null));
        assertThat(vote.getName()).isEqualTo("점심"); assertThat(vote.getClosesAt()).isEqualTo(DEADLINE);
    }
    @Test void updateVote_pastDeadlineIsRejectedBeforeChanging() {
        var vote=scoped(); error(() -> service.updateVote(1L,5L,"token",new VoteUpdateRequest("점심",NOW)),ErrorCode.VALIDATION_ERROR);
        assertThat(vote.getName()).isNull();
    }
    @Test void updateOtherParticipant_falseDeletesAllTheirBallotsOnly() {
        scoped(); var target=id(TeamMember.newMember(1L,20L,NOW),2L);
        when(members.findByIdAndTeamId(2L,1L)).thenReturn(Optional.of(target));
        var participant=VoteParticipant.create(5L,2L,true,NOW);
        when(participants.findBySessionIdAndTeamMemberId(5L,2L)).thenReturn(Optional.of(participant));
        when(participants.findProfile(5L,2L)).thenReturn(Optional.of(new VoteParticipantRow(7L,5L,2L,"다른 팀원",false)));
        var result=service.updateParticipation(1L,5L,"token",2L,false);
        assertThat(result.teamMemberId()).isEqualTo("member_2"); assertThat(participant.isParticipating()).isFalse();
        verify(ballots).deleteMyBallots(5L,2L); verify(ballots,never()).deleteMyBallots(5L,1L);
    }
    @Test void updateParticipant_wrongTeamTargetIsRejected() {
        scoped(); error(() -> service.updateParticipation(1L,5L,"token",2L,false),ErrorCode.TEAM_MEMBER_NOT_FOUND);
        verifyNoInteractions(ballots);
    }
    @Test void updateParticipant_inactiveTargetIsRejected() {
        scoped(); var target=id(TeamMember.newMember(1L,20L,NOW),2L); target.leave(NOW);
        when(members.findByIdAndTeamId(2L,1L)).thenReturn(Optional.of(target));
        error(() -> service.updateParticipation(1L,5L,"token",2L,false),ErrorCode.TEAM_MEMBER_NOT_FOUND);
    }
    @Test void addCandidate_usesSearchCachedRestaurantWithoutTeamRegistrationRequirement() {
        scoped(); var restaurant=id(Restaurant.create("123","식당","서울",BigDecimal.ONE,BigDecimal.TEN,"한식","url",NOW),3L);
        when(restaurants.findByKakaoPlaceId("123")).thenReturn(Optional.of(restaurant));
        when(candidates.saveAndFlush(any())).thenAnswer(invocation -> id(invocation.getArgument(0),100L));
        when(candidates.findDetail(100L,5L,1L)).thenReturn(Optional.of(candidateRow(100L)));
        assertThat(service.addCandidate(1L,5L,"token",new VoteCandidateCreateRequest("123",CandidateSource.MANUAL)).id()).isEqualTo("candidate_100");
    }
    @Test void addCandidate_duplicateIsRejected() {
        scoped(); var restaurant=id(Restaurant.create("123","식당","서울",BigDecimal.ONE,BigDecimal.TEN,"한식","url",NOW),3L);
        when(restaurants.findByKakaoPlaceId("123")).thenReturn(Optional.of(restaurant));
        when(candidates.existsBySessionIdAndRestaurantId(5L,3L)).thenReturn(true);
        error(() -> service.addCandidate(1L,5L,"token",new VoteCandidateCreateRequest("123",CandidateSource.MANUAL)),ErrorCode.VOTE_CANDIDATE_ALREADY_EXISTS);
        verify(candidates,never()).saveAndFlush(any());
    }
    @Test void deleteCandidate_deletesLinkedBallotsBeforeCandidate() {
        scoped(); var candidate=id(VoteCandidate.create(5L,3L,CandidateSource.MANUAL,NOW),100L);
        when(candidates.findByIdAndSessionId(100L,5L)).thenReturn(Optional.of(candidate));
        service.deleteCandidate(1L,5L,"token",100L);
        var order=inOrder(ballots,candidates); order.verify(ballots).deleteCandidateBallots(5L,100L); order.verify(candidates).delete(candidate);
    }
    @Test void saveBallots_deduplicatesAndReplacesOwnSelectionAtomically() {
        scoped(); participating(); when(candidates.countBySessionIdAndIdIn(eq(5L),any())).thenReturn(2L);
        when(ballots.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result=service.saveBallots(1L,5L,"token",List.of(100L,200L,100L));
        assertThat(result).extracting(BallotResponse::candidateId).containsExactly("candidate_100","candidate_200");
        var order=inOrder(ballots); order.verify(ballots).deleteMyBallots(5L,1L); order.verify(ballots).saveAll(any());
    }
    @Test void saveBallots_invalidCandidatePreservesExistingSelection() {
        scoped(); participating(); when(candidates.countBySessionIdAndIdIn(eq(5L),any())).thenReturn(1L);
        error(() -> service.saveBallots(1L,5L,"token",List.of(100L,200L)),ErrorCode.VOTE_CANDIDATE_NOT_FOUND);
        verify(ballots,never()).deleteMyBallots(any(),any()); verify(ballots,never()).saveAll(any());
    }
    @Test void saveBallots_absentMemberIsRejected() {
        scoped(); when(participants.findBySessionIdAndTeamMemberId(5L,1L)).thenReturn(Optional.of(VoteParticipant.create(5L,1L,false,NOW)));
        error(() -> service.saveBallots(1L,5L,"token",List.of(100L)),ErrorCode.VOTE_PARTICIPATION_REQUIRED);
        verifyNoInteractions(ballots);
    }
    @Test void cancelBallots_deletesOwnBallotsOnly() {
        scoped(); participating(); service.cancelBallots(1L,5L,"token"); verify(ballots).deleteMyBallots(5L,1L);
    }
    @Test void results_percentageUsesDistinctVotersAndCanSumOver100() {
        scoped(); when(candidates.findTally(5L)).thenReturn(List.of(new VoteTally.Entry(100L,3L,2),new VoteTally.Entry(200L,4L,2)));
        when(ballots.countVoters(5L)).thenReturn(2L);
        var result=service.getResults(1L,5L,"token");
        assertThat(result.results()).extracting(VoteResultItem::percentage).containsExactly(100.0,100.0);
    }
    @Test void settleExpired_uniquePositiveLeaderAutomaticallyConfirmsExactlyOnce() {
        var vote=id(LunchVoteSession.create(1L,1L,NOW.minusSeconds(1),NOW.minusSeconds(10)),5L);
        when(sessions.findForUpdate(5L)).thenReturn(Optional.of(vote));
        when(candidates.findTally(5L)).thenReturn(List.of(new VoteTally.Entry(100L,3L,2),new VoteTally.Entry(200L,4L,1)));
        service.settleExpired(5L); service.settleExpired(5L);
        assertThat(vote.getStatus()).isEqualTo(VoteStatus.CONFIRMED);
        verify(decisions).saveAndFlush(argThat(d -> d.getConfirmationType()==ConfirmationType.AUTO && d.getConfirmedByTeamMemberId()==null && d.getRestaurantId()==3L));
    }
    @Test void settleExpired_tieClosesWithoutDecision() {
        var vote=id(LunchVoteSession.create(1L,1L,NOW.minusSeconds(1),NOW.minusSeconds(10)),5L);
        when(sessions.findForUpdate(5L)).thenReturn(Optional.of(vote));
        when(candidates.findTally(5L)).thenReturn(List.of(new VoteTally.Entry(100L,3L,2),new VoteTally.Entry(200L,4L,2)));
        service.settleExpired(5L); assertThat(vote.getStatus()).isEqualTo(VoteStatus.CLOSED); verifyNoInteractions(decisions);
    }
    @Test void settleExpired_noBallotsClosesWithoutDecision() {
        var vote=id(LunchVoteSession.create(1L,1L,NOW.minusSeconds(1),NOW.minusSeconds(10)),5L);
        when(sessions.findForUpdate(5L)).thenReturn(Optional.of(vote));
        when(candidates.findTally(5L)).thenReturn(List.of(new VoteTally.Entry(100L,3L,0)));
        service.settleExpired(5L); assertThat(vote.getStatus()).isEqualTo(VoteStatus.CLOSED); verifyNoInteractions(decisions);
    }
    @Test
    @DisplayName("마감된 투표의 생성자는 0표인 단독 후보를 수동 확정할 수 있다")
    void manualDecision_singleCandidateWithoutBallots_confirmsManually() {
        var vote = scoped(); vote.close(DEADLINE);
        when(candidates.findTally(5L)).thenReturn(List.of(new VoteTally.Entry(100L, 3L, 0)));
        when(decisions.saveAndFlush(any())).thenAnswer(invocation -> id(invocation.getArgument(0), 7L));
        var result = service.createDecision(1L, 5L, "token", 3L);
        assertThat(result.confirmationType()).isEqualTo(ConfirmationType.MANUAL);
        assertThat(vote.getStatus()).isEqualTo(VoteStatus.CONFIRMED);
        verify(decisions).saveAndFlush(argThat(decision -> decision.getRestaurantId().equals(3L)
                && decision.getConfirmedByTeamMemberId().equals(member.getId())));
    }

    @Test void manualDecision_onlyCreatorAndTiedLeaderAllowed() {
        var vote=scoped(); vote.close(DEADLINE);
        when(candidates.findTally(5L)).thenReturn(List.of(new VoteTally.Entry(100L,3L,1),new VoteTally.Entry(200L,4L,1)));
        when(decisions.saveAndFlush(any())).thenAnswer(invocation -> id(invocation.getArgument(0),7L));
        var result=service.createDecision(1L,5L,"token",3L);
        assertThat(result.confirmationType()).isEqualTo(ConfirmationType.MANUAL); assertThat(vote.getStatus()).isEqualTo(VoteStatus.CONFIRMED);
    }
    @Test void manualDecision_nonCreatorIsRejected() {
        identity(); var vote=id(LunchVoteSession.create(1L,2L,DEADLINE,NOW),5L);
        when(sessions.findScopedForUpdate(5L,1L)).thenReturn(Optional.of(vote));
        error(() -> service.createDecision(1L,5L,"token",3L),ErrorCode.VOTE_CREATOR_REQUIRED); verifyNoInteractions(decisions);
    }
    @Test void manualDecision_openSessionIsRejected() {
        scoped(); error(() -> service.createDecision(1L,5L,"token",3L),ErrorCode.VOTE_NOT_CLOSED);
    }
    @Test void manualDecision_nonLeaderIsRejected() {
        var vote=scoped(); vote.close(DEADLINE);
        when(candidates.findTally(5L)).thenReturn(List.of(new VoteTally.Entry(100L,3L,2),new VoteTally.Entry(200L,4L,2),new VoteTally.Entry(300L,6L,1)));
        error(() -> service.createDecision(1L,5L,"token",6L),ErrorCode.INVALID_DECISION_CANDIDATE);
    }
    @Test void deleteDecision_clearsResultAndClosesVote() {
        var vote=scoped(); vote.close(DEADLINE); vote.confirm();
        var decision=ConfirmedMenu.automatic(5L,3L,NOW);
        when(decisions.findBySessionId(5L)).thenReturn(Optional.of(decision));
        service.deleteDecision(1L,5L,"token"); assertThat(vote.getStatus()).isEqualTo(VoteStatus.CLOSED); verify(decisions).delete(decision);
    }
    @Test void updateDecision_canSelectAnyCandidateAndBecomesManual() {
        var vote=scoped(); vote.close(DEADLINE); vote.confirm();
        var decision=ConfirmedMenu.automatic(5L,3L,NOW);
        when(decisions.findBySessionId(5L)).thenReturn(Optional.of(decision));
        when(candidates.findBySessionIdAndRestaurantId(5L,4L)).thenReturn(Optional.of(VoteCandidate.create(5L,4L,CandidateSource.MANUAL,NOW)));
        var result=service.updateDecision(1L,5L,"token",4L); assertThat(result.restaurantId()).isEqualTo("restaurant_4"); assertThat(result.confirmationType()).isEqualTo(ConfirmationType.MANUAL);
    }
    @Test void restart_preservesCandidatesAndParticipantsButClearsAllBallots() {
        var vote=scoped(); vote.close(DEADLINE); service.restart(1L,5L,"token");
        assertThat(vote.getStatus()).isEqualTo(VoteStatus.OPEN); assertThat(vote.getClosesAt()).isEqualTo(DEADLINE);
        verify(ballots).deleteBySession(5L); verifyNoInteractions(candidates,participants,decisions);
    }
    @Test void deleteVote_deletesAllOwnedDataInOrderButNoRestaurant() {
        var vote=scoped(); service.deleteVote(1L,5L,"token");
        var order=inOrder(decisions,ballots,participants,candidates,sessions);
        order.verify(decisions).deleteBySession(5L); order.verify(ballots).deleteBySession(5L);
        order.verify(participants).deleteBySession(5L); order.verify(candidates).deleteBySession(5L); order.verify(sessions).delete(vote);
        verifyNoInteractions(restaurants);
    }
    @Test void history_usesSeoulWeekHalfOpenRange() {
        identity(); service.getHistory(1L,"token","WEEK",LocalDate.of(2026,10,4),null);
        verify(decisions).findHistory(1L,Instant.parse("2026-09-27T15:00:00Z"),Instant.parse("2026-10-04T15:00:00Z"));
    }
    @Test void recommendation_passesSevenDayCutoffAndSingleItemCursor() {
        scoped(); service.recommend(1L,5L,"token",3);
        verify(recommendations).findRecommendations(eq(1L),eq(5L),eq(NOW.minusSeconds(7*86400)),argThat(page -> page.getPageSize()==1 && page.getOffset()==3));
    }
    @Test void inactiveMember_cannotReadVotes() {
        identity(); member.leave(NOW); error(() -> service.getVotes(1L,"token"),ErrorCode.NOT_TEAM_MEMBER); verifyNoInteractions(sessions);
    }
}
