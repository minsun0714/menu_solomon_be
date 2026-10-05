package com.menusolomon.vote.service;

import static com.menusolomon.vote.fixture.VoteFixture.NOW;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.menusolomon.vote.repository.LunchVoteSessionRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class VoteClosingSchedulerTest {
    @Test void scheduler_settlesEachDueSessionIndependentlyAndContinuesAfterFailure() {
        var sessions=mock(LunchVoteSessionRepository.class);
        var service=mock(VoteService.class);
        when(sessions.findDueIds(eq(NOW),any())).thenReturn(List.of(1L,2L));
        doThrow(new IllegalStateException("DB unavailable")).when(service).settleExpired(1L);
        new VoteClosingScheduler(sessions,service,Clock.fixed(NOW,ZoneOffset.UTC)).closeExpiredVotes();
        verify(service).settleExpired(1L); verify(service).settleExpired(2L);
    }
}
