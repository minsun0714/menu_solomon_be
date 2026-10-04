package com.menusolomon.vote.repository;

import com.menusolomon.vote.domain.LunchVoteSession;
import com.menusolomon.vote.domain.VoteStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LunchVoteSessionRepository extends JpaRepository<LunchVoteSession, Long> {
    Optional<LunchVoteSession> findByIdAndTeamId(Long id, Long teamId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select vote from LunchVoteSession vote where vote.id = :id and vote.teamId = :teamId")
    Optional<LunchVoteSession> findByIdAndTeamIdForUpdate(@Param("id") Long id, @Param("teamId") Long teamId);

    String SUMMARY = """
            select new com.menusolomon.vote.repository.VoteSummaryRow(
                vote.id, vote.teamId, vote.title, vote.status, vote.createdAt,
                (select count(p.id) from VoteParticipant p where p.lunchVoteSessionId = vote.id and p.participating = true),
                (select count(c.id) from VoteCandidate c where c.lunchVoteSessionId = vote.id),
                coalesce(mine.participating, false), record.voteCandidateId)
            from LunchVoteSession vote
            left join VoteParticipant mine on mine.lunchVoteSessionId = vote.id and mine.teamMemberId = :memberId
            left join VoteRecord record on record.lunchVoteSessionId = vote.id and record.teamMemberId = :memberId
            """;

    @Query(SUMMARY + """
            where vote.teamId = :teamId and (:status is null or vote.status = :status)
            order by case when vote.status = com.menusolomon.vote.domain.VoteStatus.OPEN then 0 else 1 end,
                     vote.createdAt desc, vote.id desc
            """)
    List<VoteSummaryRow> findSummaries(@Param("teamId") Long teamId, @Param("memberId") Long memberId,
            @Param("status") VoteStatus status);

    @Query(SUMMARY + "where vote.id = :id and vote.teamId = :teamId")
    Optional<VoteSummaryRow> findSummary(@Param("id") Long id, @Param("teamId") Long teamId,
            @Param("memberId") Long memberId);
    @Modifying
    @Query("delete from LunchVoteSession vote where vote.teamId = :teamId")
    void deleteAllByTeamId(@Param("teamId") Long teamId);

}
