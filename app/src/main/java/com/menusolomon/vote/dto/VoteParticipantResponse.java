package com.menusolomon.vote.dto;



public record VoteParticipantResponse(String id, String sessionId, String teamMemberId, String nickname, boolean participating) {}
