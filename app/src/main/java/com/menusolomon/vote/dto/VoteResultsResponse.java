package com.menusolomon.vote.dto;

import java.util.List;

public record VoteResultsResponse(List<VoteResultItem> results, List<BallotResponse> ballots) {}
