package com.menusolomon.vote.integration;

import static com.menusolomon.vote.fixture.VoteFixture.NOW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.restaurant.domain.Restaurant;
import com.menusolomon.restaurant.domain.TeamRestaurant;
import com.menusolomon.restaurant.fixture.RestaurantFixture;
import com.menusolomon.restaurant.repository.RestaurantRepository;
import com.menusolomon.team.domain.Team;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.service.TeamService;
import com.menusolomon.user.domain.User;
import com.menusolomon.user.service.UserService;
import com.menusolomon.vote.domain.*;
import com.menusolomon.vote.repository.*;
import com.menusolomon.vote.service.VoteService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:vote_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@ActiveProfiles("test")
@Import(VoteConcurrencyTest.FixedTimeConfiguration.class)
class VoteConcurrencyTest {
    @Autowired VoteService service;
    @Autowired TeamService teams;
    @Autowired VoteRecordRepository records;
    @Autowired ConfirmedMenuRepository menus;
    @Autowired LunchVoteSessionRepository sessions;
    @Autowired VoteCandidateRepository candidates;
    @Autowired VoteParticipantRepository participants;
    @Autowired RestaurantRepository restaurants;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean UserService users;
    Long teamId;
    Long voteId;
    Long firstCandidateId;
    Long secondCandidateId;
    Long teamRestaurantId;
    User user;

    @BeforeEach
    void seedCommittedData() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (String entity : List.of("ConfirmedMenu", "VoteRecord", "VoteParticipant", "VoteCandidate", "LunchVoteSession",
                    "Review", "TeamRestaurant", "TeamMember", "Team", "Restaurant", "User")) {
                entityManager.createQuery("delete from " + entity).executeUpdate();
            }
            user = User.create("concurrent-user", "익명", NOW);
            entityManager.persist(user);
            var team = Team.create("팀", "", "concurrent-invite", NOW);
            entityManager.persist(team);
            teamId = team.getId();
            var member = TeamMember.newAdmin(teamId, user.getId(), NOW);
            entityManager.persist(member);
            var restaurant = RestaurantFixture.restaurant("123", "첫 식당", "한식");
            var second = RestaurantFixture.restaurant("456", "다음 식당", "양식");
            entityManager.persist(restaurant);
            entityManager.persist(second);
            var teamRestaurant = TeamRestaurant.create(teamId, restaurant.getId(), member.getId(), NOW);
            entityManager.persist(teamRestaurant);
            teamRestaurantId = teamRestaurant.getId();
            entityManager.persist(TeamRestaurant.create(teamId, second.getId(), member.getId(), NOW));
            var vote = LunchVoteSession.create(teamId, "점심", member.getId(), NOW);
            entityManager.persist(vote);
            voteId = vote.getId();
            entityManager.persist(VoteParticipant.create(voteId, member.getId(), true, NOW));
            var firstCandidate = VoteCandidate.create(voteId, restaurant.getId(), member.getId(), NOW);
            var secondCandidate = VoteCandidate.create(voteId, second.getId(), member.getId(), NOW);
            entityManager.persist(firstCandidate);
            entityManager.persist(secondCandidate);
            firstCandidateId = firstCandidate.getId();
            secondCandidateId = secondCandidate.getId();
        });
        when(users.findBySessionToken("token")).thenReturn(Optional.of(user));
    }

    @Test
    void sameMemberConcurrentVote_doesNotCreateDuplicateVoteRecords() throws Exception {
        runTogether(() -> service.vote(teamId, voteId, "token", firstCandidateId),
                () -> service.vote(teamId, voteId, "token", secondCandidateId));
        assertThat(records.findAll()).singleElement().satisfies(record -> {
            assertThat(record.getLunchVoteSessionId()).isEqualTo(voteId);
            assertThat(record.getVoteCandidateId()).isIn(firstCandidateId, secondCandidateId);
        });
    }

    @Test
    void concurrentConfirm_createsOnlyOneConfirmedMenu() throws Exception {
        var results = runTogether(() -> confirmOutcome(firstCandidateId), () -> confirmOutcome(secondCandidateId));
        assertThat(results).containsExactlyInAnyOrder("CONFIRMED", "VOTE_ALREADY_CONFIRMED");
        assertThat(menus.count()).isEqualTo(1);
        assertThat(sessions.findByIdAndTeamId(voteId, teamId).orElseThrow().getStatus()).isEqualTo(VoteStatus.CONFIRMED);
    }

    @Test
    void confirmedVote_canSelectNonWinningCandidate_andThenRejectsAllMutations() {
        service.vote(teamId, voteId, "token", firstCandidateId);
        var result = service.confirm(teamId, voteId, "token", secondCandidateId);
        assertThat(result.confirmedMenu().voteCount()).isZero();
        assertThat(result.confirmedMenu().candidateId()).isEqualTo("candidate_" + secondCandidateId);
        assertAlreadyConfirmed(() -> service.vote(teamId, voteId, "token", firstCandidateId));
        assertAlreadyConfirmed(() -> service.updateParticipation(teamId, voteId, "token", false));
        assertAlreadyConfirmed(() -> service.addCandidate(teamId, voteId, "token", 99L));
        assertAlreadyConfirmed(() -> service.confirm(teamId, voteId, "token", firstCandidateId));
    }

    @Test
    void participationIsPerVote_andAbsenceRemovesOnlyThatVotesRecord() {
        var second = service.createVote(teamId, "token", "다른 점심");
        Long secondVoteId = Long.valueOf(second.id().substring("vote_".length()));
        var candidate = service.addCandidate(teamId, secondVoteId, "token", teamRestaurantId);
        Long candidateId = Long.valueOf(candidate.candidateId().substring("candidate_".length()));
        service.vote(teamId, voteId, "token", firstCandidateId);
        service.vote(teamId, secondVoteId, "token", candidateId);
        service.updateParticipation(teamId, voteId, "token", false);
        assertThat(records.findAll()).singleElement().satisfies(record ->
                assertThat(record.getLunchVoteSessionId()).isEqualTo(secondVoteId));
        assertThat(service.getVoteDetail(teamId, voteId, "token").myParticipation()).isFalse();
        assertThat(service.getVoteDetail(teamId, secondVoteId, "token").myParticipation()).isTrue();
    }

    @Test
    void lastAdminLeave_removesVoteOwnedDataAndKeepsSharedRestaurants() {
        service.vote(teamId, voteId, "token", firstCandidateId);
        service.confirm(teamId, voteId, "token", firstCandidateId);
        teams.leaveTeam(teamId, "token");
        assertThat(records.count()).isZero();
        assertThat(menus.count()).isZero();
        assertThat(participants.count()).isZero();
        assertThat(candidates.count()).isZero();
        assertThat(sessions.count()).isZero();
        assertThat(restaurants.count()).isEqualTo(2);
    }

    private String confirmOutcome(Long candidateId) {
        try {
            return service.confirm(teamId, voteId, "token", candidateId).status();
        } catch (BusinessException exception) {
            return exception.getErrorCode().name();
        }
    }

    private void assertAlreadyConfirmed(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VOTE_ALREADY_CONFIRMED));
    }

    private <T> List<T> runTogether(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out"); return first.call(); });
            var two = executor.submit(() -> { ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out"); return second.call(); });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
    }
    @TestConfiguration
    static class FixedTimeConfiguration {
        @Bean
        @Primary
        Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

}
