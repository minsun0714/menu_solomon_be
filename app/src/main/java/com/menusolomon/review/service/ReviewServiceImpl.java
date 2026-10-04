package com.menusolomon.review.service;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.restaurant.repository.TeamRestaurantRepository;
import com.menusolomon.review.domain.Review;
import com.menusolomon.review.dto.ReviewListItem;
import com.menusolomon.review.dto.ReviewResponse;
import com.menusolomon.review.dto.ReviewSaveResult;
import com.menusolomon.review.repository.ReviewRepository;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewServiceImpl implements ReviewService {
    private final UserService users;
    private final TeamMemberRepository members;
    private final TeamRestaurantRepository restaurants;
    private final ReviewRepository reviews;
    private final Clock clock;

    public ReviewServiceImpl(UserService users, TeamMemberRepository members,
            TeamRestaurantRepository restaurants, ReviewRepository reviews, Clock clock) {
        this.users = users;
        this.members = members;
        this.restaurants = restaurants;
        this.reviews = reviews;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReviewSaveResult saveMyReview(Long teamId, Long teamRestaurantId, String rawSessionToken,
            int rating, String content) {
        var user = users.findBySessionToken(rawSessionToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        TeamMember member = requireActiveMember(teamId, user.getId());
        restaurants.findByIdAndTeamIdForUpdate(teamRestaurantId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_RESTAURANT_NOT_FOUND));
        var existing = reviews.findByTeamRestaurantIdAndTeamMemberId(teamRestaurantId, member.getId());
        Instant now = Instant.now(clock);
        Review review;
        boolean created = existing.isEmpty();
        if (created) {
            review = reviews.save(Review.create(teamRestaurantId, member.getId(), rating, content, now));
        } else {
            review = existing.get();
            review.update(rating, content, now);
        }
        return new ReviewSaveResult(ReviewResponse.from(review, user.getNickname()), created);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewListItem> getReviews(Long teamId, Long teamRestaurantId, String rawSessionToken) {
        TeamMember member = currentActiveMember(teamId, rawSessionToken);
        requireTeamRestaurant(teamId, teamRestaurantId);
        return reviews.findReviews(teamRestaurantId).stream()
                .map(row -> ReviewListItem.from(row, member.getId())).toList();
    }

    @Override
    @Transactional
    public void deleteMyReview(Long teamId, Long teamRestaurantId, String rawSessionToken) {
        TeamMember member = currentActiveMember(teamId, rawSessionToken);
        restaurants.findByIdAndTeamIdForUpdate(teamRestaurantId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_RESTAURANT_NOT_FOUND));
        Review review = reviews.findByTeamRestaurantIdAndTeamMemberId(teamRestaurantId, member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        reviews.delete(review);
    }

    private TeamMember currentActiveMember(Long teamId, String rawSessionToken) {
        var user = users.findBySessionToken(rawSessionToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        return requireActiveMember(teamId, user.getId());
    }

    private TeamMember requireActiveMember(Long teamId, Long userId) {
        return members.findByTeamIdAndUserId(teamId, userId).filter(TeamMember::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
    }

    private void requireTeamRestaurant(Long teamId, Long teamRestaurantId) {
        restaurants.findByIdAndTeamId(teamRestaurantId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_RESTAURANT_NOT_FOUND));
    }
}
