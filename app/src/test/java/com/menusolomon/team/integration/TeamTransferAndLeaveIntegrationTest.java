package com.menusolomon.team.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.menusolomon.common.config.ClockConfiguration;
import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.team.domain.Team;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.team.repository.TeamRepository;
import com.menusolomon.team.service.TeamService;
import com.menusolomon.team.service.TeamServiceImpl;
import com.menusolomon.user.domain.User;
import com.menusolomon.user.service.UserService;
import com.menusolomon.vote.service.VoteService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@Import({TeamServiceImpl.class, ClockConfiguration.class})
@TestPropertySource(properties = "app.frontend-base-url=https://frontend.example")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TeamTransferAndLeaveIntegrationTest {
    @Autowired TeamService service;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean UserService users;
    @MockitoBean VoteService votes;
    Long teamId;
    Long adminId;
    Long targetId;

    @BeforeEach
    void seedCommittedMembers() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var now = Instant.parse("2026-10-04T04:00:00Z");
            var team = teams.save(Team.create("팀", "", UUID.randomUUID().toString(), now));
            teamId = team.getId();
            adminId = members.save(TeamMember.newAdmin(teamId, 10L, now)).getId();
            targetId = members.save(TeamMember.newMember(teamId, 20L, now)).getId();
        });
        var user = User.create("hash", "익명", Instant.parse("2026-10-04T04:00:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 10L);
        when(users.findBySessionToken("token")).thenReturn(Optional.of(user));
    }

    @Test
    void transferAndLeave_commitsPromotionAndDepartureTogether() {
        service.transferAndLeave(teamId, "token", targetId);
        assertThat(members.findById(adminId).orElseThrow().isActive()).isFalse();
        assertThat(members.findById(adminId).orElseThrow().isAdmin()).isFalse();
        assertThat(members.findById(targetId).orElseThrow().isAdmin()).isTrue();
        assertThat(teams.findById(teamId)).isPresent();
    }

    @Test
    void transferAndLeave_invalidTarget_preservesPersistedRolesAndMembership() {
        assertThatThrownBy(() -> service.transferAndLeave(teamId, "token", adminId))
                .isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        assertThat(members.findById(adminId).orElseThrow().isAdmin()).isTrue();
        assertThat(members.findById(adminId).orElseThrow().isActive()).isTrue();
        assertThat(members.findById(targetId).orElseThrow().isAdmin()).isFalse();
    }

    @Test
    void transferAndLeave_transactionFailure_rollsBackBothChanges() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.transferAndLeave(teamId, "token", targetId);
            members.flush();
            throw new IllegalStateException("Failure before transaction commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(members.findById(adminId).orElseThrow().isActive()).isTrue();
        assertThat(members.findById(adminId).orElseThrow().isAdmin()).isTrue();
        assertThat(members.findById(targetId).orElseThrow().isAdmin()).isFalse();
    }
}
