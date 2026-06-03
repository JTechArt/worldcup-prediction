package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.Player;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.integration.football.dto.*;
import com.worldcup.prediction.repository.PlayerRepository;
import com.worldcup.prediction.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class TeamSyncService {

    private final FootballApiClient client;
    private final FootballApiRateLimiter rateLimiter;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    public SyncResult syncTeamsAndSquads() {
        FootballApiTeamsResponseDto response = rateLimiter.call(client::fetchTeamsWithSquads);
        if (response == null || response.teams() == null) {
            return SyncResult.skipped("No API response");
        }

        int teamsUpdated = 0;
        int playersUpserted = 0;

        for (FootballApiTeamWithSquadDto apiTeam : response.teams()) {
            Team team = teamRepository.findByExternalId(apiTeam.id())
                    .or(() -> teamRepository.findByFifaCodeIgnoreCase(apiTeam.tla()))
                    .orElse(null);

            if (team == null) {
                log.warn("No team found for tla={} externalId={} — skipping", apiTeam.tla(), apiTeam.id());
                continue;
            }

            team.setExternalId(apiTeam.id());
            team.setName(apiTeam.name());
            team.setShortName(apiTeam.shortName());
            teamRepository.save(team);
            teamsUpdated++;

            if (apiTeam.squad() == null) continue;

            for (FootballApiPlayerDto p : apiTeam.squad()) {
                Player player = playerRepository.findByExternalId(p.id()).orElse(new Player());
                player.setExternalId(p.id());
                player.setTeam(team);
                player.setName(p.name());
                player.setPosition(p.position());
                player.setNationality(p.nationality());
                player.setShirtNumber(p.shirtNumber());
                if (p.dateOfBirth() != null && p.dateOfBirth().length() >= 10) {
                    player.setDateOfBirth(LocalDate.parse(p.dateOfBirth().substring(0, 10)));
                }
                playerRepository.save(player);
                playersUpserted++;
            }
        }

        return SyncResult.success(teamsUpdated + " teams, " + playersUpserted + " players upserted");
    }
}
