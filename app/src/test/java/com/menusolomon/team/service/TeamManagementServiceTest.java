package com.menusolomon.team.service;

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
import com.menusolomon.user.repository.UserRepository;
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
    void getInvitation_member_throwsAdminRequired() {
        current(member());
        assertError(() -> service.getInvitation(1L, "token"), ErrorCode.ADMIN_REQUIRED);
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

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
