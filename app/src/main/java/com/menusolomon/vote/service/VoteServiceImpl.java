package com.menusolomon.vote.service;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.restaurant.repository.TeamRestaurantRepository;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.user.service.UserService;
import com.menusolomon.vote.domain.LunchVoteSession;
import com.menusolomon.vote.domain.VoteCandidate;
import com.menusolomon.vote.domain.VoteParticipant;
import com.menusolomon.vote.domain.VoteRecord;
import com.menusolomon.vote.domain.ConfirmedMenu;
import com.menusolomon.vote.domain.VoteStatus;
import com.menusolomon.vote.dto.VoteCreateResponse;
import com.menusolomon.vote.dto.VoteSummaryResponse;
import com.menusolomon.vote.dto.VoteDetailResponse;
import com.menusolomon.vote.dto.VoteParticipantResponse;
import com.menusolomon.vote.dto.VoteParticipationResponse;
import com.menusolomon.vote.dto.VoteCandidateResponse;
import com.menusolomon.vote.dto.VoteConfirmResponse;
import com.menusolomon.vote.dto.ConfirmedMenuResponse;
import com.menusolomon.vote.dto.VoteHistoryResponse;
import com.menusolomon.vote.repository.LunchVoteSessionRepository;
import com.menusolomon.vote.repository.VoteCandidateRepository;
import com.menusolomon.vote.repository.VoteParticipantRepository;
import com.menusolomon.vote.repository.VoteRecordRepository;
import com.menusolomon.vote.repository.ConfirmedMenuRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class VoteServiceImpl implements VoteService {
    private final UserService users;
    private final TeamMemberRepository members;
    private final TeamRestaurantRepository restaurants;
    private final LunchVoteSessionRepository sessions;
    private final VoteParticipantRepository participants;
    private final VoteCandidateRepository candidates;
    private final VoteRecordRepository records;
    private final ConfirmedMenuRepository menus;
    private final Clock clock;

    public VoteServiceImpl(UserService users, TeamMemberRepository members, TeamRestaurantRepository restaurants,
            LunchVoteSessionRepository sessions, VoteParticipantRepository participants, VoteCandidateRepository candidates,
            VoteRecordRepository records, ConfirmedMenuRepository menus, Clock clock) {
        this.users = users;
        this.members = members;
        this.restaurants = restaurants;
        this.sessions = sessions;
        this.participants = participants;
        this.candidates = candidates;
        this.records = records;
        this.menus = menus;
        this.clock = clock;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VoteCreateResponse createVote(Long teamId, String token, String title) {
        TeamMember member = requireMember(teamId, token);
        Instant now = Instant.now(clock);
        var session = sessions.save(LunchVoteSession.create(teamId, title, member.getId(), now));
        participants.saveAll(members.findAllByTeamIdAndLeftAtIsNull(teamId).stream()
                .map(active -> VoteParticipant.create(session.getId(), active.getId(), true, now)).toList());
        return VoteCreateResponse.from(session);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoteSummaryResponse> getVotes(Long teamId, String token, VoteStatus status) {
        var member = requireMember(teamId, token);
        return sessions.findSummaries(teamId, member.getId(), status).stream().map(VoteSummaryResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VoteDetailResponse getVoteDetail(Long teamId, Long voteId, String token) {
        var member = requireMember(teamId, token);
        var row = sessions.findSummary(voteId, teamId, member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VOTE_NOT_FOUND));
        return VoteDetailResponse.from(row,
                participants.findParticipants(voteId).stream().map(VoteParticipantResponse::from).toList(),
                candidates.findCandidates(voteId, teamId, member.getId()).stream().map(VoteCandidateResponse::from).toList(),
                menus.findResult(voteId).map(ConfirmedMenuResponse::from).orElse(null));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VoteParticipationResponse updateParticipation(Long teamId, Long voteId, String token, boolean participating) {
        var member = requireMember(teamId, token);
        requireOpenVote(teamId, voteId);
        Instant now = Instant.now(clock);
        var participant = participants.findByLunchVoteSessionIdAndTeamMemberId(voteId, member.getId())
                .orElseGet(() -> participants.save(VoteParticipant.create(voteId, member.getId(), participating, now)));
        participant.changeParticipation(participating, now);
        if (!participating) records.deleteMyVote(voteId, member.getId());
        return new VoteParticipationResponse("member_" + member.getId(), participant.isParticipating());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VoteCandidateResponse addCandidate(Long teamId, Long voteId, String token, Long teamRestaurantId) {
        var member = requireMember(teamId, token);
        requireOpenVote(teamId, voteId);
        var restaurant = restaurants.findByIdAndTeamId(teamRestaurantId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_RESTAURANT_NOT_FOUND));
        if (candidates.existsByLunchVoteSessionIdAndRestaurantId(voteId, restaurant.getRestaurantId())) {
            throw new BusinessException(ErrorCode.VOTE_CANDIDATE_ALREADY_EXISTS);
        }
        VoteCandidate candidate;
        try {
            candidate = candidates.saveAndFlush(VoteCandidate.create(voteId, restaurant.getRestaurantId(), member.getId(), Instant.now(clock)));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.VOTE_CANDIDATE_ALREADY_EXISTS);
        }
        return candidateResponse(teamId, voteId, member.getId(), candidate.getId());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VoteCandidateResponse vote(Long teamId, Long voteId, String token, Long candidateId) {
        var member = requireMember(teamId, token);
        requireOpenVote(teamId, voteId);
        var participant = participants.findByLunchVoteSessionIdAndTeamMemberId(voteId, member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VOTE_PARTICIPATION_REQUIRED));
        participant.requireParticipation();
        requireCandidate(voteId, candidateId);
        Instant now = Instant.now(clock);
        records.findByLunchVoteSessionIdAndTeamMemberId(voteId, member.getId())
                .ifPresentOrElse(record -> record.changeCandidate(candidateId, now),
                        () -> records.save(VoteRecord.create(voteId, member.getId(), candidateId, now)));
        records.flush();
        return candidateResponse(teamId, voteId, member.getId(), candidateId);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VoteConfirmResponse confirm(Long teamId, Long voteId, String token, Long candidateId) {
        var member = requireMember(teamId, token);
        var session = requireOpenVote(teamId, voteId);
        requireCandidate(voteId, candidateId);
        Instant now = Instant.now(clock);
        menus.saveAndFlush(ConfirmedMenu.create(voteId, candidateId, member.getId(), now));
        session.confirm(now);
        var result = menus.findResult(voteId).map(ConfirmedMenuResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        return new VoteConfirmResponse("vote_" + voteId, session.getStatus().name(), result);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoteHistoryResponse> getHistory(Long teamId, String token) {
        requireMember(teamId, token);
        return menus.findHistory(teamId).stream().map(VoteHistoryResponse::from).toList();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteTeamData(Long teamId) {
        menus.deleteAllByTeamId(teamId);
        records.deleteAllByTeamId(teamId);
        participants.deleteAllByTeamId(teamId);
        candidates.deleteAllByTeamId(teamId);
        sessions.deleteAllByTeamId(teamId);
    }

    private TeamMember requireMember(Long teamId, String token) {
        var user = users.findBySessionToken(token).orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        return members.findByTeamIdAndUserId(teamId, user.getId()).filter(TeamMember::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
    }

    private LunchVoteSession requireOpenVote(Long teamId, Long voteId) {
        var session = sessions.findByIdAndTeamIdForUpdate(voteId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOTE_NOT_FOUND));
        session.requireOpen();
        return session;
    }

    private void requireCandidate(Long voteId, Long candidateId) {
        candidates.findByIdAndLunchVoteSessionId(candidateId, voteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOTE_CANDIDATE_NOT_FOUND));
    }

    private VoteCandidateResponse candidateResponse(Long teamId, Long voteId, Long memberId, Long candidateId) {
        return candidates.findCandidate(candidateId, voteId, teamId, memberId).map(VoteCandidateResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOTE_CANDIDATE_NOT_FOUND));
    }
}
