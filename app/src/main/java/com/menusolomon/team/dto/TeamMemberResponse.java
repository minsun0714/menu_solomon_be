package com.menusolomon.team.dto;

import com.menusolomon.team.repository.TeamMemberRow;
import java.time.Instant;

public record TeamMemberResponse(String teamMemberId, String userId, String nickname, String role,
        Instant joinedAt, boolean isMe) {
    public static TeamMemberResponse from(TeamMemberRow row, Long currentMemberId) {
        return new TeamMemberResponse("member_" + row.teamMemberId(), "user_" + row.userId(), row.nickname(),
                row.role().name(), row.joinedAt(), row.teamMemberId().equals(currentMemberId));
    }
}
