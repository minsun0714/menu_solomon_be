package com.menusolomon.vote.integration;

import com.menusolomon.vote.domain.LunchVoteSession;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface VoteSettlementProbeRepository extends Repository<LunchVoteSession, Long> {
    @Query(value = "select version()", nativeQuery = true)
    String mysqlVersion();

    @Query(value = "select count(*) from information_schema.statistics where table_schema=database() and table_name='lunch_vote_sessions' and index_name='idx_vote_session_team_status'", nativeQuery = true)
    long legacyIndexColumnCount();

    @Modifying
    @Transactional
    @Query(value = "create index idx_vote_session_team_status on lunch_vote_sessions(team_id,status,created_at)", nativeQuery = true)
    void createLegacyIndex();

    @Query(value = "select count(*) from lunch_decisions where session_id=:voteId", nativeQuery = true)
    long decisionCount(@Param("voteId") Long voteId);

    @Query(value = """
            select count(*) from performance_schema.data_lock_waits w
            join performance_schema.data_locks l on l.engine_lock_id=w.requesting_engine_lock_id
            where l.object_schema=database() and l.object_name='lunch_vote_sessions'
            """, nativeQuery = true)
    long waitingVoteLockCount();

    @Query(value = "explain select id from lunch_vote_sessions where team_id=:teamId and status='OPEN' and closes_at<=:now", nativeQuery = true)
    List<Map<String, Object>> explainCandidateDiscovery(@Param("teamId") Long teamId, @Param("now") Instant now);

    @Query(value = "explain select * from lunch_vote_sessions where id in (:voteIds) order by id for update", nativeQuery = true)
    List<Map<String, Object>> explainPrimaryLock(@Param("voteIds") List<Long> voteIds);
}
