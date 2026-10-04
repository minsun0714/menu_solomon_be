package com.menusolomon.team.controller;

import java.util.List;
import com.menusolomon.team.dto.TeamMemberResponse;
import com.menusolomon.team.dto.MyTeamResponse;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.team.dto.AdminTransferResponse;
import com.menusolomon.team.dto.InvitationResponse;
import com.menusolomon.team.dto.OfficeLocationResponse;
import com.menusolomon.team.dto.TeamUpdateRequest;
import com.menusolomon.team.dto.TeamUpdateResponse;
import com.menusolomon.team.service.TeamService;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TeamController.class)
class TeamManagementControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TeamService service;
    @Test
    void getMyTeams_returnsActiveMembershipContract() throws Exception {
        when(service.getMyTeams("token")).thenReturn(List.of(
                new MyTeamResponse("team_1", "팀", "소개", "ADMIN", 2)));
        mvc.perform(get("/api/teams").cookie(cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].teamId").value("team_1"))
                .andExpect(jsonPath("$.data[0].name").value("팀"))
                .andExpect(jsonPath("$.data[0].description").value("소개"))
                .andExpect(jsonPath("$.data[0].myRole").value("ADMIN"))
                .andExpect(jsonPath("$.data[0].memberCount").value(2))
                .andExpect(header().doesNotExist("Set-Cookie"));
        verify(service).getMyTeams("token");
    }

    @Test
    void getMyTeams_withoutSession_returns401ProblemDetail() throws Exception {
        when(service.getMyTeams(null)).thenThrow(new BusinessException(ErrorCode.SESSION_REQUIRED));
        mvc.perform(get("/api/teams")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
    }

    @Test
    void getMembers_returnsProfilesAndIsMe() throws Exception {
        when(service.getMembers(1L, "token")).thenReturn(List.of(
                new TeamMemberResponse("member_1", "user_10", "익명", "ADMIN",
                        com.menusolomon.team.fixture.TeamFixture.NOW, true)));
        mvc.perform(get("/api/teams/team_1/members").cookie(cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].teamMemberId").value("member_1"))
                .andExpect(jsonPath("$.data[0].userId").value("user_10"))
                .andExpect(jsonPath("$.data[0].nickname").value("익명"))
                .andExpect(jsonPath("$.data[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.data[0].joinedAt").value("2026-10-04T08:30:00Z"))
                .andExpect(jsonPath("$.data[0].isMe").value(true));
        verify(service).getMembers(1L, "token");
    }

    @Test
    void getMembers_nonMember_returns403ProblemDetail() throws Exception {
        when(service.getMembers(1L, "token")).thenThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        mvc.perform(get("/api/teams/1/members").cookie(cookie())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_TEAM_MEMBER"));
    }

    @Test
    void deleteTeam_returns204() throws Exception {
        mvc.perform(delete("/api/teams/team_1").cookie(cookie())).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).deleteTeam(1L, "token");
    }

    @Test
    void deleteTeam_member_returns403ProblemDetail() throws Exception {
        doThrow(new BusinessException(ErrorCode.ADMIN_REQUIRED)).when(service).deleteTeam(1L, "token");
        mvc.perform(delete("/api/teams/1").cookie(cookie())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
    }

    @Test
    void transferAndLeave_returns204AndBindsTarget() throws Exception {
        mvc.perform(post("/api/teams/team_1/transfer-and-leave").cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetTeamMemberId\":10}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).transferAndLeave(1L, "token", 10L);
    }

    @Test
    void transferAndLeave_invalidTarget_returns400WithoutServiceCall() throws Exception {
        mvc.perform(post("/api/teams/1/transfer-and-leave").cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetTeamMemberId\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void transferAndLeave_unknownTarget_returns404ProblemDetail() throws Exception {
        doThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND)).when(service).transferAndLeave(1L, "token", 10L);
        mvc.perform(post("/api/teams/1/transfer-and-leave").cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetTeamMemberId\":10}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    private Cookie cookie() { return new Cookie(WebConstants.SESSION_COOKIE_NAME, "token"); }
    private static final String OFFICE = """
            {"kakaoPlaceId":"123","name":"엔셀","address":"서울","latitude":37.123,"longitude":127.123}
            """;

    @Test
    void updateTeam_returns200() throws Exception {
        when(service.updateTeam(1L, "token", new TeamUpdateRequest("플랫폼 개발팀", "")))
                .thenReturn(new TeamUpdateResponse("team_1", "플랫폼 개발팀", ""));
        mvc.perform(patch("/api/teams/team_1").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"플랫폼 개발팀\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("team_1"))
                .andExpect(jsonPath("$.data.name").value("플랫폼 개발팀"))
                .andExpect(jsonPath("$.data.description").value(""));
        verify(service).updateTeam(1L, "token", new TeamUpdateRequest("플랫폼 개발팀", ""));
    }

    @Test
    void updateTeam_member_returns403ProblemDetail() throws Exception {
        when(service.updateTeam(eq(1L), eq("token"), any())).thenThrow(new BusinessException(ErrorCode.ADMIN_REQUIRED));
        mvc.perform(patch("/api/teams/1").cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"새 팀\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\" \"}", "{\"name\":null}"})
    void updateTeam_invalidName_returns400ProblemDetail(String body) throws Exception {
        mvc.perform(patch("/api/teams/1").cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void getInvitation_returns200() throws Exception {
        when(service.getInvitation(1L, "token")).thenReturn(new InvitationResponse("https://frontend.example/invite/token"));
        mvc.perform(get("/api/teams/1/invitation").cookie(cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteUrl").value("https://frontend.example/invite/token"));
        verify(service).getInvitation(1L, "token");
    }

    @Test
    void regenerateInvitation_returns200() throws Exception {
        when(service.regenerateInvitation(1L, "token")).thenReturn(new InvitationResponse("https://frontend.example/invite/new"));
        mvc.perform(post("/api/teams/1/invitation/regenerate").cookie(cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteUrl").value("https://frontend.example/invite/new"));
        verify(service).regenerateInvitation(1L, "token");
    }

    @Test
    void transferAdmin_returns200() throws Exception {
        when(service.transferAdmin(1L, "token", 10L)).thenReturn(new AdminTransferResponse("member_10"));
        mvc.perform(post("/api/teams/1/admin-transfer").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetTeamMemberId\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.adminTeamMemberId").value("member_10"));
        verify(service).transferAdmin(1L, "token", 10L);
    }

    @Test
    void leaveTeam_returns204() throws Exception {
        mvc.perform(delete("/api/teams/1/members/me").cookie(cookie())).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).leaveTeam(1L, "token");
    }

    @Test
    void leaveTeam_adminWithOthers_returns409ProblemDetail() throws Exception {
        doThrow(new BusinessException(ErrorCode.ADMIN_TRANSFER_REQUIRED)).when(service).leaveTeam(1L, "token");
        mvc.perform(delete("/api/teams/1/members/me").cookie(cookie())).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("ADMIN_TRANSFER_REQUIRED"));
    }

    @Test
    void getOfficeLocation_returns200() throws Exception {
        when(service.getOfficeLocation(1L, "token")).thenReturn(new OfficeLocationResponse("123", "엔셀", "서울", new BigDecimal("37.123"), new BigDecimal("127.123")));
        mvc.perform(get("/api/teams/1/office").cookie(cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kakaoPlaceId").value("123"))
                .andExpect(jsonPath("$.data.name").value("엔셀"))
                .andExpect(jsonPath("$.data.latitude").value(37.123))
                .andExpect(jsonPath("$.data.longitude").value(127.123));
    }

    @Test
    void getOfficeLocation_unset_returnsNullData() throws Exception {
        mvc.perform(get("/api/teams/1/office").cookie(cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void updateOfficeLocation_returns200() throws Exception {
        when(service.updateOfficeLocation(eq(1L), eq("token"), any())).thenReturn(new OfficeLocationResponse("123", "엔셀", "서울", new BigDecimal("37.123"), new BigDecimal("127.123")));
        mvc.perform(put("/api/teams/1/office").cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content(OFFICE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("엔셀"));
        verify(service).updateOfficeLocation(eq(1L), eq("token"), argThat(request -> request.kakaoPlaceId().equals("123")
                && request.latitude().compareTo(new BigDecimal("37.123")) == 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "latitude", "longitude"})
    void updateOfficeLocation_invalidRequiredField_returns400ProblemDetail(String field) throws Exception {
        String body = switch (field) {
            case "name" -> OFFICE.replace("\"엔셀\"", "\" \"");
            case "latitude" -> OFFICE.replace("37.123", "null");
            default -> OFFICE.replace("127.123", "null");
        };
        mvc.perform(put("/api/teams/1/office").cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void updateOfficeLocation_nonMember_returns403ProblemDetail() throws Exception {
        when(service.updateOfficeLocation(eq(1L), eq("token"), any())).thenThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        mvc.perform(put("/api/teams/1/office").cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content(OFFICE))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_TEAM_MEMBER"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
