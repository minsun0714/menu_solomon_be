package com.menusolomon.user.controller;

import com.menusolomon.common.response.ApiResponse;
import com.menusolomon.common.web.SessionCookies;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.user.dto.CurrentUserResponse;
import com.menusolomon.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final UserService userService;

    public SessionController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> getMe(
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String rawSessionToken
    ) {
        var session = userService.getOrCreateSession(rawSessionToken);
        return ResponseEntity.ok().headers(SessionCookies.headers(session.issuedToken()))
                .body(ApiResponse.of(CurrentUserResponse.from(session.user())));
    }
}
