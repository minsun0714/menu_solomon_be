package com.menusolomon.team.dto;

import com.menusolomon.team.repository.MyTeamRow;

public record MyTeamResponse(String teamId, String name, String description, String myRole, long memberCount) {
    public static MyTeamResponse from(MyTeamRow row) {
        return new MyTeamResponse("team_" + row.teamId(), row.name(), row.description(), row.role().name(), row.memberCount());
    }
}
