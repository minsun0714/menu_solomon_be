package com.menusolomon.user.controller;

import static com.menusolomon.user.fixture.UserFixture.user;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.user.dto.UserSession;
import com.menusolomon.user.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Test
    @DisplayName("유효한 세션은 닉네임을 변경하고 새 쿠키 없이 현재 사용자 정보를 반환한다")
    void patchMe_changesNicknameWithoutIssuingCookie() throws Exception {
        when(service.updateNickname("existing-token", "익명4d88d"))
                .thenReturn(user(1L, "stored-hash", "익명4d88d"));
        mvc.perform(patch("/api/session/me")
                        .cookie(new Cookie(WebConstants.SESSION_COOKIE_NAME, "existing-token"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"  익명4d88d  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("user_1"))
                .andExpect(jsonPath("$.data.nickname").value("익명4d88d"))
                .andExpect(jsonPath("$.data.anonymousTokenHash").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
        verify(service).updateNickname("existing-token", "익명4d88d");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"nickname\":null}", "{\"nickname\":\" \"}",
            "{\"nickname\":\"a\"}", "{\"nickname\":\"1234567890123\"}",
            "{\"nickname\":\"ab\\nc\"}"})
    @DisplayName("잘못된 닉네임은 400 ProblemDetail과 필드 오류를 반환한다")
    void patchMe_invalidNickname_returns400(String body) throws Exception {
        mvc.perform(patch("/api/session/me").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.nickname").exists());
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    @DisplayName("세션이 없는 닉네임 변경은 새 사용자를 생성하지 않고 401을 반환한다")
    void patchMe_missingSession_returns401() throws Exception {
        when(service.updateNickname(null, "수달4821"))
                .thenThrow(new BusinessException(ErrorCode.SESSION_REQUIRED));
        mvc.perform(patch("/api/session/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"수달4821\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 500 대신 405 ProblemDetail을 반환한다")
    void unsupportedSessionMethod_returns405() throws Exception {
        mvc.perform(post("/api/session/me"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(header().string("Allow", allOf(containsString("GET"), containsString("PATCH"))));
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    private UserSession session(String issuedToken) {
        return new UserSession(user(1L, "stored-hash", "익명 사용자 1234"), issuedToken);
    }
}
