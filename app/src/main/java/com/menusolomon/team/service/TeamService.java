package com.menusolomon.team.service;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.team.domain.Team;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.domain.TeamRole;
import com.menusolomon.team.dto.response.TeamMemberProfileResponse;
import com.menusolomon.team.dto.response.TeamPreviewResponse;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.team.repository.TeamRepository;
import com.menusolomon.user.domain.User;
import com.menusolomon.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final Clock clock;

    public TeamService(
            UserRepository userRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.clock = clock;
    }

    @Transactional
    public Team createTeam(String name, String description, Long creatorUserId) {
        Instant now = Instant.now(clock);
        Team team = teamRepository.save(new Team(name, description, newInviteToken(), now));
        teamMemberRepository.save(new TeamMember(team.getId(), creatorUserId, TeamRole.ADMIN, now));
        return team;
    }

    @Transactional(readOnly = true)
    public Team getTeam(Long teamId) {
        return findTeam(teamId);
    }

    @Transactional(readOnly = true)
    public List<TeamMemberProfileResponse> getActiveMembers(Long teamId) {
        findTeam(teamId);
        return teamMemberRepository.findAllByTeamIdAndLeftAtIsNull(teamId).stream()
                .map(this::toProfileResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamPreviewResponse getTeamPreviewByInviteToken(String inviteToken) {
        Team team = teamRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_TOKEN));
        List<TeamMemberProfileResponse> members = teamMemberRepository
                .findAllByTeamIdAndLeftAtIsNull(team.getId()).stream()
                .map(this::toProfileResponse)
                .toList();
        return new TeamPreviewResponse(
                team.getId().toString(),
                team.getName(),
                team.getDescription(),
                members.size(),
                members
        );
    }

    @Transactional
    public TeamMemberResult joinByInviteToken(String inviteToken, Long userId) {
        Team team = teamRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_TOKEN));
        Instant now = Instant.now(clock);
        return teamMemberRepository.findByTeamIdAndUserId(team.getId(), userId)
                .map(member -> reactivateIfInactive(member, now))
                .orElseGet(() -> new TeamMemberResult(
                        teamMemberRepository.save(new TeamMember(team.getId(), userId, TeamRole.MEMBER, now)),
                        TeamMemberResult.Outcome.CREATED
                ));
    }

    @Transactional
    public void leaveTeam(Long teamId, Long userId) {
        TeamMember member = teamMemberRepository.findByTeamIdAndUserIdAndLeftAtIsNull(teamId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        long activeMembers = teamMemberRepository.countByTeamIdAndLeftAtIsNull(teamId);
        if (member.isAdmin() && activeMembers > 1) {
            throw new BusinessException(ErrorCode.ADMIN_TRANSFER_REQUIRED);
        }
        if (activeMembers == 1) {
            teamRepository.delete(findTeam(teamId));
            return;
        }
        member.leave(Instant.now(clock));
    }

    @Transactional
    public void transferAdmin(Long teamId, Long currentUserId, Long targetMemberId) {
        TeamMember currentAdmin = teamMemberRepository
                .findByTeamIdAndUserIdAndLeftAtIsNull(teamId, currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERMISSION_DENIED));
        if (!currentAdmin.isAdmin()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        TeamMember target = teamMemberRepository.findAllByTeamIdAndLeftAtIsNull(teamId).stream()
                .filter(member -> member.getId().equals(targetMemberId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (target.getId().equals(currentAdmin.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        currentAdmin.demoteToMember();
        target.promoteToAdmin();
    }

    @Transactional(readOnly = true)
    public String getInviteToken(Long teamId) {
        return findTeam(teamId).getInviteToken();
    }

    @Transactional
    public String regenerateInviteToken(Long teamId) {
        Team team = findTeam(teamId);
        team.regenerateInviteToken(newInviteToken(), Instant.now(clock));
        return team.getInviteToken();
    }

    private TeamMemberResult reactivateIfInactive(TeamMember member, Instant now) {
        if (member.isActive()) {
            throw new BusinessException(ErrorCode.ALREADY_TEAM_MEMBER);
        }
        member.rejoin(now);
        return new TeamMemberResult(member, TeamMemberResult.Outcome.REACTIVATED);
    }

    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
    }

    private TeamMemberProfileResponse toProfileResponse(TeamMember member) {
        User user = userRepository.findById(member.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return new TeamMemberProfileResponse(
                member.getId().toString(),
                member.getTeamId().toString(),
                member.getUserId().toString(),
                member.getRole(),
                member.getJoinedAt(),
                user.getNickname(),
                user.getProfileImageUrl()
        );
    }

    private String newInviteToken() {
        return UUID.randomUUID().toString();
    }
}
