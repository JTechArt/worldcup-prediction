package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.integration.football.ScorersService;
import com.worldcup.prediction.integration.football.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ScorersSyncScheduler {

    private final ScorersService scorersService;

    @Scheduled(cron = "0 0 2 * * *")
    public void syncScorers() {
        try {
            SyncResult result = scorersService.syncScorers();
            if (!result.skipped()) log.info("ScorersSync: {}", result.message());
            else log.debug("ScorersSync: {}", result.message());
        } catch (Exception e) {
            log.error("ScorersSyncScheduler: unexpected error — will retry next cycle", e);
        }
    }
}
