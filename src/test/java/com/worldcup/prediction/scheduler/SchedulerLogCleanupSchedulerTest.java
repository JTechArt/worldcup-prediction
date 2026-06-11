package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.service.SchedulerLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerLogCleanupSchedulerTest {

    @Mock SchedulerLogService logService;
    @InjectMocks SchedulerLogCleanupScheduler scheduler;

    @Test
    void cleanup_callsLogServiceCleanup() {
        scheduler.cleanup();
        verify(logService).cleanup();
    }

    @Test
    void cleanup_whenServiceThrows_doesNotPropagate() {
        doThrow(new RuntimeException("db error")).when(logService).cleanup();
        scheduler.cleanup(); // must not throw
    }
}
