package com.menusolomon.vote.repository;

import static com.menusolomon.vote.fixture.VoteFixture.*;
import static org.assertj.core.api.Assertions.*;

import com.menusolomon.restaurant.domain.*;
import com.menusolomon.restaurant.repository.*;
import com.menusolomon.review.domain.Review;
import com.menusolomon.review.repository.ReviewRepository;
import com.menusolomon.team.domain.*;
import com.menusolomon.team.repository.*;
import com.menusolomon.user.domain.User;
import com.menusolomon.user.repository.UserRepository;
import com.menusolomon.vote.domain.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class VoteRepositoryTest {
    @Autowired LunchVoteSessionRepository sessions;
    @Autowired VoteCandidateRepository candidates;
    @Autowired VoteParticipantRepository participants;
    @Autowired VoteRecordRepository ballots;
    @Autowired ConfirmedMenuRepository decisions;
    @Autowired VoteRecommendationRepository recommendations;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired UserRepository users;
    @Autowired RestaurantRepository restaurants;
    @Autowired TeamRestaurantRepository links;
    @Autowired ReviewRepository reviews;
    LunchVoteSession vote;
    Team team;
    TeamMember first;
    TeamMember second;
    Restaurant restaurant;
    Restaurant other;
    VoteCandidate candidate;
    VoteCandidate another;

    @BeforeEach void seed() {
        var u1=users.save(User.create("first-hash","첫 사용자",NOW));
        var u2=users.save(User.create("second-hash","둘째 사용자",NOW));
        team=teams.save(Team.create("팀","","token",NOW));
        first=members.save(TeamMember.newAdmin(team.getId(),u1.getId(),NOW));
        second=members.save(TeamMember.newMember(team.getId(),u2.getId(),NOW));
        restaurant=restaurants.save(Restaurant.create("123","식당","서울",BigDecimal.ONE,BigDecimal.TEN,"한식","url",NOW));
        other=restaurants.save(Restaurant.create("456","다른 식당","서울",BigDecimal.ONE,BigDecimal.TEN,"양식","url",NOW));
        vote=sessions.save(LunchVoteSession.create(team.getId(),first.getId(),DEADLINE,NOW));
        candidate=candidates.save(VoteCandidate.create(vote.getId(),restaurant.getId(),CandidateSource.MANUAL,NOW));
        another=candidates.save(VoteCandidate.create(vote.getId(),other.getId(),CandidateSource.RECOMMENDED,NOW));
    }
    @Test void candidate_duplicateRestaurantInSameSession_isRejected() {
        assertThatThrownBy(() -> candidates.saveAndFlush(VoteCandidate.create(vote.getId(),restaurant.getId(),CandidateSource.MANUAL,NOW))).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void participant_duplicateSessionMember_isRejected() {
        participants.saveAndFlush(VoteParticipant.create(vote.getId(),first.getId(),true,NOW));
        assertThatThrownBy(() -> participants.saveAndFlush(VoteParticipant.create(vote.getId(),first.getId(),false,NOW))).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void ballots_allowMultipleCandidatesButRejectDuplicateCandidateMember() {
        ballots.save(VoteRecord.create(vote.getId(),first.getId(),candidate.getId(),NOW));
        ballots.saveAndFlush(VoteRecord.create(vote.getId(),first.getId(),another.getId(),NOW));
        assertThat(ballots.countVoters(vote.getId())).isEqualTo(1);
        assertThatThrownBy(() -> ballots.saveAndFlush(VoteRecord.create(vote.getId(),first.getId(),candidate.getId(),NOW))).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void decision_onePerSession_isRequired() {
        decisions.saveAndFlush(ConfirmedMenu.automatic(vote.getId(),restaurant.getId(),NOW));
        assertThatThrownBy(() -> decisions.saveAndFlush(ConfirmedMenu.manual(vote.getId(),other.getId(),first.getId(),NOW))).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void scopedLookup_preventsOtherTeamAndOtherVoteAccess() {
        assertThat(sessions.findScopedForUpdate(vote.getId(),team.getId())).isPresent();
        assertThat(sessions.findScopedForUpdate(vote.getId(),team.getId()+100)).isEmpty();
        assertThat(candidates.findByIdAndSessionId(candidate.getId(),vote.getId()+100)).isEmpty();
    }
    @Test void tally_countsSelectionsAndDistinctVotersSeparately() {
        ballots.save(VoteRecord.create(vote.getId(),first.getId(),candidate.getId(),NOW));
        ballots.save(VoteRecord.create(vote.getId(),first.getId(),another.getId(),NOW));
        ballots.saveAndFlush(VoteRecord.create(vote.getId(),second.getId(),candidate.getId(),NOW));
        assertThat(candidates.findTally(vote.getId())).extracting(VoteTally.Entry::votes).containsExactly(2L,1L);
        assertThat(ballots.countVoters(vote.getId())).isEqualTo(2);
    }
    @Test void participantProfiles_includeNicknameAndSessionScope() {
        participants.saveAndFlush(VoteParticipant.create(vote.getId(),second.getId(),false,NOW));
        var row=participants.findProfiles(vote.getId()).getFirst();
        assertThat(row.nickname()).isEqualTo("둘째 사용자"); assertThat(row.participating()).isFalse();
        assertThat(participants.findProfile(vote.getId(),first.getId())).isEmpty();
    }
    @Test void candidateProjection_keepsSearchedRestaurantWithoutTeamLinkAndDefaultsRatingToZero() {
        var response=candidates.findDetails(vote.getId(),team.getId()).getFirst().response();
        assertThat(response.restaurant().kakaoPlaceId()).isEqualTo("123"); assertThat(response.averageRating()).isZero();
        assertThat(response.source()).isEqualTo(CandidateSource.MANUAL);
    }
    @Test void candidateProjection_usesCurrentTeamReviewsOnly() {
        var link=links.save(TeamRestaurant.create(team.getId(),restaurant.getId(),first.getId(),NOW));
        reviews.save(Review.create(link.getId(),first.getId(),5,"좋아요",NOW));
        reviews.saveAndFlush(Review.create(link.getId(),second.getId(),3,"괜찮아요",NOW));
        assertThat(candidates.findDetail(candidate.getId(),vote.getId(),team.getId()).orElseThrow().response().averageRating()).isEqualTo(4);
    }
    @Test void summaries_includeCreatorAndDistinctBallotCountAndNewestFirst() {
        var newer=sessions.save(LunchVoteSession.create(team.getId(),first.getId(),DEADLINE,NOW.plusSeconds(1)));
        ballots.save(VoteRecord.create(vote.getId(),first.getId(),candidate.getId(),NOW));
        ballots.saveAndFlush(VoteRecord.create(vote.getId(),first.getId(),another.getId(),NOW));
        participants.saveAndFlush(VoteParticipant.create(vote.getId(),first.getId(),true,NOW));
        var rows=sessions.findSummaries(team.getId());
        assertThat(rows).extracting(VoteSummaryRow::id).containsExactly(newer.getId(),vote.getId());
        assertThat(rows.getLast().ballotCount()).isEqualTo(1); assertThat(rows.getLast().creatorNickname()).isEqualTo("첫 사용자");
        assertThat(rows.getLast().participantCount()).isEqualTo(1); assertThat(rows.getLast().candidateCount()).isEqualTo(2);
    }
    @Test void history_projectsAutomaticNullNicknameAndHalfOpenRange() {
        vote.close(DEADLINE); vote.confirm();
        decisions.saveAndFlush(ConfirmedMenu.automatic(vote.getId(),restaurant.getId(),DEADLINE));
        assertThat(decisions.findHistory(team.getId(),DEADLINE,DEADLINE.plusSeconds(1))).hasSize(1);
        assertThat(decisions.findHistory(team.getId(),DEADLINE,DEADLINE.plusSeconds(1)).getFirst().nickname()).isNull();
        assertThat(decisions.findHistory(team.getId(),NOW,DEADLINE)).isEmpty();
        assertThat(decisions.findHistory(team.getId()+100,NOW,DEADLINE.plusSeconds(1))).isEmpty();
    }
    @Test void recommendations_excludeCandidatesRecentDecisionsAndAbsentReviewsAndUseCursor() {
        var target=restaurants.save(Restaurant.create("789","추천","서울",BigDecimal.ONE,BigDecimal.TEN,"한식","url",NOW));
        var link=links.save(TeamRestaurant.create(team.getId(),target.getId(),first.getId(),NOW));
        reviews.save(Review.create(link.getId(),first.getId(),2,"참여자",NOW));
        reviews.save(Review.create(link.getId(),second.getId(),5,"불참자",NOW));
        participants.saveAndFlush(VoteParticipant.create(vote.getId(),second.getId(),false,NOW));
        links.saveAndFlush(TeamRestaurant.create(team.getId(),restaurant.getId(),first.getId(),NOW));
        var result=recommendations.findRecommendations(team.getId(),vote.getId(),NOW.minusSeconds(7*86400),PageRequest.of(0,1));
        assertThat(result).hasSize(1); assertThat(result.getFirst().id()).isEqualTo(target.getId()); assertThat(result.getFirst().averageRating()).isEqualTo(2);
        assertThat(recommendations.findRecommendations(team.getId(),vote.getId(),NOW.minusSeconds(7*86400),PageRequest.of(1,1))).isEmpty();
        var previous=sessions.save(LunchVoteSession.create(team.getId(),first.getId(),NOW.plusSeconds(1),NOW));
        previous.close(NOW.plusSeconds(1)); previous.confirm();
        decisions.saveAndFlush(ConfirmedMenu.automatic(previous.getId(),target.getId(),NOW));
        assertThat(recommendations.findRecommendations(team.getId(),vote.getId(),NOW.minusSeconds(7*86400),PageRequest.of(0,1))).isEmpty();
    }
    @Test void bulkDelete_doesNotRemoveOtherSessionOrSharedRestaurant() {
        var keep=sessions.save(LunchVoteSession.create(team.getId(),first.getId(),DEADLINE,NOW));
        var keptCandidate=candidates.save(VoteCandidate.create(keep.getId(),restaurant.getId(),CandidateSource.MANUAL,NOW));
        ballots.save(VoteRecord.create(vote.getId(),first.getId(),candidate.getId(),NOW));
        ballots.saveAndFlush(VoteRecord.create(keep.getId(),first.getId(),keptCandidate.getId(),NOW));
        ballots.deleteBySession(vote.getId()); participants.deleteBySession(vote.getId()); candidates.deleteBySession(vote.getId());
        assertThat(ballots.findAllBySessionIdOrderById(keep.getId())).hasSize(1); assertThat(restaurants.findById(restaurant.getId())).isPresent();
    }
}
