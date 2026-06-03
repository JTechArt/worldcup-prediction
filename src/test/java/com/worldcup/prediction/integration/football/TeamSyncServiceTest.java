package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.integration.football.dto.*;
import com.worldcup.prediction.repository.PlayerRepository;
import com.worldcup.prediction.repository.TeamRepository;
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
class TeamSyncServiceTest {

    @Mock FootballApiClient client;
    @Mock FootballApiRateLimiter rateLimiter;
    @Mock TeamRepository teamRepo;
    @Mock PlayerRepository playerRepo;
    @InjectMocks TeamSyncService service;

    @Test
    void syncTeamsAndSquads_upsetsTeamExternalIdAndPlayers() {
        FootballApiPlayerDto playerDto = new FootballApiPlayerDto(
                3359L, "Neuer", "Goalkeeper", "1986-03-27", "Germany", 1);
        FootballApiTeamWithSquadDto teamDto = new FootballApiTeamWithSquadDto(
                773L, "Germany", "Germany", "GER", List.of(playerDto));
        FootballApiTeamsResponseDto response = new FootballApiTeamsResponseDto(1, List.of(teamDto));

        when(rateLimiter.call(any())).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(client.fetchTeamsWithSquads()).thenReturn(response);

        Team team = new Team();
        team.setId(1L);
        team.setFifaCode("GER");
        when(teamRepo.findByExternalId(773L)).thenReturn(Optional.empty());
        when(teamRepo.findByFifaCodeIgnoreCase("GER")).thenReturn(Optional.of(team));
        when(playerRepo.findByExternalId(3359L)).thenReturn(Optional.empty());

        SyncResult result = service.syncTeamsAndSquads();

        assertThat(result.skipped()).isFalse();
        verify(teamRepo).save(argThat(t -> Long.valueOf(773L).equals(t.getExternalId())));
        verify(playerRepo).save(argThat(p -> p.getExternalId().equals(3359L) && p.getName().equals("Neuer")));
    }

    @Test
    void syncTeamsAndSquads_whenNullResponse_returnsSkipped() {
        when(rateLimiter.call(any())).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(client.fetchTeamsWithSquads()).thenReturn(null);

        SyncResult result = service.syncTeamsAndSquads();

        assertThat(result.skipped()).isTrue();
        verifyNoInteractions(teamRepo, playerRepo);
    }
}
