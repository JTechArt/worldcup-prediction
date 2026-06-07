package com.worldcup.prediction.bootstrap;

import com.worldcup.prediction.integration.football.FootballApiClient;
import com.worldcup.prediction.integration.football.SyncResult;
import com.worldcup.prediction.integration.football.TeamSyncService;
import com.worldcup.prediction.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SquadSyncStartupRunner implements CommandLineRunner {

    private final PlayerRepository playerRepository;
    private final TeamSyncService teamSyncService;
    private final FootballApiClient footballApiClient;

    @Override
    public void run(String... args) {
        if (!footballApiClient.isEnabled()) {
            log.debug("SquadSyncStartup: Football API disabled — skipping");
            return;
        }
        if (playerRepository.count() > 0) {
            log.debug("SquadSyncStartup: Players already populated — skipping");
            return;
        }
        log.info("SquadSyncStartup: Players table empty — syncing squads from API");
        try {
            SyncResult result = teamSyncService.syncTeamsAndSquads();
            log.info("SquadSyncStartup: {}", result.message());
        } catch (Exception e) {
            log.error("SquadSyncStartup: failed to sync squads", e);
        }
    }
}
