package com.menusolomon.team.repository;

import static com.menusolomon.team.fixture.TeamFixture.NOW;
import static org.assertj.core.api.Assertions.*;

import com.menusolomon.restaurant.domain.TeamRestaurant;
import com.menusolomon.restaurant.fixture.RestaurantFixture;
import com.menusolomon.restaurant.repository.RestaurantRepository;
import com.menusolomon.restaurant.repository.TeamRestaurantRepository;
import com.menusolomon.review.domain.Review;
import com.menusolomon.review.repository.ReviewRepository;
import com.menusolomon.team.domain.Team;
import com.menusolomon.team.domain.TeamMember;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class TeamManagementRepositoryTest {
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired RestaurantRepository restaurants;
    @Autowired TeamRestaurantRepository teamRestaurants;
    @Autowired ReviewRepository reviews;
    @Autowired EntityManager entityManager;
    Team first;
    Team second;

    @BeforeEach
    void setUp() {
        first = teams.save(Team.create("첫 팀", "", "first-token", NOW));
        second = teams.save(Team.create("다른 팀", "", "second-token", NOW));
    }

    @Test
    void findByIdAndTeamId_isScoped_andCountActiveMembers_excludesLeftMembers() {
        var admin = members.save(TeamMember.newAdmin(first.getId(), 10L, NOW));
        var inactive = members.save(TeamMember.newMember(first.getId(), 20L, NOW));
        inactive.leave(NOW);
        members.saveAndFlush(TeamMember.newMember(second.getId(), 30L, NOW));
        assertThat(members.findByIdAndTeamId(admin.getId(), first.getId())).contains(admin);
        assertThat(members.findByIdAndTeamId(admin.getId(), second.getId())).isEmpty();
        assertThat(members.countByTeamIdAndLeftAtIsNull(first.getId())).isEqualTo(1);
    }

    @Test
    void changeInvitationToken_invalidatesOldLookupImmediately() {
        first.changeInviteToken("replacement-token", NOW.plusSeconds(60));
        assertThat(teams.findByInviteToken("first-token")).isEmpty();
        assertThat(teams.findByInviteToken("replacement-token")).contains(first);
        assertThat(teams.findByInviteToken("second-token")).contains(second);
    }

    @Test
    void deleteReviewsByTeamId_preservesOtherTeamReviews() {
        var restaurant = restaurants.save(RestaurantFixture.restaurant("123", "식당", "한식"));
        var firstLink = teamRestaurants.save(TeamRestaurant.create(first.getId(), restaurant.getId(), 1L, NOW));
        var secondLink = teamRestaurants.save(TeamRestaurant.create(second.getId(), restaurant.getId(), 2L, NOW));
        reviews.save(Review.create(firstLink.getId(), 1L, 5, "첫 팀", NOW));
        reviews.saveAndFlush(Review.create(secondLink.getId(), 2L, 4, "다른 팀", NOW));
        reviews.deleteAllByTeamId(first.getId());
        assertThat(reviews.findByTeamRestaurantIdAndTeamMemberId(firstLink.getId(), 1L)).isEmpty();
        assertThat(reviews.findByTeamRestaurantIdAndTeamMemberId(secondLink.getId(), 2L)).isPresent();
    }

    @Test
    void deleteTeamOwnedData_preservesOtherTeamAndSharedRestaurant() {
        var firstMember = members.save(TeamMember.newAdmin(first.getId(), 10L, NOW));
        var oldMember = members.save(TeamMember.newMember(first.getId(), 20L, NOW));
        oldMember.leave(NOW);
        var secondMember = members.save(TeamMember.newAdmin(second.getId(), 30L, NOW));
        var restaurant = restaurants.save(RestaurantFixture.restaurant("123", "공유 식당", "한식"));
        var firstLink = teamRestaurants.save(TeamRestaurant.create(first.getId(), restaurant.getId(), firstMember.getId(), NOW));
        var secondLink = teamRestaurants.save(TeamRestaurant.create(second.getId(), restaurant.getId(), secondMember.getId(), NOW));
        reviews.save(Review.create(firstLink.getId(), firstMember.getId(), 5, "삭제 대상", NOW));
        reviews.saveAndFlush(Review.create(secondLink.getId(), secondMember.getId(), 4, "보존 대상", NOW));
        Long firstId = first.getId();
        Long secondId = second.getId();
        reviews.deleteAllByTeamId(firstId);
        teamRestaurants.deleteAllByTeamId(firstId);
        members.deleteAllByTeamId(firstId);
        teams.delete(first);
        teams.flush();
        entityManager.clear();
        assertThat(teams.findById(firstId)).isEmpty();
        assertThat(members.findById(firstMember.getId())).isEmpty();
        assertThat(members.findById(oldMember.getId())).isEmpty();
        assertThat(teamRestaurants.findByIdAndTeamId(firstLink.getId(), firstId)).isEmpty();
        assertThat(reviews.findByTeamRestaurantIdAndTeamMemberId(firstLink.getId(), firstMember.getId())).isEmpty();
        assertThat(teams.findById(secondId)).isPresent();
        assertThat(members.findById(secondMember.getId())).isPresent();
        assertThat(teamRestaurants.findByIdAndTeamId(secondLink.getId(), secondId)).isPresent();
        assertThat(reviews.findByTeamRestaurantIdAndTeamMemberId(secondLink.getId(), secondMember.getId())).isPresent();
        assertThat(restaurants.findById(restaurant.getId())).isPresent();
    }
}
