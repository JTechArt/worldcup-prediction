package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.integration.football.dto.FootballApiMatchDto;
import com.worldcup.prediction.integration.football.dto.FootballApiResponseDto;
import com.worldcup.prediction.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class MatchSyncService {

    private final FootballApiClient client;
    private final FootballApiRateLimiter rateLimiter;
    private final MatchRepository matchRepository;

    public SyncResult syncMatchExternalIds() {
        FootballApiResponseDto response = rateLimiter.call(client::fetchAllMatches);
        if (response == null || response.matches() == null) {
            return SyncResult.skipped("No API response");
        }

        int linked = 0;
        for (FootballApiMatchDto apiMatch : response.matches()) {
            if (!"GROUP_STAGE".equals(apiMatch.stage())) continue;
            if (apiMatch.id() == null) continue;

            String extId = String.valueOf(apiMatch.id());
            if (matchRepository.findByExternalId(extId).isPresent()) continue;

            Optional<Match> matchOpt = resolveByTeamsAndDate(apiMatch);
            if (matchOpt.isEmpty()) {
                log.debug("Could not resolve match id={} tla={} vs {}", apiMatch.id(),
                        apiMatch.homeTeam() != null ? apiMatch.homeTeam().tla() : "?",
                        apiMatch.awayTeam() != null ? apiMatch.awayTeam().tla() : "?");
                continue;
            }

            matchOpt.get().setExternalId(extId);
            matchRepository.save(matchOpt.get());
            linked++;
        }

        return SyncResult.success(linked + " match external IDs linked");
    }

    private Optional<Match> resolveByTeamsAndDate(FootballApiMatchDto apiMatch) {
        if (apiMatch.homeTeam() == null || apiMatch.awayTeam() == null || apiMatch.utcDate() == null
                || apiMatch.utcDate().length() < 10) {
            return Optional.empty();
        }
        LocalDate date = LocalDate.parse(apiMatch.utcDate().substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
        return matchRepository.findByHomeTeamFifaCodeAndAwayTeamFifaCodeAndKickoffBetween(
                apiMatch.homeTeam().tla(), apiMatch.awayTeam().tla(),
                date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }
}
