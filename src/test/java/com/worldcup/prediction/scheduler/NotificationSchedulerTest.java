package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.LeaderboardService;
import com.worldcup.prediction.service.NotificationService;
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

    @InjectMocks NotificationScheduler scheduler;

    @Test
    void checkPredictionWindowOpen_noMatches_skips() {
        when(matchRepository.findMatchesWhereWindowShouldOpen(any())).thenReturn(List.of());
        scheduler.checkPredictionWindowOpen();
        verify(notificationService, never()).sendPredictionWindowOpen(anyList(), any(), anyLong());
    }

    @Test
    void checkPredictionDeadline_noApproachingMatches_skips() {
        when(matchRepository.findByKickoffTimeBetween(any(), any())).thenReturn(List.of());
        scheduler.checkPredictionDeadline();
        verify(notificationService, never()).sendPredictionReminders(anyList(), any(), anyLong());
    }

    @Test
    void checkLeaderboardDigest_noMatchesToday_skips() {
        when(matchRepository.findByKickoffTimeBetween(any(), any())).thenReturn(List.of());
        scheduler.checkLeaderboardDigest();
        verify(notificationService, never()).sendLeaderboardDigest(anyString(), anyList(), anyList(), anyList(), anyLong());
    }

    @Test
    void checkLeaderboardDigest_notAllCompleted_skips() {
        Match incomplete = Match.builder()
                .id(1L).status(MatchStatus.SCHEDULED)
                .kickoffTime(LocalDateTime.now())
                .stage(MatchStage.GROUP).matchNumber(1).build();
        when(matchRepository.findByKickoffTimeBetween(any(), any())).thenReturn(List.of(incomplete));
        scheduler.checkLeaderboardDigest();
        verify(notificationService, never()).sendLeaderboardDigest(anyString(), anyList(), anyList(), anyList(), anyLong());
    }
}
