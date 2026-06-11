package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.integration.football.FootballApiSyncService;
import com.worldcup.prediction.service.SchedulerLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchResultSchedulerTest {

    @Mock FootballApiSyncService syncService;
    @Mock SchedulerLogService logService;
    @InjectMocks MatchResultScheduler scheduler;

    private final SchedulerLog stubLog = SchedulerLog.builder()
            .id(1L).jobName("MATCH_RESULT").status(SchedulerJobStatus.IN_PROGRESS)
            .startedAt(LocalDateTime.now()).build();

    @BeforeEach
    void setUp() {
        when(logService.start(anyString())).thenReturn(stubLog);
    }

    @Test
    void syncAndScore_callsSyncService() {
        when(syncService.hasActionableMatches()).thenReturn(true);
        when(syncService.syncResults()).thenReturn(List.of(1L, 2L));
        scheduler.syncAndScore();
        verify(syncService).syncResults();
        verify(logService).complete(stubLog, SchedulerJobStatus.SUCCESS, 2, "2 match(es) scored");
    }

    @Test
    void syncAndScore_whenSyncReturnsEmpty_logsSuccess() {
        when(syncService.hasActionableMatches()).thenReturn(true);
        when(syncService.syncResults()).thenReturn(List.of());
        scheduler.syncAndScore();
        verify(logService).complete(stubLog, SchedulerJobStatus.SUCCESS, 0, "No new results");
    }

    @Test
    void syncAndScore_whenSyncThrows_logsFailed() {
        when(syncService.hasActionableMatches()).thenReturn(true);
        when(syncService.syncResults()).thenThrow(new RuntimeException("network failure"));
        scheduler.syncAndScore();
        verify(logService).fail(eq(stubLog), eq("network failure"), anyString());
    }

    @Test
    void syncAndScore_whenNoActionableMatches_logsSkipped() {
        when(syncService.hasActionableMatches()).thenReturn(false);
        scheduler.syncAndScore();
        verify(syncService, never()).syncResults();
        verify(logService).complete(stubLog, SchedulerJobStatus.SKIPPED, 0, "No actionable matches");
    }
}
