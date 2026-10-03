package com.menusolomon.team.controller;

import com.menusolomon.common.config.CurrentUserProvider;
import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.team.domain.Team;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.dto.request.AdminTransferRequest;
import com.menusolomon.team.dto.request.TeamCreateRequest;
import com.menusolomon.team.dto.response.TeamInviteResponse;
import com.menusolomon.team.dto.response.TeamMemberProfileResponse;
import com.menusolomon.team.dto.response.TeamMemberResponse;
import com.menusolomon.team.dto.response.TeamPreviewResponse;
import com.menusolomon.team.dto.response.TeamResponse;
import com.menusolomon.team.service.TeamMemberResult;
import com.menusolomon.team.service.TeamService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {
    private final TeamService teamService;
    private final CurrentUserProvider currentUserProvider;

    public TeamController(TeamService teamService, CurrentUserProvider currentUserProvider) {
        this.teamService = teamService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(@Valid @RequestBody TeamCreateRequest request) {
        Team team = teamService.createTeam(
                request.name(),
                request.description(),
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toTeamResponse(team));
    }

    @GetMapping("/{teamId}")
    public TeamResponse getTeam(@PathVariable Long teamId) {
        return toTeamResponse(teamService.getTeam(teamId));
    }

    @GetMapping("/{teamId}/members")
    public List<TeamMemberProfileResponse> getActiveMembers(@PathVariable Long teamId) {
        return teamService.getActiveMembers(teamId);
    }

    @GetMapping("/invite/{inviteToken}")
    public TeamPreviewResponse getTeamPreviewByInviteToken(@PathVariable String inviteToken) {
        return teamService.getTeamPreviewByInviteToken(inviteToken);
    }

    @PostMapping("/invite/{inviteToken}/join")
    public ResponseEntity<TeamMemberResponse> joinTeam(@PathVariable String inviteToken) {
        TeamMemberResult result = teamService.joinByInviteToken(
                inviteToken,
                currentUserProvider.getCurrentUserId()
        );
        HttpStatus status = result.outcome() == TeamMemberResult.Outcome.CREATED
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(toMemberResponse(result.member()));
    }

    @DeleteMapping("/{teamId}/members/me")
    public ResponseEntity<Void> leaveTeam(@PathVariable Long teamId) {
        teamService.leaveTeam(teamId, currentUserProvider.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{teamId}/admin")
    public ResponseEntity<Void> transferAdmin(
            @PathVariable Long teamId,
            @Valid @RequestBody AdminTransferRequest request
    ) {
        teamService.transferAdmin(
                teamId,
                currentUserProvider.getCurrentUserId(),
                parseId(request.memberId())
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{teamId}/invite")
    public TeamInviteResponse getInviteToken(@PathVariable Long teamId) {
        return new TeamInviteResponse(teamService.getInviteToken(teamId));
    }

    @PostMapping("/{teamId}/invite-token")
    public TeamInviteResponse regenerateInviteToken(@PathVariable Long teamId) {
        return new TeamInviteResponse(teamService.regenerateInviteToken(teamId));
    }

    private TeamResponse toTeamResponse(Team team) {
        return new TeamResponse(team.getId().toString(), team.getName(), team.getDescription());
    }

    private TeamMemberResponse toMemberResponse(TeamMember member) {
        return new TeamMemberResponse(
                member.getId().toString(),
                member.getTeamId().toString(),
                member.getUserId().toString(),
                member.getRole(),
                member.getJoinedAt()
        );
    }

    private Long parseId(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }
}
