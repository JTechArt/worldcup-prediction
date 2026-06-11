package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.service.SchedulerLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SchedulerLogCleanupScheduler {

    private final SchedulerLogService logService;

    @Scheduled(fixedDelay = 86_400_000) // daily
    public void cleanup() {
        try {
            logService.cleanup();
            log.debug("SchedulerLogCleanup: purged entries older than 7 days");
        } catch (Exception e) {
            log.error("SchedulerLogCleanup: error during cleanup", e);
        }
    }
}
