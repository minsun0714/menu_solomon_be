package com.menusolomon.team.service;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.team.domain.Team;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.dto.InvitationMemberResponse;
import com.menusolomon.team.dto.InvitationPreviewResponse;
import com.menusolomon.team.dto.InvitationUserResponse;
import com.menusolomon.team.dto.TeamCreateRequest;
import com.menusolomon.team.dto.TeamCreateResponse;
import com.menusolomon.team.dto.TeamCreateResult;
import com.menusolomon.team.dto.TeamDetailResponse;
import com.menusolomon.team.dto.TeamJoinResponse;
import com.menusolomon.team.dto.TeamJoinResult;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.team.repository.TeamRepository;
import com.menusolomon.user.domain.User;
import com.menusolomon.user.repository.UserRepository;
import com.menusolomon.user.service.UserService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamServiceImpl implements TeamService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserService userService;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final Clock clock;
    private final String frontendBaseUrl;

    public TeamServiceImpl(
            UserService userService,
            UserRepository userRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            Clock clock,
            @Value("${app.frontend-base-url:https://example.com}") String frontendBaseUrl
    ) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.clock = clock;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @Override
    @Transactional(readOnly = true)
    public TeamDetailResponse getTeamDetail(Long teamId, String rawSessionToken) {
        User user = userService.findBySessionToken(rawSessionToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, user.getId())
                .filter(TeamMember::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        long memberCount = teamMemberRepository.countByTeamIdAndLeftAtIsNull(teamId);
        return new TeamDetailResponse("team_" + team.getId(), team.getName(),
                team.getDescription(), memberCount, member.getRole().name());
    }

    @Override
    @Transactional
    public TeamCreateResult createTeam(TeamCreateRequest request, String rawSessionToken) {
        var session = userService.getOrCreateSession(rawSessionToken);
        User creator = session.user();
        Instant now = Instant.now(clock);
        Team team = teamRepository.save(Team.create(request.name(), request.description(), newInviteToken(), now));
        teamMemberRepository.save(TeamMember.newAdmin(team.getId(), creator.getId(), now));
        return new TeamCreateResult(new TeamCreateResponse("team_" + team.getId(), team.getName(), team.getDescription(),
                "ADMIN", frontendBaseUrl + "/invite/" + team.getInviteToken()), session.issuedToken());
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationPreviewResponse getInvitationPreview(String inviteToken, String rawSessionToken) {
        Team team = findInvitationTeam(inviteToken);
        List<TeamMember> members = teamMemberRepository.findAllByTeamIdAndLeftAtIsNull(team.getId());
        List<Long> userIds = members.stream().map(TeamMember::getUserId).distinct().toList();
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<InvitationMemberResponse> profiles = members.stream().map(member -> profile(member, users)).toList();
        boolean isAlreadyMember = userService.findBySessionToken(rawSessionToken)
                .map(user -> members.stream().anyMatch(member -> member.getUserId().equals(user.getId())))
                .orElse(false);
        return new InvitationPreviewResponse("team_" + team.getId(), team.getName(), team.getDescription(),
                members.size(), isAlreadyMember, profiles);
    }

    @Override
    @Transactional
    public TeamJoinResult joinTeam(String inviteToken, String rawSessionToken) {
        Team team = findInvitationTeam(inviteToken);
        var session = userService.getOrCreateSession(rawSessionToken);
        User user = session.user();
        Instant now = Instant.now(clock);
        return teamMemberRepository.findByTeamIdAndUserId(team.getId(), user.getId())
                .map(member -> {
                    member.joinIfInactive(now);
                    return new TeamJoinResult(membership(member), false, session.issuedToken());
                })
                .orElseGet(() -> {
                    TeamMember member = teamMemberRepository.save(TeamMember.newMember(team.getId(), user.getId(), now));
                    return new TeamJoinResult(membership(member), true, session.issuedToken());
                });
    }

    private Team findInvitationTeam(String inviteToken) {
        return teamRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
    }

    private InvitationMemberResponse profile(TeamMember member, Map<Long, User> users) {
        User user = users.get(member.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return new InvitationMemberResponse("member_" + member.getId(), member.getRole().name(), member.getJoinedAt(),
                new InvitationUserResponse("user_" + user.getId(), user.getNickname()));
    }

    private TeamJoinResponse membership(TeamMember member) {
        return new TeamJoinResponse("member_" + member.getId(), "team_" + member.getTeamId(),
                "user_" + member.getUserId(), member.getRole().name(), member.getJoinedAt());
    }

    private String newInviteToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
