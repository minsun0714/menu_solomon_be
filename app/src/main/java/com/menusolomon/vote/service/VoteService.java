package com.menusolomon.vote.service;

import com.menusolomon.vote.dto.BallotResponse;
import com.menusolomon.vote.dto.DecisionResponse;
import com.menusolomon.vote.dto.LunchHistoryResponse;
import com.menusolomon.vote.dto.RecommendationResponse;
import com.menusolomon.vote.dto.VoteCandidateCreateRequest;
import com.menusolomon.vote.dto.VoteCandidateResponse;
import com.menusolomon.vote.dto.VoteDetailResponse;
import com.menusolomon.vote.dto.VoteParticipantResponse;
import com.menusolomon.vote.dto.VoteResultsResponse;
import com.menusolomon.vote.dto.VoteSessionResponse;
import com.menusolomon.vote.dto.VoteSummaryResponse;
import com.menusolomon.vote.dto.VoteUpdateRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public interface VoteService {
    VoteSessionResponse createVote(Long teamId, String token, String name, Instant closesAt);
    List<VoteSummaryResponse> getVotes(Long teamId, String token);
    VoteDetailResponse getVoteDetail(Long teamId, Long voteId, String token);
    VoteSessionResponse updateVote(Long teamId, Long voteId, String token, VoteUpdateRequest request);
    void deleteVote(Long teamId, Long voteId, String token);
    VoteSessionResponse closeVote(Long teamId, Long voteId, String token);
    VoteSessionResponse restart(Long teamId, Long voteId, String token);
    List<VoteParticipantResponse> getParticipants(Long teamId, Long voteId, String token);
    VoteParticipantResponse updateParticipation(Long teamId, Long voteId, String token, Long targetMemberId, boolean participating);
    List<VoteCandidateResponse> getCandidates(Long teamId, Long voteId, String token);
    VoteCandidateResponse addCandidate(Long teamId, Long voteId, String token, VoteCandidateCreateRequest request);
    void deleteCandidate(Long teamId, Long voteId, String token, Long candidateId);
    RecommendationResponse recommend(Long teamId, Long voteId, String token, int cursor);
    List<BallotResponse> saveBallots(Long teamId, Long voteId, String token, List<Long> candidateIds);
    void cancelBallots(Long teamId, Long voteId, String token);
    VoteResultsResponse getResults(Long teamId, Long voteId, String token);
    DecisionResponse createDecision(Long teamId, Long voteId, String token, Long restaurantId);
    DecisionResponse updateDecision(Long teamId, Long voteId, String token, Long restaurantId);
    void deleteDecision(Long teamId, Long voteId, String token);
    List<LunchHistoryResponse> getHistory(Long teamId, String token, String view, LocalDate date, YearMonth month);
    void settleExpired(Long voteId);
    void deleteTeamData(Long teamId);
}
