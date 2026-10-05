package com.menusolomon.vote.domain;

import com.menusolomon.common.exception.BusinessException;
import com.menusolomon.common.exception.ErrorCode;
import java.util.List;
import java.util.Optional;

public record VoteTally(List<Entry> entries, long voterCount) {
    public record Entry(Long candidateId, Long restaurantId, long votes) {}
    public Optional<Entry> automaticWinner() {
        var leaders = leaders();
        return leaders.size() == 1 && leaders.getFirst().votes() > 0 ? Optional.of(leaders.getFirst()) : Optional.empty();
    }
    public void requireManualCandidate(Long restaurantId) {
        var leaders = leaders();
        boolean singleUnvotedCandidate = entries.size() == 1 && entries.getFirst().votes() == 0;
        if ((!singleUnvotedCandidate && leaders.size() < 2)
                || leaders.stream().noneMatch(entry -> entry.restaurantId().equals(restaurantId)))
            throw new BusinessException(ErrorCode.INVALID_DECISION_CANDIDATE);
    }
    public double percentage(long votes) { return voterCount == 0 ? 0 : 100.0 * votes / voterCount; }
    private List<Entry> leaders() {
        long max = entries.stream().mapToLong(Entry::votes).max().orElse(0);
        return entries.stream().filter(entry -> entry.votes() == max).toList();
    }
}
