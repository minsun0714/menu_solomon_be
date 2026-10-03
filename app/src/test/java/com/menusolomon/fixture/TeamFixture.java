package com.menusolomon.fixture;

import com.menusolomon.team.domain.Team;
import java.time.Instant;

public final class TeamFixture {
    private TeamFixture() {
    }

    public static Team team(String inviteToken) {
        return new Team("Team", "Description", inviteToken, Instant.parse("2025-01-01T00:00:00Z"));
    }
}
