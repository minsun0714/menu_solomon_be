package com.menusolomon.vote.repository;

import com.menusolomon.vote.domain.VoteCandidate;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteCandidateRepository extends JpaRepository<VoteCandidate, Long> {
    boolean existsByLunchVoteSessionIdAndRestaurantId(Long voteId, Long restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VoteCandidate> findByIdAndLunchVoteSessionId(Long id, Long voteId);

    String CANDIDATE = """
            select new com.menusolomon.vote.repository.VoteCandidateRow(
                c.id, r.id, r.name, r.category, r.address,
                (select avg(review.rating) from Review review join TeamRestaurant tr on tr.id = review.teamRestaurantId
                 where tr.teamId = :teamId and tr.restaurantId = r.id),
                (select count(record.id) from VoteRecord record where record.voteCandidateId = c.id),
                case when exists(select record.id from VoteRecord record
                    where record.voteCandidateId = c.id and record.teamMemberId = :memberId) then true else false end)
            from VoteCandidate c join Restaurant r on r.id = c.restaurantId
            join LunchVoteSession vote on vote.id = c.lunchVoteSessionId
            """;

    @Query(CANDIDATE + "where vote.id = :voteId and vote.teamId = :teamId order by c.createdAt, c.id")
    List<VoteCandidateRow> findCandidates(@Param("voteId") Long voteId, @Param("teamId") Long teamId,
            @Param("memberId") Long memberId);

    @Query(CANDIDATE + "where c.id = :id and vote.id = :voteId and vote.teamId = :teamId")
    Optional<VoteCandidateRow> findCandidate(@Param("id") Long id, @Param("voteId") Long voteId,
            @Param("teamId") Long teamId, @Param("memberId") Long memberId);
    @Modifying
    @Query("""
            delete from VoteCandidate candidate where candidate.lunchVoteSessionId in
                (select vote.id from LunchVoteSession vote where vote.teamId = :teamId)
            """)
    void deleteAllByTeamId(@Param("teamId") Long teamId);

}
