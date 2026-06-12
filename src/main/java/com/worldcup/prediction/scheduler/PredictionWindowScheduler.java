package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.service.PredictionWindowService;
import com.worldcup.prediction.service.SchedulerLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.prediction-window.enabled", havingValue = "true")
public class PredictionWindowScheduler {

    private final PredictionWindowService windowService;
    private final SchedulerLogService logService;

    @Scheduled(fixedDelay = 300_000)
    public void activateScheduledWindows() {
        SchedulerLog entry = logService.start("PREDICTION_WINDOW_ACTIVATE");
        try {
            List<PredictionWindow> ready = windowService.findScheduledReadyToActivate(LocalDateTime.now());
            if (ready.isEmpty()) {
                logService.complete(entry, SchedulerJobStatus.SKIPPED, 0, "No windows ready to activate");
                return;
            }
            for (PredictionWindow w : ready) {
                windowService.activateWindow(w.getId());
                log.info("Activated prediction window id={} label={}", w.getId(), w.getLabel());
            }
            logService.complete(entry, SchedulerJobStatus.SUCCESS, ready.size(),
                    ready.size() + " window(s) activated");
        } catch (Exception e) {
            log.error("PredictionWindowScheduler.activateScheduledWindows error", e);
            logService.fail(entry, e.getMessage(), SchedulerLogService.stackTraceString(e));
        }
    }

    @Scheduled(fixedDelay = 300_000)
    public void closeExpiredWindows() {
        SchedulerLog entry = logService.start("PREDICTION_WINDOW_CLOSE");
        try {
            List<PredictionWindow> expired = windowService.findExpiredOpenWindows(LocalDateTime.now());
            if (expired.isEmpty()) {
                logService.complete(entry, SchedulerJobStatus.SKIPPED, 0, "No expired windows");
                return;
            }
            for (PredictionWindow w : expired) {
                windowService.closeWindow(w.getId());
                log.info("Closed expired prediction window id={} label={}", w.getId(), w.getLabel());
            }
            logService.complete(entry, SchedulerJobStatus.SUCCESS, expired.size(),
                    expired.size() + " window(s) closed");
        } catch (Exception e) {
            log.error("PredictionWindowScheduler.closeExpiredWindows error", e);
            logService.fail(entry, e.getMessage(), SchedulerLogService.stackTraceString(e));
        }
    }
}
