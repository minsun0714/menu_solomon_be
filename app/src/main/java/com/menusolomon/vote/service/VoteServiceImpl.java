package com.menusolomon.vote.service;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.restaurant.repository.RestaurantRepository;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.user.service.UserService;
import com.menusolomon.vote.domain.ConfirmedMenu;
import com.menusolomon.vote.domain.HistoryPeriod;
import com.menusolomon.vote.domain.LunchVoteSession;
import com.menusolomon.vote.domain.VoteCandidate;
import com.menusolomon.vote.domain.VoteParticipant;
import com.menusolomon.vote.domain.VoteRecord;
import com.menusolomon.vote.domain.VoteTally;
import com.menusolomon.vote.dto.BallotResponse;
import com.menusolomon.vote.dto.DecisionResponse;
import com.menusolomon.vote.dto.LunchHistoryResponse;
import com.menusolomon.vote.dto.RecommendationItem;
import com.menusolomon.vote.dto.RecommendationResponse;
import com.menusolomon.vote.dto.VoteCandidateCreateRequest;
import com.menusolomon.vote.dto.VoteCandidateResponse;
import com.menusolomon.vote.dto.VoteDetailResponse;
import com.menusolomon.vote.dto.VoteParticipantResponse;
import com.menusolomon.vote.dto.VoteResultItem;
import com.menusolomon.vote.dto.VoteResultsResponse;
import com.menusolomon.vote.dto.VoteSessionResponse;
import com.menusolomon.vote.dto.VoteSummaryResponse;
import com.menusolomon.vote.dto.VoteUpdateRequest;
import com.menusolomon.vote.repository.ConfirmedMenuRepository;
import com.menusolomon.vote.repository.LunchVoteSessionRepository;
import com.menusolomon.vote.repository.VoteCandidateRepository;
import com.menusolomon.vote.repository.VoteCandidateRow;
import com.menusolomon.vote.repository.VoteHistoryRow;
import com.menusolomon.vote.repository.VoteParticipantRepository;
import com.menusolomon.vote.repository.VoteParticipantRow;
import com.menusolomon.vote.repository.VoteRecommendationRepository;
import com.menusolomon.vote.repository.VoteRecordRepository;
import com.menusolomon.vote.repository.VoteSummaryRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class VoteServiceImpl implements VoteService {
    private final UserService users;
    private final TeamMemberRepository members;
    private final RestaurantRepository restaurants;
    private final LunchVoteSessionRepository sessions;
    private final VoteParticipantRepository participants;
    private final VoteCandidateRepository candidates;
    private final VoteRecordRepository ballots;
    private final ConfirmedMenuRepository decisions;
    private final VoteRecommendationRepository recommendations;
    private final Clock clock;

    public VoteServiceImpl(UserService users, TeamMemberRepository members, RestaurantRepository restaurants,
            LunchVoteSessionRepository sessions, VoteParticipantRepository participants, VoteCandidateRepository candidates,
            VoteRecordRepository ballots, ConfirmedMenuRepository decisions, VoteRecommendationRepository recommendations, Clock clock) {
        this.users=users; this.members=members; this.restaurants=restaurants; this.sessions=sessions;
        this.participants=participants; this.candidates=candidates; this.ballots=ballots; this.decisions=decisions;
        this.recommendations=recommendations; this.clock=clock;
    }

    @Override
    public VoteSessionResponse createVote(Long teamId, String token, Instant closesAt) {
        var member = requireMember(teamId, token);
        Instant now = Instant.now(clock);
        var vote = sessions.save(LunchVoteSession.create(teamId, member.getId(), closesAt, now));
        participants.saveAll(members.findAllByTeamIdAndLeftAtIsNull(teamId).stream()
                .map(active -> VoteParticipant.create(vote.getId(), active.getId(), true, now)).toList());
        return VoteSessionResponse.from(vote);
    }

    @Override
    public List<VoteSummaryResponse> getVotes(Long teamId, String token) {
        var member = requireMember(teamId, token);
        Instant now = Instant.now(clock);
        // Each due aggregate requires its own state transition under the same lock as mutations.
        for (var vote : sessions.findDueForTeam(teamId, now)) settle(vote, now);
        sessions.flush();
        var rows = sessions.findSummaries(teamId);
        Map<Long, List<String>> mine = rows.isEmpty() ? Map.of() : ballots
                .findAllBySessionIdInAndTeamMemberIdOrderById(rows.stream().map(VoteSummaryRow::id).toList(), member.getId())
                .stream().collect(Collectors.groupingBy(VoteRecord::getSessionId,
                        Collectors.mapping(ballot -> "candidate_"+ballot.getCandidateId(), Collectors.toList())));
        return rows.stream().map(row -> new VoteSummaryResponse("vote_"+row.id(), "team_"+row.teamId(), row.name(),
                "member_"+row.creatorId(), row.creatorNickname(), row.status(), row.closesAt(), row.createdAt(),
                row.participantCount(), row.candidateCount(), row.ballotCount(), mine.getOrDefault(row.id(), List.of()))).toList();
    }

    @Override
    public VoteDetailResponse getVoteDetail(Long teamId, Long voteId, String token) {
        requireMember(teamId, token);
        var vote = scopedVote(teamId, voteId); settle(vote, Instant.now(clock));
        String creator = sessions.findCreatorNickname(voteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return new VoteDetailResponse(VoteSessionResponse.from(vote), creator,
                decisions.findBySessionId(voteId).map(DecisionResponse::from).orElse(null));
    }

    @Override
    public VoteSessionResponse updateVote(Long teamId, Long voteId, String token, VoteUpdateRequest request) {
        requireMember(teamId, token);
        var vote = scopedVote(teamId, voteId);
        vote.update(request.name(), request.closesAt(), Instant.now(clock));
        return VoteSessionResponse.from(vote);
    }

    @Override
    public void deleteVote(Long teamId, Long voteId, String token) {
        requireMember(teamId, token);
        var vote = scopedVote(teamId, voteId);
        decisions.deleteBySession(voteId); ballots.deleteBySession(voteId);
        participants.deleteBySession(voteId); candidates.deleteBySession(voteId); sessions.delete(vote);
    }

    @Override
    public VoteSessionResponse restart(Long teamId, Long voteId, String token) {
        var member = requireMember(teamId, token);
        var vote = scopedVote(teamId, voteId);
        vote.restart(member.getId(), Instant.now(clock)); ballots.deleteBySession(voteId);
        return VoteSessionResponse.from(vote);
    }

    @Override
    public List<VoteParticipantResponse> getParticipants(Long teamId, Long voteId, String token) {
        requireMember(teamId, token); scopedVote(teamId, voteId);
        return participants.findProfiles(voteId).stream().map(this::participantResponse).toList();
    }

    @Override
    public VoteParticipantResponse updateParticipation(Long teamId, Long voteId, String token, Long targetMemberId, boolean participating) {
        requireMember(teamId, token); openVote(teamId, voteId);
        var target = members.findByIdAndTeamId(targetMemberId, teamId).filter(TeamMember::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER_NOT_FOUND));
        Instant now = Instant.now(clock);
        var participant = participants.findBySessionIdAndTeamMemberId(voteId, target.getId())
                .orElseGet(() -> participants.save(VoteParticipant.create(voteId, target.getId(), participating, now)));
        participant.changeParticipation(participating, now);
        if (!participating) ballots.deleteMyBallots(voteId, target.getId());
        participants.flush();
        return participants.findProfile(voteId, target.getId()).map(this::participantResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER_NOT_FOUND));
    }

    @Override
    public List<VoteCandidateResponse> getCandidates(Long teamId, Long voteId, String token) {
        requireMember(teamId, token); scopedVote(teamId, voteId);
        return candidates.findDetails(voteId, teamId).stream().map(VoteCandidateRow::response).toList();
    }

    @Override
    public VoteCandidateResponse addCandidate(Long teamId, Long voteId, String token, VoteCandidateCreateRequest request) {
        requireMember(teamId, token); openVote(teamId, voteId);
        var restaurant = restaurants.findByKakaoPlaceId(request.kakaoPlaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.KAKAO_PLACE_NOT_FOUND));
        if (candidates.existsBySessionIdAndRestaurantId(voteId, restaurant.getId()))
            throw new BusinessException(ErrorCode.VOTE_CANDIDATE_ALREADY_EXISTS);
        var candidate = candidates.saveAndFlush(VoteCandidate.create(voteId, restaurant.getId(), request.source(), Instant.now(clock)));
        return candidates.findDetail(candidate.getId(), voteId, teamId).map(VoteCandidateRow::response)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOTE_CANDIDATE_NOT_FOUND));
    }

    @Override
    public void deleteCandidate(Long teamId, Long voteId, String token, Long candidateId) {
        requireMember(teamId, token); openVote(teamId, voteId);
        var candidate = candidates.findByIdAndSessionId(candidateId, voteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOTE_CANDIDATE_NOT_FOUND));
        ballots.deleteCandidateBallots(voteId, candidateId); candidates.delete(candidate);
    }

    @Override
    public RecommendationResponse recommend(Long teamId, Long voteId, String token, int cursor) {
        requireMember(teamId, token); scopedVote(teamId, voteId);
        if (cursor < 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        Instant since = Instant.now(clock).atZone(HistoryPeriod.SEOUL).minusDays(7).toInstant();
        var items = recommendations.findRecommendations(teamId, voteId, since, PageRequest.of(cursor, 1)).stream()
                .map(row -> new RecommendationItem(row.restaurant(), row.averageRating()==null ? 0 : row.averageRating(),
                        "참여자 리뷰 평점 반영 · 최근 7일 내 선택 안 함")).toList();
        return new RecommendationResponse(items, items.isEmpty() ? null : cursor+1);
    }

    @Override
    public List<BallotResponse> saveBallots(Long teamId, Long voteId, String token, List<Long> candidateIds) {
        var member = requireMember(teamId, token); openVote(teamId, voteId);
        participants.findBySessionIdAndTeamMemberId(voteId, member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VOTE_PARTICIPATION_REQUIRED)).requireParticipation();
        if (candidateIds == null || candidateIds.isEmpty() || candidateIds.stream().anyMatch(id -> id == null || id <= 0))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        var selected = new LinkedHashSet<>(candidateIds);
        if (candidates.countBySessionIdAndIdIn(voteId, selected) != selected.size())
            throw new BusinessException(ErrorCode.VOTE_CANDIDATE_NOT_FOUND);
        ballots.deleteMyBallots(voteId, member.getId());
        Instant now = Instant.now(clock);
        var saved = ballots.saveAll(selected.stream().map(id -> VoteRecord.create(voteId, member.getId(), id, now)).toList());
        ballots.flush(); return saved.stream().map(BallotResponse::from).toList();
    }

    @Override
    public void cancelBallots(Long teamId, Long voteId, String token) {
        var member = requireMember(teamId, token); openVote(teamId, voteId);
        participants.findBySessionIdAndTeamMemberId(voteId, member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VOTE_PARTICIPATION_REQUIRED)).requireParticipation();
        ballots.deleteMyBallots(voteId, member.getId());
    }

    @Override
    public VoteResultsResponse getResults(Long teamId, Long voteId, String token) {
        requireMember(teamId, token);
        var vote = scopedVote(teamId, voteId); settle(vote, Instant.now(clock));
        var tally = tally(voteId);
        var results = tally.entries().stream().map(entry -> new VoteResultItem("candidate_"+entry.candidateId(),
                entry.votes(), tally.percentage(entry.votes()))).toList();
        return new VoteResultsResponse(results, ballots.findAllBySessionIdOrderById(voteId).stream().map(BallotResponse::from).toList());
    }

    @Override
    public DecisionResponse createDecision(Long teamId, Long voteId, String token, Long restaurantId) {
        var member = requireMember(teamId, token);
        var vote = scopedVote(teamId, voteId); vote.requireCreator(member.getId());
        vote.requireClosed();
        tally(voteId).requireManualCandidate(restaurantId);
        var decision = decisions.saveAndFlush(ConfirmedMenu.manual(voteId, restaurantId, member.getId(), Instant.now(clock)));
        vote.confirm(); return DecisionResponse.from(decision);
    }

    @Override
    public DecisionResponse updateDecision(Long teamId, Long voteId, String token, Long restaurantId) {
        var member = requireMember(teamId, token);
        var vote = scopedVote(teamId, voteId); vote.requireCreator(member.getId());
        var decision = requireDecision(voteId);
        candidates.findBySessionIdAndRestaurantId(voteId, restaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_DECISION_CANDIDATE));
        decision.changeRestaurant(restaurantId, member.getId(), Instant.now(clock));
        return DecisionResponse.from(decision);
    }

    @Override
    public void deleteDecision(Long teamId, Long voteId, String token) {
        var member = requireMember(teamId, token);
        var vote = scopedVote(teamId, voteId); vote.requireCreator(member.getId());
        var decision = requireDecision(voteId); vote.removeDecision(member.getId()); decisions.delete(decision);
    }

    @Override
    public List<LunchHistoryResponse> getHistory(Long teamId, String token, String view, LocalDate date, YearMonth month) {
        requireMember(teamId, token);
        var period = HistoryPeriod.of(view, date, month);
        return decisions.findHistory(teamId, period.from(), period.until()).stream().map(VoteHistoryRow::response).toList();
    }

    @Override
    public void settleExpired(Long voteId) {
        sessions.findForUpdate(voteId).ifPresent(vote -> settle(vote, Instant.now(clock)));
    }

    @Override
    public void deleteTeamData(Long teamId) {
        decisions.deleteAllByTeamId(teamId); ballots.deleteAllByTeamId(teamId); participants.deleteAllByTeamId(teamId);
        candidates.deleteAllByTeamId(teamId); sessions.deleteAllByTeamId(teamId);
    }

    private void settle(LunchVoteSession vote, Instant now) {
        if (!vote.isDue(now)) return;
        var winner = tally(vote.getId()).automaticWinner();
        vote.close(now);
        winner.ifPresent(entry -> {
            decisions.saveAndFlush(ConfirmedMenu.automatic(vote.getId(), entry.restaurantId(), now)); vote.confirm();
        });
    }
    private VoteTally tally(Long voteId) { return new VoteTally(candidates.findTally(voteId), ballots.countVoters(voteId)); }
    private ConfirmedMenu requireDecision(Long voteId) {
        return decisions.findBySessionId(voteId).orElseThrow(() -> new BusinessException(ErrorCode.DECISION_NOT_FOUND));
    }
    private TeamMember requireMember(Long teamId, String token) {
        var user = users.findBySessionToken(token).orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        return members.findByTeamIdAndUserId(teamId, user.getId()).filter(TeamMember::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
    }
    private LunchVoteSession scopedVote(Long teamId, Long voteId) {
        return sessions.findScopedForUpdate(voteId, teamId).orElseThrow(() -> new BusinessException(ErrorCode.VOTE_NOT_FOUND));
    }
    private LunchVoteSession openVote(Long teamId, Long voteId) {
        var vote = scopedVote(teamId, voteId); vote.requireOpen(Instant.now(clock)); return vote;
    }
    private VoteParticipantResponse participantResponse(VoteParticipantRow row) {
        return new VoteParticipantResponse("participant_"+row.id(), "vote_"+row.sessionId(), "member_"+row.memberId(), row.nickname(), row.participating());
    }
}
