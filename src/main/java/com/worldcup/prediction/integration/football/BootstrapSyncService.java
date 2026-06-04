package com.worldcup.prediction.integration.football;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the one-time full data bootstrap in the correct order:
 *   1. Teams + squads  (1 API call — externalId + players)
 *   2. Group stage matches (1 API call — wipes wrong seed, recreates from API)
 *   3. Standings        (1 API call — initial group_standings rows)
 *
 * Total: 3 API calls. Safe to re-run (idempotent).
 *
 * After running, export the DB and commit as R__wc2026_data.sql so future
 * app starts don't need the bootstrap profile.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BootstrapSyncService {

    private final TeamSyncService teamSyncService;
    private final MatchSyncService matchSyncService;
    private final StandingSyncService standingSyncService;

    public String runFullBootstrap() {
        log.info("=== Full Bootstrap starting ===");
        StringBuilder result = new StringBuilder();

        SyncResult teams = teamSyncService.syncTeamsAndSquads();
        log.info("Step 1 Teams+Squads: {}", teams.message());
        result.append("Teams+Squads: ").append(teams.message()).append("\n");

        SyncResult matches = matchSyncService.syncGroupStageMatches();
        log.info("Step 2 Group Stage Matches: {}", matches.message());
        result.append("Matches: ").append(matches.message()).append("\n");

        SyncResult standings = standingSyncService.syncStandings();
        log.info("Step 3 Standings: {}", standings.message());
        result.append("Standings: ").append(standings.message()).append("\n");

        log.info("=== Bootstrap complete ===");
        return result.toString().trim();
    }
}
