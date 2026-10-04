package com.menusolomon.review.service;

import com.menusolomon.review.dto.ReviewListItem;
import com.menusolomon.review.dto.ReviewSaveResult;
import java.util.List;

public interface ReviewService {
    ReviewSaveResult saveMyReview(Long teamId, Long teamRestaurantId, String rawSessionToken, int rating, String content);
    List<ReviewListItem> getReviews(Long teamId, Long teamRestaurantId, String rawSessionToken);
    void deleteMyReview(Long teamId, Long teamRestaurantId, String rawSessionToken);
}
