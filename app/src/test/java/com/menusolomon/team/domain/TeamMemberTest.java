package com.menusolomon.team.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TeamMemberTest {
    private static final Instant JOINED_AT = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2025-02-01T00:00:00Z");

    @Test
    void newMember_isActive() {
        assertThat(new TeamMember(1L, 2L, TeamRole.MEMBER, JOINED_AT).isActive()).isTrue();
    }

    @Test
    void leave_setsLeftAt() {
        TeamMember member = new TeamMember(1L, 2L, TeamRole.MEMBER, JOINED_AT);

        member.leave(NOW);

        assertThat(member.isActive()).isFalse();
        assertThat(member.getLeftAt()).isEqualTo(NOW);
    }

    @Test
    void inactiveMember_canRejoin() {
        TeamMember member = inactiveMember(TeamRole.MEMBER);

        member.rejoin(NOW);

        assertThat(member.isActive()).isTrue();
    }

    @Test
    void rejoin_clearsLeftAt() {
        TeamMember member = inactiveMember(TeamRole.MEMBER);

        member.rejoin(NOW);

        assertThat(member.getLeftAt()).isNull();
    }

    @Test
    void rejoin_updatesJoinedAt() {
        TeamMember member = inactiveMember(TeamRole.MEMBER);

        member.rejoin(NOW);

        assertThat(member.getJoinedAt()).isEqualTo(NOW);
    }

    @Test
    void formerAdmin_rejoinsAsMember() {
        TeamMember member = inactiveMember(TeamRole.ADMIN);

        member.rejoin(NOW);

        assertThat(member.getRole()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    void activeMember_cannotRejoin() {
        TeamMember member = new TeamMember(1L, 2L, TeamRole.MEMBER, JOINED_AT);

        assertThatThrownBy(() -> member.rejoin(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void promoteToAdmin_changesRole() {
        TeamMember member = new TeamMember(1L, 2L, TeamRole.MEMBER, JOINED_AT);

        member.promoteToAdmin();

        assertThat(member.getRole()).isEqualTo(TeamRole.ADMIN);
    }

    @Test
    void demoteToMember_changesRole() {
        TeamMember member = new TeamMember(1L, 2L, TeamRole.ADMIN, JOINED_AT);

        member.demoteToMember();

        assertThat(member.getRole()).isEqualTo(TeamRole.MEMBER);
    }

    private TeamMember inactiveMember(TeamRole role) {
        TeamMember member = new TeamMember(1L, 2L, role, JOINED_AT);
        member.leave(NOW);
        return member;
    }
}
