package com.menusolomon.team.repository;

import com.menusolomon.team.domain.TeamMember;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);

    long countByTeamIdAndLeftAtIsNull(Long teamId);

    List<TeamMember> findAllByTeamIdAndLeftAtIsNull(Long teamId);
}
