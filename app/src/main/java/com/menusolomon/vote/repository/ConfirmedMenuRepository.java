package com.menusolomon.vote.repository;

import com.menusolomon.vote.domain.ConfirmedMenu;
import com.menusolomon.vote.domain.LunchVoteSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConfirmedMenuRepository extends JpaRepository<ConfirmedMenu, Long> {
    Optional<ConfirmedMenu> findBySessionId(Long sessionId);
    @Query("""
        select new com.menusolomon.vote.repository.VoteHistoryRow(d.id, d.sessionId, d.confirmedAt, d.confirmationType, m.nickname,
            r.id, r.kakaoPlaceId, r.name, r.address, r.latitude, r.longitude, r.category, r.kakaoPlaceUrl)
        from ConfirmedMenu d join LunchVoteSession s on s.id=d.sessionId join Restaurant r on r.id=d.restaurantId
        left join TeamMember m on m.id=d.confirmedByTeamMemberId
        where s.teamId=:teamId and s.status=com.menusolomon.vote.domain.VoteStatus.CONFIRMED
            and d.confirmedAt >= :from and d.confirmedAt < :until order by d.confirmedAt desc, d.id desc
        """)
    List<VoteHistoryRow> findHistory(@Param("teamId") Long teamId, @Param("from") Instant from, @Param("until") Instant until);
    @Modifying @Query("delete from ConfirmedMenu d where d.sessionId=:id")
    void deleteBySession(@Param("id") Long id);
    @Modifying @Query("delete from ConfirmedMenu d where d.sessionId in (select s.id from LunchVoteSession s where s.teamId=:teamId)")
    void deleteAllByTeamId(@Param("teamId") Long teamId);
}
