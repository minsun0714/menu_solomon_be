package com.menusolomon.fixture;

import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.domain.TeamRole;
import java.time.Instant;

public final class TeamMemberFixture {
    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    private TeamMemberFixture() {
    }

    public static TeamMember admin(Long teamId, Long userId) {
        return new TeamMember(teamId, userId, TeamRole.ADMIN, NOW);
    }

    public static TeamMember activeMember(Long teamId, Long userId) {
        return new TeamMember(teamId, userId, TeamRole.MEMBER, NOW);
    }

    public static TeamMember inactiveMember(Long teamId, Long userId) {
        TeamMember member = activeMember(teamId, userId);
        member.leave(NOW);
        return member;
    }
}
