package com.menusolomon.team.domain;

import static com.menusolomon.team.fixture.TeamFixture.*;
import static org.assertj.core.api.Assertions.*;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TeamMemberManagementTest {
    @Test
    void promoteToAdmin_changesRole() {
        var member = member();
        member.promoteToAdmin();
        assertThat(member.isAdmin()).isTrue();
    }

    @Test
    void demoteToMember_changesRole() {
        var admin = admin();
        admin.demoteToMember();
        assertThat(admin.getRole()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    void transferAdminTo_changesBothRoles() {
        var admin = admin();
        var target = member();
        admin.transferAdminTo(target);
        assertThat(admin.isAdmin()).isFalse();
        assertThat(target.isAdmin()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"self", "inactive", "otherTeam", "admin"})
    void transferAdminTo_invalidTarget_preservesBothRoles(String kind) {
        var admin = admin();
        var target = switch (kind) {
            case "self" -> admin;
            case "otherTeam" -> TeamMember.newMember(2L, 20L, NOW);
            case "admin" -> TeamMember.newAdmin(1L, 20L, NOW);
            default -> member();
        };
        if (kind.equals("inactive")) target.leave(NOW);
        TeamRole role = target.getRole();
        assertError(() -> admin.transferAdminTo(target), ErrorCode.MEMBER_NOT_FOUND);
        assertThat(admin.isAdmin()).isTrue();
        assertThat(target.getRole()).isEqualTo(role);
    }

    @Test
    void requireAdmin_ordinaryMember_isRejected() {
        assertError(() -> member().requireAdmin(), ErrorCode.ADMIN_REQUIRED);
    }

    @Test
    void promoteToAdmin_inactiveMember_isRejected() {
        var member = member();
        member.leave(NOW);
        assertError(member::promoteToAdmin, ErrorCode.NOT_TEAM_MEMBER);
    }

    @Test
    void shouldDeleteTeamOnLeave_adminWithOtherActiveMembers_requiresTransfer() {
        assertError(() -> admin().shouldDeleteTeamOnLeave(2), ErrorCode.ADMIN_TRANSFER_REQUIRED);
    }

    @Test
    void shouldDeleteTeamOnLeave_lastAdmin_deletesTeam() {
        assertThat(admin().shouldDeleteTeamOnLeave(1)).isTrue();
    }

    @Test
    void shouldDeleteTeamOnLeave_member_preservesTeam() {
        assertThat(member().shouldDeleteTeamOnLeave(2)).isFalse();
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
