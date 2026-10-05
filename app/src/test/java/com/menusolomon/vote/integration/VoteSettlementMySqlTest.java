package com.menusolomon.vote.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.menusolomon.restaurant.domain.Restaurant;
import com.menusolomon.restaurant.repository.RestaurantRepository;
import com.menusolomon.team.domain.*;
import com.menusolomon.team.repository.*;
import com.menusolomon.user.domain.User;
import com.menusolomon.user.repository.UserRepository;
import com.menusolomon.user.service.UserService;
import com.menusolomon.vote.domain.*;
import com.menusolomon.vote.repository.*;
import com.menusolomon.vote.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({VoteServiceImpl.class, VoteSettlementMySqlTest.Configuration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "MYSQL_VOTE_TEST_URL", matches = ".+/vote_lock_test(?:\\?.*)?")
public class VoteSettlementMySqlTest {
    static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    static final SqlCapture SQL = new SqlCapture();
    public static class SqlCapture implements StatementInspector {
        final List<String> statements = new CopyOnWriteArrayList<>();
        volatile CountDownLatch lockingRead;
        @Override public String inspect(String sql) {
            SQL.statements.add(sql);
            if (sql.contains("lunch_vote_sessions") && sql.contains("for update") && SQL.lockingRead != null)
                SQL.lockingRead.countDown();
            return sql;
        }
    }
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", () -> System.getenv("MYSQL_VOTE_TEST_URL"));
        props.add("spring.datasource.username", () -> System.getenv("MYSQL_VOTE_TEST_USER"));
        props.add("spring.datasource.password", () -> System.getenv("MYSQL_VOTE_TEST_PASSWORD"));
        props.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        props.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        props.add("spring.jpa.properties.hibernate.session_factory.statement_inspector", () -> SqlCapture.class.getName());
    }
    @TestConfiguration static class Configuration {
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
    @Autowired VoteService service;
    @Autowired LunchVoteSessionRepository sessions;
    @Autowired VoteCandidateRepository candidates;
    @Autowired VoteRecordRepository ballots;
    @Autowired ConfirmedMenuRepository decisions;
    @Autowired RestaurantRepository restaurants;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @Autowired VoteSettlementProbeRepository probe;
    @MockitoBean UserService identity;
    Long teamId, voteId;

    @BeforeEach void seedCommittedExpiredVoteWithSingleWinner() {
        assertThat(probe.mysqlVersion()).startsWith("8.");
        if (probe.legacyIndexColumnCount() == 0) probe.createLegacyIndex();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            String suffix = UUID.randomUUID().toString();
            var user = users.save(User.create(suffix, "생성자", NOW));
            when(identity.findBySessionToken("creator")).thenReturn(Optional.of(user));
            teamId = teams.save(Team.create("정산 검증", "", suffix, NOW)).getId();
            Long memberId = members.save(TeamMember.newAdmin(teamId, user.getId(), NOW)).getId();
            Long restaurantId = restaurants.save(Restaurant.create(suffix, "식당", "서울", BigDecimal.ONE, BigDecimal.TEN, "한식", "url", NOW)).getId();
            voteId = sessions.save(LunchVoteSession.create(teamId, memberId, NOW.minusSeconds(1), NOW.minusSeconds(60))).getId();
            Long candidateId = candidates.save(VoteCandidate.create(voteId, restaurantId, CandidateSource.MANUAL, NOW)).getId();
            ballots.save(VoteRecord.create(voteId, memberId, candidateId, NOW));
        });
        SQL.statements.clear(); SQL.lockingRead = null;
    }

    @Test
    @DisplayName("같은 투표를 동시에 정산하면 두 요청이 정상 종료하고 확정 결과는 하나만 생성된다")
    void sameVoteConcurrentSettlement_bothRequestsSucceedWithOneDecision() throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var requests = new TransactionTemplate(transactions).execute(tx -> {
                sessions.findForUpdate(voteId).orElseThrow();
                Callable<Void> action = () -> {
                    ready.countDown(); await(start); service.settleExpired(voteId); return null;
                };
                var first = pool.submit(action); var second = pool.submit(action);
                await(ready); start.countDown();
                awaitDatabaseLockWait(2);
                return List.of(first, second);
            });
            for (var request : requests) request.get(15, TimeUnit.SECONDS);
        }
        assertThat(probe.decisionCount(voteId)).isEqualTo(1);
        assertThat(sessions.findById(voteId).orElseThrow().getStatus()).isEqualTo(VoteStatus.CONFIRMED);
    }

    @Test
    @DisplayName("REPEATABLE READ에서도 후보 탐색 후 잠금 조회는 다른 트랜잭션이 확정한 최신 상태를 읽는다")
    void repeatableRead_afterCandidateScan_lockingReadSeesCommittedClosedState() throws Exception {
        var scanned = new CountDownLatch(1); var changed = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var updater = pool.submit(() -> {
                await(scanned);
                try { service.settleExpired(voteId); } finally { changed.countDown(); }
                return null;
            });
            var snapshotTransaction = new TransactionTemplate(transactions);
            snapshotTransaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
            snapshotTransaction.executeWithoutResult(tx -> {
                assertThat(sessions.findDueIdsForTeam(teamId, NOW)).contains(voteId);
                scanned.countDown(); await(changed);
                var latest = sessions.findAllForUpdate(List.of(voteId)).getFirst();
                assertThat(latest.getStatus()).isEqualTo(VoteStatus.CONFIRMED);
                assertThat(latest.isDue(NOW)).isFalse();
            });
            updater.get(15, TimeUnit.SECONDS);
        }
        service.settleExpired(voteId);
        assertThat(probe.decisionCount(voteId)).isEqualTo(1);
    }

    @Test
    @DisplayName("팀 투표 정산은 잠금 없이 후보 ID를 탐색한 뒤 PK 기준으로만 잠금을 획득한다")
    void teamSettlement_doesNotLockSecondaryIndexDuringCandidateDiscovery() {
        service.getVotes(teamId, "creator");
        var voteSelects = SQL.statements.stream().filter(sql -> sql.startsWith("select") && sql.contains("lunch_vote_sessions")).toList();
        voteSelects.forEach(sql -> System.out.println("MYSQL settlement SQL: " + sql));
        assertThat(voteSelects).anySatisfy(sql -> {
            assertThat(sql).contains("closes_at");
            assertThat(sql).doesNotContain("for update");
            assertThat(sql.substring(0, sql.indexOf(" from "))).doesNotContain("closes_at", "status");
        });
        assertThat(voteSelects.stream().filter(sql -> sql.contains("for update"))).isNotEmpty().allSatisfy(sql -> {
            String predicate = sql.substring(sql.indexOf(" where "));
            assertThat(predicate).doesNotContain(".status", ".team_id");
        });
    }

    @Test
    @DisplayName("투표 상태 UPDATE와 팀 투표 정산 SELECT FOR UPDATE가 경쟁해도 데드락이 발생하지 않는다")
    void pkStatusUpdateRacingWithTeamSettlement_doesNotDeadlock() throws Exception {
        for (int iteration = 0; iteration < 5; iteration++) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                var vote = sessions.findForUpdate(voteId).orElseThrow();
                vote.restart(vote.getCreatedByTeamMemberId(), NOW.minusSeconds(10801));
            });
            try (var pool = Executors.newSingleThreadExecutor()) {
                var competing = new TransactionTemplate(transactions).execute(tx -> {
                    var vote = sessions.findForUpdate(voteId).orElseThrow();

                    SQL.lockingRead = new CountDownLatch(1);
                    var request = pool.submit(() -> service.getVotes(teamId, "creator"));
                    await(SQL.lockingRead);

                    awaitDatabaseLockWait(1); // 메인 스레드가 별도 스레드의 SELECT ... FOR UPDATE가 PK 락을 기다리는 상태가 될 때까지 대기
                    vote.close(NOW);
                    sessions.flush();
                    return request;
                });
                competing.get(15, TimeUnit.SECONDS);
            } finally { SQL.lockingRead = null; }
            assertThat(sessions.findById(voteId).orElseThrow().getStatus()).isEqualTo(VoteStatus.CLOSED);
            assertThat(decisions.findBySessionId(voteId)).isEmpty();
        }
    }

    @Test
    @DisplayName("EXPLAIN으로 후보 탐색의 인덱스와 covering 여부 및 잠금 조회의 PRIMARY 접근을 확인한다")
    void explain_discoveryAndPrimaryLock_showActualIndexAndCovering() {
        var discoveryPlan = probe.explainCandidateDiscovery(teamId, NOW).stream().map(LinkedHashMap::new).toList();
        var additionalVotes = new TransactionTemplate(transactions).execute(tx -> {
            var creatorId = sessions.findById(voteId).orElseThrow().getCreatedByTeamMemberId();
            var votes = java.util.stream.IntStream.range(0, 32).mapToObj(index ->
                    LunchVoteSession.create(teamId, creatorId, NOW.minusSeconds(1), NOW.minusSeconds(60))).toList();
            return sessions.saveAllAndFlush(votes);
        });
        var lockingPlan = probe.explainPrimaryLock(List.of(voteId, additionalVotes.getLast().getId())).stream().map(LinkedHashMap::new).toList();
        System.out.println("MYSQL EXPLAIN candidate discovery: " + discoveryPlan);
        System.out.println("MYSQL EXPLAIN PK locking read: " + lockingPlan);
        assertThat(lockingPlan.getFirst().get("key")).isEqualTo("PRIMARY");
    }

    private void awaitDatabaseLockWait(int expectedWaiters) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            long waits = probe.waitingVoteLockCount();
            if (waits >= expectedWaiters) return;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        }
        fail("MySQL did not report the expected row lock wait");
    }
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Transaction coordination timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Transaction coordination interrupted", exception);
        }
    }
}
