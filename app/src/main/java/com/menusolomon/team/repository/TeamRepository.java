package com.menusolomon.team.repository;

import com.menusolomon.team.domain.Team;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByInviteToken(String inviteToken);
}
