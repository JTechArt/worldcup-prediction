package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.RoundWindowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoundWindowServiceTest {

    @Mock private RoundWindowRepository roundWindowRepository;
    @Mock private MatchRepository matchRepository;

    private RoundWindowService service;

    private final LocalDateTime autoOpens = LocalDateTime.of(2026, 6, 10, 18, 0);
    private final LocalDateTime autoCloses = LocalDateTime.of(2026, 6, 12, 17, 0);

    @BeforeEach
    void setUp() {
        service = new RoundWindowService(roundWindowRepository, matchRepository);
    }

    @Nested
    @DisplayName("isRoundOpen")
    class IsRoundOpen {

        @Test @DisplayName("returns false when round window not found")
        void notFound_returnsFalse() {
            when(roundWindowRepository.findByRoundLabel("Unknown")).thenReturn(Optional.empty());
            assertThat(service.isRoundOpen("Unknown", autoOpens)).isFalse();
        }

        @Test @DisplayName("returns true when within auto window")
        void withinAutoWindow_returnsTrue() {
            RoundWindow rw = buildWindow(null);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(service.isRoundOpen("Matchday 1", autoOpens.plusHours(1))).isTrue();
        }

        @Test @DisplayName("returns false before auto window opens")
        void beforeAutoWindow_returnsFalse() {
            RoundWindow rw = buildWindow(null);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(service.isRoundOpen("Matchday 1", autoOpens.minusHours(1))).isFalse();
        }

        @Test @DisplayName("returns false after auto window closes")
        void afterAutoWindow_returnsFalse() {
            RoundWindow rw = buildWindow(null);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(service.isRoundOpen("Matchday 1", autoCloses.plusHours(1))).isFalse();
        }

        @Test @DisplayName("returns true at exactly autoOpensAt")
        void atAutoOpensAt_returnsTrue() {
            RoundWindow rw = buildWindow(null);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(service.isRoundOpen("Matchday 1", autoOpens)).isTrue();
        }

        @Test @DisplayName("returns false at exactly autoClosesAt (exclusive)")
        void atAutoClosesAt_returnsFalse() {
            RoundWindow rw = buildWindow(null);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(service.isRoundOpen("Matchday 1", autoCloses)).isFalse();
        }

        @Test @DisplayName("FORCE_OPEN overrides auto closed")
        void forceOpen_overridesAutoClosed() {
            RoundWindow rw = buildWindow(RoundOverrideStatus.FORCE_OPEN);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(service.isRoundOpen("Matchday 1", autoCloses.plusHours(5))).isTrue();
        }

        @Test @DisplayName("FORCE_CLOSED overrides auto open")
        void forceClosed_overridesAutoOpen() {
            RoundWindow rw = buildWindow(RoundOverrideStatus.FORCE_CLOSED);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(service.isRoundOpen("Matchday 1", autoOpens.plusHours(1))).isFalse();
        }
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
}
