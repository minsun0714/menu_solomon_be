package com.menusolomon.vote.repository;

import com.menusolomon.vote.domain.LunchVoteSession;
import com.menusolomon.vote.domain.VoteCandidate;
import com.menusolomon.vote.domain.VoteParticipant;
import com.menusolomon.vote.domain.VoteRecord;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LunchVoteSessionRepository extends JpaRepository<LunchVoteSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LunchVoteSession s where s.id = :id and s.teamId = :teamId")
    Optional<LunchVoteSession> findScopedForUpdate(@Param("id") Long id, @Param("teamId") Long teamId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LunchVoteSession s where s.id = :id")
    Optional<LunchVoteSession> findForUpdate(@Param("id") Long id);
    @Query("select s.id from LunchVoteSession s where s.status = com.menusolomon.vote.domain.VoteStatus.OPEN and s.closesAt <= :now order by s.id")
    List<Long> findDueIds(@Param("now") Instant now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LunchVoteSession s where s.teamId = :teamId and s.status = com.menusolomon.vote.domain.VoteStatus.OPEN and s.closesAt <= :now order by s.id")
    List<LunchVoteSession> findDueForTeam(@Param("teamId") Long teamId, @Param("now") Instant now);
    @Query("""
        select new com.menusolomon.vote.repository.VoteSummaryRow(s.id, s.teamId, s.name, s.createdByTeamMemberId, u.nickname,
            s.status, s.closesAt, s.createdAt,
            (select count(p.id) from VoteParticipant p where p.sessionId=s.id and p.participating=true),
            (select count(c.id) from VoteCandidate c where c.sessionId=s.id),
            (select count(distinct b.teamMemberId) from VoteRecord b where b.sessionId=s.id))
        from LunchVoteSession s join TeamMember m on m.id=s.createdByTeamMemberId join User u on u.id=m.userId
        where s.teamId=:teamId order by s.createdAt desc, s.id desc
        """)
    List<VoteSummaryRow> findSummaries(@Param("teamId") Long teamId);
    @Query("select u.nickname from LunchVoteSession s join TeamMember m on m.id=s.createdByTeamMemberId join User u on u.id=m.userId where s.id=:id")
    Optional<String> findCreatorNickname(@Param("id") Long id);
    @Modifying @Query("delete from LunchVoteSession s where s.teamId=:teamId")
    void deleteAllByTeamId(@Param("teamId") Long teamId);
}
