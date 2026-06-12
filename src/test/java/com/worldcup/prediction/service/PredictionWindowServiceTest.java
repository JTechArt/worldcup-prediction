package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.TournamentSettings;
import com.worldcup.prediction.domain.enums.PredictionWindowStatus;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.domain.enums.WindowMode;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionWindowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PredictionWindowServiceTest {

    @Mock private PredictionWindowRepository windowRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private TournamentSettingsService tournamentSettingsService;
    @InjectMocks private PredictionWindowService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(tournamentSettingsService.getSettings()).thenReturn(
                TournamentSettings.builder()
                        .id(1L).windowMode(WindowMode.ROUND)
                        .dailyWindowCloseOffsetMinutes(30).build());
    }

    // ---- generatePreview ----

    @Test
    void generatePreview_returnsMatchesInRangeAndAdjacent() {
        LocalDateTime from = LocalDateTime.of(2026, 6, 14, 0, 0);
        LocalDateTime to   = LocalDateTime.of(2026, 6, 14, 23, 59);
        Match m1   = matchWithKickoff(LocalDateTime.of(2026, 6, 14, 10, 0));
        Match prev = matchWithKickoff(LocalDateTime.of(2026, 6, 13, 20, 0));
        Match next = matchWithKickoff(LocalDateTime.of(2026, 6, 15, 10, 0));

        when(matchRepository.findByKickoffTimeBetweenOrderByKickoffTimeAsc(from, to)).thenReturn(List.of(m1));
        when(matchRepository.findFirstByKickoffTimeLessThanOrderByKickoffTimeDesc(from)).thenReturn(Optional.of(prev));
        when(matchRepository.findFirstByKickoffTimeGreaterThanOrderByKickoffTimeAsc(to)).thenReturn(Optional.of(next));

        PredictionWindowService.WindowPreview preview = service.generatePreview(from, to);

        assertThat(preview.includedMatches()).containsExactly(m1);
        assertThat(preview.prevMatch()).contains(prev);
        assertThat(preview.nextMatch()).contains(next);
    }

    // ---- publish lifecycle ----

    @Test
    void publish_changesDraftToScheduled() {
        PredictionWindow window = PredictionWindow.builder()
                .id(1L).label("June 14").openAt(LocalDateTime.now().plusHours(2))
                .status(PredictionWindowStatus.DRAFT).build();
        when(windowRepository.findById(1L)).thenReturn(Optional.of(window));
        when(windowRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PredictionWindow result = service.publish(1L);
        assertThat(result.getStatus()).isEqualTo(PredictionWindowStatus.SCHEDULED);
    }

    @Test
    void publish_throwsWhenWindowNotDraft() {
        PredictionWindow window = PredictionWindow.builder()
                .id(1L).status(PredictionWindowStatus.OPEN).build();
        when(windowRepository.findById(1L)).thenReturn(Optional.of(window));

        assertThatThrownBy(() -> service.publish(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only DRAFT");
    }

    // ---- activateWindow ----

    @Test
    void activateWindow_computesEffectiveCloseAtFromOffset_whenNoExplicitCloseAt() {
        LocalDateTime kickoff = LocalDateTime.of(2026, 6, 14, 10, 0);
        Match m = matchWithKickoff(kickoff);
        PredictionWindow window = PredictionWindow.builder()
                .id(1L).status(PredictionWindowStatus.SCHEDULED)
                .openAt(LocalDateTime.now().minusMinutes(1))
                .matches(new HashSet<>(Set.of(m))).build();
        when(windowRepository.findById(1L)).thenReturn(Optional.of(window));
        when(windowRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PredictionWindow result = service.activateWindow(1L);

        assertThat(result.getStatus()).isEqualTo(PredictionWindowStatus.OPEN);
        assertThat(result.getEffectiveCloseAt()).isEqualTo(kickoff.minusMinutes(30));
    }

    @Test
    void activateWindow_usesExplicitCloseAtWhenSet() {
        LocalDateTime explicitClose = LocalDateTime.of(2026, 6, 14, 18, 0);
        PredictionWindow window = PredictionWindow.builder()
                .id(1L).status(PredictionWindowStatus.SCHEDULED)
                .openAt(LocalDateTime.now().minusMinutes(1))
                .closeAt(explicitClose)
                .matches(new HashSet<>(Set.of(matchWithKickoff(LocalDateTime.of(2026, 6, 14, 20, 0))))).build();
        when(windowRepository.findById(1L)).thenReturn(Optional.of(window));
        when(windowRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PredictionWindow result = service.activateWindow(1L);
        assertThat(result.getEffectiveCloseAt()).isEqualTo(explicitClose);
    }

    // ---- isWindowOpen ----

    @Test
    void isWindowOpen_returnsTrueForOpenWindowInTimeRange() {
        LocalDateTime now = LocalDateTime.now();
        Match m = matchWithKickoff(now.plusHours(2));
        PredictionWindow window = PredictionWindow.builder()
                .id(1L).status(PredictionWindowStatus.OPEN)
                .openAt(now.minusHours(1))
                .effectiveCloseAt(now.plusHours(1))
                .matches(new HashSet<>(Set.of(m))).build();
        when(windowRepository.findForceOpenCommunityWindowForMatch(any(), any())).thenReturn(Optional.empty());
        when(windowRepository.findForceClosedCommunityWindowForMatch(any(), any())).thenReturn(Optional.empty());
        when(windowRepository.findOpenCommunityWindowForMatch(any(), any())).thenReturn(Optional.empty());
        when(windowRepository.findForceOpenGlobalWindowForMatch(any())).thenReturn(Optional.empty());
        when(windowRepository.findForceClosedGlobalWindowForMatch(any())).thenReturn(Optional.empty());
        when(windowRepository.findOpenGlobalWindowForMatch(m.getId())).thenReturn(Optional.of(window));

        assertThat(service.isWindowOpen(m, now, 99L)).isTrue();
    }

    @Test
    void isWindowOpen_returnsFalseWhenNoWindow() {
        Match m = matchWithKickoff(LocalDateTime.now().plusHours(3));
        when(windowRepository.findForceOpenCommunityWindowForMatch(any(), any())).thenReturn(Optional.empty());
        when(windowRepository.findForceClosedCommunityWindowForMatch(any(), any())).thenReturn(Optional.empty());
        when(windowRepository.findOpenCommunityWindowForMatch(any(), any())).thenReturn(Optional.empty());
        when(windowRepository.findForceOpenGlobalWindowForMatch(any())).thenReturn(Optional.empty());
        when(windowRepository.findForceClosedGlobalWindowForMatch(any())).thenReturn(Optional.empty());
        when(windowRepository.findOpenGlobalWindowForMatch(any())).thenReturn(Optional.empty());

        assertThat(service.isWindowOpen(m, LocalDateTime.now(), 99L)).isFalse();
    }

    @Test
    void isWindowOpen_returnsTrueForForceOpenWindow() {
        Match m = matchWithKickoff(LocalDateTime.now().plusHours(2));
        PredictionWindow window = PredictionWindow.builder()
                .id(1L).status(PredictionWindowStatus.SCHEDULED)
                .openAt(LocalDateTime.now().plusHours(1))
                .overrideStatus(RoundOverrideStatus.FORCE_OPEN)
                .matches(new HashSet<>(Set.of(m))).build();
        when(windowRepository.findForceOpenCommunityWindowForMatch(any(), any())).thenReturn(Optional.empty());
        when(windowRepository.findForceOpenGlobalWindowForMatch(m.getId())).thenReturn(Optional.of(window));

        assertThat(service.isWindowOpen(m, LocalDateTime.now(), 99L)).isTrue();
    }

    @Test
    void isWindowOpen_returnsFalseForForceClosedWindow() {
        Match m = matchWithKickoff(LocalDateTime.now().plusHours(2));
        PredictionWindow window = PredictionWindow.builder()
                .id(1L).status(PredictionWindowStatus.OPEN)
                .openAt(LocalDateTime.now().minusHours(1))
                .effectiveCloseAt(LocalDateTime.now().plusHours(1))
                .overrideStatus(RoundOverrideStatus.FORCE_CLOSED)
                .matches(new HashSet<>(Set.of(m))).build();
        when(windowRepository.findForceOpenCommunityWindowForMatch(any(), any())).thenReturn(Optional.empty());
        when(windowRepository.findForceClosedCommunityWindowForMatch(any(), any())).thenReturn(Optional.empty());
        when(windowRepository.findForceOpenGlobalWindowForMatch(any())).thenReturn(Optional.empty());
        when(windowRepository.findForceClosedGlobalWindowForMatch(m.getId())).thenReturn(Optional.of(window));

        assertThat(service.isWindowOpen(m, LocalDateTime.now(), 99L)).isFalse();
    }

    @Test
    void delete_throwsWhenWindowIsOpen() {
        PredictionWindow window = PredictionWindow.builder()
                .id(1L).status(PredictionWindowStatus.OPEN).build();
        when(windowRepository.findById(1L)).thenReturn(Optional.of(window));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot delete an OPEN window");
    }

    // ---- helpers ----

    private Match matchWithKickoff(LocalDateTime kickoff) {
        Match m = new Match();
        m.setKickoffTime(kickoff);
        return m;
    }
}
