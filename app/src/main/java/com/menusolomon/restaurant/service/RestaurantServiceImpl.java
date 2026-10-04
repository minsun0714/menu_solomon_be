package com.menusolomon.restaurant.service;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import com.menusolomon.restaurant.domain.Restaurant;
import com.menusolomon.restaurant.domain.TeamRestaurant;
import com.menusolomon.restaurant.dto.RestaurantDetailResponse;
import com.menusolomon.restaurant.dto.RestaurantListResponse;
import com.menusolomon.restaurant.dto.RestaurantRegisterResponse;
import com.menusolomon.restaurant.dto.RestaurantSort;
import com.menusolomon.restaurant.repository.RestaurantRepository;
import com.menusolomon.restaurant.repository.TeamRestaurantRepository;
import com.menusolomon.review.repository.ReviewRepository;
import com.menusolomon.team.domain.TeamMember;
import com.menusolomon.team.repository.TeamMemberRepository;
import com.menusolomon.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantServiceImpl implements RestaurantService {
    private final RestaurantRepository restaurants;
    private final TeamRestaurantRepository teamRestaurants;
    private final TeamMemberRepository members;
    private final UserService users;
    private final Clock clock;
    private final ReviewRepository reviews;

    public RestaurantServiceImpl(RestaurantRepository restaurants, TeamRestaurantRepository teamRestaurants,
            TeamMemberRepository members, UserService users, Clock clock, ReviewRepository reviews) {
        this.restaurants = restaurants;
        this.teamRestaurants = teamRestaurants;
        this.members = members;
        this.users = users;
        this.clock = clock;
        this.reviews = reviews;
    }

    @Override
    @Transactional
    public RestaurantRegisterResponse registerRestaurant(Long teamId, String rawSessionToken, String kakaoPlaceId) {
        TeamMember member = requireActiveMember(teamId, rawSessionToken);
        Restaurant restaurant = restaurants.findByKakaoPlaceId(kakaoPlaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KAKAO_PLACE_NOT_FOUND));
        if (teamRestaurants.existsByTeamIdAndRestaurantId(teamId, restaurant.getId())) {
            throw new BusinessException(ErrorCode.RESTAURANT_ALREADY_REGISTERED);
        }
        try {
            TeamRestaurant link = teamRestaurants.saveAndFlush(TeamRestaurant.create(teamId, restaurant.getId(),
                    member.getId(), Instant.now(clock)));
            return RestaurantRegisterResponse.from(link, restaurant);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.RESTAURANT_ALREADY_REGISTERED);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantListResponse getRestaurants(Long teamId, String rawSessionToken, String keyword,
            String category, RestaurantSort sort) {
        requireActiveMember(teamId, rawSessionToken);
        Sort order = sort == RestaurantSort.NAME ? Sort.by("r.name").and(Sort.by("tr.id"))
                : Sort.by(Sort.Direction.DESC, "tr.createdAt", "tr.id");
        var rows = sort == RestaurantSort.RATING_DESC
                ? teamRestaurants.findListByRating(teamId, normalized(keyword), normalized(category))
                : teamRestaurants.findList(teamId, normalized(keyword), normalized(category), order);
        var items = rows.stream().map(RestaurantDetailResponse::from).toList();
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        teamRestaurants.countCategories(teamId).forEach(count -> categoryCounts.put(count.category(), count.count()));
        long totalCount = categoryCounts.values().stream().mapToLong(Long::longValue).sum();
        return new RestaurantListResponse(items, totalCount, reviews.countByTeamId(teamId), categoryCounts);
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantDetailResponse getRestaurant(Long teamId, Long teamRestaurantId, String rawSessionToken) {
        requireActiveMember(teamId, rawSessionToken);
        return teamRestaurants.findDetail(teamRestaurantId, teamId).map(RestaurantDetailResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_RESTAURANT_NOT_FOUND));
    }

    @Override
    @Transactional
    public void deleteRestaurant(Long teamId, Long teamRestaurantId, String rawSessionToken) {
        requireActiveMember(teamId, rawSessionToken);
        TeamRestaurant link = teamRestaurants.findByIdAndTeamIdForUpdate(teamRestaurantId, teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_RESTAURANT_NOT_FOUND));
        reviews.deleteByTeamRestaurantId(teamRestaurantId);
        teamRestaurants.delete(link);
    }

    private TeamMember requireActiveMember(Long teamId, String rawSessionToken) {
        var user = users.findBySessionToken(rawSessionToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
        return members.findByTeamIdAndUserId(teamId, user.getId()).filter(TeamMember::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_TEAM_MEMBER));
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
