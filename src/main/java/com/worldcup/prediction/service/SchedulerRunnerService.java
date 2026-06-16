package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.scheduler.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SchedulerRunnerService {

    private final MatchResultScheduler matchResultScheduler;
    private final StandingSyncScheduler standingSyncScheduler;
    private final ScorersSyncScheduler scorersSyncScheduler;
    private final SchedulerLogService logService;

    @Autowired(required = false)
    private LineupSyncScheduler lineupSyncScheduler;

    @Autowired(required = false)
    private NotificationScheduler notificationScheduler;

    @Autowired(required = false)
    private PredictionWindowScheduler predictionWindowScheduler;

    public String run(SchedulerJobType jobType) {
        switch (jobType) {
            case MATCH_RESULT    -> matchResultScheduler.syncAndScore();
            case LINEUP_SYNC     -> {
                if (lineupSyncScheduler == null) return logDisabled(jobType);
                lineupSyncScheduler.syncLineups();
            }
            case STANDING_SYNC   -> standingSyncScheduler.syncStandings();
            case SCORERS_SYNC    -> scorersSyncScheduler.syncScorers();
            case NOTIF_WINDOW_OPEN -> {
                if (notificationScheduler == null) return logDisabled(jobType);
                notificationScheduler.checkPredictionWindowOpen();
            }
            case NOTIF_DEADLINE  -> {
                if (notificationScheduler == null) return logDisabled(jobType);
                notificationScheduler.checkPredictionDeadline();
            }
            case NOTIF_DIGEST    -> {
                if (notificationScheduler == null) return logDisabled(jobType);
                notificationScheduler.checkLeaderboardDigest();
            }
            case PREDICTION_WINDOW_ACTIVATE -> {
                if (predictionWindowScheduler == null) return logDisabled(jobType);
                predictionWindowScheduler.activateScheduledWindows();
            }
            case PREDICTION_WINDOW_CLOSE -> {
                if (predictionWindowScheduler == null) return logDisabled(jobType);
                predictionWindowScheduler.closeExpiredWindows();
            }
            default -> throw new IllegalArgumentException("Unhandled job type: " + jobType);
        }
        return logService.findLatest(jobType.name())
                .map(l -> l.getStatus() + ": " + (l.getMessage() != null ? l.getMessage() : ""))
                .orElse("Triggered");
    }

    private String logDisabled(SchedulerJobType jobType) {
        SchedulerLog log = logService.start(jobType.name());
        String reason = "Scheduler disabled (" + jobType.getEnabledProperty() + "=false)";
        logService.complete(log, SchedulerJobStatus.SKIPPED, 0, reason);
        return "SKIPPED: " + reason;
    }
}
