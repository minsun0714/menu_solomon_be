package com.menusolomon.team.dto.response;

import com.menusolomon.team.domain.TeamRole;
import java.time.Instant;

public record TeamMemberProfileResponse(
        String id,
        String teamId,
        String userId,
        TeamRole role,
        Instant joinedAt,
        String nickname,
        String profileImageUrl
) {
}
