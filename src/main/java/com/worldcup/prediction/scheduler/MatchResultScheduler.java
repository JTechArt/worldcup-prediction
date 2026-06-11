package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.integration.football.FootballApiSyncService;
import com.worldcup.prediction.service.SchedulerLogService;
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
    private final SchedulerLogService logService;

    @Scheduled(fixedDelay = 300_000)
    public void syncAndScore() {
        SchedulerLog entry = logService.start(SchedulerJobType.MATCH_RESULT.name());
        try {
            if (!syncService.hasActionableMatches()) {
                log.debug("MatchResultScheduler: no actionable matches — skipping");
                logService.complete(entry, SchedulerJobStatus.SKIPPED, 0, "No actionable matches");
                return;
            }
            List<Long> finished = syncService.syncResults();
            String msg = finished.isEmpty() ? "No new results" : finished.size() + " match(es) scored";
            if (!finished.isEmpty()) log.info("Scheduler: {} match(es) newly finished and scored: {}", finished.size(), finished);
            logService.complete(entry, SchedulerJobStatus.SUCCESS, finished.size(), msg);
        } catch (Exception e) {
            log.error("Scheduler: unexpected error — will retry next cycle", e);
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
