package com.worldcup.prediction.bootstrap;

import com.worldcup.prediction.integration.football.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("bootstrap")
@Slf4j
@RequiredArgsConstructor
public class FootballApiBootstrapRunner implements CommandLineRunner {

    private final TeamSyncService teamSyncService;
    private final MatchSyncService matchSyncService;
    private final StandingSyncService standingSyncService;

    @Override
    public void run(String... args) {
        log.info("=== Football API Bootstrap starting ===");

        SyncResult teams = teamSyncService.syncTeamsAndSquads();
        log.info("Teams + Squads: {}", teams.message());

        SyncResult matches = matchSyncService.syncMatchExternalIds();
        log.info("Matches:        {}", matches.message());

        SyncResult standings = standingSyncService.syncStandings();
        log.info("Standings:      {}", standings.message());

        log.info("=== Bootstrap complete. Export DB and commit as R__wc2026_data.sql ===");
    }
}
