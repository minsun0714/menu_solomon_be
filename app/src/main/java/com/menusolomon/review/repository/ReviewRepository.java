package com.menusolomon.review.repository;

import com.menusolomon.review.domain.Review;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Review> findByTeamRestaurantIdAndTeamMemberId(Long teamRestaurantId, Long teamMemberId);

    @Query("""
            select new com.menusolomon.review.repository.ReviewRow(
                review.id, review.teamMemberId, review.rating, review.content,
                member.nickname, review.createdAt, review.updatedAt)
            from Review review
            join TeamMember member on member.id = review.teamMemberId
            where review.teamRestaurantId = :teamRestaurantId
            order by review.updatedAt desc, review.id desc
            """)
    List<ReviewRow> findReviews(@Param("teamRestaurantId") Long teamRestaurantId);

    @Query("""
            select count(review.id) from Review review
            join TeamRestaurant restaurant on restaurant.id = review.teamRestaurantId
            where restaurant.teamId = :teamId
            """)
    long countByTeamId(@Param("teamId") Long teamId);

    @Modifying
    @Query("delete from Review review where review.teamRestaurantId = :teamRestaurantId")
    void deleteByTeamRestaurantId(@Param("teamRestaurantId") Long teamRestaurantId);
    @Modifying
    @Query("""
            delete from Review review where review.teamRestaurantId in
                (select restaurant.id from TeamRestaurant restaurant where restaurant.teamId = :teamId)
            """)
    void deleteAllByTeamId(@Param("teamId") Long teamId);

}
