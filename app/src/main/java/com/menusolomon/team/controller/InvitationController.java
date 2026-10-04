package com.menusolomon.team.controller;

import com.menusolomon.common.response.ApiResponse;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.common.web.SessionCookies;
import com.menusolomon.team.dto.InvitationPreviewResponse;
import com.menusolomon.team.dto.TeamJoinResponse;
import com.menusolomon.team.dto.TeamJoinResult;
import com.menusolomon.team.service.TeamService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    private final TeamService teamService;

    public InvitationController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/{inviteToken}")
    public ApiResponse<InvitationPreviewResponse> getInvitationPreview(
            @PathVariable String inviteToken,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String rawSessionToken
    ) {
        return ApiResponse.of(teamService.getInvitationPreview(inviteToken, rawSessionToken));
    }

    @PostMapping("/{inviteToken}/join")
    public ResponseEntity<ApiResponse<TeamJoinResponse>> joinTeam(
            @PathVariable String inviteToken,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String rawSessionToken
    ) {
        TeamJoinResult result = teamService.joinTeam(inviteToken, rawSessionToken);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).headers(SessionCookies.headers(result.issuedToken()))
                .body(ApiResponse.of(result.membership()));
    }
}
