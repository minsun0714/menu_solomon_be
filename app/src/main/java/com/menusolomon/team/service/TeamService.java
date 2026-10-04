package com.menusolomon.team.service;

import com.menusolomon.team.dto.InvitationPreviewResponse;
import com.menusolomon.team.dto.TeamCreateRequest;
import com.menusolomon.team.dto.TeamCreateResponse;
import com.menusolomon.team.dto.TeamJoinResult;

public interface TeamService {

    TeamCreateResponse createTeam(TeamCreateRequest request, String rawSessionToken);

    InvitationPreviewResponse getInvitationPreview(String inviteToken, String rawSessionToken);

    TeamJoinResult joinTeam(String inviteToken, String rawSessionToken);
}
