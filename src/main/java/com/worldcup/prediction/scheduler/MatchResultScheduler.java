package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.integration.football.FootballApiSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class MatchResultScheduler {

    private final FootballApiSyncService syncService;

    /**
     * Polls football-data.org every 5 minutes for new results.
     * fixedDelay means next run starts 5 minutes after previous run completes.
     */
    @Scheduled(fixedDelay = 300_000)
    public void syncAndScore() {
        try {
            List<Long> finished = syncService.syncResults();
            if (!finished.isEmpty()) {
                log.info("Scheduler: {} match(es) newly finished and scored: {}", finished.size(), finished);
            }
        } catch (Exception e) {
            log.error("Scheduler: unexpected error during match result sync — will retry next cycle", e);
        }
    }
}
