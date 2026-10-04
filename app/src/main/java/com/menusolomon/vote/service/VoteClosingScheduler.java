package com.menusolomon.vote.service;

import com.menusolomon.vote.repository.LunchVoteSessionRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.vote.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class VoteClosingScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(VoteClosingScheduler.class);
    private final LunchVoteSessionRepository sessions;
    private final VoteService service;
    private final Clock clock;
    public VoteClosingScheduler(LunchVoteSessionRepository sessions, VoteService service, Clock clock) {
        this.sessions=sessions; this.service=service; this.clock=clock;
    }
    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    public void closeExpiredVotes() {
        // Separate transactions keep locks bounded and let other sessions continue after a failure.
        for (Long id : sessions.findDueIds(Instant.now(clock), PageRequest.of(0, 100))) {
            try { service.settleExpired(id); }
            catch (RuntimeException exception) { LOG.error("Failed to settle vote {}", id, exception); }
        }
    }
}
