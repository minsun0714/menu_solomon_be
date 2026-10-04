package com.menusolomon.team.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TeamMemberTest {

    private static final Instant JOINED_AT = Instant.parse("2026-10-01T02:00:00Z");
    private static final Instant LEFT_AT = Instant.parse("2026-10-02T02:00:00Z");
    private static final Instant REJOINED_AT = Instant.parse("2026-10-04T02:00:00Z");

    @Test
    void newAdmin_isActive() {
        assertThat(TeamMember.newAdmin(1L, 2L, JOINED_AT).isActive()).isTrue();
    }

    @Test
    void newAdmin_isAdmin() {
        assertThat(TeamMember.newAdmin(1L, 2L, JOINED_AT).isAdmin()).isTrue();
    }

    @Test
    void newMember_isActive() {
        assertThat(TeamMember.newMember(1L, 2L, JOINED_AT).isActive()).isTrue();
    }

    @Test
    void newMember_isNotAdmin() {
        assertThat(TeamMember.newMember(1L, 2L, JOINED_AT).isAdmin()).isFalse();
    }

    @Test
    void leave_makesInactive() {
        TeamMember member = TeamMember.newMember(1L, 2L, JOINED_AT);

        member.leave(LEFT_AT);

        assertThat(member.isActive()).isFalse();
    }

    @Test
    void rejoin_makesActive() {
        TeamMember member = inactiveMember();

        member.rejoin(REJOINED_AT);

        assertThat(member.isActive()).isTrue();
    }

    @Test
    void rejoin_updatesJoinedAt() {
        TeamMember member = inactiveMember();

        member.rejoin(REJOINED_AT);

        assertThat(member.getJoinedAt()).isEqualTo(REJOINED_AT);
    }

    @Test
    void rejoin_clearsLeftAt() {
        TeamMember member = inactiveMember();

        member.rejoin(REJOINED_AT);

        assertThat(member.getLeftAt()).isNull();
    }

    @Test
    void rejoin_resetsRoleToMember() {
        TeamMember formerAdmin = TeamMember.newAdmin(1L, 2L, JOINED_AT);
        formerAdmin.leave(LEFT_AT);

        formerAdmin.rejoin(REJOINED_AT);

        assertThat(formerAdmin.isAdmin()).isFalse();
        assertThat(formerAdmin.getRole()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    void rejoin_activeMember_isRejected() {
        TeamMember member = TeamMember.newMember(1L, 2L, JOINED_AT);

        assertThatIllegalStateException().isThrownBy(() -> member.rejoin(REJOINED_AT));
    }

    private TeamMember inactiveMember() {
        TeamMember member = TeamMember.newMember(1L, 2L, JOINED_AT);
        member.leave(LEFT_AT);
        return member;
    }
}
