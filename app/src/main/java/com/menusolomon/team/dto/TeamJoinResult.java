package com.menusolomon.team.dto;

public record TeamJoinResult(TeamJoinResponse membership, boolean created, String issuedToken) {

    public TeamJoinResult(TeamJoinResponse membership, boolean created) {
        this(membership, created, null);
    }
}
