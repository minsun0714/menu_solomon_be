package com.menusolomon.vote.dto;

import com.menusolomon.vote.repository.VoteParticipantRow;
public record VoteParticipantResponse(String teamMemberId, String nickname, boolean participating) {
    public static VoteParticipantResponse from(VoteParticipantRow row) {
        return new VoteParticipantResponse("member_" + row.teamMemberId(), row.nickname(), row.participating());
    }
}
