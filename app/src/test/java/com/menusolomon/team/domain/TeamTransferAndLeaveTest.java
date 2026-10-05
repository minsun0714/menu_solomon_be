package com.menusolomon.team.domain;

import static com.menusolomon.team.fixture.TeamFixture.*;
import static org.assertj.core.api.Assertions.*;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TeamTransferAndLeaveTest {
    @Test
    void transferAndLeave_promotesTargetAndLeavesFormerAdmin() {
        var current = admin();
        var target = member();
        current.transferAdminAndLeave(target, NOW.plusSeconds(60));
        assertThat(current.isAdmin()).isFalse();
        assertThat(current.isActive()).isFalse();
        assertThat(current.getLeftAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(target.isAdmin()).isTrue();
        assertThat(target.isActive()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"self", "inactive", "otherTeam", "admin"})
    void transferAndLeave_invalidTarget_preservesCurrentAdmin(String type) {
        var current = admin();
        var target = switch (type) {
            case "self" -> current;
            case "otherTeam" -> TeamMember.newMember(2L, 30L, NOW);
            case "admin" -> TeamMember.newAdmin(1L, 30L, NOW);
            default -> member();
        };
        if (type.equals("inactive")) target.leave(NOW);
        assertThatThrownBy(() -> current.transferAdminAndLeave(target, NOW))
                .isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        assertThat(current.isAdmin()).isTrue();
        assertThat(current.isActive()).isTrue();
    }
}
