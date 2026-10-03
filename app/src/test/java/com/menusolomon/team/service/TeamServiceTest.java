package com.menusolomon.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.fixture.TeamFixture;
import com.menusolomon.fixture.TeamMemberFixture;
import com.menusolomon.team.domain.Team;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.domain.TeamRole;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.team.repository.TeamRepository;
import com.menusolomon.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {
    private static final Long TEAM_ID = 10L;
    private static final Long USER_ID = 20L;
    private static final Instant NOW = Instant.parse("2025-03-01T00:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private Clock clock;
    @InjectMocks
    private TeamService teamService;

    @BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        lenient().when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void createTeam_createsAdminMembership() {
        Team savedTeam = mock(Team.class);
        when(savedTeam.getId()).thenReturn(TEAM_ID);
        when(teamRepository.save(any(Team.class))).thenReturn(savedTeam);

        teamService.createTeam("name", "description", USER_ID);

        ArgumentCaptor<TeamMember> memberCaptor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getTeamId()).isEqualTo(TEAM_ID);
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(TeamRole.ADMIN);
        assertThat(memberCaptor.getValue().getJoinedAt()).isEqualTo(NOW);
        assertThat(memberCaptor.getValue().getLeftAt()).isNull();
    }

    @Test
    void join_whenInviteTokenInvalid_throwsInvalidInviteToken() {
        when(teamRepository.findByInviteToken("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.joinByInviteToken("invalid", USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INVITE_TOKEN);
    }

    @Test
    void join_whenNoMembershipExists_createsMember() {
        stubTeamByInviteToken();
        when(teamMemberRepository.findByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(Optional.empty());
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamMemberResult result = teamService.joinByInviteToken("token", USER_ID);

        assertThat(result.outcome()).isEqualTo(TeamMemberResult.Outcome.CREATED);
        assertThat(result.member().getRole()).isEqualTo(TeamRole.MEMBER);
        assertThat(result.member().getJoinedAt()).isEqualTo(NOW);
        assertThat(result.member().getLeftAt()).isNull();
    }

    @Test
    void join_whenMembershipIsActive_throwsAlreadyTeamMember() {
        stubTeamByInviteToken();
        when(teamMemberRepository.findByTeamIdAndUserId(TEAM_ID, USER_ID))
                .thenReturn(Optional.of(TeamMemberFixture.activeMember(TEAM_ID, USER_ID)));

        assertThatThrownBy(() -> teamService.joinByInviteToken("token", USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_TEAM_MEMBER);
    }

    @Test
    void join_whenMembershipIsInactive_reactivatesExistingMembership() {
        stubTeamByInviteToken();
        TeamMember inactive = TeamMemberFixture.inactiveMember(TEAM_ID, USER_ID);
        when(teamMemberRepository.findByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(Optional.of(inactive));

        TeamMemberResult result = teamService.joinByInviteToken("token", USER_ID);

        assertThat(result.member()).isSameAs(inactive);
        assertThat(result.outcome()).isEqualTo(TeamMemberResult.Outcome.REACTIVATED);
        assertThat(result.member().isActive()).isTrue();
    }

    @Test
    void join_whenFormerAdminRejoins_resetsRoleToMember() {
        stubTeamByInviteToken();
        TeamMember formerAdmin = TeamMemberFixture.admin(TEAM_ID, USER_ID);
        formerAdmin.leave(NOW.minusSeconds(60));
        when(teamMemberRepository.findByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(Optional.of(formerAdmin));

        TeamMemberResult result = teamService.joinByInviteToken("token", USER_ID);

        assertThat(result.member().getRole()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    void leave_whenNormalMember_setsLeftAt() {
        TeamMember member = TeamMemberFixture.activeMember(TEAM_ID, USER_ID);
        when(teamMemberRepository.findByTeamIdAndUserIdAndLeftAtIsNull(TEAM_ID, USER_ID))
                .thenReturn(Optional.of(member));
        when(teamMemberRepository.countByTeamIdAndLeftAtIsNull(TEAM_ID)).thenReturn(2L);

        teamService.leaveTeam(TEAM_ID, USER_ID);

        assertThat(member.getLeftAt()).isEqualTo(NOW);
    }

    @Test
    void leave_whenAdminHasOtherActiveMembers_throwsAdminTransferRequired() {
        when(teamMemberRepository.findByTeamIdAndUserIdAndLeftAtIsNull(TEAM_ID, USER_ID))
                .thenReturn(Optional.of(TeamMemberFixture.admin(TEAM_ID, USER_ID)));
        when(teamMemberRepository.countByTeamIdAndLeftAtIsNull(TEAM_ID)).thenReturn(2L);

        assertThatThrownBy(() -> teamService.leaveTeam(TEAM_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ADMIN_TRANSFER_REQUIRED);
    }

    @Test
    void leave_whenLastActiveMember_deletesTeam() {
        Team team = TeamFixture.team("token");
        when(teamMemberRepository.findByTeamIdAndUserIdAndLeftAtIsNull(TEAM_ID, USER_ID))
                .thenReturn(Optional.of(TeamMemberFixture.admin(TEAM_ID, USER_ID)));
        when(teamMemberRepository.countByTeamIdAndLeftAtIsNull(TEAM_ID)).thenReturn(1L);
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team));

        teamService.leaveTeam(TEAM_ID, USER_ID);

        verify(teamRepository).delete(team);
    }

    @Test
    void transferAdmin_demotesCurrentAdminAndPromotesTarget() {
        TeamMember currentAdmin = mock(TeamMember.class);
        when(currentAdmin.isAdmin()).thenReturn(true);
        when(currentAdmin.getId()).thenReturn(30L);
        TeamMember target = mock(TeamMember.class);
        when(target.getId()).thenReturn(40L);
        when(teamMemberRepository.findByTeamIdAndUserIdAndLeftAtIsNull(TEAM_ID, USER_ID))
                .thenReturn(Optional.of(currentAdmin));
        when(teamMemberRepository.findAllByTeamIdAndLeftAtIsNull(TEAM_ID))
                .thenReturn(List.of(currentAdmin, target));

        teamService.transferAdmin(TEAM_ID, USER_ID, 40L);

        verify(currentAdmin).demoteToMember();
        verify(target).promoteToAdmin();
    }

    private void stubTeamByInviteToken() {
        Team team = mock(Team.class);
        when(team.getId()).thenReturn(TEAM_ID);
        when(teamRepository.findByInviteToken("token")).thenReturn(Optional.of(team));
    }

}
