package com.menusolomon.vote.repository;

import com.menusolomon.vote.domain.LunchVoteSession;
import com.menusolomon.vote.domain.VoteParticipant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteParticipantRepository extends JpaRepository<VoteParticipant, Long> {
    Optional<VoteParticipant> findBySessionIdAndTeamMemberId(Long sessionId, Long memberId);
    List<VoteParticipant> findAllBySessionIdInAndTeamMemberId(List<Long> sessionIds, Long memberId);
    String PROFILE = """
        select new com.menusolomon.vote.repository.VoteParticipantRow(p.id, p.sessionId, p.teamMemberId, m.nickname, p.participating)
        from VoteParticipant p join TeamMember m on m.id=p.teamMemberId
        """;
    @Query(PROFILE + "where p.sessionId=:id order by p.id")
    List<VoteParticipantRow> findProfiles(@Param("id") Long id);
    @Query(PROFILE + "where p.sessionId=:id and p.teamMemberId=:memberId")
    Optional<VoteParticipantRow> findProfile(@Param("id") Long id, @Param("memberId") Long memberId);
    @Modifying @Query("delete from VoteParticipant p where p.sessionId=:id")
    void deleteBySession(@Param("id") Long id);
    @Modifying @Query("delete from VoteParticipant p where p.sessionId in (select s.id from LunchVoteSession s where s.teamId=:teamId)")
    void deleteAllByTeamId(@Param("teamId") Long teamId);
}
