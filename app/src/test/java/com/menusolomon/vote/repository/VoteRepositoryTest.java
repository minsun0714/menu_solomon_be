package com.menusolomon.vote.repository;

import static com.menusolomon.vote.fixture.VoteFixture.NOW;
import static org.assertj.core.api.Assertions.*;

import com.menusolomon.restaurant.domain.TeamRestaurant;
import com.menusolomon.restaurant.fixture.RestaurantFixture;
import com.menusolomon.restaurant.repository.RestaurantRepository;
import com.menusolomon.restaurant.repository.TeamRestaurantRepository;
import com.menusolomon.review.domain.Review;
import com.menusolomon.review.repository.ReviewRepository;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.user.domain.User;
import com.menusolomon.user.repository.UserRepository;
import com.menusolomon.vote.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class VoteRepositoryTest {
    @Autowired LunchVoteSessionRepository sessions;
    @Autowired VoteCandidateRepository candidates;
    @Autowired VoteParticipantRepository participants;
    @Autowired VoteRecordRepository records;
    @Autowired ConfirmedMenuRepository menus;
    @Autowired RestaurantRepository restaurants;
    @Autowired TeamRestaurantRepository teamRestaurants;
    @Autowired ReviewRepository reviews;
    @Autowired TeamMemberRepository members;
    @Autowired UserRepository users;
    LunchVoteSession session;
    VoteCandidate candidate;
    Long memberId;
    Long secondMemberId;

    @BeforeEach
    void setUp() {
        var user = users.save(User.create("vote-user", "첫 사용자", NOW));
        var second = users.save(User.create("second-user", "다음 사용자", NOW));
        memberId = members.save(TeamMember.newAdmin(1L, user.getId(), NOW)).getId();
        secondMemberId = members.save(TeamMember.newMember(1L, second.getId(), NOW)).getId();
        var restaurant = restaurants.save(RestaurantFixture.restaurant("123", "을지다락", "양식"));
        var link = teamRestaurants.save(TeamRestaurant.create(1L, restaurant.getId(), memberId, NOW));
        reviews.save(Review.create(link.getId(), memberId, 5, "리뷰", NOW));
        reviews.save(Review.create(link.getId(), secondMemberId, 4, "리뷰", NOW));
        session = sessions.save(LunchVoteSession.create(1L, "점심", memberId, NOW));
        candidate = candidates.saveAndFlush(VoteCandidate.create(session.getId(), restaurant.getId(), memberId, NOW));
    }

    @Test
    void duplicateCandidateForSameVoteAndRestaurant_isRejected() {
        assertThatThrownBy(() -> candidates.saveAndFlush(VoteCandidate.create(session.getId(), candidate.getRestaurantId(), secondMemberId, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateParticipantForSameVoteAndMember_isRejected() {
        participants.saveAndFlush(VoteParticipant.create(session.getId(), memberId, true, NOW));
        assertThatThrownBy(() -> participants.saveAndFlush(VoteParticipant.create(session.getId(), memberId, false, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateVoteRecordForSameVoteAndMember_isRejected() {
        records.saveAndFlush(VoteRecord.create(session.getId(), memberId, candidate.getId(), NOW));
        assertThatThrownBy(() -> records.saveAndFlush(VoteRecord.create(session.getId(), memberId, candidate.getId(), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateConfirmedMenuForSameVote_isRejected() {
        menus.saveAndFlush(ConfirmedMenu.create(session.getId(), candidate.getId(), memberId, NOW));
        assertThatThrownBy(() -> menus.saveAndFlush(ConfirmedMenu.create(session.getId(), candidate.getId(), secondMemberId, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void voteAndCandidateLookups_areScoped() {
        assertThat(sessions.findByIdAndTeamId(session.getId(), 1L)).contains(session);
        assertThat(sessions.findByIdAndTeamId(session.getId(), 2L)).isEmpty();
        assertThat(sessions.findByIdAndTeamIdForUpdate(session.getId(), 2L)).isEmpty();
        assertThat(candidates.findByIdAndLunchVoteSessionId(candidate.getId(), session.getId())).contains(candidate);
        assertThat(candidates.findByIdAndLunchVoteSessionId(candidate.getId(), session.getId() + 1)).isEmpty();
    }

    @Test
    void participantAndRecordLookups_arePerVoteAndMember() {
        var participant = participants.save(VoteParticipant.create(session.getId(), memberId, true, NOW));
        var record = records.saveAndFlush(VoteRecord.create(session.getId(), memberId, candidate.getId(), NOW));
        assertThat(participants.findByLunchVoteSessionIdAndTeamMemberId(session.getId(), memberId)).contains(participant);
        assertThat(participants.findByLunchVoteSessionIdAndTeamMemberId(session.getId(), secondMemberId)).isEmpty();
        assertThat(records.findByLunchVoteSessionIdAndTeamMemberId(session.getId(), memberId)).contains(record);
        assertThat(records.findByLunchVoteSessionIdAndTeamMemberId(session.getId() + 1, memberId)).isEmpty();
        records.deleteMyVote(session.getId(), secondMemberId);
        assertThat(records.findByLunchVoteSessionIdAndTeamMemberId(session.getId(), memberId)).isPresent();
        records.deleteMyVote(session.getId(), memberId);
        assertThat(records.findByLunchVoteSessionIdAndTeamMemberId(session.getId(), memberId)).isEmpty();
    }

    @Test
    void candidateProjection_aggregatesVotesRatingsAndMySelection() {
        records.save(VoteRecord.create(session.getId(), memberId, candidate.getId(), NOW));
        records.saveAndFlush(VoteRecord.create(session.getId(), secondMemberId, candidate.getId(), NOW));
        var row = candidates.findCandidates(session.getId(), 1L, memberId).getFirst();
        assertThat(row.voteCount()).isEqualTo(2);
        assertThat(row.averageRating()).isEqualTo(4.5);
        assertThat(row.isMyVote()).isTrue();
        assertThat(row.name()).isEqualTo("을지다락");
        assertThat(candidates.findCandidates(session.getId(), 2L, memberId)).isEmpty();
    }

    @Test
    void summaryAndParticipants_projectCountsNicknameAndCurrentParticipation() {
        participants.save(VoteParticipant.create(session.getId(), memberId, true, NOW));
        participants.save(VoteParticipant.create(session.getId(), secondMemberId, false, NOW));
        records.saveAndFlush(VoteRecord.create(session.getId(), memberId, candidate.getId(), NOW));
        var summary = sessions.findSummary(session.getId(), 1L, memberId).orElseThrow();
        assertThat(summary.participantCount()).isEqualTo(1);
        assertThat(summary.candidateCount()).isEqualTo(1);
        assertThat(summary.myParticipation()).isTrue();
        assertThat(summary.myVoteCandidateId()).isEqualTo(candidate.getId());
        assertThat(participants.findParticipants(session.getId())).extracting(VoteParticipantRow::nickname)
                .containsExactly("첫 사용자", "다음 사용자");
        assertThat(sessions.findSummary(session.getId(), 2L, memberId)).isEmpty();
    }

    @Test
    void summaries_allowMultipleOpenVotes_andFilterAndOrderByStatus() {
        var newer = sessions.save(LunchVoteSession.create(1L, "새 점심", memberId, NOW.plusSeconds(60)));
        var confirmed = sessions.save(LunchVoteSession.create(1L, "확정", memberId, NOW.plusSeconds(120)));
        confirmed.confirm(NOW.plusSeconds(120));
        sessions.saveAndFlush(LunchVoteSession.create(2L, "다른 팀", memberId, NOW));
        assertThat(sessions.findSummaries(1L, memberId, null)).extracting(VoteSummaryRow::id)
                .containsExactly(newer.getId(), session.getId(), confirmed.getId());
        assertThat(sessions.findSummaries(1L, memberId, VoteStatus.OPEN)).hasSize(2);
        assertThat(sessions.findSummaries(1L, memberId, VoteStatus.CONFIRMED)).hasSize(1);
    }

    @Test
    void confirmedHistory_excludesOpenAndOtherTeams_andOrdersConfirmedAtDescending() {
        participants.save(VoteParticipant.create(session.getId(), memberId, true, NOW));
        records.save(VoteRecord.create(session.getId(), memberId, candidate.getId(), NOW));
        session.confirm(NOW);
        menus.save(ConfirmedMenu.create(session.getId(), candidate.getId(), memberId, NOW));
        var newer = sessions.save(LunchVoteSession.create(1L, "새 투표", memberId, NOW));
        var nextCandidate = candidates.save(VoteCandidate.create(newer.getId(), candidate.getRestaurantId(), memberId, NOW));
        newer.confirm(NOW.plusSeconds(60));
        menus.save(ConfirmedMenu.create(newer.getId(), nextCandidate.getId(), memberId, NOW.plusSeconds(60)));
        sessions.save(LunchVoteSession.create(1L, "미확정", memberId, NOW));
        var other = sessions.save(LunchVoteSession.create(2L, "다른 팀", memberId, NOW));
        var otherCandidate = candidates.save(VoteCandidate.create(other.getId(), candidate.getRestaurantId(), memberId, NOW));
        other.confirm(NOW.plusSeconds(120));
        menus.saveAndFlush(ConfirmedMenu.create(other.getId(), otherCandidate.getId(), memberId, NOW.plusSeconds(120)));
        assertThat(menus.findHistory(1L)).extracting(VoteHistoryRow::voteId).containsExactly(newer.getId(), session.getId());
        assertThat(menus.findHistory(1L).getLast().voteCount()).isEqualTo(1);
        assertThat(menus.findHistory(1L).getLast().participantCount()).isEqualTo(1);
        assertThat(menus.findResult(session.getId()).orElseThrow().name()).isEqualTo("을지다락");
    }
    @Test
    void teamVoteCleanup_preservesOtherTeamsVotesAndSharedRestaurant() {
        participants.save(VoteParticipant.create(session.getId(), memberId, true, NOW));
        records.save(VoteRecord.create(session.getId(), memberId, candidate.getId(), NOW));
        session.confirm(NOW);
        menus.save(ConfirmedMenu.create(session.getId(), candidate.getId(), memberId, NOW));
        Long otherMemberId = members.save(TeamMember.newAdmin(2L,
                members.findById(secondMemberId).orElseThrow().getUserId(), NOW)).getId();
        var other = sessions.save(LunchVoteSession.create(2L, "다른 팀", otherMemberId, NOW));
        var otherCandidate = candidates.save(VoteCandidate.create(other.getId(), candidate.getRestaurantId(), otherMemberId, NOW));
        participants.save(VoteParticipant.create(other.getId(), otherMemberId, true, NOW));
        records.save(VoteRecord.create(other.getId(), otherMemberId, otherCandidate.getId(), NOW));
        other.confirm(NOW);
        menus.saveAndFlush(ConfirmedMenu.create(other.getId(), otherCandidate.getId(), otherMemberId, NOW));
        menus.deleteAllByTeamId(1L);
        records.deleteAllByTeamId(1L);
        participants.deleteAllByTeamId(1L);
        candidates.deleteAllByTeamId(1L);
        sessions.deleteAllByTeamId(1L);
        assertThat(sessions.findByIdAndTeamId(session.getId(), 1L)).isEmpty();
        assertThat(sessions.findByIdAndTeamId(other.getId(), 2L)).isPresent();
        assertThat(menus.findHistory(2L)).singleElement().satisfies(row -> {
            assertThat(row.voteCount()).isEqualTo(1);
            assertThat(row.participantCount()).isEqualTo(1);
        });
        assertThat(candidates.findByIdAndLunchVoteSessionId(otherCandidate.getId(), other.getId())).isPresent();
        assertThat(records.count()).isEqualTo(1);
        assertThat(participants.count()).isEqualTo(1);
        assertThat(candidates.count()).isEqualTo(1);
        assertThat(menus.count()).isEqualTo(1);
        assertThat(restaurants.findByKakaoPlaceId("123")).isPresent();
    }

}
