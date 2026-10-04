package com.menusolomon.team.dto;

import java.time.Instant;

public record TeamJoinResponse(
        String id,
        String teamId,
        String userId,
        String role,
        Instant joinedAt
) {
}
