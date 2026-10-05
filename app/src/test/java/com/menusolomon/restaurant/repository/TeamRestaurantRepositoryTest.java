package com.menusolomon.restaurant.repository;

import com.menusolomon.review.domain.Review;
import com.menusolomon.review.repository.ReviewRepository;

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
    @Autowired ReviewRepository reviews;
    Long secondMemberId;
    Long memberId;

    @BeforeEach
    void setUp() {
        var user = users.save(User.create("hash", "등록자", NOW));
        memberId = members.save(TeamMember.newAdmin(1L, user.getId(), NOW)).getId();
        var secondUser = users.save(User.create("second-hash", "두 번째 작성자", NOW));
        secondMemberId = members.save(TeamMember.newMember(1L, secondUser.getId(), NOW)).getId();
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

    @Test
    void averageRating_reviewCount_andLatestReview_areCalculatedForListAndDetail() {
        var link = save("123", "가식당", "한식", 1L, 0);
        var first = reviews.save(Review.create(link.getId(), memberId, 5, "처음 작성", NOW));
        reviews.save(Review.create(link.getId(), secondMemberId, 4, "다음 작성", NOW.plusSeconds(10)));
        first.update(3, "최신 수정", NOW.plusSeconds(60));
        reviews.flush();
        var rows = links.findList(1L, null, null, Sort.by("tr.id"));
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.averageRating()).isEqualTo(3.5);
            assertThat(row.reviewCount()).isEqualTo(2);
            assertThat(row.latestNickname()).isEqualTo("등록자");
            assertThat(row.latestContent()).isEqualTo("최신 수정");
            assertThat(row.latestUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
        });
        assertThat(links.findDetail(link.getId(), 1L)).contains(rows.getFirst());
    }

    @Test
    void restaurantWithoutReviews_isIncludedWithNullAverageAndZeroCount() {
        var link = save("123", "가식당", "한식", 1L, 0);
        assertThat(links.findList(1L, null, null, Sort.by("tr.id"))).singleElement().satisfies(row -> {
            assertThat(row.averageRating()).isNull();
            assertThat(row.reviewCount()).isZero();
            assertThat(row.latestContent()).isNull();
            assertThat(row.latestUpdatedAt()).isNull();
        });
        assertThat(links.findDetail(link.getId(), 1L)).isPresent();
    }

    @Test
    void latestReview_equalUpdatedAt_usesHighestReviewIdWithoutDuplicatingRestaurant() {
        var link = save("123", "가식당", "한식", 1L, 0);
        reviews.save(Review.create(link.getId(), memberId, 5, "첫 리뷰", NOW));
        reviews.saveAndFlush(Review.create(link.getId(), secondMemberId, 4, "다음 리뷰", NOW));
        assertThat(links.findList(1L, null, null, Sort.by("tr.id"))).singleElement().satisfies(row -> {
            assertThat(row.reviewCount()).isEqualTo(2);
            assertThat(row.latestNickname()).isEqualTo("두 번째 작성자");
            assertThat(row.latestContent()).isEqualTo("다음 리뷰");
        });
    }

    @Test
    void ratingDesc_ordersHigherAverageFirst_withStableTiesAndUnratedLast() {
        var lower = save("low", "가식당", "한식", 1L, 0);
        var higher = save("high", "나식당", "양식", 1L, 0);
        var sameNewer = save("equal", "다식당", "양식", 1L, 60);
        save("none", "라식당", "양식", 1L, 120);
        var other = save("other", "다른 팀", "한식", 2L, 0);
        reviews.save(Review.create(lower.getId(), memberId, 4, "리뷰", NOW));
        for (var link : java.util.List.of(higher, sameNewer)) {
            reviews.save(Review.create(link.getId(), memberId, 5, "좋아요", NOW));
            reviews.save(Review.create(link.getId(), secondMemberId, 4, "괜찮아요", NOW));
        }
        reviews.saveAndFlush(Review.create(other.getId(), memberId, 5, "다른 팀", NOW));
        assertThat(links.findListByRating(1L, null, null)).extracting(TeamRestaurantRow::kakaoPlaceId)
                .containsExactly("equal", "high", "low", "none");
        assertThat(links.findListByRating(1L, "나식당", "양식"))
                .extracting(TeamRestaurantRow::kakaoPlaceId).containsExactly("high");
    }

    @Test
    void totalReviewCount_countsAllReviewsForOnlyRequestedTeam() {
        var first = save("first", "가식당", "한식", 1L, 0);
        var second = save("second", "나식당", "양식", 1L, 0);
        var other = save("other", "다른 팀", "한식", 2L, 0);
        reviews.save(Review.create(first.getId(), memberId, 5, "리뷰", NOW));
        reviews.save(Review.create(second.getId(), memberId, 4, "리뷰", NOW));
        reviews.save(Review.create(second.getId(), secondMemberId, 3, "리뷰", NOW));
        reviews.saveAndFlush(Review.create(other.getId(), memberId, 5, "리뷰", NOW));
        assertThat(reviews.countByTeamId(1L)).isEqualTo(3);
        assertThat(reviews.countByTeamId(2L)).isEqualTo(1);
        assertThat(reviews.countByTeamId(3L)).isZero();
    }

    @Test
    void deletingReview_recalculatesRatingAndLatestReviewWithoutCachedCounters() {
        var link = save("123", "가식당", "한식", 1L, 0);
        var review = reviews.saveAndFlush(Review.create(link.getId(), memberId, 5, "리뷰", NOW));
        assertThat(links.findDetail(link.getId(), 1L).orElseThrow().averageRating()).isEqualTo(5.0);
        reviews.delete(review);
        reviews.flush();
        var row = links.findDetail(link.getId(), 1L).orElseThrow();
        assertThat(row.reviewCount()).isZero();
        assertThat(row.averageRating()).isNull();
        assertThat(row.latestContent()).isNull();
    }

    @Test
    void findByIdAndTeamIdForUpdate_keepsTeamScope() {
        var link = save("123", "가식당", "한식", 1L, 0);
        assertThat(links.findByIdAndTeamIdForUpdate(link.getId(), 1L)).contains(link);
        assertThat(links.findByIdAndTeamIdForUpdate(link.getId(), 2L)).isEmpty();
    }

    private TeamRestaurant save(String placeId, String name, String category, Long teamId, long offset) {
        var restaurant = restaurants.save(restaurant(placeId, name, category));
        return links.saveAndFlush(TeamRestaurant.create(teamId, restaurant.getId(), memberId, NOW.plusSeconds(offset)));
    }
}
