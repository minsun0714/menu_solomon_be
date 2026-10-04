package com.menusolomon.team.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menusolomon.common.exception.GlobalExceptionHandler;
import com.menusolomon.team.domain.TeamRole;
import com.menusolomon.team.dto.TeamCreateRequest;
import com.menusolomon.team.dto.TeamCreateResponse;
import com.menusolomon.team.service.TeamService;
import com.menusolomon.user.dto.AnonymousIdentity;
import com.menusolomon.user.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class TeamControllerTest {
    private static final String BODY = """
            {"name":"솔로몬 개발팀","description":"점심 메뉴를 함께 정해요"}
            """;

    private final TeamService teamService = mock(TeamService.class);
    private final UserService userService = mock(UserService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TeamController(teamService, userService, true))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        when(teamService.createTeam(eq(1L), any(TeamCreateRequest.class)))
                .thenReturn(new TeamCreateResponse("1", "솔로몬 개발팀", "점심 메뉴를 함께 정해요",
                        TeamRole.ADMIN, "https://example.com/invite/abc"));
    }

    @Test
    void createTeam_returns201() throws Exception {
        when(userService.identify("tok")).thenReturn(new AnonymousIdentity(1L, "tok", false));

        mockMvc.perform(post("/api/teams").cookie(new Cookie("menu_solomon_session", "tok"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.name").value("솔로몬 개발팀"))
                .andExpect(jsonPath("$.data.description").value("점심 메뉴를 함께 정해요"))
                .andExpect(jsonPath("$.data.myRole").value("ADMIN"))
                .andExpect(jsonPath("$.data.inviteUrl").value("https://example.com/invite/abc"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void createTeam_blankName_returns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/teams").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void createTeam_withoutSessionCookie_canSetAnonymousCookie() throws Exception {
        when(userService.identify(null)).thenReturn(new AnonymousIdentity(1L, "new-token", true));

        mockMvc.perform(post("/api/teams").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("menu_solomon_session=new-token"),
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString("Secure"),
                                org.hamcrest.Matchers.containsString("SameSite=Lax"),
                                org.hamcrest.Matchers.containsString("Path=/"))));
    }
}
