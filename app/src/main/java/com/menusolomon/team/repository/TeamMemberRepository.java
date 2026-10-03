package com.menusolomon.team.repository;

import com.menusolomon.team.domain.TeamMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);

    Optional<TeamMember> findByTeamIdAndUserIdAndLeftAtIsNull(Long teamId, Long userId);

    List<TeamMember> findAllByTeamIdAndLeftAtIsNull(Long teamId);

    long countByTeamIdAndLeftAtIsNull(Long teamId);
}
