package com.menusolomon.vote.repository;

import com.menusolomon.vote.domain.LunchVoteSession;
import com.menusolomon.vote.domain.VoteRecord;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteRecordRepository extends JpaRepository<VoteRecord, Long> {
    List<VoteRecord> findAllBySessionIdOrderById(Long sessionId);
    List<VoteRecord> findAllBySessionIdAndTeamMemberIdOrderById(Long sessionId, Long memberId);
    List<VoteRecord> findAllBySessionIdInAndTeamMemberIdOrderById(Collection<Long> sessionIds, Long memberId);
    @Query("select count(distinct b.teamMemberId) from VoteRecord b where b.sessionId=:id")
    long countVoters(@Param("id") Long id);
    @Modifying @Query("delete from VoteRecord b where b.sessionId=:id and b.teamMemberId=:memberId")
    void deleteMyBallots(@Param("id") Long id, @Param("memberId") Long memberId);
    @Modifying @Query("delete from VoteRecord b where b.sessionId=:id and b.candidateId=:candidateId")
    void deleteCandidateBallots(@Param("id") Long id, @Param("candidateId") Long candidateId);
    @Modifying @Query("delete from VoteRecord b where b.sessionId=:id")
    void deleteBySession(@Param("id") Long id);
    @Modifying @Query("delete from VoteRecord b where b.sessionId in (select s.id from LunchVoteSession s where s.teamId=:teamId)")
    void deleteAllByTeamId(@Param("teamId") Long teamId);
}
