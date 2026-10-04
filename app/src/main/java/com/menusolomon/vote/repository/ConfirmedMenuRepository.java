package com.menusolomon.vote.repository;

import com.menusolomon.vote.domain.ConfirmedMenu;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConfirmedMenuRepository extends JpaRepository<ConfirmedMenu, Long> {
    @Query("""
            select new com.menusolomon.vote.repository.ConfirmedMenuRow(
                candidate.id, restaurant.id, restaurant.name,
                (select count(record.id) from VoteRecord record where record.voteCandidateId = candidate.id), menu.confirmedAt)
            from ConfirmedMenu menu join VoteCandidate candidate on candidate.id = menu.voteCandidateId
            join Restaurant restaurant on restaurant.id = candidate.restaurantId
            where menu.lunchVoteSessionId = :voteId
            """)
    Optional<ConfirmedMenuRow> findResult(@Param("voteId") Long voteId);

    @Query("""
            select new com.menusolomon.vote.repository.VoteHistoryRow(vote.id, vote.title, menu.confirmedAt,
                restaurant.id, restaurant.name,
                (select count(record.id) from VoteRecord record where record.voteCandidateId = candidate.id),
                (select count(participant.id) from VoteParticipant participant
                 where participant.lunchVoteSessionId = vote.id and participant.participating = true))
            from ConfirmedMenu menu join LunchVoteSession vote on vote.id = menu.lunchVoteSessionId
            join VoteCandidate candidate on candidate.id = menu.voteCandidateId
            join Restaurant restaurant on restaurant.id = candidate.restaurantId
            where vote.teamId = :teamId and vote.status = com.menusolomon.vote.domain.VoteStatus.CONFIRMED
            order by menu.confirmedAt desc, menu.id desc
            """)
    List<VoteHistoryRow> findHistory(@Param("teamId") Long teamId);
    @Modifying
    @Query("""
            delete from ConfirmedMenu menu where menu.lunchVoteSessionId in
                (select vote.id from LunchVoteSession vote where vote.teamId = :teamId)
            """)
    void deleteAllByTeamId(@Param("teamId") Long teamId);

}
