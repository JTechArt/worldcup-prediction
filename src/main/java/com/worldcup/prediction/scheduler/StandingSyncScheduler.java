package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.integration.football.StandingSyncService;
import com.worldcup.prediction.integration.football.SyncResult;
import com.worldcup.prediction.service.SchedulerLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class StandingSyncScheduler {

    private final StandingSyncService syncService;
    private final SchedulerLogService logService;

    @Scheduled(cron = "0 0 */6 * * *", zone = "${app.timezone}")
    public void syncStandings() {
        SchedulerLog entry = logService.start(SchedulerJobType.STANDING_SYNC.name());
        try {
            SyncResult result = syncService.syncStandings();
            if (!result.skipped()) log.info("StandingSync: {}", result.message());
            else log.debug("StandingSync: {}", result.message());
            SchedulerJobStatus status = result.skipped() ? SchedulerJobStatus.SKIPPED : SchedulerJobStatus.SUCCESS;
            logService.complete(entry, status, 0, result.message());
        } catch (Exception e) {
            log.error("StandingSyncScheduler: unexpected error — will retry next cycle", e);
            logService.fail(entry, e.getMessage(), stackTraceString(e));
        }
    }

    private static String stackTraceString(Throwable e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
