package com.menusolomon.team.service;

import com.menusolomon.team.dto.TeamCreateRequest;
import com.menusolomon.team.dto.TeamCreateResponse;

public interface TeamService {
    TeamCreateResponse createTeam(Long userId, TeamCreateRequest request);
}
