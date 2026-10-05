package com.menusolomon.vote.repository;

import com.menusolomon.vote.domain.LunchVoteSession;
import com.menusolomon.vote.domain.VoteCandidate;
import com.menusolomon.vote.domain.VoteRecord;
import com.menusolomon.vote.domain.VoteTally;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteCandidateRepository extends JpaRepository<VoteCandidate, Long> {
    boolean existsBySessionIdAndRestaurantId(Long sessionId, Long restaurantId);
    Optional<VoteCandidate> findByIdAndSessionId(Long id, Long sessionId);
    long countBySessionIdAndIdIn(Long sessionId, Collection<Long> ids);
    Optional<VoteCandidate> findBySessionIdAndRestaurantId(Long sessionId, Long restaurantId);
    String DETAIL = """
        select new com.menusolomon.vote.repository.VoteCandidateRow(c.id, c.sessionId, c.source, r.id, r.kakaoPlaceId, r.name, r.address,
            r.latitude, r.longitude, r.category, r.kakaoPlaceUrl,
            (select avg(review.rating) from Review review join TeamRestaurant tr on tr.id=review.teamRestaurantId
                where tr.teamId=:teamId and tr.restaurantId=r.id))
        from VoteCandidate c join Restaurant r on r.id=c.restaurantId
        """;
    @Query(DETAIL + "where c.sessionId=:id order by c.createdAt, c.id")
    List<VoteCandidateRow> findDetails(@Param("id") Long id, @Param("teamId") Long teamId);
    @Query(DETAIL + "where c.sessionId=:sessionId and c.id=:id")
    Optional<VoteCandidateRow> findDetail(@Param("id") Long id, @Param("sessionId") Long sessionId, @Param("teamId") Long teamId);
    @Query("""
        select new com.menusolomon.vote.domain.VoteTally$Entry(c.id, c.restaurantId, count(b.id))
        from VoteCandidate c left join VoteRecord b on b.candidateId=c.id and b.sessionId=c.sessionId
        where c.sessionId=:id group by c.id, c.restaurantId order by c.id
        """)
    List<VoteTally.Entry> findTally(@Param("id") Long id);
    @Modifying @Query("delete from VoteCandidate c where c.sessionId=:id")
    void deleteBySession(@Param("id") Long id);
    @Modifying @Query("delete from VoteCandidate c where c.sessionId in (select s.id from LunchVoteSession s where s.teamId=:teamId)")
    void deleteAllByTeamId(@Param("teamId") Long teamId);
}
