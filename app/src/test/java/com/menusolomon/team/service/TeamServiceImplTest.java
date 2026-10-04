package com.menusolomon.team.service;

import static com.menusolomon.team.fixture.TeamFixture.NOW;
import static com.menusolomon.team.fixture.TeamFixture.admin;
import static com.menusolomon.team.fixture.TeamFixture.member;
import static com.menusolomon.team.fixture.TeamFixture.team;
import static com.menusolomon.user.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.review.repository.ReviewRepository;
import com.menusolomon.restaurant.repository.TeamRestaurantRepository;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.team.domain.Team;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.domain.TeamRole;
import com.menusolomon.team.dto.TeamCreateRequest;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.team.repository.TeamRepository;
import com.menusolomon.user.domain.User;
import com.menusolomon.user.repository.UserRepository;
import com.menusolomon.user.service.UserService;
import com.menusolomon.user.dto.UserSession;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TeamServiceImplTest {

    private static final String TOKEN = "opaque-session-token";
    private static final String FRONTEND = "https://menu-solomon.vercel.app";

    @Mock private ReviewRepository reviewRepository;
    @Mock private TeamRestaurantRepository teamRestaurantRepository;
    @Mock private UserService userService;
    @Mock private UserRepository userRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;

    private TeamServiceImpl teamService;

    @BeforeEach
    void setUp() {
        teamService = new TeamServiceImpl(userService, userRepository, teamRepository,
                teamMemberRepository, reviewRepository, teamRestaurantRepository, Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC), FRONTEND + "/");
    }

    @Test
    @DisplayName("활성 관리자는 팀 상세와 활성 멤버 수를 조회한다")
    void getTeamDetail_activeAdmin_returnsTeamAndActiveCount() {
        when(userService.findBySessionToken(TOKEN)).thenReturn(Optional.of(user(10L, "hash", "관리자")));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team()));
        when(teamMemberRepository.findByTeamIdAndUserId(1L, 10L)).thenReturn(Optional.of(admin()));
        when(teamMemberRepository.countByTeamIdAndLeftAtIsNull(1L)).thenReturn(6L);

        var response = teamService.getTeamDetail(1L, TOKEN);

        assertThat(response.id()).isEqualTo("team_1");
        assertThat(response.name()).isEqualTo("솔로몬 개발팀");
        assertThat(response.description()).isEqualTo("점심 메뉴를 함께 정해요");
        assertThat(response.memberCount()).isEqualTo(6);
        assertThat(response.myRole()).isEqualTo("ADMIN");
        verify(userService, never()).getOrCreateBySessionToken(any());
        verify(userService, never()).getOrCreateSession(any());
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("활성 일반 멤버의 팀 상세 역할은 MEMBER다")
    void getTeamDetail_activeMember_returnsMemberRole() {
        when(userService.findBySessionToken(TOKEN)).thenReturn(Optional.of(user(20L, "hash", "멤버")));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team()));
        when(teamMemberRepository.findByTeamIdAndUserId(1L, 20L)).thenReturn(Optional.of(member()));
        when(teamMemberRepository.countByTeamIdAndLeftAtIsNull(1L)).thenReturn(2L);

        assertThat(teamService.getTeamDetail(1L, TOKEN).myRole()).isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("가입한 적 없는 사용자는 NOT_TEAM_MEMBER이며 인원 집계도 하지 않는다")
    void getTeamDetail_nonMember_throwsNotTeamMember() {
        when(userService.findBySessionToken(TOKEN)).thenReturn(Optional.of(user(20L, "hash", "외부인")));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team()));
        when(teamMemberRepository.findByTeamIdAndUserId(1L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeamDetail(1L, TOKEN))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_TEAM_MEMBER);

        verify(teamMemberRepository, never()).countByTeamIdAndLeftAtIsNull(any());
    }

    @Test
    @DisplayName("탈퇴한 멤버도 NOT_TEAM_MEMBER다")
    void getTeamDetail_inactiveMember_throwsNotTeamMember() {
        TeamMember left = member();
        left.leave(NOW.plusSeconds(1));
        when(userService.findBySessionToken(TOKEN)).thenReturn(Optional.of(user(20L, "hash", "탈퇴자")));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team()));
        when(teamMemberRepository.findByTeamIdAndUserId(1L, 20L)).thenReturn(Optional.of(left));

        assertThatThrownBy(() -> teamService.getTeamDetail(1L, TOKEN))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_TEAM_MEMBER);

        verify(teamMemberRepository, never()).countByTeamIdAndLeftAtIsNull(any());
    }

    @Test
    @DisplayName("팀 상세의 무효 세션은 사용자 생성이나 팀 조회 없이 거절한다")
    void getTeamDetail_invalidSession_returnsNotTeamMemberWithoutQueries() {
        when(userService.findBySessionToken(TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeamDetail(1L, TOKEN))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_TEAM_MEMBER);

        verify(userService, never()).getOrCreateBySessionToken(any());
        verify(userService, never()).getOrCreateSession(any());
        verifyNoInteractions(teamRepository, teamMemberRepository, userRepository);
    }

    @Test
    @DisplayName("존재하지 않는 팀은 TEAM_NOT_FOUND다")
    void getTeamDetail_missingTeam_throwsTeamNotFound() {
        when(userService.findBySessionToken(TOKEN)).thenReturn(Optional.of(user(10L, "hash", "사용자")));
        when(teamRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeamDetail(1L, TOKEN))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TEAM_NOT_FOUND);

        verifyNoInteractions(teamMemberRepository);
    }

    @Test
    @DisplayName("팀 생성은 생성자를 ADMIN으로 등록하고 완성된 초대 링크를 반환한다")
    void createTeam_savesTeamAndInitialAdminWithInvitation() {
        when(userService.getOrCreateSession(TOKEN)).thenReturn(new UserSession(user(10L, "hash", "생성자"), null));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        var response = teamService.createTeam(new TeamCreateRequest("새 팀", null), TOKEN).team();

        ArgumentCaptor<Team> teams = ArgumentCaptor.forClass(Team.class);
        ArgumentCaptor<TeamMember> members = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamRepository).save(teams.capture());
        verify(teamMemberRepository).save(members.capture());
        assertThat(teams.getValue().getName()).isEqualTo("새 팀");
        assertThat(teams.getValue().getDescription()).isEmpty();
        assertThat(teams.getValue().getInviteToken()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(teams.getValue().getCreatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(members.getValue().getTeamId()).isEqualTo(1L);
        assertThat(members.getValue().getUserId()).isEqualTo(10L);
        assertThat(members.getValue().getRole()).isEqualTo(TeamRole.ADMIN);
        assertThat(response.id()).isEqualTo("team_1");
        assertThat(response.myRole()).isEqualTo("ADMIN");
        assertThat(response.inviteUrl()).isEqualTo(FRONTEND + "/invite/" + teams.getValue().getInviteToken());
    }

    @Test
    @DisplayName("초대 미리보기는 공개 조회이며 활성 멤버 프로필만 반환하고 세션을 생성하지 않는다")
    void preview_withoutSession_returnsActiveMembersWithoutCreatingUser() {
        when(teamRepository.findByInviteToken("invite-token")).thenReturn(Optional.of(team()));
        when(teamMemberRepository.findAllByTeamIdAndLeftAtIsNull(1L)).thenReturn(List.of(admin(), member()));
        when(userRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(
                user(10L, "hash-a", "관리자"), user(20L, "hash-b", "멤버")));
        when(userService.findBySessionToken(null)).thenReturn(Optional.empty());

        var response = teamService.getInvitationPreview("invite-token", null);

        assertThat(response.teamId()).isEqualTo("team_1");
        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.isAlreadyMember()).isFalse();
        assertThat(response.members()).extracting(row -> row.user().nickname()).containsExactly("관리자", "멤버");
        assertThat(response.members().getFirst().user().id()).isEqualTo("user_10");
        verify(userService, never()).getOrCreateBySessionToken(any());
        verify(userService, never()).getOrCreateSession(any());
    }

    @Test
    @DisplayName("초대 미리보기의 현재 활성 멤버는 이미 가입한 상태다")
    void preview_activeSessionMember_isAlreadyMember() {
        when(teamRepository.findByInviteToken("invite-token")).thenReturn(Optional.of(team()));
        when(teamMemberRepository.findAllByTeamIdAndLeftAtIsNull(1L)).thenReturn(List.of(member()));
        when(userRepository.findAllById(List.of(20L))).thenReturn(List.of(user(20L, "hash", "멤버")));
        when(userService.findBySessionToken(TOKEN)).thenReturn(Optional.of(user(20L, "hash", "멤버")));

        assertThat(teamService.getInvitationPreview("invite-token", TOKEN).isAlreadyMember()).isTrue();
    }

    @Test
    @DisplayName("탈퇴자는 공개 초대 미리보기에서 이미 가입한 상태로 표시되지 않는다")
    void preview_inactiveSessionMember_isNotAlreadyMember() {
        when(teamRepository.findByInviteToken("invite-token")).thenReturn(Optional.of(team()));
        when(teamMemberRepository.findAllByTeamIdAndLeftAtIsNull(1L)).thenReturn(List.of());
        when(userService.findBySessionToken(TOKEN)).thenReturn(Optional.of(user(20L, "hash", "탈퇴자")));

        var response = teamService.getInvitationPreview("invite-token", TOKEN);

        assertThat(response.isAlreadyMember()).isFalse();
        assertThat(response.memberCount()).isZero();
        verify(userService, never()).getOrCreateBySessionToken(any());
        verify(userService, never()).getOrCreateSession(any());
    }

    @Test
    @DisplayName("무효 초대 링크는 사용자 식별 없이 INVITATION_NOT_FOUND다")
    void preview_invalidInvitation_throwsNotFoundWithoutIdentityLookup() {
        when(teamRepository.findByInviteToken("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getInvitationPreview("invalid", null))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITATION_NOT_FOUND);

        verifyNoInteractions(userService, userRepository, teamMemberRepository);
    }

    @Test
    @DisplayName("신규 가입은 MEMBER를 생성하고 created true를 반환한다")
    void joinTeam_newMember_createsMember() {
        when(teamRepository.findByInviteToken("invite-token")).thenReturn(Optional.of(team()));
        when(userService.getOrCreateSession(TOKEN)).thenReturn(new UserSession(user(20L, "hash", "멤버"), null));
        when(teamMemberRepository.findByTeamIdAndUserId(1L, 20L)).thenReturn(Optional.empty());
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(invocation -> {
            TeamMember saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });

        var result = teamService.joinTeam("invite-token", TOKEN);

        assertThat(result.created()).isTrue();
        assertThat(result.membership().id()).isEqualTo("member_2");
        assertThat(result.membership().teamId()).isEqualTo("team_1");
        assertThat(result.membership().userId()).isEqualTo("user_20");
        assertThat(result.membership().role()).isEqualTo("MEMBER");
        assertThat(result.membership().joinedAt()).isEqualTo(NOW.plusSeconds(60));
        verify(teamMemberRepository).save(any(TeamMember.class));
    }

    @Test
    @DisplayName("기존 관리자의 반복 가입은 역할과 가입 시각을 유지하고 중복 저장하지 않는다")
    void joinTeam_existingAdmin_returnsExistingMemberWithoutSave() {
        when(teamRepository.findByInviteToken("invite-token")).thenReturn(Optional.of(team()));
        when(userService.getOrCreateSession(TOKEN)).thenReturn(new UserSession(user(10L, "hash", "관리자"), null));
        when(teamMemberRepository.findByTeamIdAndUserId(1L, 10L)).thenReturn(Optional.of(admin()));

        var result = teamService.joinTeam("invite-token", TOKEN);

        assertThat(result.created()).isFalse();
        assertThat(result.membership().id()).isEqualTo("member_1");
        assertThat(result.membership().role()).isEqualTo("ADMIN");
        assertThat(result.membership().joinedAt()).isEqualTo(NOW);
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("탈퇴한 멤버의 재가입은 같은 행을 활성화하며 created false다")
    void joinTeam_inactiveMember_reactivatesWithoutCreatingRow() {
        TeamMember former = admin();
        former.leave(NOW.plusSeconds(1));
        when(teamRepository.findByInviteToken("invite-token")).thenReturn(Optional.of(team()));
        when(userService.getOrCreateSession(TOKEN)).thenReturn(new UserSession(user(10L, "hash", "탈퇴자"), null));
        when(teamMemberRepository.findByTeamIdAndUserId(1L, 10L)).thenReturn(Optional.of(former));

        var result = teamService.joinTeam("invite-token", TOKEN);

        assertThat(result.created()).isFalse();
        assertThat(result.membership().id()).isEqualTo("member_1");
        assertThat(result.membership().role()).isEqualTo("MEMBER");
        assertThat(result.membership().joinedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(former.isActive()).isTrue();
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("무효 링크 가입 요청은 사용자와 멤버를 생성하지 않는다")
    void joinTeam_invalidInvitation_doesNotCreateUserOrMember() {
        when(teamRepository.findByInviteToken("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.joinTeam("invalid", TOKEN))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITATION_NOT_FOUND);

        verifyNoInteractions(userService, teamMemberRepository, userRepository);
    }
    @Test
    @DisplayName("쿠키 없는 팀 상세는 팀 데이터를 조회하지 않고 NOT_TEAM_MEMBER다")
    void getTeamDetail_withoutCookie_returnsNotTeamMemberWithoutCreatingIdentity() {
        assertThatThrownBy(() -> teamService.getTeamDetail(1L, null))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_TEAM_MEMBER);
        verify(userService).findBySessionToken(null);
        verify(userService, never()).getOrCreateSession(any());
        verifyNoInteractions(teamRepository, teamMemberRepository, userRepository);
    }

    @Test
    @DisplayName("최초 팀 생성은 새 세션 토큰을 웹 계층에 전달한다")
    void createTeam_withoutCookie_propagatesIssuedSessionToken() {
        when(userService.getOrCreateSession(null)).thenReturn(new UserSession(user(10L, "hash", "생성자"), "issued-token"));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        var result = teamService.createTeam(new TeamCreateRequest("새 팀", null), null);
        assertThat(result.issuedToken()).isEqualTo("issued-token");
        assertThat(result.team().myRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("최초 가입은 새 세션 토큰과 신규 멤버 결과를 함께 전달한다")
    void joinTeam_withoutCookie_propagatesIssuedSessionToken() {
        when(teamRepository.findByInviteToken("invite-token")).thenReturn(Optional.of(team()));
        when(userService.getOrCreateSession(null)).thenReturn(new UserSession(user(20L, "hash", "멤버"), "issued-token"));
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(invocation -> {
            TeamMember saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });
        var result = teamService.joinTeam("invite-token", null);
        assertThat(result.issuedToken()).isEqualTo("issued-token");
        assertThat(result.created()).isTrue();
    }

}
