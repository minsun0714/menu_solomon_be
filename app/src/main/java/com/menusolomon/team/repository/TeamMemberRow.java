package com.menusolomon.team.repository;

import com.menusolomon.team.domain.TeamRole;
import java.time.Instant;

public record TeamMemberRow(Long teamMemberId, Long userId, String nickname, TeamRole role, Instant joinedAt) {}
