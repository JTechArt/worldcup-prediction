package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.PredictionWindowStatus;
import com.worldcup.prediction.service.PredictionWindowService;
import com.worldcup.prediction.service.SchedulerLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PredictionWindowSchedulerTest {

    @Mock private PredictionWindowService windowService;
    @Mock private SchedulerLogService logService;
    @InjectMocks private PredictionWindowScheduler scheduler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(logService.start(any())).thenReturn(new SchedulerLog());
    }

    @Test
    void activateScheduledWindows_activatesEachReadyWindow() {
        PredictionWindow w1 = PredictionWindow.builder().id(1L)
                .status(PredictionWindowStatus.SCHEDULED).build();
        PredictionWindow w2 = PredictionWindow.builder().id(2L)
                .status(PredictionWindowStatus.SCHEDULED).build();
        when(windowService.findScheduledReadyToActivate(any())).thenReturn(List.of(w1, w2));

        scheduler.activateScheduledWindows();

        verify(windowService).activateWindow(1L);
        verify(windowService).activateWindow(2L);
        verify(logService).complete(any(), any(), eq(2), any());
    }

    @Test
    void activateScheduledWindows_skipsWhenNoneReady() {
        when(windowService.findScheduledReadyToActivate(any())).thenReturn(List.of());

        scheduler.activateScheduledWindows();

        verify(windowService, never()).activateWindow(any());
        verify(logService).complete(any(), any(), eq(0), any());
    }

    @Test
    void closeExpiredWindows_closesEachExpiredWindow() {
        PredictionWindow w = PredictionWindow.builder().id(3L)
                .status(PredictionWindowStatus.OPEN).build();
        when(windowService.findExpiredOpenWindows(any())).thenReturn(List.of(w));

        scheduler.closeExpiredWindows();

        verify(windowService).closeWindow(3L);
        verify(logService).complete(any(), any(), eq(1), any());
    }

    @Test
    void closeExpiredWindows_skipsWhenNoneExpired() {
        when(windowService.findExpiredOpenWindows(any())).thenReturn(List.of());

        scheduler.closeExpiredWindows();

        verify(windowService, never()).closeWindow(any());
    }

    @Test
    void activateScheduledWindows_logsFailureOnException() {
        when(windowService.findScheduledReadyToActivate(any())).thenThrow(new RuntimeException("DB error"));

        scheduler.activateScheduledWindows();

        verify(logService).fail(any(), eq("DB error"), any());
    }
}
