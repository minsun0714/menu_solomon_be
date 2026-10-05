package com.menusolomon.team.dto;

import com.menusolomon.team.domain.Team;

public record TeamUpdateResponse(String id, String name, String description) {
    public static TeamUpdateResponse from(Team team) {
        return new TeamUpdateResponse("team_" + team.getId(), team.getName(), team.getDescription());
    }
}
