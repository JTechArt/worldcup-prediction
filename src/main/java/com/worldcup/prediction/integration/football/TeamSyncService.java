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
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class TeamSyncService {

    private final FootballApiClient client;
    private final FootballApiRateLimiter rateLimiter;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    // TLA (as returned by football-data.org) → ISO 3166-1 alpha-2 flag code for circle-flags CDN
    private static final Map<String, String> TLA_TO_FLAG = Map.ofEntries(
        // CONCACAF
        Map.entry("USA", "us"), Map.entry("MEX", "mx"), Map.entry("CAN", "ca"),
        Map.entry("JAM", "jm"), Map.entry("PAN", "pa"), Map.entry("CRC", "cr"),
        Map.entry("HON", "hn"), Map.entry("TRI", "tt"), Map.entry("HAI", "ht"),
        Map.entry("CUW", "cw"), Map.entry("CUB", "cu"), Map.entry("GUA", "gt"),
        // CONMEBOL — API uses URY not URU
        Map.entry("ARG", "ar"), Map.entry("BRA", "br"), Map.entry("URY", "uy"),
        Map.entry("URU", "uy"),  // both variants
        Map.entry("COL", "co"), Map.entry("ECU", "ec"), Map.entry("VEN", "ve"),
        Map.entry("PER", "pe"), Map.entry("BOL", "bo"), Map.entry("CHI", "cl"),
        Map.entry("PAR", "py"),
        // UEFA
        Map.entry("ESP", "es"), Map.entry("FRA", "fr"), Map.entry("GER", "de"),
        Map.entry("ENG", "gb-eng"), Map.entry("POR", "pt"), Map.entry("NED", "nl"),
        Map.entry("BEL", "be"), Map.entry("ITA", "it"), Map.entry("CRO", "hr"),
        Map.entry("SUI", "ch"), Map.entry("AUT", "at"), Map.entry("TUR", "tr"),
        Map.entry("NOR", "no"), Map.entry("SCO", "gb-sct"), Map.entry("WAL", "gb-wls"),
        Map.entry("SRB", "rs"), Map.entry("ROU", "ro"), Map.entry("CZE", "cz"),
        Map.entry("SVK", "sk"), Map.entry("HUN", "hu"), Map.entry("GRE", "gr"),
        Map.entry("ALB", "al"), Map.entry("UKR", "ua"), Map.entry("POL", "pl"),
        Map.entry("DEN", "dk"), Map.entry("SWE", "se"), Map.entry("FIN", "fi"),
        Map.entry("ISL", "is"), Map.entry("IRL", "ie"), Map.entry("BIH", "ba"),
        Map.entry("MKD", "mk"), Map.entry("SVN", "si"),
        // CAF — API uses RSA not ZAF
        Map.entry("MAR", "ma"), Map.entry("SEN", "sn"), Map.entry("EGY", "eg"),
        Map.entry("NGA", "ng"), Map.entry("CIV", "ci"), Map.entry("GHA", "gh"),
        Map.entry("CMR", "cm"), Map.entry("MLI", "ml"), Map.entry("COD", "cd"),
        Map.entry("ALG", "dz"), Map.entry("TUN", "tn"), Map.entry("RSA", "za"),
        Map.entry("ZAF", "za"),  // both variants
        Map.entry("CPV", "cv"), Map.entry("BEN", "bj"), Map.entry("EQG", "gq"),
        // AFC
        Map.entry("JPN", "jp"), Map.entry("KOR", "kr"), Map.entry("AUS", "au"),
        Map.entry("IRN", "ir"), Map.entry("KSA", "sa"), Map.entry("QAT", "qa"),
        Map.entry("UAE", "ae"), Map.entry("JOR", "jo"), Map.entry("UZB", "uz"),
        Map.entry("IND", "in"), Map.entry("CHN", "cn"), Map.entry("THA", "th"),
        Map.entry("IRQ", "iq"), Map.entry("OMA", "om"), Map.entry("KWT", "kw"),
        Map.entry("TJK", "tj"), Map.entry("KGZ", "kg"),
        // OFC
        Map.entry("NZL", "nz"), Map.entry("FIJ", "fj"), Map.entry("VAN", "vu"),
        Map.entry("PNG", "pg"), Map.entry("SOL", "sb")
    );

    public SyncResult syncTeamsAndSquads() {
        FootballApiTeamsResponseDto response = rateLimiter.call(client::fetchTeamsWithSquads);
        if (response == null || response.teams() == null) {
            return SyncResult.skipped("No API response");
        }

        int teamsCreated = 0;
        int teamsUpdated = 0;
        int playersUpserted = 0;

        for (FootballApiTeamWithSquadDto apiTeam : response.teams()) {
            Team team = teamRepository.findByExternalId(apiTeam.id())
                    .or(() -> teamRepository.findByFifaCodeIgnoreCase(apiTeam.tla()))
                    .or(() -> teamRepository.findByNameIgnoreCase(apiTeam.name()))
                    .orElse(null);

            if (team == null) {
                // Team doesn't exist yet — create it from API data
                String tla = apiTeam.tla() != null ? apiTeam.tla().toUpperCase() : "???";
                String flagCode = TLA_TO_FLAG.getOrDefault(tla, tla.toLowerCase());
                team = Team.builder()
                        .name(apiTeam.name())
                        .shortName(apiTeam.shortName())
                        .fifaCode(tla)
                        .flagCode(flagCode)
                        .build();
                log.info("Creating team from API: {} ({}) flagCode={}", apiTeam.name(), tla, flagCode);
                teamsCreated++;
            } else {
                teamsUpdated++;
            }

            team.setExternalId(apiTeam.id());
            team.setName(apiTeam.name());
            team.setShortName(apiTeam.shortName());
            teamRepository.save(team);

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

        return SyncResult.success(teamsCreated + " created, " + teamsUpdated + " updated, "
                + playersUpserted + " players upserted");
    }
}
