package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.integration.football.LineupSyncService;
import com.worldcup.prediction.integration.football.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LineupSyncScheduler {

    private final LineupSyncService syncService;

    @Scheduled(fixedDelay = 1_800_000)
    public void syncLineups() {
        try {
            SyncResult result = syncService.syncLineups();
            if (!result.skipped()) log.info("LineupSync: {}", result.message());
            else log.debug("LineupSync: {}", result.message());
        } catch (Exception e) {
            log.error("LineupSyncScheduler: unexpected error — will retry next cycle", e);
        }
    }
}
