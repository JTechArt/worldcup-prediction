package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.scheduler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerRunnerServiceTest {

    @Mock MatchResultScheduler matchResultScheduler;
    @Mock StandingSyncScheduler standingSyncScheduler;
    @Mock ScorersSyncScheduler scorersSyncScheduler;
    @Mock SchedulerLogService logService;
    @InjectMocks SchedulerRunnerService service;

    @Test
    void run_matchResult_callsSyncAndScore() {
        SchedulerLog log = SchedulerLog.builder().status(SchedulerJobStatus.SUCCESS).message("2 match(es) scored").build();
        when(logService.findLatest("MATCH_RESULT")).thenReturn(Optional.of(log));
        String result = service.run(SchedulerJobType.MATCH_RESULT);
        verify(matchResultScheduler).syncAndScore();
        assertThat(result).contains("SUCCESS");
    }

    @Test
    void run_lineupSync_callsSyncLineups() {
        LineupSyncScheduler mockLineup = mock(LineupSyncScheduler.class);
        ReflectionTestUtils.setField(service, "lineupSyncScheduler", mockLineup);
        SchedulerLog log = SchedulerLog.builder().status(SchedulerJobStatus.SUCCESS).message("Lineups synced").build();
        when(logService.findLatest("LINEUP_SYNC")).thenReturn(Optional.of(log));
        service.run(SchedulerJobType.LINEUP_SYNC);
        verify(mockLineup).syncLineups();
    }

    @Test
    void run_standingSync_callsSyncStandings() {
        when(logService.findLatest("STANDING_SYNC")).thenReturn(Optional.empty());
        service.run(SchedulerJobType.STANDING_SYNC);
        verify(standingSyncScheduler).syncStandings();
    }

    @Test
    void run_scorersSync_callsSyncScorers() {
        when(logService.findLatest("SCORERS_SYNC")).thenReturn(Optional.empty());
        service.run(SchedulerJobType.SCORERS_SYNC);
        verify(scorersSyncScheduler).syncScorers();
    }

    @Test
    void run_notifWindowOpen_whenNotificationSchedulerNull_logsSkipped() {
        // notificationScheduler field is null (not injected — @ConditionalOnProperty disabled)
        SchedulerLog stubLog = SchedulerLog.builder().id(1L).status(SchedulerJobStatus.IN_PROGRESS).startedAt(LocalDateTime.now()).build();
        when(logService.start("NOTIF_WINDOW_OPEN")).thenReturn(stubLog);
        String result = service.run(SchedulerJobType.NOTIF_WINDOW_OPEN);
        verify(logService).complete(stubLog, SchedulerJobStatus.SKIPPED, 0, "Scheduler disabled (app.notification.enabled=false)");
        assertThat(result).contains("SKIPPED");
    }

    @Test
    void run_notifWindowOpen_whenNotificationSchedulerPresent_callsMethod() {
        NotificationScheduler mockNotif = mock(NotificationScheduler.class);
        ReflectionTestUtils.setField(service, "notificationScheduler", mockNotif);
        SchedulerLog log = SchedulerLog.builder().status(SchedulerJobStatus.SKIPPED).message("No newly-open rounds").build();
        when(logService.findLatest("NOTIF_WINDOW_OPEN")).thenReturn(Optional.of(log));
        service.run(SchedulerJobType.NOTIF_WINDOW_OPEN);
        verify(mockNotif).checkPredictionWindowOpen();
    }
}
