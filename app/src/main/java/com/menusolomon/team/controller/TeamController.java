package com.menusolomon.team.controller;

import com.menusolomon.common.response.ApiResponse;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.team.dto.TeamCreateRequest;
import com.menusolomon.team.dto.TeamCreateResponse;
import com.menusolomon.team.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
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

    @PostMapping
    public ResponseEntity<ApiResponse<TeamCreateResponse>> createTeam(
            @Valid @RequestBody TeamCreateRequest request,
            @CookieValue(WebConstants.SESSION_COOKIE_NAME) String rawSessionToken
    ) {
        TeamCreateResponse response = teamService.createTeam(request, rawSessionToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }
}
