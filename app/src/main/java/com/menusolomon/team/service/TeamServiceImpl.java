package com.menusolomon.team.service;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.team.domain.Team;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.dto.TeamUpdateRequest;
import com.menusolomon.team.dto.TeamUpdateResponse;
import com.menusolomon.team.dto.InvitationResponse;
import com.menusolomon.team.dto.AdminTransferResponse;
import com.menusolomon.team.dto.OfficeLocationRequest;
import com.menusolomon.team.dto.OfficeLocationResponse;
import com.menusolomon.review.repository.ReviewRepository;
import com.menusolomon.restaurant.repository.TeamRestaurantRepository;
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
    private final ReviewRepository reviewRepository;
    private final TeamRestaurantRepository teamRestaurantRepository;
    private final Clock clock;
    private final String frontendBaseUrl;

    public TeamServiceImpl(
            UserService userService,
            UserRepository userRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            ReviewRepository reviewRepository,
            TeamRestaurantRepository teamRestaurantRepository,
            Clock clock,
            @Value("${app.frontend-base-url:https://example.com}") String frontendBaseUrl
    ) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.reviewRepository = reviewRepository;
        this.teamRestaurantRepository = teamRestaurantRepository;
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

    @Override
    @Transactional
    public TeamUpdateResponse updateTeam(Long teamId, String rawSessionToken, TeamUpdateRequest request) {
        requireActiveMember(teamId, rawSessionToken).requireAdmin();
        Team team = requireTeam(teamId);
        team.changeInfo(request.name(), request.description(), Instant.now(clock));
        return TeamUpdateResponse.from(team);
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationResponse getInvitation(Long teamId, String rawSessionToken) {
        requireActiveMember(teamId, rawSessionToken).requireAdmin();
        return invitation(requireTeam(teamId));
    }

    @Override
    @Transactional
    public InvitationResponse regenerateInvitation(Long teamId, String rawSessionToken) {
        requireActiveMember(teamId, rawSessionToken).requireAdmin();
        Team team = requireTeam(teamId);
        team.changeInviteToken(newInviteToken(), Instant.now(clock));
        return invitation(team);
    }

    @Override
    @Transactional
    public AdminTransferResponse transferAdmin(Long teamId, String rawSessionToken, Long targetTeamMemberId) {
        TeamMember current = requireActiveMember(teamId, rawSessionToken);
        current.requireAdmin();
        TeamMember target = teamMemberRepository.findByIdAndTeamId(targetTeamMemberId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        current.transferAdminTo(target);
        return new AdminTransferResponse("member_" + target.getId());
    }

    @Override
    @Transactional
    public void leaveTeam(Long teamId, String rawSessionToken) {
        TeamMember member = requireActiveMember(teamId, rawSessionToken);
        long activeCount = teamMemberRepository.countByTeamIdAndLeftAtIsNull(teamId);
        if (member.shouldDeleteTeamOnLeave(activeCount)) {
            Team team = requireTeam(teamId);
            reviewRepository.deleteAllByTeamId(teamId);
            teamRestaurantRepository.deleteAllByTeamId(teamId);
            teamMemberRepository.deleteAllByTeamId(teamId);
            teamRepository.delete(team);
        } else {
            member.leave(Instant.now(clock));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OfficeLocationResponse getOfficeLocation(Long teamId, String rawSessionToken) {
        requireActiveMember(teamId, rawSessionToken);
        return OfficeLocationResponse.from(requireTeam(teamId));
    }

    @Override
    @Transactional
    public OfficeLocationResponse updateOfficeLocation(Long teamId, String rawSessionToken, OfficeLocationRequest request) {
        requireActiveMember(teamId, rawSessionToken);
        Team team = requireTeam(teamId);
        team.changeOfficeLocation(request.kakaoPlaceId(), request.name(), request.address(),
                request.latitude(), request.longitude(), Instant.now(clock));
        return OfficeLocationResponse.from(team);
    }

    private TeamMember requireActiveMember(Long teamId, String rawSessionToken) {
        User user = userService.findBySessionToken(rawSessionToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        return teamMemberRepository.findByTeamIdAndUserId(teamId, user.getId())
                .filter(TeamMember::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
    }

    private Team requireTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
    }

    private InvitationResponse invitation(Team team) {
        return new InvitationResponse(frontendBaseUrl + "/invite/" + team.getInviteToken());
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
