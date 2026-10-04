package com.menusolomon.vote.controller;

import static com.menusolomon.vote.fixture.VoteFixture.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.vote.domain.VoteStatus;
import com.menusolomon.vote.dto.*;
import com.menusolomon.vote.service.VoteService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VoteController.class)
class VoteControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean VoteService service;
    private static final String ROOT = "/api/teams/team_1/votes";
    private static final String VOTE = ROOT + "/vote_5";
    private Cookie cookie() { return new Cookie(WebConstants.SESSION_COOKIE_NAME, "token"); }

    @Test
    void createVote_returns201() throws Exception {
        when(service.createVote(1L, "token", "점심")).thenReturn(VoteCreateResponse.from(session()));
        mvc.perform(post(ROOT).cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"점심\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value("vote_5"))
                .andExpect(jsonPath("$.data.teamId").value("team_1"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.createdAt").value(NOW.toString()))
                .andExpect(header().doesNotExist("Set-Cookie"));
        verify(service).createVote(1L, "token", "점심");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":\" \"}"})
    void createVote_blankTitle_returns400ProblemDetail(String body) throws Exception {
        mvc.perform(post(ROOT).cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void createVote_titleOver100_returns400ProblemDetail() throws Exception {
        mvc.perform(post(ROOT).cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + "가".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getVotes_returns200_andBindsStatus() throws Exception {
        when(service.getVotes(1L, "token", VoteStatus.OPEN)).thenReturn(List.of(VoteSummaryResponse.from(summary())));
        mvc.perform(get(ROOT).cookie(cookie()).param("status", "OPEN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].participantCount").value(2))
                .andExpect(jsonPath("$.data[0].candidateCount").value(1))
                .andExpect(jsonPath("$.data[0].myParticipation").value(true))
                .andExpect(jsonPath("$.data[0].myVoteCandidateId").value("candidate_100"));
        verify(service).getVotes(1L, "token", VoteStatus.OPEN);
    }

    @Test
    void getVoteDetail_returns200() throws Exception {
        when(service.getVoteDetail(1L, 5L, "token")).thenReturn(VoteDetailResponse.from(summary(),
                List.of(new VoteParticipantResponse("member_1", "익명", true)),
                List.of(VoteCandidateResponse.from(candidateRow(100L))), null));
        mvc.perform(get(VOTE).cookie(cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("vote_5"))
                .andExpect(jsonPath("$.data.participants[0].nickname").value("익명"))
                .andExpect(jsonPath("$.data.candidates[0].voteCount").value(2))
                .andExpect(jsonPath("$.data.candidates[0].averageRating").value(4.5))
                .andExpect(jsonPath("$.data.candidates[0].isMyVote").value(true));
        verify(service).getVoteDetail(1L, 5L, "token");
    }

    @Test
    void updateParticipation_returns200() throws Exception {
        when(service.updateParticipation(1L, 5L, "token", false)).thenReturn(new VoteParticipationResponse("member_1", false));
        mvc.perform(put(VOTE + "/participants/me").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participating\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.participating").value(false));
        verify(service).updateParticipation(1L, 5L, "token", false);
    }

    @Test
    void addCandidate_returns201() throws Exception {
        when(service.addCandidate(1L, 5L, "token", 10L)).thenReturn(VoteCandidateResponse.from(candidateRow(100L)));
        mvc.perform(post(VOTE + "/candidates").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamRestaurantId\":10}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.candidateId").value("candidate_100"))
                .andExpect(jsonPath("$.data.restaurantId").value("restaurant_3"));
        verify(service).addCandidate(1L, 5L, "token", 10L);
    }

    @Test
    void addCandidate_duplicate_returns409ProblemDetail() throws Exception {
        when(service.addCandidate(1L, 5L, "token", 10L)).thenThrow(new BusinessException(ErrorCode.VOTE_CANDIDATE_ALREADY_EXISTS));
        mvc.perform(post(VOTE + "/candidates").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamRestaurantId\":10}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("VOTE_CANDIDATE_ALREADY_EXISTS"));
    }

    @Test
    void vote_returns200() throws Exception {
        when(service.vote(1L, 5L, "token", 100L)).thenReturn(VoteCandidateResponse.from(candidateRow(100L)));
        mvc.perform(put(VOTE + "/vote").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voteCandidateId\":100}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isMyVote").value(true));
        verify(service).vote(1L, 5L, "token", 100L);
    }

    @Test
    void vote_nonParticipant_returns409ProblemDetail() throws Exception {
        when(service.vote(1L, 5L, "token", 100L)).thenThrow(new BusinessException(ErrorCode.VOTE_PARTICIPATION_REQUIRED));
        mvc.perform(put(VOTE + "/vote").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voteCandidateId\":100}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VOTE_PARTICIPATION_REQUIRED"));
    }

    @Test
    void confirmVote_returns200() throws Exception {
        when(service.confirm(1L, 5L, "token", 100L)).thenReturn(new VoteConfirmResponse("vote_5", "CONFIRMED", ConfirmedMenuResponse.from(result())));
        mvc.perform(post(VOTE + "/confirm").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voteCandidateId\":100}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedMenu.candidateId").value("candidate_100"))
                .andExpect(jsonPath("$.data.confirmedMenu.confirmedAt").value(NOW.toString()));
    }

    @Test
    void confirmAlreadyConfirmed_returns409ProblemDetail() throws Exception {
        when(service.confirm(1L, 5L, "token", 100L)).thenThrow(new BusinessException(ErrorCode.VOTE_ALREADY_CONFIRMED));
        mvc.perform(post(VOTE + "/confirm").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voteCandidateId\":100}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VOTE_ALREADY_CONFIRMED"));
    }

    @Test
    void getHistory_returns200() throws Exception {
        when(service.getHistory(1L, "token")).thenReturn(List.of(new VoteHistoryResponse("vote_5", "점심", NOW, "restaurant_3", "을지다락", 2, 3)));
        mvc.perform(get(ROOT + "/history").cookie(cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].restaurantName").value("을지다락"))
                .andExpect(jsonPath("$.data[0].voteCount").value(2));
        verify(service).getHistory(1L, "token");
    }

    @Test
    void missingSession_returns403WithoutCreatingSession() throws Exception {
        when(service.getVotes(1L, null, null)).thenThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        mvc.perform(get(ROOT)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_TEAM_MEMBER"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void otherTeamVote_returns404ProblemDetail() throws Exception {
        when(service.getVoteDetail(1L, 5L, "token")).thenThrow(new BusinessException(ErrorCode.VOTE_NOT_FOUND));
        mvc.perform(get(VOTE).cookie(cookie())).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VOTE_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/vote", "/participants/me"})
    void missingRequiredMutationField_returns400ProblemDetail(String path) throws Exception {
        mvc.perform(put(VOTE + path).cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }
}
