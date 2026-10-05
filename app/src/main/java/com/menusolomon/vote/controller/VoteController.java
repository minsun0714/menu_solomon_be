package com.menusolomon.vote.controller;

import com.menusolomon.common.response.ApiResponse;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.vote.dto.BallotResponse;
import com.menusolomon.vote.dto.BallotSaveRequest;
import com.menusolomon.vote.dto.DecisionRequest;
import com.menusolomon.vote.dto.DecisionResponse;
import com.menusolomon.vote.dto.RecommendationResponse;
import com.menusolomon.vote.dto.VoteCandidateCreateRequest;
import com.menusolomon.vote.dto.VoteCandidateResponse;
import com.menusolomon.vote.dto.VoteCreateRequest;
import com.menusolomon.vote.dto.VoteDetailResponse;
import com.menusolomon.vote.dto.VoteParticipantResponse;
import com.menusolomon.vote.dto.VoteParticipantUpdateRequest;
import com.menusolomon.vote.dto.VoteResultsResponse;
import com.menusolomon.vote.dto.VoteSessionResponse;
import com.menusolomon.vote.dto.VoteSummaryResponse;
import com.menusolomon.vote.dto.VoteUpdateRequest;
import com.menusolomon.vote.service.VoteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams/{teamId}/votes")
public class VoteController {
    private final VoteService service;
    public VoteController(VoteService service) { this.service=service; }

    @PostMapping
    public ResponseEntity<ApiResponse<VoteSessionResponse>> create(@PathVariable Long teamId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token, @Valid @RequestBody VoteCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.of(service.createVote(teamId, token, request.name(), request.closesAt())));
    }

    @GetMapping
    public ApiResponse<List<VoteSummaryResponse>> list(@PathVariable Long teamId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(service.getVotes(teamId, token));
    }

    @GetMapping("/{voteId}")
    public ApiResponse<VoteDetailResponse> detail(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(service.getVoteDetail(teamId, voteId, token));
    }

    @PatchMapping("/{voteId}")
    public ApiResponse<VoteSessionResponse> update(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token, @Valid @RequestBody VoteUpdateRequest request) {
        return ApiResponse.of(service.updateVote(teamId, voteId, token, request));
    }

    @DeleteMapping("/{voteId}")
    public ResponseEntity<Void> delete(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        service.deleteVote(teamId, voteId, token); return ResponseEntity.noContent().build();
    }

    @PostMapping("/{voteId}/close")
    public ApiResponse<VoteSessionResponse> close(@PathVariable Long teamId, @PathVariable Long voteId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(service.closeVote(teamId, voteId, token));
    }

    @PostMapping("/{voteId}/restart")
    public ApiResponse<VoteSessionResponse> restart(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(service.restart(teamId, voteId, token));
    }

    @GetMapping("/{voteId}/participants")
    public ApiResponse<List<VoteParticipantResponse>> participants(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(service.getParticipants(teamId, voteId, token));
    }

    @PutMapping("/{voteId}/participants/{targetTeamMemberId}")
    public ApiResponse<VoteParticipantResponse> participation(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token, @PathVariable Long targetTeamMemberId, @Valid @RequestBody VoteParticipantUpdateRequest request) {
        return ApiResponse.of(service.updateParticipation(teamId, voteId, token, targetTeamMemberId, request.participating()));
    }

    @GetMapping("/{voteId}/candidates")
    public ApiResponse<List<VoteCandidateResponse>> candidates(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(service.getCandidates(teamId, voteId, token));
    }

    @PostMapping("/{voteId}/candidates")
    public ResponseEntity<ApiResponse<VoteCandidateResponse>> addCandidate(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token, @Valid @RequestBody VoteCandidateCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.of(service.addCandidate(teamId, voteId, token, request)));
    }

    @DeleteMapping("/{voteId}/candidates/{candidateId}")
    public ResponseEntity<Void> deleteCandidate(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token, @PathVariable Long candidateId) {
        service.deleteCandidate(teamId, voteId, token, candidateId); return ResponseEntity.noContent().build();
    }

    @GetMapping("/{voteId}/recommendations")
    public ApiResponse<RecommendationResponse> recommend(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token, @RequestParam(defaultValue = "0") @Min(0) int cursor) {
        return ApiResponse.of(service.recommend(teamId, voteId, token, cursor));
    }

    @PutMapping("/{voteId}/ballots/me")
    public ApiResponse<List<BallotResponse>> saveBallots(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token, @Valid @RequestBody BallotSaveRequest request) {
        return ApiResponse.of(service.saveBallots(teamId, voteId, token, request.candidateIds()));
    }

    @DeleteMapping("/{voteId}/ballots/me")
    public ResponseEntity<Void> cancelBallots(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        service.cancelBallots(teamId, voteId, token); return ResponseEntity.noContent().build();
    }

    @GetMapping("/{voteId}/results")
    public ApiResponse<VoteResultsResponse> results(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(service.getResults(teamId, voteId, token));
    }

    @PostMapping("/{voteId}/decision")
    public ResponseEntity<ApiResponse<DecisionResponse>> createDecision(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token, @Valid @RequestBody DecisionRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.of(service.createDecision(teamId, voteId, token, request.restaurantId())));
    }

    @PatchMapping("/{voteId}/decision")
    public ApiResponse<DecisionResponse> updateDecision(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token, @Valid @RequestBody DecisionRequest request) {
        return ApiResponse.of(service.updateDecision(teamId, voteId, token, request.restaurantId()));
    }

    @DeleteMapping("/{voteId}/decision")
    public ResponseEntity<Void> deleteDecision(@PathVariable Long teamId, @PathVariable Long voteId, @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        service.deleteDecision(teamId, voteId, token); return ResponseEntity.noContent().build();
    }
}
