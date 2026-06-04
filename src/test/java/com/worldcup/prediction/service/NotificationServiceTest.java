package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock EmailService emailService;
    @Mock NotificationLogRepository notificationLogRepository;

    NotificationService service;
    User testUser;
    Match testMatch;

    @BeforeEach
    void setUp() {
        service = new NotificationService(emailService, notificationLogRepository);
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("alice@example.com");
        testUser.setFirstName("Alice");

        Team home = Team.builder().name("Mexico").fifaCode("MEX").flagCode("mx").build();
        Team away = Team.builder().name("Canada").fifaCode("CAN").flagCode("ca").build();
        testMatch = Match.builder()
                .id(10L)
                .homeTeam(home).awayTeam(away)
                .kickoffTime(LocalDateTime.of(2026, 6, 11, 19, 0))
                .stage(MatchStage.GROUP).matchNumber(1).build();
    }

    @Test
    void sendPredictionWindowOpen_sendsAndLogs() {
        when(notificationLogRepository.existsByReferenceKey(anyString())).thenReturn(false);
        boolean result = service.sendPredictionWindowOpen(List.of(testUser), testMatch);
        assertTrue(result);
        verify(emailService).sendPredictionWindowOpen(anyList(), anyList());
        verify(notificationLogRepository).save(any());
    }

    @Test
    void sendPredictionWindowOpen_skipsIfAlreadySent() {
        when(notificationLogRepository.existsByReferenceKey("PREDICTION_WINDOW_OPEN:match:10")).thenReturn(true);
        boolean result = service.sendPredictionWindowOpen(List.of(testUser), testMatch);
        assertFalse(result);
        verify(emailService, never()).sendPredictionWindowOpen(anyList(), anyList());
    }

    @Test
    void sendPredictionReminders_skipsAlreadySentUsers() {
        when(notificationLogRepository.existsByReferenceKey("PREDICTION_REMINDER:user:1:match:10")).thenReturn(true);
        int sent = service.sendPredictionReminders(List.of(testUser), testMatch);
        assertEquals(0, sent);
        verify(emailService, never()).sendPredictionReminder(anyList(), any());
    }

    @Test
    void sendPredictionReminders_sendsForNewUsers() {
        when(notificationLogRepository.existsByReferenceKey(anyString())).thenReturn(false);
        int sent = service.sendPredictionReminders(List.of(testUser), testMatch);
        assertEquals(1, sent);
        verify(emailService).sendPredictionReminder(anyList(), eq(testMatch));
    }

    @Test
    void sendLeaderboardDigest_skipsIfAlreadySent() {
        when(notificationLogRepository.existsByReferenceKey("LEADERBOARD_DIGEST:date:2026-06-11")).thenReturn(true);
        boolean result = service.sendLeaderboardDigest("2026-06-11", List.of(testUser),
                List.of(Map.of("rank", 1, "name", "Alice", "points", 25)), List.of());
        assertFalse(result);
    }

    @Test
    void sendInvitation_sendsAndLogs() {
        when(notificationLogRepository.existsByReferenceKey(anyString())).thenReturn(false);
        service.sendInvitation("bob@example.com", testUser);
        verify(emailService).sendInvitation("bob@example.com", testUser);
        verify(notificationLogRepository).save(any());
    }
}
