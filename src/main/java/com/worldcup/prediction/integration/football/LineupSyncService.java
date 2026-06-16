package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.integration.football.dto.*;
import com.worldcup.prediction.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class LineupSyncService {

    private final FootballApiClient client;
    private final FootballApiRateLimiter rateLimiter;
    private final MatchRepository matchRepository;
    private final LineupSyncPersistence persistence;

    public SyncResult syncLineups() {
        // Short read transaction via Spring Data's default — connection released before API calls begin
        List<Match> pending = matchRepository.findByStatusAndLineupFetchedFalse(MatchStatus.COMPLETED);
        if (pending.isEmpty()) {
            return SyncResult.skipped("No completed matches without lineups");
        }

        int fetched = 0;
        for (Match match : pending) {
            if (match.getExternalId() == null) {
                log.warn("Match id={} has no externalId — skipping lineup fetch", match.getId());
                continue;
            }
            long extId;
            try {
                extId = Long.parseLong(match.getExternalId());
            } catch (NumberFormatException e) {
                log.warn("Match id={} has non-numeric externalId='{}' — skipping", match.getId(), match.getExternalId());
                continue;
            }

            // API call happens outside any transaction — no DB connection held during HTTP I/O
            FootballApiMatchDetailDto detail = rateLimiter.call(() -> client.fetchMatchDetail(extId));
            if (detail == null) continue;

            boolean hasLineups = hasTeamLineup(detail.homeTeam()) || hasTeamLineup(detail.awayTeam());
            boolean hasGoals   = detail.goals() != null && !detail.goals().isEmpty();
            if (!hasLineups && !hasGoals) {
                log.warn("Match id={} externalId={} returned no lineups and no goals — skipping flag update", match.getId(), extId);
                continue;
            }

            // Short write transaction per match — connection acquired here and immediately released
            persistence.persistMatchResults(match.getId(), detail);
            fetched++;
        }

        return SyncResult.success(fetched + " match lineups fetched");
    }

    private boolean hasTeamLineup(FootballApiTeamDto team) {
        return team != null && team.lineup() != null && !team.lineup().isEmpty();
    }
}
