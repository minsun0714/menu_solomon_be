package com.menusolomon.team.controller;

import com.menusolomon.common.response.ApiResponse;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.common.web.SessionCookies;
import com.menusolomon.team.dto.MyTeamResponse;
import com.menusolomon.team.dto.TeamMemberResponse;
import java.util.List;
import com.menusolomon.team.dto.TeamUpdateRequest;
import com.menusolomon.team.dto.TeamUpdateResponse;
import com.menusolomon.team.dto.InvitationResponse;
import com.menusolomon.team.dto.AdminTransferResponse;
import com.menusolomon.team.dto.OfficeLocationRequest;
import com.menusolomon.team.dto.OfficeLocationResponse;
import com.menusolomon.team.dto.AdminTransferRequest;
import com.menusolomon.team.dto.TeamCreateRequest;
import com.menusolomon.team.dto.TeamCreateResponse;
import com.menusolomon.team.dto.TeamDetailResponse;
import com.menusolomon.team.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public ApiResponse<List<MyTeamResponse>> getMyTeams(
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(teamService.getMyTeams(token));
    }

    @GetMapping("/{teamId}/members")
    public ApiResponse<List<TeamMemberResponse>> getMembers(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(teamService.getMembers(teamId, token));
    }

    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> deleteTeam(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        teamService.deleteTeam(teamId, token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{teamId}/transfer-and-leave")
    public ResponseEntity<Void> transferAndLeave(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token,
            @Valid @RequestBody AdminTransferRequest request) {
        teamService.transferAndLeave(teamId, token, request.targetTeamMemberId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{teamId}")
    public ApiResponse<TeamDetailResponse> getTeam(
            @PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String rawSessionToken
    ) {
        return ApiResponse.of(teamService.getTeamDetail(teamId, rawSessionToken));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeamCreateResponse>> createTeam(
            @Valid @RequestBody TeamCreateRequest request,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String rawSessionToken
    ) {
        var result = teamService.createTeam(request, rawSessionToken);
        return ResponseEntity.status(HttpStatus.CREATED).headers(SessionCookies.headers(result.issuedToken()))
                .body(ApiResponse.of(result.team()));
    }
    @PatchMapping("/{teamId}")
    public ApiResponse<TeamUpdateResponse> update(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token,
            @Valid @RequestBody TeamUpdateRequest request) {
        return ApiResponse.of(teamService.updateTeam(teamId, token, request));
    }

    @GetMapping("/{teamId}/invitation")
    public ApiResponse<InvitationResponse> invitation(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(teamService.getInvitation(teamId, token));
    }

    @PostMapping("/{teamId}/invitation/regenerate")
    public ApiResponse<InvitationResponse> regenerateInvitation(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(teamService.regenerateInvitation(teamId, token));
    }

    @PostMapping("/{teamId}/admin-transfer")
    public ApiResponse<AdminTransferResponse> transferAdmin(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token,
            @Valid @RequestBody AdminTransferRequest request) {
        return ApiResponse.of(teamService.transferAdmin(teamId, token, request.targetTeamMemberId()));
    }

    @DeleteMapping("/{teamId}/members/me")
    public ResponseEntity<Void> leaveTeam(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        teamService.leaveTeam(teamId, token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{teamId}/office")
    public ApiResponse<OfficeLocationResponse> office(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(teamService.getOfficeLocation(teamId, token));
    }

    @PutMapping("/{teamId}/office")
    public ApiResponse<OfficeLocationResponse> updateOffice(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token,
            @Valid @RequestBody OfficeLocationRequest request) {
        return ApiResponse.of(teamService.updateOfficeLocation(teamId, token, request));
    }

}
