package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.*;
import com.worldcup.prediction.dto.PredictionSubmitDto;
import com.worldcup.prediction.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictionViewServiceSubmissionTest {

    @Mock private MatchRepository matchRepository;
    @Mock private PredictionRepository predictionRepository;
    @Mock private UserRepository userRepository;
    @Mock private CommunityRepository communityRepository;
    @Mock private RoundWindowService roundWindowService;
    @Mock private RoundSubmissionService roundSubmissionService;
    @Mock private TournamentSettingsService tournamentSettingsService;
    @Mock private PredictionWindowService predictionWindowService;
    @Mock private UserRoundOverrideService userRoundOverrideService;

    private PredictionViewService service;

    private static final Long USER_ID = 1L;
    private static final Long COMMUNITY_ID = 10L;
    private static final String ROUND = "Matchday 1";

    @BeforeEach
    void setUp() {
        service = new PredictionViewService(matchRepository, predictionRepository,
                userRepository, communityRepository, roundWindowService, roundSubmissionService,
                tournamentSettingsService, predictionWindowService, userRoundOverrideService);
        lenient().when(tournamentSettingsService.getEffectiveMode(any())).thenReturn(WindowMode.ROUND);
        // set timezone via reflection since @Value cannot inject in unit tests
        try {
            var f = PredictionViewService.class.getDeclaredField("timezoneId");
            f.setAccessible(true);
            f.set(service, "UTC");
            var init = PredictionViewService.class.getDeclaredMethod("initFormatters");
            init.setAccessible(true);
            init.invoke(service);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void submitPredictionsForRound_upsertsCalled_afterSavingPredictions() {
        LocalDateTime kickoff = LocalDateTime.now().plusHours(5);
        Match match = Match.builder().id(99L).kickoffTime(kickoff).roundLabel(ROUND).build();
        User user = User.builder().id(USER_ID).firstName("A").lastName("B").email("a@b.com").build();
        Community community = Community.builder().id(COMMUNITY_ID).build();

        when(matchRepository.findByRoundLabelWithTeams(ROUND)).thenReturn(List.of(match));
        when(roundWindowService.isRoundOpen(eq(ROUND), any())).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
        when(predictionRepository.findByUserIdAndMatchIdAndCommunityId(USER_ID, 99L, COMMUNITY_ID))
                .thenReturn(Optional.empty());

        PredictionSubmitDto dto = new PredictionSubmitDto();
        dto.setRoundLabel(ROUND);
        PredictionSubmitDto.SinglePrediction sp = new PredictionSubmitDto.SinglePrediction();
        sp.setMatchId(99L);
        sp.setHomeScore(1);
        sp.setAwayScore(0);
        dto.setPredictions(List.of(sp));

        service.submitPredictionsForRound(USER_ID, dto, COMMUNITY_ID);

        verify(roundSubmissionService).upsert(USER_ID, COMMUNITY_ID, ROUND);
    }

    @Test
    void submitPredictionsForRound_DAILY_mode_savesAndCallsUpsertForWindow() {
        when(tournamentSettingsService.getEffectiveMode(COMMUNITY_ID)).thenReturn(WindowMode.DAILY);

        LocalDateTime kickoff = LocalDateTime.now().plusHours(5);
        Match match = Match.builder().id(99L).kickoffTime(kickoff).roundLabel(ROUND).build();
        User user = User.builder().id(USER_ID).firstName("A").lastName("B").email("a@b.com").build();
        Community community = Community.builder().id(COMMUNITY_ID).build();

        PredictionWindow pw = PredictionWindow.builder()
                .id(7L).label("June 14 Matches")
                .openAt(LocalDateTime.now().minusHours(1))
                .matches(new HashSet<>(Set.of(match)))
                .build();

        when(predictionWindowService.findById(7L)).thenReturn(pw);
        when(predictionWindowService.isWindowOpen(eq(match), any(), eq(COMMUNITY_ID))).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
        when(predictionRepository.findByUserIdAndMatchIdAndCommunityId(USER_ID, 99L, COMMUNITY_ID))
                .thenReturn(Optional.empty());

        PredictionSubmitDto dto = new PredictionSubmitDto();
        dto.setRoundLabel(ROUND);
        dto.setWindowId(7L);
        PredictionSubmitDto.SinglePrediction sp = new PredictionSubmitDto.SinglePrediction();
        sp.setMatchId(99L);
        sp.setHomeScore(2);
        sp.setAwayScore(1);
        dto.setPredictions(List.of(sp));

        service.submitPredictionsForRound(USER_ID, dto, COMMUNITY_ID);

        verify(roundSubmissionService).upsertForWindow(USER_ID, COMMUNITY_ID, 7L, "June 14 Matches");
        verify(roundSubmissionService, never()).upsert(any(), any(), any());
    }

    @Test
    void submitPredictionsForRound_DAILY_mode_nullWindowId_throws() {
        when(tournamentSettingsService.getEffectiveMode(COMMUNITY_ID)).thenReturn(WindowMode.DAILY);

        PredictionSubmitDto dto = new PredictionSubmitDto();
        dto.setRoundLabel(ROUND);
        dto.setWindowId(null);
        dto.setPredictions(List.of());

        assertThatThrownBy(() -> service.submitPredictionsForRound(USER_ID, dto, COMMUNITY_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("windowId required");
    }

    @Test
    void submitPredictionsForRound_DAILY_mode_windowNotOpen_throws() {
        when(tournamentSettingsService.getEffectiveMode(COMMUNITY_ID)).thenReturn(WindowMode.DAILY);

        Match match = Match.builder().id(99L).kickoffTime(LocalDateTime.now().plusHours(5)).roundLabel(ROUND).build();
        PredictionWindow pw = PredictionWindow.builder()
                .id(7L).label("June 14 Matches")
                .openAt(LocalDateTime.now().minusHours(1))
                .matches(new HashSet<>(Set.of(match)))
                .build();

        when(predictionWindowService.findById(7L)).thenReturn(pw);
        when(predictionWindowService.isWindowOpen(eq(match), any(), eq(COMMUNITY_ID))).thenReturn(false);

        PredictionSubmitDto dto = new PredictionSubmitDto();
        dto.setRoundLabel(ROUND);
        dto.setWindowId(7L);
        dto.setPredictions(List.of());

        assertThatThrownBy(() -> service.submitPredictionsForRound(USER_ID, dto, COMMUNITY_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not open");
    }
}
