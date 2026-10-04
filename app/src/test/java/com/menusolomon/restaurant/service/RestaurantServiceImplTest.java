package com.menusolomon.restaurant.service;

import com.menusolomon.review.repository.ReviewRepository;

import static com.menusolomon.restaurant.fixture.RestaurantFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.restaurant.domain.TeamRestaurant;
import com.menusolomon.restaurant.dto.RestaurantSort;
import com.menusolomon.restaurant.repository.*;
import com.menusolomon.team.fixture.TeamFixture;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.user.fixture.UserFixture;
import com.menusolomon.user.service.UserService;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceImplTest {
    @Mock RestaurantRepository restaurants;
    @Mock TeamRestaurantRepository links;
    @Mock TeamMemberRepository members;
    @Mock UserService users;
    @Mock ReviewRepository reviews;
    RestaurantServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RestaurantServiceImpl(restaurants, links, members, users, Clock.fixed(NOW, ZoneOffset.UTC), reviews);
    }

    private void activeMember() {
        when(users.findBySessionToken("token")).thenReturn(Optional.of(UserFixture.user(10L, "hash", "익명")));
        when(members.findByTeamIdAndUserId(1L, 10L)).thenReturn(Optional.of(TeamFixture.admin()));
    }

    @Test
    void registerRestaurant_existingRestaurant_reusesServerSearchCacheWithoutExternalCall() {
        activeMember();
        when(restaurants.findByKakaoPlaceId("123")).thenReturn(Optional.of(savedRestaurant()));
        when(links.saveAndFlush(any())).thenAnswer(invocation -> {
            TeamRestaurant link = invocation.getArgument(0);
            assertThat(link.getRestaurantId()).isEqualTo(3L);
            assertThat(link.getRegisteredByTeamMemberId()).isEqualTo(1L);
            assertThat(link.getCreatedAt()).isEqualTo(NOW);
            return savedLink();
        });
        var result = service.registerRestaurant(1L, "token", "123");
        assertThat(result.id()).isEqualTo("teamRestaurant_5");
        assertThat(result.restaurantId()).isEqualTo("restaurant_3");
        verify(restaurants, never()).save(any());
        verify(restaurants, never()).saveAndFlush(any());
        verify(links).saveAndFlush(any());
    }

    @Test
    void registerRestaurant_unsearchedPlace_throwsKakaoPlaceNotFound() {
        activeMember();
        assertError(() -> service.registerRestaurant(1L, "token", "missing"), ErrorCode.KAKAO_PLACE_NOT_FOUND);
        verify(links, never()).saveAndFlush(any());
    }

    @Test
    void registerRestaurant_duplicate_throwsRestaurantAlreadyRegistered() {
        activeMember();
        when(restaurants.findByKakaoPlaceId("123")).thenReturn(Optional.of(savedRestaurant()));
        when(links.existsByTeamIdAndRestaurantId(1L, 3L)).thenReturn(true);
        assertError(() -> service.registerRestaurant(1L, "token", "123"), ErrorCode.RESTAURANT_ALREADY_REGISTERED);
        verify(links, never()).saveAndFlush(any());
    }

    @Test
    void registerRestaurant_concurrentDuplicate_returnsSameConflictCode() {
        activeMember();
        when(restaurants.findByKakaoPlaceId("123")).thenReturn(Optional.of(savedRestaurant()));
        when(links.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));
        assertError(() -> service.registerRestaurant(1L, "token", "123"), ErrorCode.RESTAURANT_ALREADY_REGISTERED);
    }

    @Test
    void registerRestaurant_nonMember_throwsNotTeamMember() {
        assertError(() -> service.registerRestaurant(1L, "token", "123"), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(restaurants, links);
    }

    @Test
    void registerRestaurant_inactiveMember_throwsNotTeamMember() {
        activeMember();
        var inactive = TeamFixture.admin();
        inactive.leave(NOW);
        when(members.findByTeamIdAndUserId(1L, 10L)).thenReturn(Optional.of(inactive));
        assertError(() -> service.registerRestaurant(1L, "token", "123"), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(restaurants, links);
    }

    @Test
    void getRestaurants_activeMember_returnsListAndTeamWideCounts() {
        activeMember();
        when(links.findList(eq(1L), isNull(), isNull(), any())).thenReturn(List.of(row()));
        when(reviews.countByTeamId(1L)).thenReturn(7L);
        when(links.countCategories(1L)).thenReturn(List.of(new CategoryCount("양식", 2L), new CategoryCount("한식", 1L)));
        var response = service.getRestaurants(1L, "token", null, null, RestaurantSort.LATEST);
        assertThat(response.restaurants()).singleElement().extracting("id").isEqualTo("teamRestaurant_5");
        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.totalReviewCount()).isEqualTo(7);
        assertThat(response.restaurants().getFirst().reviewCount()).isEqualTo(2);
        assertThat(response.restaurants().getFirst().latestReview().nickname()).isEqualTo("리뷰 작성자");
        assertThat(response.categoryCounts()).containsEntry("양식", 2L);
    }

    @Test
    void getRestaurants_passesKeywordCategorySortToDatabase() {
        activeMember();
        service.getRestaurants(1L, "token", " 강남 ", "한식", RestaurantSort.NAME);
        verify(links).findList(1L, "강남", "한식", Sort.by("r.name").and(Sort.by("tr.id")));
    }

    @Test
    void getRestaurants_ratingSort_queriesDatabaseRatings() {
        activeMember();
        when(links.findListByRating(1L, null, null)).thenReturn(List.of(row()));
        var response = service.getRestaurants(1L, "token", null, null, RestaurantSort.RATING_DESC);
        assertThat(response.restaurants().getFirst().averageRating()).isEqualTo(4.5);
        verify(links).findListByRating(1L, null, null);
        verify(links, never()).findList(any(), any(), any(), any());
    }

    @Test
    void getRestaurants_nonMember_throwsNotTeamMember() {
        assertError(() -> service.getRestaurants(1L, "token", null, null, RestaurantSort.LATEST), ErrorCode.NOT_TEAM_MEMBER);
    }

    @Test
    void getRestaurant_activeMember_returnsDetail() {
        activeMember();
        when(links.findDetail(5L, 1L)).thenReturn(Optional.of(row()));
        var response = service.getRestaurant(1L, 5L, "token");
        assertThat(response.name()).isEqualTo("을지다락");
        assertThat(response.registeredByNickname()).isEqualTo("익명 사용자");
        assertThat(response.averageRating()).isEqualTo(4.5);
        assertThat(response.reviewCount()).isEqualTo(2);
    }

    @Test
    void getRestaurant_notFound_throwsTeamRestaurantNotFound() {
        activeMember();
        assertError(() -> service.getRestaurant(1L, 99L, "token"), ErrorCode.TEAM_RESTAURANT_NOT_FOUND);
        verify(links).findDetail(99L, 1L);
    }

    @Test
    void getRestaurant_otherTeam_throwsTeamRestaurantNotFound() {
        activeMember();
        assertError(() -> service.getRestaurant(1L, 5L, "token"), ErrorCode.TEAM_RESTAURANT_NOT_FOUND);
        verify(links).findDetail(5L, 1L);
    }

    @Test
    void getRestaurant_nonMember_throwsNotTeamMember() {
        assertError(() -> service.getRestaurant(1L, 5L, "token"), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(links);
    }

    @Test
    void deleteRestaurant_activeMember_deletesOnlyTeamRestaurant() {
        activeMember();
        var link = savedLink();
        when(links.findByIdAndTeamIdForUpdate(5L, 1L)).thenReturn(Optional.of(link));
        service.deleteRestaurant(1L, 5L, "token");
        var order = inOrder(reviews, links);
        order.verify(reviews).deleteByTeamRestaurantId(5L);
        order.verify(links).delete(link);
        verifyNoInteractions(restaurants);
    }

    @Test
    void deleteRestaurant_nonMember_throwsNotTeamMember() {
        assertError(() -> service.deleteRestaurant(1L, 5L, "token"), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(links, restaurants);
    }

    @Test
    void deleteRestaurant_notFound_throwsTeamRestaurantNotFound() {
        activeMember();
        assertError(() -> service.deleteRestaurant(1L, 5L, "token"), ErrorCode.TEAM_RESTAURANT_NOT_FOUND);
        verify(links, never()).delete(any());
    }

    @Test
    void getRestaurant_activeOrdinaryMember_hasSameAccessAsAdmin() {
        when(users.findBySessionToken("token")).thenReturn(Optional.of(UserFixture.user(20L, "hash", "익명")));
        when(members.findByTeamIdAndUserId(1L, 20L)).thenReturn(Optional.of(TeamFixture.member()));
        when(links.findDetail(5L, 1L)).thenReturn(Optional.of(row()));
        assertThat(service.getRestaurant(1L, 5L, "token").id()).isEqualTo("teamRestaurant_5");
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }
}
