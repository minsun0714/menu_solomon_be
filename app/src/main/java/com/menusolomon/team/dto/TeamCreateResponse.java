package com.menusolomon.team.dto;

public record TeamCreateResponse(
        String id,
        String name,
        String description,
        String myRole,
        String inviteUrl
) {
}
