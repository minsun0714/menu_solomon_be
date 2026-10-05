package com.menusolomon.team.repository;

import com.menusolomon.team.domain.TeamRole;

public record MyTeamRow(Long teamId, String name, String description, TeamRole role, long memberCount) {}
