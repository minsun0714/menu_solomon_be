package com.menusolomon.restaurant.repository;

import static com.menusolomon.restaurant.fixture.RestaurantFixture.*;
import static org.assertj.core.api.Assertions.*;

import com.menusolomon.restaurant.domain.TeamRestaurant;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.user.domain.User;
import com.menusolomon.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class TeamRestaurantRepositoryTest {
    @Autowired TeamRestaurantRepository links;
    @Autowired RestaurantRepository restaurants;
    @Autowired TeamMemberRepository members;
    @Autowired UserRepository users;
    Long memberId;

    @BeforeEach
    void setUp() {
        var user = users.save(User.create("hash", "등록자", NOW));
        memberId = members.save(TeamMember.newAdmin(1L, user.getId(), NOW)).getId();
    }

    @Test
    void existsByTeamIdAndRestaurantId_returnsTrue() {
        var link = save("123", "가식당", "한식", 1L, 0);
        assertThat(links.existsByTeamIdAndRestaurantId(1L, link.getRestaurantId())).isTrue();
    }

    @Test
    void findByIdAndTeamId_returnsRestaurant() {
        var link = save("123", "가식당", "한식", 1L, 0);
        assertThat(links.findByIdAndTeamId(link.getId(), 1L)).contains(link);
        assertThat(links.findDetail(link.getId(), 1L)).get().extracting(TeamRestaurantRow::registeredByNickname)
                .isEqualTo("등록자");
    }

    @Test
    void findByIdAndWrongTeamId_returnsEmpty() {
        var link = save("123", "가식당", "한식", 1L, 0);
        assertThat(links.findByIdAndTeamId(link.getId(), 2L)).isEmpty();
        assertThat(links.findDetail(link.getId(), 2L)).isEmpty();
    }

    @Test
    void duplicateTeamRestaurant_isRejected() {
        var link = save("123", "가식당", "한식", 1L, 0);
        assertThatThrownBy(() -> links.saveAndFlush(TeamRestaurant.create(1L, link.getRestaurantId(), memberId, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameRestaurant_canBeUsedByAnotherTeam() {
        var link = save("123", "가식당", "한식", 1L, 0);
        var other = links.saveAndFlush(TeamRestaurant.create(2L, link.getRestaurantId(), memberId, NOW));
        assertThat(other.getId()).isNotEqualTo(link.getId());
    }

    @Test
    void list_keywordSearch_matchesNameCategoryAndAddressWithinTeam() {
        save("123", "가식당", "한식", 1L, 0);
        save("456", "나식당", "양식", 2L, 0);
        for (String keyword : java.util.List.of("가식당", "한식", "강남")) {
            assertThat(links.findList(1L, keyword, null, Sort.by("tr.id")))
                    .extracting(TeamRestaurantRow::kakaoPlaceId).containsExactly("123");
        }
        assertThat(links.findList(1L, "없는검색어", null, Sort.by("tr.id"))).isEmpty();
    }

    @Test
    void list_categoryFilter_isExactAndCategoryCountsRemainTeamWide() {
        save("123", "가식당", "한식", 1L, 0);
        save("456", "나식당", "양식", 1L, 0);
        assertThat(links.findList(1L, null, "한식", Sort.by("tr.id")))
                .extracting(TeamRestaurantRow::category).containsExactly("한식");
        assertThat(links.findList(1L, null, "한", Sort.by("tr.id"))).isEmpty();
        assertThat(links.countCategories(1L)).containsExactlyInAnyOrder(new CategoryCount("한식", 1L), new CategoryCount("양식", 1L));
    }

    @Test
    void list_latestSort_ordersRegistrationTimeDescending() {
        save("123", "가식당", "한식", 1L, 0);
        save("456", "나식당", "양식", 1L, 60);
        assertThat(links.findList(1L, null, null, Sort.by(Sort.Direction.DESC, "tr.createdAt", "tr.id")))
                .extracting(TeamRestaurantRow::kakaoPlaceId).containsExactly("456", "123");
    }

    @Test
    void list_nameSort_ordersRestaurantNameAscending() {
        save("123", "나식당", "한식", 1L, 0);
        save("456", "가식당", "양식", 1L, 60);
        assertThat(links.findList(1L, null, null, Sort.by("r.name").and(Sort.by("tr.id"))))
                .extracting(TeamRestaurantRow::kakaoPlaceId).containsExactly("456", "123");
    }

    private TeamRestaurant save(String placeId, String name, String category, Long teamId, long offset) {
        var restaurant = restaurants.save(restaurant(placeId, name, category));
        return links.saveAndFlush(TeamRestaurant.create(teamId, restaurant.getId(), memberId, NOW.plusSeconds(offset)));
    }
}
