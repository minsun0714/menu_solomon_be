package com.menusolomon.team.service;

import com.menusolomon.team.domain.TeamMember;

public record TeamMemberResult(TeamMember member, Outcome outcome) {
    public enum Outcome {
        CREATED,
        REACTIVATED
    }
}
