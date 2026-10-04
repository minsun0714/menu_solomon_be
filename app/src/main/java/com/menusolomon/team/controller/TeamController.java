package com.menusolomon.team.controller;

import com.menusolomon.common.response.ApiResponse;
import com.menusolomon.common.web.AnonymousSessionCookie;
import com.menusolomon.team.dto.TeamCreateRequest;
import com.menusolomon.team.dto.TeamCreateResponse;
import com.menusolomon.team.service.TeamService;
import com.menusolomon.user.dto.AnonymousIdentity;
import com.menusolomon.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
    private final UserService userService;
    private final boolean secureCookie;

    public TeamController(
            TeamService teamService,
            UserService userService,
            @Value("${app.cookie.secure:false}") boolean secureCookie
    ) {
        this.teamService = teamService;
        this.userService = userService;
        this.secureCookie = secureCookie;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeamCreateResponse>> createTeam(
            @CookieValue(name = AnonymousSessionCookie.NAME, required = false) String sessionToken,
            @Valid @RequestBody TeamCreateRequest request
    ) {
        AnonymousIdentity identity = userService.identify(sessionToken);
        TeamCreateResponse response = teamService.createTeam(identity.userId(), request);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CREATED);
        if (identity.newlyIssued()) {
            builder.header(HttpHeaders.SET_COOKIE,
                    AnonymousSessionCookie.issue(identity.rawToken(), secureCookie).toString());
        }
        return builder.body(new ApiResponse<>(response));
    }
}
