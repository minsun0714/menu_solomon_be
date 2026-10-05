package com.menusolomon.team.repository;

import com.menusolomon.team.domain.TeamMember;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    @Query("""
            select new com.menusolomon.team.repository.TeamMemberRow(member.id, user.id, user.nickname, member.role, member.joinedAt)
            from TeamMember member join User user on user.id = member.userId
            where member.teamId = :teamId and member.leftAt is null
            order by member.joinedAt asc, member.id asc
            """)
    List<TeamMemberRow> findActiveMemberProfiles(@Param("teamId") Long teamId);

    Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);

    long countByTeamIdAndLeftAtIsNull(Long teamId);

    List<TeamMember> findAllByTeamIdAndLeftAtIsNull(Long teamId);
    Optional<TeamMember> findByIdAndTeamId(Long id, Long teamId);

    @Modifying
    @Query("delete from TeamMember member where member.teamId = :teamId")
    void deleteAllByTeamId(@Param("teamId") Long teamId);

}
