package com.menusolomon.team.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menusolomon.common.config.CurrentUserProvider;
import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.common.exception.GlobalExceptionHandler;
import com.menusolomon.team.domain.Team;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.domain.TeamRole;
import com.menusolomon.team.dto.response.TeamMemberProfileResponse;
import com.menusolomon.team.dto.response.TeamPreviewResponse;
import com.menusolomon.team.service.TeamMemberResult;
import com.menusolomon.team.service.TeamService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class TeamControllerTest {
    @Mock
    private TeamService teamService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @InjectMocks
    private TeamController teamController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(teamController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn(7L);
    }

    @Test
    void createTeam_returns201() throws Exception {
        Team team = team(1L, "Lunch Club", "A team");
        when(teamService.createTeam("Lunch Club", "A team", 7L)).thenReturn(team);

        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lunch Club\",\"description\":\"A team\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.name").value("Lunch Club"))
                .andExpect(jsonPath("$.description").value("A team"));
    }

    @Test
    void getTeam_returns200() throws Exception {
        Team team = team(1L, "Lunch Club", "A team");
        when(teamService.getTeam(1L)).thenReturn(team);

        mockMvc.perform(get("/api/v1/teams/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    void getTeamPreviewByInviteToken_returns200() throws Exception {
        when(teamService.getTeamPreviewByInviteToken("abc")).thenReturn(
                new TeamPreviewResponse("1", "Lunch Club", "A team", 1,
                        List.of(new TeamMemberProfileResponse("2", "1", "7", TeamRole.ADMIN,
                                Instant.parse("2025-01-01T00:00:00Z"), "Lee", null))));

        mockMvc.perform(get("/api/v1/teams/invite/abc"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.members[0].nickname").value("Lee"));
    }

    @Test
    void joinNewMember_returns201() throws Exception {
        TeamMemberResult result = result(TeamMemberResult.Outcome.CREATED);
        when(teamService.joinByInviteToken("abc", 7L)).thenReturn(result);

        mockMvc.perform(post("/api/v1/teams/invite/abc/join"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value("7"));
    }

    @Test
    void rejoinMember_returns200() throws Exception {
        TeamMemberResult result = result(TeamMemberResult.Outcome.REACTIVATED);
        when(teamService.joinByInviteToken("abc", 7L)).thenReturn(result);

        mockMvc.perform(post("/api/v1/teams/invite/abc/join"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void joinAlreadyActiveMember_returns409() throws Exception {
        when(teamService.joinByInviteToken("abc", 7L))
                .thenThrow(new BusinessException(ErrorCode.ALREADY_TEAM_MEMBER));

        mockMvc.perform(post("/api/v1/teams/invite/abc/join"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_TEAM_MEMBER"));
    }

    @Test
    void joinWithInvalidInviteToken_returns404() throws Exception {
        when(teamService.joinByInviteToken("bad", 7L))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INVITE_TOKEN));

        mockMvc.perform(post("/api/v1/teams/invite/bad/join"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_INVITE_TOKEN"));
    }

    @Test
    void leaveTeam_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/teams/1/members/me"))
                .andExpect(status().isNoContent());
    }

    @Test
    void leaveTeamWhenAdminTransferRequired_returns409() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.ADMIN_TRANSFER_REQUIRED))
                .when(teamService).leaveTeam(1L, 7L);

        mockMvc.perform(delete("/api/v1/teams/1/members/me"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_TRANSFER_REQUIRED"));
    }

    @Test
    void transferAdmin_returns204() throws Exception {
        mockMvc.perform(put("/api/v1/teams/1/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"9\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getInviteToken_returns200() throws Exception {
        when(teamService.getInviteToken(1L)).thenReturn("invite");

        mockMvc.perform(get("/api/v1/teams/1/invite"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.inviteToken").value("invite"));
    }

    @Test
    void regenerateInviteToken_returns200() throws Exception {
        when(teamService.regenerateInviteToken(1L)).thenReturn("new-invite");

        mockMvc.perform(post("/api/v1/teams/1/invite-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.inviteToken").value("new-invite"));
    }

    @Test
    void createTeam_withBlankName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"description\":\"Description\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void createTeam_withTooLongName_returns400() throws Exception {
        String name = "n".repeat(31);

        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"description\":\"Description\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTeam_withTooLongDescription_returns400() throws Exception {
        String description = "d".repeat(101);

        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Valid name\",\"description\":\"" + description + "\"}"))
                .andExpect(status().isBadRequest());
    }

    private Team team(Long id, String name, String description) {
        Team team = mock(Team.class);
        when(team.getId()).thenReturn(id);
        when(team.getName()).thenReturn(name);
        when(team.getDescription()).thenReturn(description);
        return team;
    }

    private TeamMemberResult result(TeamMemberResult.Outcome outcome) {
        TeamMember member = mock(TeamMember.class);
        when(member.getId()).thenReturn(2L);
        when(member.getTeamId()).thenReturn(1L);
        when(member.getUserId()).thenReturn(7L);
        when(member.getRole()).thenReturn(TeamRole.MEMBER);
        when(member.getJoinedAt()).thenReturn(Instant.parse("2025-01-01T00:00:00Z"));
        return new TeamMemberResult(member, outcome);
    }
}
