package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.integration.football.dto.*;
import com.worldcup.prediction.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchSyncServiceKnockoutTest {

    @Mock FootballApiClient client;
    @Mock FootballApiRateLimiter rateLimiter;
    @Mock MatchRepository matchRepository;
    @Mock GroupRepository groupRepository;
    @Mock TeamRepository teamRepository;
    @Mock MatchLineupRepository lineupRepository;
    @Mock MatchGoalRepository goalRepository;
    @Mock PredictionRepository predictionRepository;
    @Mock RoundWindowRepository roundWindowRepository;
    @InjectMocks MatchSyncService service;

    // FootballApiTeamDto(Long id, String name, String shortName, String tla, String crest,
    //                    String formation, List<...> lineup, List<...> bench)
    private static FootballApiTeamDto tbd(String name, String tla) {
        return new FootballApiTeamDto(null, name, null, tla, null, null, null, null);
    }

    @Test
    void syncKnockoutMatches_createsRoundOf32MatchWithPlaceholders() {
        FootballApiTeamDto homeTbd = tbd("Winner Group A", "WGA");
        FootballApiTeamDto awayTbd = tbd("Runner-up Group B", "RGB");
        FootballApiMatchDto knockoutMatch = new FootballApiMatchDto(
                5001L, "2026-07-01T18:00:00Z", "SCHEDULED",
                null, "LAST_32", null, homeTbd, awayTbd, null);
        FootballApiMatchDto groupMatch = new FootballApiMatchDto(
                1L, "2026-06-12T18:00:00Z", "FINISHED",
                1, "GROUP_STAGE", "GROUP_A", homeTbd, awayTbd, null);
        FootballApiResponseDto response = new FootballApiResponseDto(2, List.of(groupMatch, knockoutMatch));

        when(rateLimiter.call(any(java.util.function.Supplier.class))).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(client.fetchAllMatches()).thenReturn(response);
        when(matchRepository.findByExternalId("5001")).thenReturn(Optional.empty());
        when(teamRepository.findByFifaCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(teamRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());

        SyncResult result = service.syncKnockoutMatches();

        assertThat(result.skipped()).isFalse();
        verify(matchRepository).save(argThat(m ->
                m.getStage() == MatchStage.ROUND_OF_32
                && "5001".equals(m.getExternalId())
                && "Winner Group A".equals(m.getHomeTeamPlaceholder())
                && "Runner-up Group B".equals(m.getAwayTeamPlaceholder())));
    }

    @Test
    void syncKnockoutMatches_skipsNullApiResponse() {
        when(rateLimiter.call(any(java.util.function.Supplier.class))).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(client.fetchAllMatches()).thenReturn(null);

        SyncResult result = service.syncKnockoutMatches();

        assertThat(result.skipped()).isTrue();
        verify(matchRepository, never()).save(any());
    }

    @Test
    void syncKnockoutMatches_updatesExistingMatchKickoff() {
        FootballApiTeamDto home = tbd("TBD", "TBD");
        FootballApiMatchDto knockoutMatch = new FootballApiMatchDto(
                5001L, "2026-07-01T18:00:00Z", "SCHEDULED",
                null, "LAST_32", null, home, home, null);
        FootballApiResponseDto response = new FootballApiResponseDto(1, List.of(knockoutMatch));

        Match existing = new Match();
        existing.setExternalId("5001");
        existing.setStage(MatchStage.ROUND_OF_32);

        when(rateLimiter.call(any(java.util.function.Supplier.class))).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(client.fetchAllMatches()).thenReturn(response);
        when(matchRepository.findByExternalId("5001")).thenReturn(Optional.of(existing));

        service.syncKnockoutMatches();

        verify(matchRepository).save(argThat(m ->
                "5001".equals(m.getExternalId()) && m.getKickoffTime() != null));
        verify(matchRepository, times(1)).save(any());
    }

    @Test
    void syncKnockoutMatches_skipsGroupStageMatches() {
        FootballApiMatchDto groupMatch = new FootballApiMatchDto(
                1L, "2026-06-12T18:00:00Z", "FINISHED",
                1, "GROUP_STAGE", "GROUP_A", null, null, null);
        FootballApiResponseDto response = new FootballApiResponseDto(1, List.of(groupMatch));

        when(rateLimiter.call(any(java.util.function.Supplier.class))).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(client.fetchAllMatches()).thenReturn(response);

        SyncResult result = service.syncKnockoutMatches();

        assertThat(result.skipped()).isTrue();
        verify(matchRepository, never()).save(any());
    }

    @Test
    void syncKnockoutMatches_mapsAllKnownStages() {
        List<String> apiStages = List.of(
                "LAST_32", "ROUND_OF_32", "LAST_16", "ROUND_OF_16",
                "QUARTER_FINALS", "QUARTER_FINAL",
                "SEMI_FINALS", "SEMI_FINAL",
                "FINAL", "THIRD_PLACE", "PLAY_OFF_FOR_THIRD_PLACE");

        lenient().when(rateLimiter.call(any(java.util.function.Supplier.class))).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        lenient().when(matchRepository.findByExternalId(any())).thenReturn(Optional.empty());
        lenient().when(teamRepository.findByExternalId(any())).thenReturn(Optional.empty());
        lenient().when(teamRepository.findByFifaCodeIgnoreCase(any())).thenReturn(Optional.empty());
        lenient().when(teamRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());

        for (String stage : apiStages) {
            FootballApiTeamDto tbd = tbd("TBD", "TBD");
            FootballApiMatchDto m = new FootballApiMatchDto(
                    (long) stage.hashCode(), "2026-07-01T18:00:00Z", "SCHEDULED",
                    null, stage, null, tbd, tbd, null);
            FootballApiResponseDto response = new FootballApiResponseDto(1, List.of(m));

            when(client.fetchAllMatches()).thenReturn(response);

            SyncResult result = service.syncKnockoutMatches();
            assertThat(result.skipped()).as("Stage '%s' should not be skipped", stage).isFalse();
        }
    }
}
