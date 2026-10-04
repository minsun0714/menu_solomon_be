package com.menusolomon.vote.service;

import com.menusolomon.vote.domain.VoteStatus;
import com.menusolomon.vote.dto.VoteCreateResponse;
import com.menusolomon.vote.dto.VoteSummaryResponse;
import com.menusolomon.vote.dto.VoteDetailResponse;
import com.menusolomon.vote.dto.VoteParticipationResponse;
import com.menusolomon.vote.dto.VoteCandidateResponse;
import com.menusolomon.vote.dto.VoteConfirmResponse;
import com.menusolomon.vote.dto.VoteHistoryResponse;
import java.util.List;

public interface VoteService {
    VoteCreateResponse createVote(Long teamId, String token, String title);
    List<VoteSummaryResponse> getVotes(Long teamId, String token, VoteStatus status);
    VoteDetailResponse getVoteDetail(Long teamId, Long voteId, String token);
    VoteParticipationResponse updateParticipation(Long teamId, Long voteId, String token, boolean participating);
    VoteCandidateResponse addCandidate(Long teamId, Long voteId, String token, Long teamRestaurantId);
    VoteCandidateResponse vote(Long teamId, Long voteId, String token, Long candidateId);
    VoteConfirmResponse confirm(Long teamId, Long voteId, String token, Long candidateId);
    List<VoteHistoryResponse> getHistory(Long teamId, String token);
    void deleteTeamData(Long teamId);

}
