package com.menusolomon.vote.integration;

import static com.menusolomon.vote.fixture.VoteFixture.NOW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.menusolomon.common.exception.*;
import com.menusolomon.restaurant.domain.*;
import com.menusolomon.restaurant.repository.*;
import com.menusolomon.review.domain.Review;
import com.menusolomon.review.repository.ReviewRepository;
import com.menusolomon.team.domain.*;
import com.menusolomon.team.repository.*;
import com.menusolomon.team.service.*;
import com.menusolomon.user.domain.User;
import com.menusolomon.user.repository.UserRepository;
import com.menusolomon.user.service.UserService;
import com.menusolomon.vote.domain.*;
import com.menusolomon.vote.dto.*;
import com.menusolomon.vote.repository.*;
import com.menusolomon.vote.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@Import({VoteServiceImpl.class,TeamServiceImpl.class,VoteConcurrencyTest.TimeConfiguration.class})
@TestPropertySource(properties = {"app.frontend-base-url=https://frontend.example",
        "spring.datasource.url=jdbc:h2:mem:vote_ui_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VoteConcurrencyTest {
    static class MutableClock extends Clock {
        final AtomicReference<Instant> time=new AtomicReference<>(NOW);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(),zone); }
        @Override public Instant instant() { return time.get(); }
    }
    @TestConfiguration static class TimeConfiguration {
        @Bean MutableClock clock() { return new MutableClock(); }
    }
    @Autowired VoteService service;
    @Autowired TeamService teamService;
    @Autowired LunchVoteSessionRepository sessions;
    @Autowired VoteCandidateRepository candidates;
    @Autowired VoteParticipantRepository participants;
    @Autowired VoteRecordRepository ballots;
    @Autowired ConfirmedMenuRepository decisions;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurants;
    @Autowired TeamRestaurantRepository links;
    @Autowired ReviewRepository reviews;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MutableClock clock;
    @MockitoBean UserService users;
    Long teamId, voteId, firstId, secondId, firstRestaurant, secondRestaurant, firstCandidate, secondCandidate;

    @BeforeEach void seedCommittedData() {
        clock.time.set(NOW);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var suffix=UUID.randomUUID().toString();
            var u1=userRepository.save(User.create("first-"+suffix,"생성자",NOW));
            var u2=userRepository.save(User.create("second-"+suffix,"팀원",NOW));
            when(users.findBySessionToken("first")).thenReturn(Optional.of(u1));
            when(users.findBySessionToken("second")).thenReturn(Optional.of(u2));
            var team=teams.save(Team.create("팀","",suffix,NOW)); teamId=team.getId();
            firstId=members.save(TeamMember.newAdmin(teamId,u1.getId(),NOW)).getId();
            secondId=members.save(TeamMember.newMember(teamId,u2.getId(),NOW)).getId();
            firstRestaurant=restaurants.save(Restaurant.create("first-"+suffix,"첫 식당","서울",BigDecimal.ONE,BigDecimal.TEN,"한식","url",NOW)).getId();
            secondRestaurant=restaurants.save(Restaurant.create("second-"+suffix,"둘째 식당","서울",BigDecimal.ONE,BigDecimal.TEN,"양식","url",NOW)).getId();
            var vote=sessions.save(LunchVoteSession.create(teamId,firstId,NOW.plusSeconds(10800),NOW)); voteId=vote.getId();
            firstCandidate=candidates.save(VoteCandidate.create(voteId,firstRestaurant,CandidateSource.MANUAL,NOW)).getId();
            secondCandidate=candidates.save(VoteCandidate.create(voteId,secondRestaurant,CandidateSource.RECOMMENDED,NOW)).getId();
            participants.save(VoteParticipant.create(voteId,firstId,true,NOW)); participants.save(VoteParticipant.create(voteId,secondId,true,NOW));
        });
    }
    private List<String> concurrently(Runnable first, Runnable second) throws Exception {
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var tasks=List.of(first,second).stream().map(action -> pool.submit(() -> {
                ready.countDown(); if (!start.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                try { action.run(); return "OK"; } catch(BusinessException exception) { return exception.getErrorCode().name(); }
            })).toList();
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(tasks.getFirst().get(15,TimeUnit.SECONDS),tasks.getLast().get(15,TimeUnit.SECONDS));
        }
    }
    @Test void concurrentBallotReplacement_keepsExactlyOneCompleteSelectionSet() throws Exception {
        var outcomes=concurrently(() -> service.saveBallots(teamId,voteId,"first",List.of(firstCandidate,secondCandidate)),
                () -> service.saveBallots(teamId,voteId,"first",List.of(secondCandidate)));
        assertThat(outcomes).containsOnly("OK");
        var selected=ballots.findAllBySessionIdAndTeamMemberIdOrderById(voteId,firstId).stream().map(VoteRecord::getCandidateId).toList();
        assertThat(selected).doesNotHaveDuplicates();
        assertThat(selected.equals(List.of(firstCandidate,secondCandidate)) || selected.equals(List.of(secondCandidate))).isTrue();
    }
    @Test void concurrentExpiry_createsExactlyOneAutomaticDecision() throws Exception {
        service.saveBallots(teamId,voteId,"first",List.of(firstCandidate)); clock.time.set(NOW.plusSeconds(10800));
        assertThat(concurrently(() -> service.settleExpired(voteId),() -> service.settleExpired(voteId))).containsOnly("OK");
        assertThat(decisions.findBySessionId(voteId)).isPresent();
        var decision=decisions.findBySessionId(voteId).orElseThrow();
        assertThat(decision.getConfirmationType()).isEqualTo(ConfirmationType.AUTO); assertThat(decision.getConfirmedByTeamMemberId()).isNull();
        assertThat(sessions.findById(voteId).orElseThrow().getStatus()).isEqualTo(VoteStatus.CONFIRMED);
        assertThat(decisions.findAll().stream().filter(d -> d.getSessionId().equals(voteId))).hasSize(1);
    }
    @Test void concurrentManualDecision_onlyOneTiedWinnerIsConfirmed() throws Exception {
        service.saveBallots(teamId,voteId,"first",List.of(firstCandidate,secondCandidate)); clock.time.set(NOW.plusSeconds(10800));
        service.settleExpired(voteId);
        var outcomes=concurrently(() -> service.createDecision(teamId,voteId,"first",firstRestaurant),
                () -> service.createDecision(teamId,voteId,"first",secondRestaurant));
        assertThat(outcomes).containsExactlyInAnyOrder("OK","VOTE_ALREADY_CONFIRMED");
        assertThat(decisions.findAll().stream().filter(d -> d.getSessionId().equals(voteId))).hasSize(1);
    }
    @Test void absenceRacingWithBallotReplacement_leavesNoAbsentBallots() throws Exception {
        concurrently(() -> service.saveBallots(teamId,voteId,"second",List.of(firstCandidate,secondCandidate)),
                () -> service.updateParticipation(teamId,voteId,"first",secondId,false));
        assertThat(participants.findBySessionIdAndTeamMemberId(voteId,secondId).orElseThrow().isParticipating()).isFalse();
        assertThat(ballots.findAllBySessionIdAndTeamMemberIdOrderById(voteId,secondId)).isEmpty();
    }
    @Test void invalidReplacement_preservesCommittedSelectionAndOtherMembers() {
        service.saveBallots(teamId,voteId,"first",List.of(firstCandidate));
        service.saveBallots(teamId,voteId,"second",List.of(secondCandidate));
        assertThatThrownBy(() -> service.saveBallots(teamId,voteId,"first",List.of(firstCandidate,Long.MAX_VALUE)))
                .hasFieldOrPropertyWithValue("errorCode",ErrorCode.VOTE_CANDIDATE_NOT_FOUND);
        assertThat(ballots.findAllBySessionIdOrderById(voteId)).hasSize(2);
        assertThat(ballots.findAllBySessionIdAndTeamMemberIdOrderById(voteId,firstId).getFirst().getCandidateId()).isEqualTo(firstCandidate);
    }
    @Test void delayedRead_settlesDeadlineAndBlocksEveryOpenMutation() {
        service.saveBallots(teamId,voteId,"first",List.of(firstCandidate)); clock.time.set(NOW.plusSeconds(10800));
        var detail=service.getVoteDetail(teamId,voteId,"second"); assertThat(detail.session().status()).isEqualTo(VoteStatus.CONFIRMED);
        assertThat(detail.decision().confirmationType()).isEqualTo(ConfirmationType.AUTO);
        assertThatThrownBy(() -> service.saveBallots(teamId,voteId,"first",List.of(firstCandidate))).hasFieldOrPropertyWithValue("errorCode",ErrorCode.VOTE_ALREADY_CONFIRMED);
        assertThatThrownBy(() -> service.updateParticipation(teamId,voteId,"first",secondId,false)).hasFieldOrPropertyWithValue("errorCode",ErrorCode.VOTE_ALREADY_CONFIRMED);
        assertThatThrownBy(() -> service.deleteCandidate(teamId,voteId,"first",firstCandidate)).hasFieldOrPropertyWithValue("errorCode",ErrorCode.VOTE_ALREADY_CONFIRMED);
    }
    @Test void restart_keepsCandidatesAndParticipationAndClearsAllBallots() {
        service.saveBallots(teamId,voteId,"first",List.of(firstCandidate));
        service.updateParticipation(teamId,voteId,"first",secondId,false);
        var restarted=service.restart(teamId,voteId,"first");
        assertThat(restarted.closesAt()).isEqualTo(NOW.plusSeconds(10800));
        assertThat(candidates.findDetails(voteId,teamId)).hasSize(2);
        assertThat(participants.findBySessionIdAndTeamMemberId(voteId,secondId).orElseThrow().isParticipating()).isFalse();
        assertThat(ballots.findAllBySessionIdOrderById(voteId)).isEmpty();
    }
    @Test void deleteCandidate_removesAllMembersLinkedBallots() {
        service.saveBallots(teamId,voteId,"first",List.of(firstCandidate,secondCandidate));
        service.saveBallots(teamId,voteId,"second",List.of(firstCandidate));
        service.deleteCandidate(teamId,voteId,"second",firstCandidate);
        assertThat(ballots.findAllBySessionIdOrderById(voteId)).hasSize(1);
        assertThat(ballots.findAllBySessionIdOrderById(voteId).getFirst().getCandidateId()).isEqualTo(secondCandidate);
    }
    @Test void manualDecision_editDeleteAndHistoryFollowCurrentResult() {
        service.saveBallots(teamId,voteId,"first",List.of(firstCandidate,secondCandidate)); clock.time.set(NOW.plusSeconds(10800));
        service.getResults(teamId,voteId,"first");
        var decision=service.createDecision(teamId,voteId,"first",firstRestaurant);
        assertThatThrownBy(() -> service.updateDecision(teamId,voteId,"second",secondRestaurant)).hasFieldOrPropertyWithValue("errorCode",ErrorCode.VOTE_CREATOR_REQUIRED);
        assertThat(service.updateDecision(teamId,voteId,"first",secondRestaurant).id()).isEqualTo(decision.id());
        assertThat(service.getHistory(teamId,"first","MONTH",null,YearMonth.of(2026,10)).getFirst().restaurant().id()).isEqualTo("restaurant_"+secondRestaurant);
        service.deleteDecision(teamId,voteId,"first");
        assertThat(sessions.findById(voteId).orElseThrow().getStatus()).isEqualTo(VoteStatus.CLOSED);
        assertThat(service.getHistory(teamId,"first","MONTH",null,YearMonth.of(2026,10))).isEmpty();
    }
    @Test void deletingTeam_removesNewVoteTablesAndPreservesRestaurant() {
        service.saveBallots(teamId,voteId,"first",List.of(firstCandidate)); clock.time.set(NOW.plusSeconds(10800)); service.settleExpired(voteId);
        teamService.deleteTeam(teamId,"first");
        assertThat(sessions.findById(voteId)).isEmpty(); assertThat(decisions.findBySessionId(voteId)).isEmpty();
        assertThat(participants.findProfiles(voteId)).isEmpty(); assertThat(candidates.findDetails(voteId,teamId)).isEmpty(); assertThat(ballots.findAllBySessionIdOrderById(voteId)).isEmpty();
        assertThat(restaurants.findById(firstRestaurant)).isPresent();
    }
}
