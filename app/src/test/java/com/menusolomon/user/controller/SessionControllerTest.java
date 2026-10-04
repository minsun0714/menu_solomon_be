package com.menusolomon.user.controller;

import static com.menusolomon.user.fixture.UserFixture.user;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.user.dto.UserSession;
import com.menusolomon.user.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService service;

    @Test
    void getMe_withoutCookie_returnsUserAndIssuesSecureSessionCookie() throws Exception {
        when(service.getOrCreateSession(null)).thenReturn(session("server-token"));

        mvc.perform(get("/api/session/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("user_1"))
                .andExpect(jsonPath("$.data.nickname").value("익명 사용자 1234"))
                .andExpect(jsonPath("$.data.profileImageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.anonymousTokenHash").doesNotExist())
                .andExpect(jsonPath("$.data.issuedToken").doesNotExist())
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("menu_solomon_session=server-token;"),
                        containsString("Path=/"), containsString("Max-Age=31536000"),
                        containsString("Secure"), containsString("HttpOnly"),
                        containsString("SameSite=Lax"), not(containsString("Domain=")))));
        verify(service).getOrCreateSession(null);
    }

    @Test
    void getMe_existingCookie_returnsSameUserWithoutIssuingCookie() throws Exception {
        when(service.getOrCreateSession("existing-token")).thenReturn(session(null));

        mvc.perform(get("/api/session/me")
                        .cookie(new Cookie(WebConstants.SESSION_COOKIE_NAME, "existing-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("user_1"))
                .andExpect(jsonPath("$.data.nickname").value("익명 사용자 1234"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        verify(service).getOrCreateSession("existing-token");
    }

    @Test
    void getMe_unknownCookie_returnsReplacementSessionCookie() throws Exception {
        when(service.getOrCreateSession("unknown-token")).thenReturn(session("replacement-token"));

        mvc.perform(get("/api/session/me")
                        .cookie(new Cookie(WebConstants.SESSION_COOKIE_NAME, "unknown-token")))
                .andExpect(status().isOk())
                .andExpect(cookie().value(WebConstants.SESSION_COOKIE_NAME, "replacement-token"));
        verify(service).getOrCreateSession("unknown-token");
    }

    @Test
    void getMe_sessionRequired_returns401ProblemDetail() throws Exception {
        when(service.getOrCreateSession(null)).thenThrow(new BusinessException(ErrorCode.SESSION_REQUIRED));

        mvc.perform(get("/api/session/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    private UserSession session(String issuedToken) {
        return new UserSession(user(1L, "stored-hash", "익명 사용자 1234"), issuedToken);
    }
}
