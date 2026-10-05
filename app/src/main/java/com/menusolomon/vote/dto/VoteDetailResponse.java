package com.menusolomon.vote.dto;



public record VoteDetailResponse(VoteSessionResponse session, String creatorNickname, DecisionResponse decision) {}
