package com.menusolomon.vote.repository;



public record VoteParticipantRow(Long id, Long sessionId, Long memberId, String nickname, boolean participating) {}
