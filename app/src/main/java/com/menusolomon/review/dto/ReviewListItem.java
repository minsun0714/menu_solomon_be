package com.menusolomon.review.dto;

import com.menusolomon.review.repository.ReviewRow;
import java.time.Instant;

public record ReviewListItem(String id, int rating, String content, String authorNickname,
        boolean isMine, Instant createdAt, Instant updatedAt) {
    public static ReviewListItem from(ReviewRow row, Long currentMemberId) {
        return new ReviewListItem("review_" + row.id(), row.rating(), row.content(), row.authorNickname(),
                row.teamMemberId().equals(currentMemberId), row.createdAt(), row.updatedAt());
    }
}
