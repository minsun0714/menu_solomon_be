package com.menusolomon.vote.dto;

import com.menusolomon.vote.repository.VoteCandidateRow;
public record VoteCandidateResponse(String candidateId, String restaurantId, String name, String category,
        String address, Double averageRating, long voteCount, boolean isMyVote) {
    public static VoteCandidateResponse from(VoteCandidateRow row) {
        return new VoteCandidateResponse("candidate_" + row.id(), "restaurant_" + row.restaurantId(), row.name(),
                row.category(), row.address(), row.averageRating(), row.voteCount(), row.isMyVote());
    }
}
