package com.menusolomon.common.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menusolomon.team.controller.TeamController;
import com.menusolomon.team.dto.TeamDetailResponse;
import com.menusolomon.team.service.TeamService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TeamController.class, properties = "app.cors.allowed-origins=https://example.com")
class CorsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TeamService teamService;

    @Test
    void frontendOrigin_acceptsCredentialedPreflightWithoutSession() throws Exception {
        mockMvc.perform(options("/api/teams/1").header("Origin", "https://example.com")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://example.com"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Vary", org.hamcrest.Matchers.containsString("Origin")))
                .andExpect(header().doesNotExist("Set-Cookie"));

        verifyNoInteractions(teamService);
    }

    @Test
    void frontendOrigin_acceptsRequestWithSessionCookie() throws Exception {
        when(teamService.getTeamDetail(1L, "session-token"))
                .thenReturn(new TeamDetailResponse("team_1", "팀", "", 1, "ADMIN"));

        mockMvc.perform(get("/api/teams/1").header("Origin", "https://example.com")
                        .cookie(new Cookie(WebConstants.SESSION_COOKIE_NAME, "session-token")))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://example.com"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://other.example", "https://example.com.evil.test", "http://localhost:5173"})
    void unconfiguredOrigin_isRejected(String origin) throws Exception {
        mockMvc.perform(options("/api/teams/1").header("Origin", origin)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

        verifyNoInteractions(teamService);
    }
}
