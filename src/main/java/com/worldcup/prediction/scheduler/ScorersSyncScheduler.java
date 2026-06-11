package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.integration.football.ScorersService;
import com.worldcup.prediction.integration.football.SyncResult;
import com.worldcup.prediction.service.SchedulerLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ScorersSyncScheduler {

    private final ScorersService scorersService;
    private final SchedulerLogService logService;

    @Scheduled(cron = "0 0 2 * * *", zone = "${app.timezone}")
    public void syncScorers() {
        SchedulerLog entry = logService.start(SchedulerJobType.SCORERS_SYNC.name());
        try {
            SyncResult result = scorersService.syncScorers();
            if (!result.skipped()) log.info("ScorersSync: {}", result.message());
            else log.debug("ScorersSync: {}", result.message());
            SchedulerJobStatus status = result.skipped() ? SchedulerJobStatus.SKIPPED : SchedulerJobStatus.SUCCESS;
            logService.complete(entry, status, 0, result.message());
        } catch (Exception e) {
            log.error("ScorersSyncScheduler: unexpected error — will retry next cycle", e);
            logService.fail(entry, e.getMessage(), SchedulerLogService.stackTraceString(e));
        }
    }
}
