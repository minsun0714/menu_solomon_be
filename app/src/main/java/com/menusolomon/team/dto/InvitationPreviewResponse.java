package com.menusolomon.team.dto;

import java.util.List;

public record InvitationPreviewResponse(
        String teamId,
        String name,
        String description,
        long memberCount,
        boolean isAlreadyMember,
        List<InvitationMemberResponse> members
) {
}
