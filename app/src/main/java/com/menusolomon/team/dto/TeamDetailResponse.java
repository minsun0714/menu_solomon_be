package com.menusolomon.team.dto;

public record TeamDetailResponse(
        String id,
        String name,
        String description,
        long memberCount,
        String myRole
) {
}
