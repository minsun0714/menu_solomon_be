package com.menusolomon.restaurant.repository;

import com.menusolomon.restaurant.domain.TeamRestaurant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRestaurantRepository extends JpaRepository<TeamRestaurant, Long> {
    String ROW_QUERY = """
            select new com.menusolomon.restaurant.repository.TeamRestaurantRow(
                tr.id, r.id, r.kakaoPlaceId, r.name, r.address, r.latitude, r.longitude,
                r.category, r.kakaoPlaceUrl, u.nickname, tr.createdAt)
            from TeamRestaurant tr
            join Restaurant r on r.id = tr.restaurantId
            join TeamMember tm on tm.id = tr.registeredByTeamMemberId
            join User u on u.id = tm.userId
            """;

    boolean existsByTeamIdAndRestaurantId(Long teamId, Long restaurantId);
    Optional<TeamRestaurant> findByIdAndTeamId(Long id, Long teamId);

    @Query(ROW_QUERY + """
            where tr.teamId = :teamId
              and (:keyword is null or lower(r.name) like lower(concat('%', :keyword, '%'))
                   or lower(r.category) like lower(concat('%', :keyword, '%'))
                   or lower(r.address) like lower(concat('%', :keyword, '%')))
              and (:category is null or r.category = :category)
            """)
    List<TeamRestaurantRow> findList(@Param("teamId") Long teamId, @Param("keyword") String keyword,
            @Param("category") String category, Sort sort);

    @Query(ROW_QUERY + "where tr.id = :id and tr.teamId = :teamId")
    Optional<TeamRestaurantRow> findDetail(@Param("id") Long id, @Param("teamId") Long teamId);

    @Query("""
            select new com.menusolomon.restaurant.repository.CategoryCount(r.category, count(tr.id))
            from TeamRestaurant tr join Restaurant r on r.id = tr.restaurantId
            where tr.teamId = :teamId group by r.category order by r.category
            """)
    List<CategoryCount> countCategories(@Param("teamId") Long teamId);
}
