package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.integration.football.FootballApiSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchResultSchedulerTest {

    @Mock
    FootballApiSyncService syncService;

    @InjectMocks
    MatchResultScheduler scheduler;

    @Test
    void syncAndScore_callsSyncService() {
        when(syncService.syncResults()).thenReturn(List.of(1L, 2L));
        scheduler.syncAndScore();
        verify(syncService, times(1)).syncResults();
    }

    @Test
    void syncAndScore_whenSyncReturnsEmpty_doesNotThrow() {
        when(syncService.syncResults()).thenReturn(List.of());
        scheduler.syncAndScore();
        verify(syncService, times(1)).syncResults();
    }

    @Test
    void syncAndScore_whenSyncThrows_doesNotPropagate() {
        when(syncService.syncResults()).thenThrow(new RuntimeException("network failure"));
        scheduler.syncAndScore(); // must not throw
        verify(syncService, times(1)).syncResults();
    }
}
