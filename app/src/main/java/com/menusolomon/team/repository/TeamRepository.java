package com.menusolomon.team.repository;

import com.menusolomon.team.domain.Team;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {

    @Query("""
            select new com.menusolomon.team.repository.MyTeamRow(
                team.id, team.name, team.description, mine.role,
                (select count(member.id) from TeamMember member where member.teamId = team.id and member.leftAt is null))
            from Team team join TeamMember mine on mine.teamId = team.id
            where mine.userId = :userId and mine.leftAt is null
            order by team.name asc, team.id asc
            """)
    List<MyTeamRow> findMyTeams(@Param("userId") Long userId);

    Optional<Team> findByInviteToken(String inviteToken);
}
