package com.menusolomon.vote.repository;

public record VoteCandidateRow(Long id, Long restaurantId, String name, String category,
        String address, Double averageRating, Long voteCount, Boolean isMyVote) {}
