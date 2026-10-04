package com.menusolomon.vote.repository;

import com.menusolomon.vote.domain.VoteParticipant;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteParticipantRepository extends JpaRepository<VoteParticipant, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VoteParticipant> findByLunchVoteSessionIdAndTeamMemberId(Long sessionId, Long memberId);

    @Query("""
            select new com.menusolomon.vote.repository.VoteParticipantRow(p.teamMemberId, u.nickname, p.participating)
            from VoteParticipant p join TeamMember m on m.id = p.teamMemberId
            join User u on u.id = m.userId
            where p.lunchVoteSessionId = :voteId order by p.id
            """)
    List<VoteParticipantRow> findParticipants(@Param("voteId") Long voteId);
    @Modifying
    @Query("""
            delete from VoteParticipant participant where participant.lunchVoteSessionId in
                (select vote.id from LunchVoteSession vote where vote.teamId = :teamId)
            """)
    void deleteAllByTeamId(@Param("teamId") Long teamId);

}
