package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.TournamentSettings;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.domain.enums.WindowMode;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.RoundWindowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoundWindowServiceTest {

    @Mock RoundWindowRepository roundWindowRepository;
    @Mock MatchRepository matchRepository;
    @Mock TournamentSettingsService tournamentSettingsService;
    @InjectMocks RoundWindowService roundWindowService;

    private final LocalDateTime autoOpens = LocalDateTime.of(2026, 6, 10, 18, 0);
    private final LocalDateTime autoCloses = LocalDateTime.of(2026, 6, 12, 17, 0);

    private RoundWindow roundWindow;
    private LocalDateTime kickoffEarly;
    private LocalDateTime kickoffLate;

    @BeforeEach
    void setUp() {
        roundWindow = RoundWindow.builder().roundLabel("Matchday 1").build();
        kickoffEarly = LocalDateTime.of(2026, 6, 11, 16, 0);
        kickoffLate  = LocalDateTime.of(2026, 6, 11, 22, 0);
    }

    @Nested
    @DisplayName("isRoundOpen")
    class IsRoundOpen {

        @Test @DisplayName("returns false when round window not found")
        void notFound_returnsFalse() {
            when(roundWindowRepository.findByRoundLabel("Unknown")).thenReturn(Optional.empty());
            assertThat(roundWindowService.isRoundOpen("Unknown", autoOpens)).isFalse();
        }

        @Test @DisplayName("returns true when within auto window")
        void withinAutoWindow_returnsTrue() {
            RoundWindow rw = buildWindow(null);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(roundWindowService.isRoundOpen("Matchday 1", autoOpens.plusHours(1))).isTrue();
        }

        @Test @DisplayName("returns false before auto window opens")
        void beforeAutoWindow_returnsFalse() {
            RoundWindow rw = buildWindow(null);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(roundWindowService.isRoundOpen("Matchday 1", autoOpens.minusHours(1))).isFalse();
        }

        @Test @DisplayName("returns false after auto window closes")
        void afterAutoWindow_returnsFalse() {
            RoundWindow rw = buildWindow(null);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(roundWindowService.isRoundOpen("Matchday 1", autoCloses.plusHours(1))).isFalse();
        }

        @Test @DisplayName("returns true at exactly autoOpensAt")
        void atAutoOpensAt_returnsTrue() {
            RoundWindow rw = buildWindow(null);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(roundWindowService.isRoundOpen("Matchday 1", autoOpens)).isTrue();
        }

        @Test @DisplayName("returns false at exactly autoClosesAt (exclusive)")
        void atAutoClosesAt_returnsFalse() {
            RoundWindow rw = buildWindow(null);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(roundWindowService.isRoundOpen("Matchday 1", autoCloses)).isFalse();
        }

        @Test @DisplayName("FORCE_OPEN overrides auto closed")
        void forceOpen_overridesAutoClosed() {
            RoundWindow rw = buildWindow(RoundOverrideStatus.FORCE_OPEN);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(roundWindowService.isRoundOpen("Matchday 1", autoCloses.plusHours(5))).isTrue();
        }

        @Test @DisplayName("FORCE_CLOSED overrides auto open")
        void forceClosed_overridesAutoOpen() {
            RoundWindow rw = buildWindow(RoundOverrideStatus.FORCE_CLOSED);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(roundWindowService.isRoundOpen("Matchday 1", autoOpens.plusHours(1))).isFalse();
        }
    }

    @Test
    void recalculateAutoTimes_usesFirstKickoffForCloseTime() {
        Match early = buildMatch(kickoffEarly);
        Match late  = buildMatch(kickoffLate);
        when(roundWindowRepository.findByRoundLabel("Matchday 1"))
                .thenReturn(Optional.of(roundWindow));
        when(roundWindowRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(matchRepository.findByRoundLabelWithTeams("Matchday 1"))
                .thenReturn(List.of(early, late));

        TournamentSettings settings = TournamentSettings.builder()
                .id(1L).windowMode(WindowMode.ROUND)
                .dailyWindowCloseOffsetMinutes(30)
                .roundLockOffsetMinutes(60)
                .build();
        when(tournamentSettingsService.getSettings()).thenReturn(settings);

        roundWindowService.recalculateAutoTimes("Matchday 1");

        assertThat(roundWindow.getAutoOpensAt())
                .isEqualTo(kickoffEarly.minusHours(24));
        assertThat(roundWindow.getAutoClosesAt())
                .isEqualTo(kickoffEarly.minusMinutes(60));
    }

    @Test
    void recalculateAutoTimes_respectsCustomOffset() {
        Match only = buildMatch(kickoffEarly);
        when(roundWindowRepository.findByRoundLabel("Matchday 1"))
                .thenReturn(Optional.of(roundWindow));
        when(roundWindowRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(matchRepository.findByRoundLabelWithTeams("Matchday 1"))
                .thenReturn(List.of(only));

        TournamentSettings settings = TournamentSettings.builder()
                .id(1L).windowMode(WindowMode.ROUND)
                .dailyWindowCloseOffsetMinutes(30)
                .roundLockOffsetMinutes(10)
                .build();
        when(tournamentSettingsService.getSettings()).thenReturn(settings);

        roundWindowService.recalculateAutoTimes("Matchday 1");

        assertThat(roundWindow.getAutoClosesAt())
                .isEqualTo(kickoffEarly.minusMinutes(10));
    }

    private RoundWindow buildWindow(RoundOverrideStatus override) {
        return RoundWindow.builder()
                .id(1L)
                .roundLabel("Matchday 1")
                .overrideStatus(override)
                .autoOpensAt(autoOpens)
                .autoClosesAt(autoCloses)
                .build();
    }

    private Match buildMatch(LocalDateTime kickoff) {
        Match m = new Match();
        m.setKickoffTime(kickoff);
        m.setStage(MatchStage.GROUP);
        m.setStatus(MatchStatus.SCHEDULED);
        m.setMatchNumber(kickoff.getHour());
        return m;
    }
}
