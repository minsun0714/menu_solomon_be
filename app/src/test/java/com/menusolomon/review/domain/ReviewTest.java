package com.menusolomon.review.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReviewTest {
    private final Instant created = Instant.parse("2026-10-04T04:00:00Z");
    private final Instant updated = created.plusSeconds(60);

    @Test
    void createReview_validValues_succeeds() {
        Review review = Review.create(1L, 2L, 5, "점심 먹기 좋아요", created);
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getContent()).isEqualTo("점심 먹기 좋아요");
        assertThat(review.getCreatedAt()).isEqualTo(created);
        assertThat(review.getUpdatedAt()).isEqualTo(created);
    }

    @Test
    void createReview_ratingBelow1_throws() {
        assertThatThrownBy(() -> Review.create(1L, 2L, 0, "리뷰", created)).isInstanceOf(InvalidReviewException.class);
    }

    @Test
    void createReview_ratingAbove5_throws() {
        assertThatThrownBy(() -> Review.create(1L, 2L, 6, "리뷰", created)).isInstanceOf(InvalidReviewException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void createReview_blankContent_throws(String content) {
        assertThatThrownBy(() -> Review.create(1L, 2L, 5, content, created)).isInstanceOf(InvalidReviewException.class);
    }

    @Test
    void createReview_contentOver200_throws() {
        assertThatThrownBy(() -> Review.create(1L, 2L, 5, "가".repeat(201), created)).isInstanceOf(InvalidReviewException.class);
        assertThatCode(() -> Review.create(1L, 2L, 1, "가".repeat(200), created)).doesNotThrowAnyException();
    }

    @Test
    void update_changesRatingAndContent() {
        Review review = Review.create(1L, 2L, 5, "원래 리뷰", created);
        review.update(3, "변경 리뷰", updated);
        assertThat(review.getRating()).isEqualTo(3);
        assertThat(review.getContent()).isEqualTo("변경 리뷰");
    }

    @Test
    void update_changesUpdatedAt_preservesCreatedAt() {
        Review review = Review.create(1L, 2L, 5, "리뷰", created);
        review.update(4, "변경", updated);
        assertThat(review.getCreatedAt()).isEqualTo(created);
        assertThat(review.getUpdatedAt()).isEqualTo(updated);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void update_invalidRating_throwsWithoutChangingExistingState(int rating) {
        Review review = Review.create(1L, 2L, 5, "원래 리뷰", created);
        assertThatThrownBy(() -> review.update(rating, "변경", updated)).isInstanceOf(InvalidReviewException.class);
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getContent()).isEqualTo("원래 리뷰");
        assertThat(review.getUpdatedAt()).isEqualTo(created);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void update_invalidContent_throws(String content) {
        Review review = Review.create(1L, 2L, 5, "리뷰", created);
        assertThatThrownBy(() -> review.update(4, content, updated)).isInstanceOf(InvalidReviewException.class);
        assertThat(review.getRating()).isEqualTo(5);
    }

    @Test
    void update_contentOver200_throws() {
        Review review = Review.create(1L, 2L, 5, "리뷰", created);
        assertThatThrownBy(() -> review.update(4, "가".repeat(201), updated)).isInstanceOf(InvalidReviewException.class);
    }
}
