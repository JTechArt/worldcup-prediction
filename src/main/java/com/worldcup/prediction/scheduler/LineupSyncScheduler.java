package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.integration.football.LineupSyncService;
import com.worldcup.prediction.integration.football.SyncResult;
import com.worldcup.prediction.service.SchedulerLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LineupSyncScheduler {

    private final LineupSyncService syncService;
    private final SchedulerLogService logService;

    @Scheduled(fixedDelay = 1_800_000)
    public void syncLineups() {
        SchedulerLog entry = logService.start(SchedulerJobType.LINEUP_SYNC.name());
        try {
            SyncResult result = syncService.syncLineups();
            if (!result.skipped()) log.info("LineupSync: {}", result.message());
            else log.debug("LineupSync: {}", result.message());
            SchedulerJobStatus status = result.skipped() ? SchedulerJobStatus.SKIPPED : SchedulerJobStatus.SUCCESS;
            logService.complete(entry, status, 0, result.message());
        } catch (Exception e) {
            log.error("LineupSyncScheduler: unexpected error — will retry next cycle", e);
            logService.fail(entry, e.getMessage(), SchedulerLogService.stackTraceString(e));
        }
    }
}
