package com.menusolomon.team.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.team.dto.TeamDetailResponse;
import com.menusolomon.team.service.TeamService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TeamController.class)
class TeamDetailControllerTest {

    private static final String SESSION_TOKEN = "opaque-session-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeamService teamService;

    @Test
    void getTeam_activeAdmin_returns200() throws Exception {
        when(teamService.getTeamDetail(1L, SESSION_TOKEN)).thenReturn(detail("ADMIN"));

        mockMvc.perform(get("/api/teams/1").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.id").value("team_1"))
                .andExpect(jsonPath("$.data.name").value("솔로몬 개발팀"))
                .andExpect(jsonPath("$.data.description").value("점심 메뉴를 함께 정해요"))
                .andExpect(jsonPath("$.data.memberCount").value(6))
                .andExpect(jsonPath("$.data.myRole").value("ADMIN"))
                .andExpect(header().doesNotExist("Set-Cookie"));

        verify(teamService).getTeamDetail(1L, SESSION_TOKEN);
    }

    @Test
    void getTeam_activeMember_returns200() throws Exception {
        when(teamService.getTeamDetail(1L, SESSION_TOKEN)).thenReturn(detail("MEMBER"));

        mockMvc.perform(get("/api/teams/1").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRole").value("MEMBER"))
                .andExpect(header().doesNotExist("Set-Cookie"));

        verify(teamService).getTeamDetail(1L, SESSION_TOKEN);
    }

    @Test
    void getTeam_nonMember_returns403ProblemDetail() throws Exception {
        when(teamService.getTeamDetail(1L, SESSION_TOKEN))
                .thenThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER));

        mockMvc.perform(get("/api/teams/1").cookie(sessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("팀원만 사용할 수 있습니다."))
                .andExpect(jsonPath("$.instance").value("/api/teams/1"))
                .andExpect(jsonPath("$.code").value("NOT_TEAM_MEMBER"))
                .andExpect(header().doesNotExist("Set-Cookie"));

        verify(teamService).getTeamDetail(1L, SESSION_TOKEN);
    }

    @Test
    void getTeam_withoutCookie_returns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/teams/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(header().doesNotExist("Set-Cookie"));

        verifyNoInteractions(teamService);
    }

    @Test
    void getTeam_invalidSession_returns401ProblemDetail() throws Exception {
        when(teamService.getTeamDetail(1L, SESSION_TOKEN))
                .thenThrow(new BusinessException(ErrorCode.SESSION_REQUIRED));

        mockMvc.perform(get("/api/teams/1").cookie(sessionCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(header().doesNotExist("Set-Cookie"));

        verify(teamService).getTeamDetail(1L, SESSION_TOKEN);
    }

    @Test
    void getTeam_missingTeam_returns404ProblemDetail() throws Exception {
        when(teamService.getTeamDetail(1L, SESSION_TOKEN))
                .thenThrow(new BusinessException(ErrorCode.TEAM_NOT_FOUND));

        mockMvc.perform(get("/api/teams/1").cookie(sessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    private Cookie sessionCookie() {
        return new Cookie(WebConstants.SESSION_COOKIE_NAME, SESSION_TOKEN);
    }

    private TeamDetailResponse detail(String myRole) {
        return new TeamDetailResponse(
                "team_1", "솔로몬 개발팀", "점심 메뉴를 함께 정해요", 6, myRole
        );
    }
}
