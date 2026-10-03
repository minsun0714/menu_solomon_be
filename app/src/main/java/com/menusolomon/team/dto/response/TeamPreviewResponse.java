package com.menusolomon.team.dto.response;

import java.util.List;

public record TeamPreviewResponse(
        String id,
        String name,
        String description,
        long memberCount,
        List<TeamMemberProfileResponse> members
) {
}
