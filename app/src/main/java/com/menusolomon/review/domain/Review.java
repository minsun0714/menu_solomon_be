package com.menusolomon.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(name = "uk_review_restaurant_member",
        columnNames = {"team_restaurant_id", "team_member_id"}),
        indexes = @Index(name = "idx_review_restaurant_updated", columnList = "team_restaurant_id,updated_at,id"))
@Getter
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_restaurant_id", nullable = false, updatable = false)
    private Long teamRestaurantId;

    @Column(name = "team_member_id", nullable = false, updatable = false)
    private Long teamMemberId;

    @Column(nullable = false, columnDefinition = "tinyint")
    private int rating;

    @Column(nullable = false, length = 200)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Review() {}

    private Review(Long teamRestaurantId, Long teamMemberId, int rating, String content, Instant now) {
        validate(rating, content);
        this.teamRestaurantId = teamRestaurantId;
        this.teamMemberId = teamMemberId;
        this.rating = rating;
        this.content = content;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Review create(Long teamRestaurantId, Long teamMemberId, int rating, String content, Instant now) {
        return new Review(teamRestaurantId, teamMemberId, rating, content, now);
    }

    public void update(int rating, String content, Instant updatedAt) {
        validate(rating, content);
        this.rating = rating;
        this.content = content;
        this.updatedAt = updatedAt;
    }

    private static void validate(int rating, String content) {
        if (rating < 1 || rating > 5 || content == null || content.isBlank() || content.length() > 200) {
            throw new InvalidReviewException();
        }
    }
}
