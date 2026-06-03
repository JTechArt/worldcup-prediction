package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.integration.football.dto.FootballApiMatchDto;
import com.worldcup.prediction.integration.football.dto.FootballApiResponseDto;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.service.MatchAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class FootballApiSyncService {

    private final FootballApiClient apiClient;
    private final MatchRepository matchRepository;
    private final MatchAdminService matchAdminService;

    /**
     * Checks if there are any SCHEDULED matches with kickoff in the past.
     * These are candidates for result fetching.
     *
     * @return true if there are actionable matches
     */
    public boolean hasActionableMatches() {
        return matchRepository.countByStatusAndKickoffTimeBefore(
                MatchStatus.SCHEDULED, LocalDateTime.now()) > 0;
    }

    /**
     * Fetches results from football-data.org, updates COMPLETED matches,
     * and triggers scoring for newly-finished matches.
     *
     * @return list of match IDs that were newly completed and scored
     */
    @Transactional
    public List<Long> syncResults() {
        FootballApiResponseDto response = apiClient.fetchAllMatches();
        if (response == null || response.matches() == null) {
            log.debug("No API response to sync");
            return List.of();
        }

        List<Long> newlyFinished = new ArrayList<>();

        for (FootballApiMatchDto apiMatch : response.matches()) {
            if (!"FINISHED".equals(apiMatch.status())) {
                continue;
            }

            Optional<Match> matchOpt = resolveMatch(apiMatch);
            if (matchOpt.isEmpty()) {
                log.debug("Could not resolve API match id={} to a DB match", apiMatch.id());
                continue;
            }

            Match match = matchOpt.get();

            if (match.getExternalId() == null) {
                match.setExternalId(String.valueOf(apiMatch.id()));
            }

            boolean wasAlreadyCompleted = MatchStatus.COMPLETED == match.getStatus();

            if (apiMatch.score() == null || apiMatch.score().fullTime() == null) {
                log.warn("FINISHED match id={} has null fullTime score — skipping", apiMatch.id());
                continue;
            }
            Integer homeScore = apiMatch.score().fullTime().home();
            Integer awayScore = apiMatch.score().fullTime().away();
            if (homeScore == null || awayScore == null) {
                log.warn("FINISHED match id={} has null scores — skipping", apiMatch.id());
                continue;
            }

            match.setHomeScore(homeScore);
            match.setAwayScore(awayScore);
            match.setStatus(MatchStatus.COMPLETED);
            matchRepository.save(match);

            if (!wasAlreadyCompleted) {
                log.info("Match id={} newly completed — triggering scoring", match.getId());
                matchAdminService.scoreAllPredictions(match.getId());
                newlyFinished.add(match.getId());
            }
        }

        log.info("Sync complete: {} API matches processed, {} newly completed",
                response.matches().size(), newlyFinished.size());
        return newlyFinished;
    }

    private Optional<Match> resolveMatch(FootballApiMatchDto apiMatch) {
        if (apiMatch.id() != null) {
            Optional<Match> byExtId = matchRepository.findByExternalId(String.valueOf(apiMatch.id()));
            if (byExtId.isPresent()) return byExtId;
        }

        if (apiMatch.homeTeam() != null && apiMatch.awayTeam() != null
                && apiMatch.utcDate() != null && apiMatch.utcDate().length() >= 10) {
            LocalDate date = LocalDate.parse(apiMatch.utcDate().substring(0, 10),
                    DateTimeFormatter.ISO_LOCAL_DATE);
            return matchRepository.findByHomeTeamFifaCodeAndAwayTeamFifaCodeAndKickoffBetween(
                    apiMatch.homeTeam().tla(),
                    apiMatch.awayTeam().tla(),
                    date.atStartOfDay(),
                    date.plusDays(1).atStartOfDay());
        }

        return Optional.empty();
    }
}
