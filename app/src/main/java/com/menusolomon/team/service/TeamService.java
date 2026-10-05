package com.menusolomon.team.service;

import java.util.List;
import com.menusolomon.team.dto.MyTeamResponse;
import com.menusolomon.team.dto.TeamMemberResponse;
import com.menusolomon.team.dto.TeamUpdateRequest;
import com.menusolomon.team.dto.TeamUpdateResponse;
import com.menusolomon.team.dto.InvitationResponse;
import com.menusolomon.team.dto.AdminTransferResponse;
import com.menusolomon.team.dto.OfficeLocationRequest;
import com.menusolomon.team.dto.OfficeLocationResponse;
import com.menusolomon.team.dto.InvitationPreviewResponse;
import com.menusolomon.team.dto.TeamCreateRequest;
import com.menusolomon.team.dto.TeamCreateResult;
import com.menusolomon.team.dto.TeamDetailResponse;
import com.menusolomon.team.dto.TeamJoinResult;

public interface TeamService {

    List<MyTeamResponse> getMyTeams(String rawSessionToken);
    List<TeamMemberResponse> getMembers(Long teamId, String rawSessionToken);
    void deleteTeam(Long teamId, String rawSessionToken);
    void transferAndLeave(Long teamId, String rawSessionToken, Long targetTeamMemberId);


    /**
     * Returns detail only for an ACTIVE member (leftAt == null) identified by the existing session.
     * memberCount counts ACTIVE members only; myRole is ADMIN or MEMBER.
     * Missing/invalid identity, non-members and inactive members yield
     * NOT_TEAM_MEMBER; a missing team yields TEAM_NOT_FOUND. Never creates a user or session.
     */
    TeamDetailResponse getTeamDetail(Long teamId, String rawSessionToken);

    TeamCreateResult createTeam(TeamCreateRequest request, String rawSessionToken);

    InvitationPreviewResponse getInvitationPreview(String inviteToken, String rawSessionToken);

    TeamJoinResult joinTeam(String inviteToken, String rawSessionToken, String nickname);
    TeamUpdateResponse updateTeam(Long teamId, String rawSessionToken, TeamUpdateRequest request);
    InvitationResponse getInvitation(Long teamId, String rawSessionToken);
    InvitationResponse regenerateInvitation(Long teamId, String rawSessionToken);
    AdminTransferResponse transferAdmin(Long teamId, String rawSessionToken, Long targetTeamMemberId);
    void leaveTeam(Long teamId, String rawSessionToken);
    OfficeLocationResponse getOfficeLocation(Long teamId, String rawSessionToken);
    OfficeLocationResponse updateOfficeLocation(Long teamId, String rawSessionToken, OfficeLocationRequest request);

}
