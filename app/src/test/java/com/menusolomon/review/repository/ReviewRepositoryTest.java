package com.menusolomon.review.repository;

import jakarta.persistence.EntityManager;

import static com.menusolomon.review.fixture.ReviewFixture.*;
import static org.assertj.core.api.Assertions.*;

import com.menusolomon.review.domain.Review;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.user.domain.User;
import com.menusolomon.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class ReviewRepositoryTest {
    @Autowired ReviewRepository reviews;
    @Autowired TeamMemberRepository members;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;

    @Test
    void findByTeamRestaurantIdAndTeamMemberId_returnsReview() {
        var saved = reviews.saveAndFlush(Review.create(5L, 1L, 5, "리뷰", NOW));
        assertThat(reviews.findByTeamRestaurantIdAndTeamMemberId(5L, 1L)).contains(saved);
        assertThat(reviews.findByTeamRestaurantIdAndTeamMemberId(5L, 2L)).isEmpty();
    }

    @Test
    void findByTeamRestaurantIdAndTeamMemberId_returnsEmpty() {
        assertThat(reviews.findByTeamRestaurantIdAndTeamMemberId(5L, 1L)).isEmpty();
    }

    @Test
    void duplicateReviewForSameMemberAndRestaurant_isRejected() {
        reviews.saveAndFlush(Review.create(5L, 1L, 5, "리뷰", NOW));
        assertThatThrownBy(() -> reviews.saveAndFlush(Review.create(5L, 1L, 4, "중복", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reviews_areOrderedByUpdatedAtDesc_andAuthorNicknameIsProjected() {
        var first = users.save(User.create("first-hash", "첫 작성자", NOW));
        var second = users.save(User.create("second-hash", "다음 작성자", NOW));
        var firstMember = members.save(TeamMember.newAdmin(1L, first.getId(), NOW));
        var secondMember = members.save(TeamMember.newMember(1L, second.getId(), NOW));
        var old = reviews.save(Review.create(5L, firstMember.getId(), 5, "먼저 작성", CREATED));
        reviews.save(Review.create(5L, secondMember.getId(), 4, "나중 작성", NOW));
        reviews.save(Review.create(6L, firstMember.getId(), 4, "다른 식당", NOW));
        old.update(3, "수정한 리뷰", NOW.plusSeconds(60));
        reviews.flush();
        assertThat(reviews.findReviews(5L)).extracting(ReviewRow::authorNickname)
                .containsExactly("첫 작성자", "다음 작성자");
        assertThat(reviews.findReviews(5L).getFirst().content()).isEqualTo("수정한 리뷰");
    }

    @Test
    void updatingManagedReview_preservesRowAndCreatedAtAfterReload() {
        var saved = reviews.saveAndFlush(Review.create(5L, 1L, 5, "원래 리뷰", CREATED));
        Long id = saved.getId();
        saved.update(3, "덮어쓴 리뷰", NOW);
        reviews.flush();
        entityManager.clear();
        var reloaded = reviews.findByTeamRestaurantIdAndTeamMemberId(5L, 1L).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(id);
        assertThat(reloaded.getRating()).isEqualTo(3);
        assertThat(reloaded.getContent()).isEqualTo("덮어쓴 리뷰");
        assertThat(reloaded.getCreatedAt()).isEqualTo(CREATED);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(NOW);
        assertThat(reviews.count()).isEqualTo(1);
    }

    @Test
    void deleteByTeamRestaurantId_removesOnlySelectedRestaurantReviews() {
        reviews.save(Review.create(5L, 1L, 5, "삭제 대상", NOW));
        reviews.save(Review.create(6L, 1L, 4, "보존 대상", NOW));
        reviews.flush();
        reviews.deleteByTeamRestaurantId(5L);
        assertThat(reviews.findByTeamRestaurantIdAndTeamMemberId(5L, 1L)).isEmpty();
        assertThat(reviews.findByTeamRestaurantIdAndTeamMemberId(6L, 1L)).isPresent();
    }
}
