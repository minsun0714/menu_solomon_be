package com.menusolomon.vote.dto;

public record VoteConfirmResponse(String voteId, String status, ConfirmedMenuResponse confirmedMenu) {}
