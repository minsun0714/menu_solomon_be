package com.menusolomon.vote.controller;

import com.menusolomon.common.response.ApiResponse;
import com.menusolomon.common.web.WebConstants;
import com.menusolomon.vote.domain.VoteStatus;
import com.menusolomon.vote.dto.VoteCreateRequest;
import com.menusolomon.vote.dto.VoteCreateResponse;
import com.menusolomon.vote.dto.VoteSummaryResponse;
import com.menusolomon.vote.dto.VoteDetailResponse;
import com.menusolomon.vote.dto.VoteParticipantUpdateRequest;
import com.menusolomon.vote.dto.VoteParticipationResponse;
import com.menusolomon.vote.dto.VoteCandidateCreateRequest;
import com.menusolomon.vote.dto.VoteCandidateResponse;
import com.menusolomon.vote.dto.VoteRequest;
import com.menusolomon.vote.dto.VoteConfirmRequest;
import com.menusolomon.vote.dto.VoteConfirmResponse;
import com.menusolomon.vote.dto.VoteHistoryResponse;
import com.menusolomon.vote.service.VoteService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/teams/{teamId}/votes")
public class VoteController {
    private final VoteService service;

    public VoteController(VoteService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ApiResponse<VoteCreateResponse>> create(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token,
            @Valid @RequestBody VoteCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.of(service.createVote(teamId, token, request.title())));
    }

    @GetMapping
    public ApiResponse<List<VoteSummaryResponse>> list(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token,
            @RequestParam(required = false) VoteStatus status) {
        return ApiResponse.of(service.getVotes(teamId, token, status));
    }

    @GetMapping("/history")
    public ApiResponse<List<VoteHistoryResponse>> history(@PathVariable Long teamId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(service.getHistory(teamId, token));
    }

    @GetMapping("/{voteId}")
    public ApiResponse<VoteDetailResponse> detail(@PathVariable Long teamId, @PathVariable Long voteId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token) {
        return ApiResponse.of(service.getVoteDetail(teamId, voteId, token));
    }

    @PutMapping("/{voteId}/participants/me")
    public ApiResponse<VoteParticipationResponse> participation(@PathVariable Long teamId, @PathVariable Long voteId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token,
            @Valid @RequestBody VoteParticipantUpdateRequest request) {
        return ApiResponse.of(service.updateParticipation(teamId, voteId, token, request.participating()));
    }

    @PostMapping("/{voteId}/candidates")
    public ResponseEntity<ApiResponse<VoteCandidateResponse>> candidate(@PathVariable Long teamId, @PathVariable Long voteId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token,
            @Valid @RequestBody VoteCandidateCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.of(service.addCandidate(teamId, voteId, token, request.teamRestaurantId())));
    }

    @PutMapping("/{voteId}/vote")
    public ApiResponse<VoteCandidateResponse> vote(@PathVariable Long teamId, @PathVariable Long voteId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token,
            @Valid @RequestBody VoteRequest request) {
        return ApiResponse.of(service.vote(teamId, voteId, token, request.voteCandidateId()));
    }

    @PostMapping("/{voteId}/confirm")
    public ApiResponse<VoteConfirmResponse> confirm(@PathVariable Long teamId, @PathVariable Long voteId,
            @CookieValue(value = WebConstants.SESSION_COOKIE_NAME, required = false) String token,
            @Valid @RequestBody VoteConfirmRequest request) {
        return ApiResponse.of(service.confirm(teamId, voteId, token, request.voteCandidateId()));
    }
}
