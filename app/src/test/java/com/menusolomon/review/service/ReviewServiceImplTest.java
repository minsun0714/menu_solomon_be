package com.menusolomon.review.service;

import org.springframework.test.util.ReflectionTestUtils;

import static com.menusolomon.review.fixture.ReviewFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.restaurant.fixture.RestaurantFixture;
import com.menusolomon.restaurant.repository.TeamRestaurantRepository;
import com.menusolomon.review.domain.Review;
import com.menusolomon.review.repository.ReviewRepository;
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

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {
    @Mock UserService users;
    @Mock TeamMemberRepository members;
    @Mock TeamRestaurantRepository restaurants;
    @Mock ReviewRepository reviews;
    ReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReviewServiceImpl(users, members, restaurants, reviews, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void activeMember() {
        when(users.findBySessionToken("token")).thenReturn(Optional.of(UserFixture.user(10L, "hash", "익명 사용자")));
        when(members.findByTeamIdAndUserId(1L, 10L)).thenReturn(Optional.of(TeamFixture.admin()));
    }

    private void scopedRestaurantForSave() {
        when(restaurants.findByIdAndTeamIdForUpdate(5L, 1L)).thenReturn(Optional.of(RestaurantFixture.savedLink()));
    }

    private void scopedRestaurant() {
        when(restaurants.findByIdAndTeamId(5L, 1L)).thenReturn(Optional.of(RestaurantFixture.savedLink()));
    }

    @Test
    void saveMyReview_withoutExistingReview_createsReview() {
        activeMember();
        scopedRestaurantForSave();
        when(reviews.save(any())).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            assertThat(review.getTeamRestaurantId()).isEqualTo(5L);
            assertThat(review.getTeamMemberId()).isEqualTo(1L);
            assertThat(review.getCreatedAt()).isEqualTo(NOW);
            ReflectionTestUtils.setField(review, "id", 7L);
            return review;
        });
        var result = service.saveMyReview(1L, 5L, "token", 5, "맛있어요");
        assertThat(result.created()).isTrue();
        assertThat(result.review().id()).isEqualTo("review_7");
        assertThat(result.review().authorNickname()).isEqualTo("익명 사용자");
        verify(reviews).save(any());
        var order = inOrder(restaurants, reviews);
        order.verify(restaurants).findByIdAndTeamIdForUpdate(5L, 1L);
        order.verify(reviews).findByTeamRestaurantIdAndTeamMemberId(5L, 1L);
    }

    @Test
    void saveMyReview_existingReview_updatesSameReview() {
        activeMember();
        scopedRestaurantForSave();
        var existing = review();
        when(reviews.findByTeamRestaurantIdAndTeamMemberId(5L, 1L)).thenReturn(Optional.of(existing));
        var result = service.saveMyReview(1L, 5L, "token", 3, "변경된 내용");
        assertThat(result.created()).isFalse();
        assertThat(result.review().id()).isEqualTo("review_7");
        assertThat(existing.getRating()).isEqualTo(3);
        assertThat(existing.getContent()).isEqualTo("변경된 내용");
        assertThat(existing.getCreatedAt()).isEqualTo(CREATED);
        assertThat(existing.getUpdatedAt()).isEqualTo(NOW);
        verify(reviews, never()).save(any());
    }

    @Test
    void saveMyReview_nonMember_throwsNotTeamMember() {
        assertError(() -> service.saveMyReview(1L, 5L, "token", 5, "리뷰"), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(restaurants, reviews);
    }

    @Test
    void saveMyReview_validSessionWithoutMembership_throwsNotTeamMember() {
        when(users.findBySessionToken("token")).thenReturn(Optional.of(UserFixture.user(10L, "hash", "익명")));
        assertError(() -> service.saveMyReview(1L, 5L, "token", 5, "리뷰"), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(restaurants, reviews);
    }

    @Test
    void saveMyReview_inactiveMember_throwsNotTeamMember() {
        activeMember();
        var inactive = TeamFixture.admin();
        inactive.leave(NOW);
        when(members.findByTeamIdAndUserId(1L, 10L)).thenReturn(Optional.of(inactive));
        assertError(() -> service.saveMyReview(1L, 5L, "token", 5, "리뷰"), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(restaurants, reviews);
    }

    @Test
    void saveMyReview_wrongTeamRestaurant_throwsTeamRestaurantNotFound() {
        activeMember();
        assertError(() -> service.saveMyReview(1L, 5L, "token", 5, "리뷰"), ErrorCode.TEAM_RESTAURANT_NOT_FOUND);
        verify(restaurants).findByIdAndTeamIdForUpdate(5L, 1L);
        verifyNoInteractions(reviews);
    }

    @Test
    void getReviews_returnsReviewList_andSetsIsMineCorrectly() {
        activeMember();
        scopedRestaurant();
        when(reviews.findReviews(5L)).thenReturn(List.of(row(2L), row(1L)));
        var response = service.getReviews(1L, 5L, "token");
        assertThat(response).extracting(item -> item.isMine()).containsExactly(false, true);
        assertThat(response.getFirst().authorNickname()).isEqualTo("익명 사용자");
        verify(reviews).findReviews(5L);
    }

    @Test
    void getReviews_nonMember_throwsNotTeamMember() {
        assertError(() -> service.getReviews(1L, 5L, "token"), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(restaurants, reviews);
    }

    @Test
    void getReviews_wrongTeamRestaurant_throwsTeamRestaurantNotFound() {
        activeMember();
        assertError(() -> service.getReviews(1L, 5L, "token"), ErrorCode.TEAM_RESTAURANT_NOT_FOUND);
        verifyNoInteractions(reviews);
    }

    @Test
    void deleteMyReview_deletesOwnReview() {
        activeMember();
        scopedRestaurantForSave();
        var own = review();
        when(reviews.findByTeamRestaurantIdAndTeamMemberId(5L, 1L)).thenReturn(Optional.of(own));
        service.deleteMyReview(1L, 5L, "token");
        verify(reviews).findByTeamRestaurantIdAndTeamMemberId(5L, 1L);
        verify(reviews).delete(own);
        verify(reviews, never()).findById(any());
    }

    @Test
    void deleteMyReview_notFound_throwsReviewNotFound() {
        activeMember();
        scopedRestaurantForSave();
        assertError(() -> service.deleteMyReview(1L, 5L, "token"), ErrorCode.REVIEW_NOT_FOUND);
        verify(reviews, never()).delete(any());
    }

    @Test
    void deleteMyReview_nonMember_throwsNotTeamMember() {
        assertError(() -> service.deleteMyReview(1L, 5L, "token"), ErrorCode.NOT_TEAM_MEMBER);
        verifyNoInteractions(restaurants, reviews);
    }

    @Test
    void deleteMyReview_wrongTeamRestaurant_throwsTeamRestaurantNotFound() {
        activeMember();
        assertError(() -> service.deleteMyReview(1L, 5L, "token"), ErrorCode.TEAM_RESTAURANT_NOT_FOUND);
        verifyNoInteractions(reviews);
    }

    @Test
    void getReviews_ordinaryMember_hasSameAccessAsAdmin() {
        when(users.findBySessionToken("token")).thenReturn(Optional.of(UserFixture.user(20L, "hash", "익명")));
        when(members.findByTeamIdAndUserId(1L, 20L)).thenReturn(Optional.of(TeamFixture.member()));
        scopedRestaurant();
        when(reviews.findReviews(5L)).thenReturn(List.of(row(2L)));
        assertThat(service.getReviews(1L, 5L, "token").getFirst().isMine()).isTrue();
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode errorCode) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
