package com.menusolomon.vote.controller;

import static com.menusolomon.vote.fixture.VoteFixture.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.menusolomon.common.exception.*;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.vote.domain.*;
import com.menusolomon.vote.dto.*;
import com.menusolomon.vote.service.VoteService;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({VoteController.class,LunchHistoryController.class})
class VoteControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean VoteService service;
    private static final String ROOT="/api/teams/team_1/votes";
    private static final String VOTE=ROOT+"/vote_5";
    private Cookie cookie() { return new Cookie(WebConstants.SESSION_COOKIE_NAME,"token"); }
    private DecisionResponse decision() { return new DecisionResponse("decision_1","vote_5","restaurant_3","member_1",ConfirmationType.MANUAL,NOW); }

    @Test void create_returns201WithNullableNameAndDeadline() throws Exception {
        when(service.createVote(1L,"token",DEADLINE)).thenReturn(response());
        mvc.perform(post(ROOT).cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content("{\"closesAt\":\""+DEADLINE+"\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value("vote_5"))
                .andExpect(jsonPath("$.data.teamId").value("team_1"))
                .andExpect(jsonPath("$.data.createdByTeamMemberId").value("member_1"))
                .andExpect(jsonPath("$.data.name").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.closesAt").value(DEADLINE.toString()));
    }
    @Test void create_missingDeadline_returns400ProblemDetail() throws Exception {
        mvc.perform(post(ROOT).cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR")); verifyNoInteractions(service);
    }
    @Test void list_returnsCountsAndAllMySelectedIds() throws Exception {
        when(service.getVotes(1L,"token")).thenReturn(List.of(new VoteSummaryResponse("vote_5","team_1",null,"member_1","익명",VoteStatus.OPEN,DEADLINE,NOW,2,3,1,List.of("candidate_100","candidate_200"))));
        mvc.perform(get(ROOT).cookie(cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].creatorNickname").value("익명"))
                .andExpect(jsonPath("$.data[0].ballotCount").value(1))
                .andExpect(jsonPath("$.data[0].myBallotCandidateIds[1]").value("candidate_200"));
    }
    @Test void detail_returnsSessionCreatorAndDecision() throws Exception {
        when(service.getVoteDetail(1L,5L,"token")).thenReturn(new VoteDetailResponse(response(),"익명",decision()));
        mvc.perform(get(VOTE).cookie(cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.id").value("vote_5"))
                .andExpect(jsonPath("$.data.creatorNickname").value("익명"))
                .andExpect(jsonPath("$.data.decision.confirmationType").value("MANUAL"));
    }
    @Test void patch_bindsPartialNameOnly() throws Exception {
        when(service.updateVote(eq(1L),eq(5L),eq("token"),any())).thenReturn(response());
        mvc.perform(patch(VOTE).cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"금요일 점심\"}"))
                .andExpect(status().isOk());
        verify(service).updateVote(1L,5L,"token",new VoteUpdateRequest("금요일 점심",null));
    }
    @Test void patch_bindsDeadlineOnly() throws Exception {
        when(service.updateVote(eq(1L),eq(5L),eq("token"),any())).thenReturn(response());
        mvc.perform(patch(VOTE).cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content("{\"closesAt\":\""+DEADLINE+"\"}"))
                .andExpect(status().isOk());
        verify(service).updateVote(1L,5L,"token",new VoteUpdateRequest(null,DEADLINE));
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"name\":null}","{\"closesAt\":null}","{\"name\":12}","{\"name\":\" \"}","{\"name\":\"12345678901234567890123456789012345678901\"}","{\"closesAt\":\"invalid\"}"})
    void patch_invalidShape_returns400(String body) throws Exception {
        mvc.perform(patch(VOTE).cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR")); verifyNoInteractions(service);
    }
    @Test void delete_returns204() throws Exception {
        mvc.perform(delete(VOTE).cookie(cookie())).andExpect(status().isNoContent()); verify(service).deleteVote(1L,5L,"token");
    }
    @Test void restart_returnsSession() throws Exception {
        when(service.restart(1L,5L,"token")).thenReturn(response());
        mvc.perform(post(VOTE+"/restart").cookie(cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.data.closesAt").value(DEADLINE.toString()));
    }
    @Test void participants_returnsParticipantContract() throws Exception {
        when(service.getParticipants(1L,5L,"token")).thenReturn(List.of(new VoteParticipantResponse("participant_7","vote_5","member_1","익명",true)));
        mvc.perform(get(VOTE+"/participants").cookie(cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("participant_7"))
                .andExpect(jsonPath("$.data[0].sessionId").value("vote_5"));
    }
    @Test void participation_bindsOtherMemberAndReturnsNickname() throws Exception {
        when(service.updateParticipation(1L,5L,"token",2L,false)).thenReturn(new VoteParticipantResponse("participant_7","vote_5","member_2","팀원",false));
        mvc.perform(put(VOTE+"/participants/member_2").cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content("{\"participating\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("팀원"));
        verify(service).updateParticipation(1L,5L,"token",2L,false);
    }
    @Test void addCandidate_bindsKakaoPlaceAndSource() throws Exception {
        when(service.addCandidate(eq(1L),eq(5L),eq("token"),any())).thenReturn(candidateRow(100L).response());
        mvc.perform(post(VOTE+"/candidates").cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content("{\"kakaoPlaceId\":\"123\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value("candidate_100"))
                .andExpect(jsonPath("$.data.restaurant.kakaoPlaceId").value("123"))
                .andExpect(jsonPath("$.data.averageRating").value(4.5));
        verify(service).addCandidate(1L,5L,"token",new VoteCandidateCreateRequest("123",CandidateSource.MANUAL));
    }
    @Test void candidates_returnsCandidateArray() throws Exception {
        when(service.getCandidates(1L,5L,"token")).thenReturn(List.of(candidateRow(100L).response()));
        mvc.perform(get(VOTE+"/candidates").cookie(cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].source").value("MANUAL"));
    }
    @Test void deleteCandidate_returns204() throws Exception {
        mvc.perform(delete(VOTE+"/candidates/candidate_100").cookie(cookie())).andExpect(status().isNoContent()); verify(service).deleteCandidate(1L,5L,"token",100L);
    }
    @Test void recommend_bindsCursorAndReturnsOneRestaurant() throws Exception {
        when(service.recommend(1L,5L,"token",2)).thenReturn(new RecommendationResponse(List.of(new RecommendationItem(candidateRow(100L).response().restaurant(),4.5,"추천")),3));
        mvc.perform(get(VOTE+"/recommendations").cookie(cookie()).param("cursor","2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].restaurant.id").value("restaurant_3"))
                .andExpect(jsonPath("$.data.nextCursor").value(3));
    }
    @Test void recommend_negativeCursor_returns400() throws Exception {
        mvc.perform(get(VOTE+"/recommendations").cookie(cookie()).param("cursor","-1")).andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void saveBallots_bindsMultipleNumericCandidateIds() throws Exception {
        when(service.saveBallots(1L,5L,"token",List.of(100L,200L))).thenReturn(List.of(ballot()));
        mvc.perform(put(VOTE+"/ballots/me").cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content("{\"candidateIds\":[100,200]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value("ballot_1"))
                .andExpect(jsonPath("$.data[0].candidateId").value("candidate_100"));
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"candidateIds\":[]}","{\"candidateIds\":[0]}","{\"candidateIds\":[null]}"})
    void saveBallots_invalidArray_returns400(String body) throws Exception {
        mvc.perform(put(VOTE+"/ballots/me").cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR")); verifyNoInteractions(service);
    }
    @Test void cancelBallots_returns204() throws Exception {
        mvc.perform(delete(VOTE+"/ballots/me").cookie(cookie())).andExpect(status().isNoContent()); verify(service).cancelBallots(1L,5L,"token");
    }
    @Test void results_returnsPercentagesAndBallots() throws Exception {
        when(service.getResults(1L,5L,"token")).thenReturn(new VoteResultsResponse(List.of(new VoteResultItem("candidate_100",3,75)),List.of(ballot())));
        mvc.perform(get(VOTE+"/results").cookie(cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].percentage").value(75))
                .andExpect(jsonPath("$.data.ballots[0].sessionId").value("vote_5"));
    }
    @Test void decision_createReturns201_editReturns200_deleteReturns204() throws Exception {
        when(service.createDecision(1L,5L,"token",3L)).thenReturn(decision());
        when(service.updateDecision(1L,5L,"token",3L)).thenReturn(decision());
        mvc.perform(post(VOTE+"/decision").cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content("{\"restaurantId\":3}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value("decision_1"));
        mvc.perform(patch(VOTE+"/decision").cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content("{\"restaurantId\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.confirmedByTeamMemberId").value("member_1"));
        mvc.perform(delete(VOTE+"/decision").cookie(cookie())).andExpect(status().isNoContent()); verify(service).deleteDecision(1L,5L,"token");
    }
    @Test void creatorOnlyAction_returns403ProblemDetail() throws Exception {
        when(service.restart(1L,5L,"token")).thenThrow(new BusinessException(ErrorCode.VOTE_CREATOR_REQUIRED));
        mvc.perform(post(VOTE+"/restart").cookie(cookie())).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(jsonPath("$.code").value("VOTE_CREATOR_REQUIRED"));
    }
    @Test void closedVoteMutation_returns409ProblemDetail() throws Exception {
        when(service.saveBallots(eq(1L),eq(5L),eq("token"),any())).thenThrow(new BusinessException(ErrorCode.VOTE_NOT_OPEN));
        mvc.perform(put(VOTE+"/ballots/me").cookie(cookie()).contentType(MediaType.APPLICATION_JSON).content("{\"candidateIds\":[100]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VOTE_NOT_OPEN"));
    }
    @Test void nonMemberRead_returns403WithoutCreatingCookie() throws Exception {
        when(service.getVotes(1L,null)).thenThrow(new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        mvc.perform(get(ROOT)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_TEAM_MEMBER"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
    @Test void history_bindsSeoulWeekAndMonthParameters() throws Exception {
        mvc.perform(get("/api/teams/team_1/lunch-history").cookie(cookie()).param("view","WEEK").param("date","2026-10-04"))
                .andExpect(status().isOk());
        verify(service).getHistory(1L,"token","WEEK",LocalDate.of(2026,10,4),null);
        mvc.perform(get("/api/teams/1/lunch-history").cookie(cookie()).param("view","MONTH").param("month","2026-10"))
                .andExpect(status().isOk());
        verify(service).getHistory(1L,"token","MONTH",null,YearMonth.of(2026,10));
    }
}
