package com.menusolomon.vote.repository;

import com.menusolomon.restaurant.domain.Restaurant;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface VoteRecommendationRepository extends Repository<Restaurant, Long> {
    @Query("""
        select new com.menusolomon.vote.repository.RestaurantRow(r.id, r.kakaoPlaceId, r.name, r.address,
            r.latitude, r.longitude, r.category, r.kakaoPlaceUrl, avg(review.rating))
        from TeamRestaurant tr join Restaurant r on r.id=tr.restaurantId
        left join Review review on review.teamRestaurantId=tr.id and not exists
            (select p.id from VoteParticipant p where p.sessionId=:sessionId and p.teamMemberId=review.teamMemberId and p.participating=false)
        where tr.teamId=:teamId and not exists
            (select c.id from VoteCandidate c where c.sessionId=:sessionId and c.restaurantId=r.id)
            and not exists
            (select d.id from ConfirmedMenu d join LunchVoteSession s on s.id=d.sessionId
             where s.teamId=:teamId and d.restaurantId=r.id and d.confirmedAt >= :since)
        group by r.id, r.kakaoPlaceId, r.name, r.address, r.latitude, r.longitude, r.category, r.kakaoPlaceUrl
        order by coalesce(avg(review.rating), 0) desc, r.id asc
        """)
    List<RestaurantRow> findRecommendations(@Param("teamId") Long teamId, @Param("sessionId") Long sessionId,
            @Param("since") Instant since, Pageable pageable);
}
