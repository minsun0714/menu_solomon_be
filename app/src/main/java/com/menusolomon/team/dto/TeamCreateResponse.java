package com.menusolomon.team.dto;

import com.menusolomon.team.domain.TeamRole;

public record TeamCreateResponse(
        String id,
        String name,
        String description,
        TeamRole myRole,
        String inviteUrl
) {
}
