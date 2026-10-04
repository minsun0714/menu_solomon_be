package com.menusolomon.team.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TeamMemberTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void newAdmin_isActive() {
        TeamMember member = TeamMember.newAdmin(1L, 2L, NOW);

        assertThat(member.isActive()).isTrue();
        assertThat(member.getLeftAt()).isNull();
    }

    @Test
    void newAdmin_isAdmin() {
        TeamMember member = TeamMember.newAdmin(1L, 2L, NOW);

        assertThat(member.isAdmin()).isTrue();
        assertThat(member.getRole()).isEqualTo(TeamRole.ADMIN);
    }
}
