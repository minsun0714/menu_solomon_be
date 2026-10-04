package com.menusolomon.team.service;

import com.menusolomon.team.dto.InvitationPreviewResponse;
import com.menusolomon.team.dto.TeamCreateRequest;
import com.menusolomon.team.dto.TeamCreateResponse;
import com.menusolomon.team.dto.TeamDetailResponse;
import com.menusolomon.team.dto.TeamJoinResult;

public interface TeamService {

    /**
     * Returns detail only for an ACTIVE member (leftAt == null) identified by the existing session.
     * memberCount counts ACTIVE members only; myRole is ADMIN or MEMBER.
     * Missing/invalid identity yields SESSION_REQUIRED; non-members and inactive members yield
     * NOT_TEAM_MEMBER; a missing team yields TEAM_NOT_FOUND. Never creates a user or session.
     */
    TeamDetailResponse getTeamDetail(Long teamId, String rawSessionToken);

    TeamCreateResponse createTeam(TeamCreateRequest request, String rawSessionToken);

    InvitationPreviewResponse getInvitationPreview(String inviteToken, String rawSessionToken);

    TeamJoinResult joinTeam(String inviteToken, String rawSessionToken);
}
