package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.LeaderboardService;
import com.worldcup.prediction.service.NotificationService;
import com.worldcup.prediction.service.PredictionWindowService;
import com.worldcup.prediction.service.RoundWindowService;
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
class NotificationSchedulerTest {

    @Mock MatchRepository matchRepository;
    @Mock UserRepository userRepository;
    @Mock PredictionRepository predictionRepository;
    @Mock NotificationService notificationService;
    @Mock LeaderboardService leaderboardService;
    @Mock CommunityRepository communityRepository;
    @Mock RoundWindowService roundWindowService;
    @Mock PredictionWindowService predictionWindowService;
    @Mock SchedulerLogService logService;
    @InjectMocks NotificationScheduler scheduler;

    private final SchedulerLog stubLog = SchedulerLog.builder()
            .id(1L).status(SchedulerJobStatus.IN_PROGRESS).startedAt(LocalDateTime.now()).build();

    @BeforeEach
    void setUp() {
        when(logService.start(anyString())).thenReturn(stubLog);
    }

    @Test
    void checkPredictionWindowOpen_noMatches_logsSkipped() {
        when(roundWindowService.findAll()).thenReturn(List.of());
        when(predictionWindowService.findAllGlobal()).thenReturn(List.of());
        scheduler.checkPredictionWindowOpen();
        verify(notificationService, never()).sendPredictionWindowOpen(anyList(), any(), anyLong());
        verify(logService).complete(stubLog, SchedulerJobStatus.SKIPPED, 0, "No newly-open rounds or windows");
    }

    @Test
    void checkPredictionDeadline_noApproachingMatches_logsSkipped() {
        when(matchRepository.findByKickoffTimeBetween(any(), any())).thenReturn(List.of());
        scheduler.checkPredictionDeadline();
        verify(notificationService, never()).sendPredictionReminders(anyList(), any(), anyLong());
        verify(logService).complete(stubLog, SchedulerJobStatus.SKIPPED, 0, "No approaching deadlines");
    }

    @Test
    void checkLeaderboardDigest_noMatchesToday_logsSkipped() {
        when(matchRepository.findByKickoffTimeBetweenWithTeams(any(), any())).thenReturn(List.of());
        scheduler.checkLeaderboardDigest();
        verify(notificationService, never()).sendLeaderboardDigest(anyString(), anyList(), anyList(), anyList(), anyLong());
        verify(logService).complete(stubLog, SchedulerJobStatus.SKIPPED, 0, "No matches today");
    }

    @Test
    void checkLeaderboardDigest_notAllCompleted_logsSkipped() {
        Match incomplete = Match.builder().id(1L).status(MatchStatus.SCHEDULED)
                .kickoffTime(LocalDateTime.now()).stage(MatchStage.GROUP).matchNumber(1).build();
        when(matchRepository.findByKickoffTimeBetweenWithTeams(any(), any())).thenReturn(List.of(incomplete));
        scheduler.checkLeaderboardDigest();
        verify(notificationService, never()).sendLeaderboardDigest(anyString(), anyList(), anyList(), anyList(), anyLong());
        verify(logService).complete(stubLog, SchedulerJobStatus.SKIPPED, 0, "Not all today's matches completed");
    }
}
