package com.menusolomon.vote.repository;

import com.menusolomon.vote.domain.VoteRecord;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteRecordRepository extends JpaRepository<VoteRecord, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VoteRecord> findByLunchVoteSessionIdAndTeamMemberId(Long sessionId, Long memberId);

    @Modifying
    @Query("delete from VoteRecord r where r.lunchVoteSessionId = :voteId and r.teamMemberId = :memberId")
    void deleteMyVote(@Param("voteId") Long voteId, @Param("memberId") Long memberId);
    @Modifying
    @Query("""
            delete from VoteRecord record where record.lunchVoteSessionId in
                (select vote.id from LunchVoteSession vote where vote.teamId = :teamId)
            """)
    void deleteAllByTeamId(@Param("teamId") Long teamId);

}
