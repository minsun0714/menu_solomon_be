package com.menusolomon.restaurant.repository;

import com.menusolomon.restaurant.domain.TeamRestaurant;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRestaurantRepository extends JpaRepository<TeamRestaurant, Long> {
    String ROW_QUERY = """
            select new com.menusolomon.restaurant.repository.TeamRestaurantRow(
                tr.id, r.id, r.kakaoPlaceId, r.name, r.address, r.latitude, r.longitude,
                r.category, r.kakaoPlaceUrl, u.nickname, tr.createdAt,
                (select avg(review.rating) from Review review where review.teamRestaurantId = tr.id),
                (select count(review.id) from Review review where review.teamRestaurantId = tr.id),
                latestUser.nickname, latest.rating, latest.content, latest.updatedAt)
            from TeamRestaurant tr
            join Restaurant r on r.id = tr.restaurantId
            join TeamMember tm on tm.id = tr.registeredByTeamMemberId
            join User u on u.id = tm.userId
            left join Review latest on latest.teamRestaurantId = tr.id
                and not exists (select newer.id from Review newer
                    where newer.teamRestaurantId = tr.id and
                    (newer.updatedAt > latest.updatedAt or
                     (newer.updatedAt = latest.updatedAt and newer.id > latest.id)))
            left join TeamMember latestMember on latestMember.id = latest.teamMemberId
            left join User latestUser on latestUser.id = latestMember.userId
            """;

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tr from TeamRestaurant tr where tr.id = :id and tr.teamId = :teamId")
    Optional<TeamRestaurant> findByIdAndTeamIdForUpdate(@Param("id") Long id, @Param("teamId") Long teamId);

    boolean existsByTeamIdAndRestaurantId(Long teamId, Long restaurantId);
    Optional<TeamRestaurant> findByIdAndTeamId(Long id, Long teamId);

    String LIST_FILTER = """
            where tr.teamId = :teamId
              and (:keyword is null or lower(r.name) like lower(concat('%', :keyword, '%'))
                   or lower(r.category) like lower(concat('%', :keyword, '%'))
                   or lower(r.address) like lower(concat('%', :keyword, '%')))
              and (:category is null or r.category = :category)
            """;

    @Query(ROW_QUERY + LIST_FILTER)
    List<TeamRestaurantRow> findList(@Param("teamId") Long teamId, @Param("keyword") String keyword,
            @Param("category") String category, Sort sort);

    @Query(ROW_QUERY + LIST_FILTER + """
            order by (select coalesce(avg(review.rating), 0.0) from Review review
                      where review.teamRestaurantId = tr.id) desc,
                     tr.createdAt desc, tr.id desc
            """)
    List<TeamRestaurantRow> findListByRating(@Param("teamId") Long teamId,
            @Param("keyword") String keyword, @Param("category") String category);

    @Query(ROW_QUERY + "where tr.id = :id and tr.teamId = :teamId")
    Optional<TeamRestaurantRow> findDetail(@Param("id") Long id, @Param("teamId") Long teamId);

    @Query("""
            select new com.menusolomon.restaurant.repository.CategoryCount(r.category, count(tr.id))
            from TeamRestaurant tr join Restaurant r on r.id = tr.restaurantId
            where tr.teamId = :teamId group by r.category order by r.category
            """)
    List<CategoryCount> countCategories(@Param("teamId") Long teamId);
    @Modifying
    @Query("delete from TeamRestaurant restaurant where restaurant.teamId = :teamId")
    void deleteAllByTeamId(@Param("teamId") Long teamId);

}
