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

    @Scheduled(fixedDelay = 300_000)
    public void syncAndScore() {
        try {
            if (!syncService.hasActionableMatches()) {
                log.debug("MatchResultScheduler: no actionable matches — skipping");
                return;
            }
            List<Long> finished = syncService.syncResults();
            if (!finished.isEmpty()) {
                log.info("Scheduler: {} match(es) newly finished and scored: {}", finished.size(), finished);
            }
        } catch (Exception e) {
            log.error("Scheduler: unexpected error — will retry next cycle", e);
        }
    }
}
