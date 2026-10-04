package com.menusolomon.team.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.team.dto.InvitationMemberResponse;
import com.menusolomon.team.dto.InvitationPreviewResponse;
import com.menusolomon.team.dto.InvitationUserResponse;
import com.menusolomon.team.dto.TeamCreateRequest;
import com.menusolomon.team.dto.TeamCreateResponse;
import com.menusolomon.team.dto.TeamCreateResult;
import com.menusolomon.team.dto.TeamJoinResponse;
import com.menusolomon.team.dto.TeamJoinResult;
import com.menusolomon.team.service.TeamService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {TeamController.class, InvitationController.class})
class TeamApiControllerTest {

    private static final String SESSION_TOKEN = "opaque-session-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeamService teamService;

    @Test
    void createTeam_returns201() throws Exception {
        when(teamService.createTeam(
                new TeamCreateRequest("솔로몬 개발팀", "점심 메뉴를 함께 정해요"),
                SESSION_TOKEN
        )).thenReturn(new TeamCreateResult(new TeamCreateResponse(
                "team_1",
                "솔로몬 개발팀",
                "점심 메뉴를 함께 정해요",
                "ADMIN",
                "https://example.com/invite/token"
        ), null));

        mockMvc.perform(post("/api/teams")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"솔로몬 개발팀","description":"점심 메뉴를 함께 정해요"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("team_1"))
                .andExpect(jsonPath("$.data.myRole").value("ADMIN"));
    }

    @Test
    void createTeam_blankName_returns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/teams")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" ","description":"설명"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void getInvitationPreview_returns200() throws Exception {
        when(teamService.getInvitationPreview("invite-token", SESSION_TOKEN))
                .thenReturn(preview(true));

        mockMvc.perform(get("/api/invitations/invite-token").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").value("team_1"))
                .andExpect(jsonPath("$.data.isAlreadyMember").value(true))
                .andExpect(jsonPath("$.data.members[0].user.nickname").value("익명 사용자 1234"));
    }

    @Test
    void getInvitationPreview_withoutCookie_doesNotSetCookie() throws Exception {
        when(teamService.getInvitationPreview("invite-token", null)).thenReturn(preview(false));

        mockMvc.perform(get("/api/invitations/invite-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isAlreadyMember").value(false))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeader("Set-Cookie")
                ).isNull());

        verify(teamService).getInvitationPreview("invite-token", null);
    }

    @Test
    void getInvitationPreview_invalidToken_returns404ProblemDetail() throws Exception {
        when(teamService.getInvitationPreview("invalid-token", null))
                .thenThrow(new BusinessException(ErrorCode.INVITATION_NOT_FOUND));

        mockMvc.perform(get("/api/invitations/invalid-token"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Invitation was not found"));
    }

    @Test
    void join_newMember_returns201() throws Exception {
        when(teamService.joinTeam("invite-token", SESSION_TOKEN))
                .thenReturn(joinResult(true));

        mockMvc.perform(post("/api/invitations/invite-token/join").cookie(sessionCookie()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.id").value("member_2"));
    }

    @Test
    void join_existingMember_returns200() throws Exception {
        when(teamService.joinTeam("invite-token", SESSION_TOKEN))
                .thenReturn(joinResult(false));

        mockMvc.perform(post("/api/invitations/invite-token/join").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("member_2"));
    }

    @Test
    void join_reactivatedMember_returns200() throws Exception {
        when(teamService.joinTeam("invite-token", SESSION_TOKEN))
                .thenReturn(joinResult(false));

        mockMvc.perform(post("/api/invitations/invite-token/join").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
    }

    @Test
    void createTeam_withoutCookie_issuesHostOnlySecureSessionCookie() throws Exception {
        String issuedToken = "s".repeat(43);
        when(teamService.createTeam(new TeamCreateRequest("새 팀", ""), null))
                .thenReturn(new TeamCreateResult(new TeamCreateResponse(
                        "team_1", "새 팀", "", "ADMIN", "https://example.com/invite/token"
                ), issuedToken));

        mockMvc.perform(post("/api/teams").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새 팀\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("team_1"))
                .andExpect(jsonPath("$.data.issuedToken").doesNotExist())
                .andExpect(result -> assertSessionCookie(result, issuedToken));

        verify(teamService).createTeam(new TeamCreateRequest("새 팀", ""), null);
    }

    @Test
    void join_withoutCookie_issuesSessionCookieAndReturns201() throws Exception {
        String issuedToken = "j".repeat(43);
        when(teamService.joinTeam("invite-token", null)).thenReturn(new TeamJoinResult(
                joinResult(true).membership(), true, issuedToken
        ));

        mockMvc.perform(post("/api/invitations/invite-token/join"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.issuedToken").doesNotExist())
                .andExpect(result -> assertSessionCookie(result, issuedToken));

        verify(teamService).joinTeam("invite-token", null);
    }

    @Test
    void join_existingSession_doesNotReplaceCookie() throws Exception {
        when(teamService.joinTeam("invite-token", SESSION_TOKEN)).thenReturn(joinResult(false));

        mockMvc.perform(post("/api/invitations/invite-token/join").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().doesNotExist("Set-Cookie"));
    }

    @Test
    void join_invalidInvitation_doesNotIssueCookie() throws Exception {
        when(teamService.joinTeam("invalid-token", null))
                .thenThrow(new BusinessException(ErrorCode.INVITATION_NOT_FOUND));

        mockMvc.perform(post("/api/invitations/invalid-token/join"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().doesNotExist("Set-Cookie"));
    }

    private void assertSessionCookie(org.springframework.test.web.servlet.MvcResult result, String token) {
        Cookie cookie = result.getResponse().getCookie(WebConstants.SESSION_COOKIE_NAME);
        org.assertj.core.api.Assertions.assertThat(cookie).isNotNull();
        org.assertj.core.api.Assertions.assertThat(cookie.getValue()).isEqualTo(token);
        org.assertj.core.api.Assertions.assertThat(cookie.isHttpOnly()).isTrue();
        org.assertj.core.api.Assertions.assertThat(cookie.getSecure()).isTrue();
        org.assertj.core.api.Assertions.assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        org.assertj.core.api.Assertions.assertThat(cookie.getPath()).isEqualTo("/");
        org.assertj.core.api.Assertions.assertThat(cookie.getMaxAge()).isEqualTo(31536000);
        org.assertj.core.api.Assertions.assertThat(cookie.getDomain()).isNull();
    }

    private Cookie sessionCookie() {
        return new Cookie(WebConstants.SESSION_COOKIE_NAME, SESSION_TOKEN);
    }

    private InvitationPreviewResponse preview(boolean isAlreadyMember) {
        return new InvitationPreviewResponse(
                "team_1",
                "솔로몬 개발팀",
                "",
                1,
                isAlreadyMember,
                List.of(new InvitationMemberResponse(
                        "member_1",
                        "ADMIN",
                        Instant.parse("2026-10-01T02:00:00Z"),
                        new InvitationUserResponse("user_1", "익명 사용자 1234")
                ))
        );
    }

    private TeamJoinResult joinResult(boolean created) {
        return new TeamJoinResult(
                new TeamJoinResponse(
                        "member_2",
                        "team_1",
                        "user_2",
                        "MEMBER",
                        Instant.parse("2026-10-04T08:30:00Z")
                ),
                created
        );
    }
}
