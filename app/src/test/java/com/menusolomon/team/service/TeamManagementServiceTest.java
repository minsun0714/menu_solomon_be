package com.menusolomon.team.service;

import java.util.List;
import com.menusolomon.team.repository.MyTeamRow;
import com.menusolomon.team.repository.TeamMemberRow;
import com.menusolomon.team.domain.TeamRole;
import com.menusolomon.team.dto.TeamMemberResponse;
import com.menusolomon.user.repository.UserRepository;

import static com.menusolomon.team.fixture.TeamFixture.*;
import static com.menusolomon.user.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.menusolomon.vote.service.VoteService;
import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.restaurant.repository.TeamRestaurantRepository;
import com.menusolomon.review.repository.ReviewRepository;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.dto.OfficeLocationRequest;
import com.menusolomon.team.dto.TeamUpdateRequest;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.team.repository.TeamRepository;
import com.menusolomon.user.service.UserService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamManagementServiceTest {
    @Mock VoteService voteService;
    @Mock UserService users;
    @Mock UserRepository userRepository;
    @Mock TeamRepository teams;
    @Mock TeamMemberRepository members;
    @Mock TeamRestaurantRepository restaurants;
    @Mock ReviewRepository reviews;
    TeamServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TeamServiceImpl(users, userRepository, teams, members, reviews, restaurants, voteService,
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC), "https://frontend.example/");
    }

    private void current(TeamMember member) {
        when(users.findBySessionToken("token")).thenReturn(Optional.of(user(member.getUserId(), "hash", "익명")));
        when(members.findByTeamIdAndUserId(1L, member.getUserId())).thenReturn(Optional.of(member));
    }

    @Test
    void updateTeam_admin_updatesTeam() {
        current(admin());
        var team = team();
        when(teams.findById(1L)).thenReturn(Optional.of(team));
        var response = service.updateTeam(1L, "token", new TeamUpdateRequest("플랫폼 개발팀", null));
        assertThat(response.id()).isEqualTo("team_1");
        assertThat(team.getName()).isEqualTo("플랫폼 개발팀");
        assertThat(team.getDescription()).isEmpty();
        assertThat(team.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void updateTeam_member_throwsAdminRequired() {
        current(member());
        assertError(() -> service.updateTeam(1L, "token", new TeamUpdateRequest("새 팀", "")), ErrorCode.ADMIN_REQUIRED);
        verifyNoInteractions(teams);
    }

    @Test
    void getInvitation_admin_returnsInviteUrl() {
        current(admin());
        when(teams.findById(1L)).thenReturn(Optional.of(team()));
        assertThat(service.getInvitation(1L, "token").inviteUrl()).isEqualTo("https://frontend.example/invite/invite-token");
    }

    @Test
    void getInvitation_member_returnsInviteUrl() {
        current(member());
        when(teams.findById(1L)).thenReturn(Optional.of(team()));
        assertThat(service.getInvitation(1L, "token").inviteUrl()).isEqualTo("https://frontend.example/invite/invite-token");
    }

    @Test
    void regenerateInvitation_changesToken() {
        current(admin());
        var team = team();
        when(teams.findById(1L)).thenReturn(Optional.of(team));
        var response = service.regenerateInvitation(1L, "token");
        assertThat(team.getInviteToken()).isNotEqualTo("invite-token").matches("[A-Za-z0-9_-]{43}");
        assertThat(java.util.Base64.getUrlDecoder().decode(team.getInviteToken())).hasSize(32);
        assertThat(response.inviteUrl()).isEqualTo("https://frontend.example/invite/" + team.getInviteToken());
        assertThat(team.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void transferAdmin_changesBothRoles() {
        var admin = admin();
        var target = member();
        current(admin);
        when(members.findByIdAndTeamId(2L, 1L)).thenReturn(Optional.of(target));
        assertThat(service.transferAdmin(1L, "token", 2L).adminTeamMemberId()).isEqualTo("member_2");
        assertThat(admin.isAdmin()).isFalse();
        assertThat(target.isAdmin()).isTrue();
    }

    @Test
    void transferAdmin_memberRequester_throwsAdminRequired() {
        current(member());
        assertError(() -> service.transferAdmin(1L, "token", 2L), ErrorCode.ADMIN_REQUIRED);
        verify(members, never()).findByIdAndTeamId(any(), any());
    }

    @Test
    void transferAdmin_invalidTarget_throwsMemberNotFound() {
        current(admin());
        assertError(() -> service.transferAdmin(1L, "token", 99L), ErrorCode.MEMBER_NOT_FOUND);
        verify(members).findByIdAndTeamId(99L, 1L);
    }

    @Test
    void leaveTeam_member_setsLeftAt() {
        var member = member();
        current(member);
        when(members.countByTeamIdAndLeftAtIsNull(1L)).thenReturn(2L);
        service.leaveTeam(1L, "token");
        assertThat(member.getLeftAt()).isEqualTo(NOW.plusSeconds(60));
        verifyNoInteractions(teams, restaurants, reviews);
    }

    @Test
    void leaveTeam_adminWithOthers_throwsAdminTransferRequired() {
        current(admin());
        when(members.countByTeamIdAndLeftAtIsNull(1L)).thenReturn(2L);
        assertError(() -> service.leaveTeam(1L, "token"), ErrorCode.ADMIN_TRANSFER_REQUIRED);
        verifyNoInteractions(teams, restaurants, reviews);
    }

    @Test
    void leaveTeam_lastAdmin_deletesTeamInDependencyOrder() {
        current(admin());
        when(members.countByTeamIdAndLeftAtIsNull(1L)).thenReturn(1L);
        var team = team();
        when(teams.findById(1L)).thenReturn(Optional.of(team));
        service.leaveTeam(1L, "token");
        var order = inOrder(voteService, reviews, restaurants, members, teams);
        order.verify(voteService).deleteTeamData(1L);
        order.verify(reviews).deleteAllByTeamId(1L);
        order.verify(restaurants).deleteAllByTeamId(1L);
        order.verify(members).deleteAllByTeamId(1L);
        order.verify(teams).delete(team);
    }

    @Test
    void getOfficeLocation_activeMember_returnsOffice() {
        current(member());
        var team = team();
        team.changeOfficeLocation("123", "엔셀", "서울", BigDecimal.ONE, BigDecimal.TEN, NOW);
        when(teams.findById(1L)).thenReturn(Optional.of(team));
        var response = service.getOfficeLocation(1L, "token");
        assertThat(response.kakaoPlaceId()).isEqualTo("123");
        assertThat(response.name()).isEqualTo("엔셀");
    }

    @Test
    void getOfficeLocation_withoutOffice_returnsEmptyState() {
        current(member());
        when(teams.findById(1L)).thenReturn(Optional.of(team()));
        assertThat(service.getOfficeLocation(1L, "token")).isNull();
    }

    @Test
    void getOfficeLocation_nonMember_throwsNotTeamMember() {
        assertError(() -> service.getOfficeLocation(1L, "token"), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(teams);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void updateOfficeLocation_adminAndMember_canUpdate(boolean isAdmin) {
        current(isAdmin ? admin() : member());
        var team = team();
        when(teams.findById(1L)).thenReturn(Optional.of(team));
        var response = service.updateOfficeLocation(1L, "token", new OfficeLocationRequest("123", "엔셀", "서울", BigDecimal.ONE, BigDecimal.TEN));
        assertThat(response.kakaoPlaceId()).isEqualTo("123");
        assertThat(team.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void updateOfficeLocation_nonMember_throwsNotTeamMember() {
        assertError(() -> service.updateOfficeLocation(1L, "token", new OfficeLocationRequest("123", "엔셀", "서울", BigDecimal.ONE, BigDecimal.TEN)), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(teams);
    }

    @Test
    void updateTeam_inactiveMember_throwsNotTeamMember() {
        var inactive = admin();
        inactive.leave(NOW);
        current(inactive);
        assertError(() -> service.updateTeam(1L, "token", new TeamUpdateRequest("새 팀", "")), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(teams);
    }

    @Test
    void getMyTeams_returnsProjectedActiveTeams() {
        when(users.findBySessionToken("token")).thenReturn(Optional.of(user(10L, "hash", "익명")));
        when(teams.findMyTeams(10L)).thenReturn(List.of(
                new MyTeamRow(1L, "팀", "소개", TeamRole.ADMIN, 2L)));
        var result = service.getMyTeams("token");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().teamId()).isEqualTo("team_1");
        assertThat(result.getFirst().myRole()).isEqualTo("ADMIN");
        assertThat(result.getFirst().memberCount()).isEqualTo(2);
        verify(users, never()).getOrCreateSession(any());
    }

    @Test
    void getMyTeams_withoutSession_throwsSessionRequired() {
        assertError(() -> service.getMyTeams(null), ErrorCode.SESSION_REQUIRED);
        verifyNoInteractions(teams);
    }

    @Test
    void getMyTeams_withoutMembership_returnsEmptyList() {
        when(users.findBySessionToken("token")).thenReturn(Optional.of(user(10L, "hash", "익명")));
        assertThat(service.getMyTeams("token")).isEmpty();
    }

    @Test
    void getMembers_setsIsMeAndMapsProfiles() {
        current(admin());
        when(members.findActiveMemberProfiles(1L)).thenReturn(List.of(
                new TeamMemberRow(1L, 10L, "관리자", TeamRole.ADMIN, NOW),
                new TeamMemberRow(2L, 20L, "팀원", TeamRole.MEMBER, NOW)));
        var result = service.getMembers(1L, "token");
        assertThat(result).extracting(TeamMemberResponse::isMe).containsExactly(true, false);
        assertThat(result.getLast().userId()).isEqualTo("user_20");
        verifyNoInteractions(userRepository);
    }

    @Test
    void getMembers_nonMember_throwsNotTeamMember() {
        assertError(() -> service.getMembers(1L, "token"), ErrorCode.NOT_TEAM_MEMBER);
        verify(members, never()).findActiveMemberProfiles(any());
    }

    @Test
    void deleteTeam_admin_deletesOwnedDataInOrder() {
        current(admin());
        var team = team();
        when(teams.findById(1L)).thenReturn(Optional.of(team));
        service.deleteTeam(1L, "token");
        var order = inOrder(voteService, reviews, restaurants, members, teams);
        order.verify(voteService).deleteTeamData(1L);
        order.verify(reviews).deleteAllByTeamId(1L);
        order.verify(restaurants).deleteAllByTeamId(1L);
        order.verify(members).deleteAllByTeamId(1L);
        order.verify(teams).delete(team);
    }

    @Test
    void deleteTeam_member_throwsAdminRequiredWithoutDeleting() {
        current(member());
        assertError(() -> service.deleteTeam(1L, "token"), ErrorCode.ADMIN_REQUIRED);
        verifyNoInteractions(voteService, reviews, restaurants, teams);
    }

    @Test
    void deleteTeam_nonMember_throwsNotTeamMember() {
        assertError(() -> service.deleteTeam(1L, "token"), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(voteService, reviews, restaurants, teams);
    }

    @Test
    void transferAndLeave_promotesTargetAndLeavesCurrentAdmin() {
        var current = admin();
        var target = member();
        current(current);
        when(members.findByIdAndTeamId(2L, 1L)).thenReturn(Optional.of(target));
        service.transferAndLeave(1L, "token", 2L);
        assertThat(target.isAdmin()).isTrue();
        assertThat(current.isAdmin()).isFalse();
        assertThat(current.getLeftAt()).isEqualTo(NOW.plusSeconds(60));
        verifyNoInteractions(voteService, reviews, restaurants, teams);
    }

    @Test
    void transferAndLeave_invalidTarget_doesNotChangeCurrentAdmin() {
        var current = admin();
        current(current);
        assertError(() -> service.transferAndLeave(1L, "token", 99L), ErrorCode.MEMBER_NOT_FOUND);
        assertThat(current.isAdmin()).isTrue();
        assertThat(current.isActive()).isTrue();
    }

    @Test
    void transferAndLeave_memberRequester_throwsAdminRequired() {
        current(member());
        assertError(() -> service.transferAndLeave(1L, "token", 1L), ErrorCode.ADMIN_REQUIRED);
        verify(members, never()).findByIdAndTeamId(any(), any());
    }

    @Test
    void regenerateInvitation_member_throwsAdminRequired() {
        current(member());
        assertError(() -> service.regenerateInvitation(1L, "token"), ErrorCode.ADMIN_REQUIRED);
    }

    @Test
    void getInvitation_inactiveMember_throwsNotTeamMember() {
        var inactive = member();
        inactive.leave(NOW);
        current(inactive);
        assertError(() -> service.getInvitation(1L, "token"), ErrorCode.NOT_TEAM_MEMBER);
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
