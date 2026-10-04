package com.menusolomon.review.fixture;

import com.menusolomon.review.domain.Review;
import com.menusolomon.review.repository.ReviewRow;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

public final class ReviewFixture {
    public static final Instant CREATED = Instant.parse("2026-10-03T04:00:00Z");
    public static final Instant NOW = Instant.parse("2026-10-04T04:00:00Z");
    private ReviewFixture() {}

    public static Review review() {
        var review = Review.create(5L, 1L, 5, "맛있어요", CREATED);
        ReflectionTestUtils.setField(review, "id", 7L);
        return review;
    }

    public static ReviewRow row(Long memberId) {
        return new ReviewRow(7L, memberId, 5, "맛있어요", "익명 사용자", CREATED, NOW);
    }
}
