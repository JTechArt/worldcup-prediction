package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.integration.football.StandingSyncService;
import com.worldcup.prediction.integration.football.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class StandingSyncScheduler {

    private final StandingSyncService syncService;

    @Scheduled(cron = "0 0 */6 * * *")
    public void syncStandings() {
        try {
            SyncResult result = syncService.syncStandings();
            if (!result.skipped()) log.info("StandingSync: {}", result.message());
            else log.debug("StandingSync: {}", result.message());
        } catch (Exception e) {
            log.error("StandingSyncScheduler: unexpected error — will retry next cycle", e);
        }
    }
}
