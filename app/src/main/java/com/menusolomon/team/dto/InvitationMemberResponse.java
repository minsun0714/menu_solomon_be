package com.menusolomon.team.dto;

import java.time.Instant;

public record InvitationMemberResponse(
        String id,
        String role,
        Instant joinedAt,
        InvitationUserResponse user
) {
}
