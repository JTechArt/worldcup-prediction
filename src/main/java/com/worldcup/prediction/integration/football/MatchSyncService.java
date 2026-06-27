package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.Group;
import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.integration.football.dto.FootballApiMatchDto;
import com.worldcup.prediction.integration.football.dto.FootballApiResponseDto;
import com.worldcup.prediction.integration.football.dto.FootballApiTeamDto;
import com.worldcup.prediction.repository.GroupRepository;
import com.worldcup.prediction.repository.MatchGoalRepository;
import com.worldcup.prediction.repository.MatchLineupRepository;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.repository.RoundWindowRepository;
import com.worldcup.prediction.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class MatchSyncService {

    private final FootballApiClient client;
    private final FootballApiRateLimiter rateLimiter;
    private final MatchRepository matchRepository;
    private final GroupRepository groupRepository;
    private final TeamRepository teamRepository;
    private final MatchLineupRepository lineupRepository;
    private final MatchGoalRepository goalRepository;
    private final PredictionRepository predictionRepository;
    private final RoundWindowRepository roundWindowRepository;

    @Value("${app.timezone}")
    private String timezoneId;

    /**
     * Wipes ALL match-dependent data (predictions, lineups, goals, matches) and
     * group-team associations, then re-creates the 72 group stage matches from
     * the API with correct groups, teams, kickoff times and venues.
     *
     * Safe to re-run — idempotent via delete-all + recreate.
     */
    public SyncResult syncGroupStageMatches() {
        FootballApiResponseDto response = rateLimiter.call(client::fetchAllMatches);
        if (response == null || response.matches() == null) {
            return SyncResult.skipped("No API response");
        }

        List<FootballApiMatchDto> groupMatches = response.matches().stream()
                .filter(m -> "GROUP_STAGE".equals(m.stage()))
                .filter(m -> m.id() != null && m.homeTeam() != null && m.awayTeam() != null
                        && m.group() != null && m.utcDate() != null)
                .sorted(Comparator.comparing(FootballApiMatchDto::utcDate))
                .toList();

        if (groupMatches.isEmpty()) {
            return SyncResult.skipped("No GROUP_STAGE matches in API response");
        }

        // Clear in FK order: child rows first, then groups
        lineupRepository.deleteAllLineups();
        goalRepository.deleteAllGoals();
        predictionRepository.deleteAllPredictions();
        matchRepository.deleteAllMatches();
        groupRepository.deleteAllGroupTeams();
        groupRepository.deleteAll();
        log.info("Cleared all existing match, group-team and group data");

        AtomicInteger matchNumber = new AtomicInteger(1);
        int created = 0;
        int skipped = 0;

        for (FootballApiMatchDto apiMatch : groupMatches) {
            String groupName = apiMatch.group()
                    .replace("GROUP_", "")
                    .replace("Group ", "")
                    .trim();
            Group group = groupRepository.findByNameIgnoreCase(groupName)
                    .orElseGet(() -> {
                        log.info("Creating group '{}'", groupName);
                        return groupRepository.save(Group.builder().name(groupName).build());
                    });

            Optional<Team> homeOpt = resolveTeam(apiMatch.homeTeam());
            Optional<Team> awayOpt = resolveTeam(apiMatch.awayTeam());
            if (homeOpt.isEmpty() || awayOpt.isEmpty()) {
                log.warn("Cannot resolve teams for match id={} ({} vs {})",
                        apiMatch.id(), apiMatch.homeTeam().tla(), apiMatch.awayTeam().tla());
                skipped++;
                continue;
            }

            Team home = homeOpt.get();
            Team away = awayOpt.get();

            // Link teams to group (set handles duplicates via group entity)
            if (!group.getTeams().contains(home)) group.getTeams().add(home);
            if (!group.getTeams().contains(away)) group.getTeams().add(away);
            groupRepository.save(group);

            Match match = Match.builder()
                    .externalId(String.valueOf(apiMatch.id()))
                    .stage(MatchStage.GROUP)
                    .group(group)
                    .matchNumber(matchNumber.getAndIncrement())
                    .roundLabel("Matchday " + apiMatch.matchday())
                    .homeTeam(home)
                    .awayTeam(away)
                    .kickoffTime(parseUtc(apiMatch.utcDate()))
                    .status(mapStatus(apiMatch.status()))
                    .build();

            matchRepository.save(match);
            created++;
        }

        // Create or update RoundWindow entries for synced rounds
        List<String> roundLabels = matchRepository.findDistinctRoundLabels();
        for (String label : roundLabels) {
            var matches = matchRepository.findByRoundLabelWithTeams(label);
            if (matches.isEmpty()) continue;
            LocalDateTime firstKickoff = matches.stream()
                    .map(Match::getKickoffTime).min(LocalDateTime::compareTo).orElse(null);
            LocalDateTime lastKickoff = matches.stream()
                    .map(Match::getKickoffTime).max(LocalDateTime::compareTo).orElse(null);
            RoundWindow rw = roundWindowRepository.findByRoundLabel(label)
                    .orElse(RoundWindow.builder().roundLabel(label).build());
            if (firstKickoff != null) rw.setAutoOpensAt(firstKickoff.minusHours(24));
            if (lastKickoff != null) rw.setAutoClosesAt(lastKickoff.minusHours(1));
            roundWindowRepository.save(rw);
        }

        return SyncResult.success(created + " group stage matches created" +
                (skipped > 0 ? ", " + skipped + " skipped" : ""));
    }

    /**
     * Non-destructive update: fetches all matches from the API and updates kickoff
     * times for existing matches (matched by externalId). Predictions are untouched.
     *
     * Comparison is done in UTC (epoch ms) to avoid ambiguity from SQLite storing
     * kickoff_time as epoch-millisecond integers that Hibernate may read without
     * applying the configured timezone offset.
     */
    public SyncResult syncMatchDatesOnly() {
        FootballApiResponseDto response = rateLimiter.call(client::fetchAllMatches);
        if (response == null || response.matches() == null) {
            return SyncResult.skipped("No API response");
        }

        int updated = 0;
        int skipped = 0;
        ZoneId zone = ZoneId.of(timezoneId);

        for (FootballApiMatchDto apiMatch : response.matches()) {
            if (apiMatch.id() == null || apiMatch.utcDate() == null) {
                skipped++;
                continue;
            }
            Optional<Match> matchOpt = matchRepository.findByExternalId(String.valueOf(apiMatch.id()));
            if (matchOpt.isEmpty()) {
                skipped++;
                continue;
            }

            OffsetDateTime apiUtc;
            try {
                apiUtc = OffsetDateTime.parse(apiMatch.utcDate());
            } catch (Exception e) {
                log.warn("Cannot parse utcDate '{}' for match id={}", apiMatch.utcDate(), apiMatch.id());
                skipped++;
                continue;
            }

            Match match = matchOpt.get();

            // Compare in UTC: the DB may store epoch ms integers that Hibernate reads back
            // without timezone conversion, so LocalDateTime.equals() is unreliable here.
            long apiEpochMs = apiUtc.toInstant().toEpochMilli();
            long dbEpochMs  = match.getKickoffTime().atZone(zone).toInstant().toEpochMilli();

            if (apiEpochMs != dbEpochMs) {
                LocalDateTime newKickoff = apiUtc.atZoneSameInstant(zone).toLocalDateTime();
                log.info("Updating match {} (ext={}) kickoff: {} → {} (UTC was {})",
                        match.getId(), apiMatch.id(), match.getKickoffTime(), newKickoff, apiUtc);
                match.setKickoffTime(newKickoff);
                matchRepository.save(match);
                updated++;
            }
        }

        return SyncResult.success(updated + " kickoff time(s) updated" +
                (skipped > 0 ? ", " + skipped + " skipped" : ""));
    }

    /**
     * Non-destructive upsert of knockout stage matches from the API.
     * Creates new matches for unseen externalIds; updates kickoff/status for existing ones.
     * Never touches predictions or group stage matches.
     */
    public SyncResult syncKnockoutMatches() {
        FootballApiResponseDto response = rateLimiter.call(client::fetchAllMatches);
        if (response == null || response.matches() == null) {
            return SyncResult.skipped("No API response");
        }

        response.matches().stream()
                .map(FootballApiMatchDto::stage)
                .filter(s -> s != null && !"GROUP_STAGE".equals(s))
                .distinct()
                .forEach(s -> log.info("Knockout API stage string observed: '{}'", s));

        List<FootballApiMatchDto> knockoutMatches = response.matches().stream()
                .filter(m -> m.stage() != null && !"GROUP_STAGE".equals(m.stage()))
                .filter(m -> m.id() != null && m.utcDate() != null)
                .sorted(Comparator.comparing(FootballApiMatchDto::utcDate))
                .toList();

        if (knockoutMatches.isEmpty()) {
            return SyncResult.skipped("No knockout matches in API response");
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        int nextMatchNumber = matchRepository.findMaxMatchNumber().orElse(72) + 1;

        for (FootballApiMatchDto apiMatch : knockoutMatches) {
            MatchStage stage = mapKnockoutStage(apiMatch.stage());
            if (stage == null) {
                log.warn("Unknown knockout stage '{}' for match id={} — skipping", apiMatch.stage(), apiMatch.id());
                skipped++;
                continue;
            }

            String extId = String.valueOf(apiMatch.id());
            Optional<Match> existing = matchRepository.findByExternalId(extId);

            if (existing.isPresent()) {
                Match m = existing.get();
                m.setKickoffTime(parseUtc(apiMatch.utcDate()));
                m.setStatus(mapStatus(apiMatch.status()));
                if (m.getHomeTeam() == null && apiMatch.homeTeam() != null) {
                    resolveTeam(apiMatch.homeTeam()).ifPresent(t -> {
                        m.setHomeTeam(t);
                        m.setHomeTeamPlaceholder(null);
                    });
                }
                if (m.getAwayTeam() == null && apiMatch.awayTeam() != null) {
                    resolveTeam(apiMatch.awayTeam()).ifPresent(t -> {
                        m.setAwayTeam(t);
                        m.setAwayTeamPlaceholder(null);
                    });
                }
                matchRepository.save(m);
                updated++;
                continue;
            }

            Team home = null;
            Team away = null;
            String homePlaceholder = null;
            String awayPlaceholder = null;

            if (apiMatch.homeTeam() != null) {
                Optional<Team> opt = resolveTeam(apiMatch.homeTeam());
                if (opt.isPresent()) {
                    home = opt.get();
                } else {
                    homePlaceholder = apiMatch.homeTeam().name() != null ? apiMatch.homeTeam().name() : "TBD";
                }
            } else {
                homePlaceholder = "TBD";
            }

            if (apiMatch.awayTeam() != null) {
                Optional<Team> opt = resolveTeam(apiMatch.awayTeam());
                if (opt.isPresent()) {
                    away = opt.get();
                } else {
                    awayPlaceholder = apiMatch.awayTeam().name() != null ? apiMatch.awayTeam().name() : "TBD";
                }
            } else {
                awayPlaceholder = "TBD";
            }

            Match match = Match.builder()
                    .externalId(extId)
                    .stage(stage)
                    .matchNumber(nextMatchNumber++)
                    .roundLabel(stage.getDisplayName())
                    .homeTeam(home)
                    .awayTeam(away)
                    .homeTeamPlaceholder(homePlaceholder)
                    .awayTeamPlaceholder(awayPlaceholder)
                    .kickoffTime(parseUtc(apiMatch.utcDate()))
                    .status(mapStatus(apiMatch.status()))
                    .build();

            matchRepository.save(match);
            created++;
        }

        // Create or refresh RoundWindow for each knockout stage (window closes 1h before first match)
        for (MatchStage stage : List.of(MatchStage.ROUND_OF_32, MatchStage.ROUND_OF_16,
                MatchStage.QUARTER_FINAL, MatchStage.SEMI_FINAL, MatchStage.THIRD_PLACE, MatchStage.FINAL)) {
            List<Match> stageMatches = matchRepository.findByStage(stage);
            if (stageMatches.isEmpty()) continue;
            String label = stageMatches.get(0).getRoundLabel();
            if (label == null) continue;
            LocalDateTime firstKickoff = stageMatches.stream()
                    .map(Match::getKickoffTime).filter(java.util.Objects::nonNull)
                    .min(LocalDateTime::compareTo).orElse(null);
            if (firstKickoff == null) continue;
            RoundWindow rw = roundWindowRepository.findByRoundLabel(label)
                    .orElse(RoundWindow.builder().roundLabel(label).build());
            rw.setAutoOpensAt(firstKickoff.minusHours(24));
            rw.setAutoClosesAt(firstKickoff.minusHours(1));
            roundWindowRepository.save(rw);
        }

        return SyncResult.success(created + " created, " + updated + " updated" +
                (skipped > 0 ? ", " + skipped + " skipped" : ""));
    }

    private MatchStage mapKnockoutStage(String apiStage) {
        if (apiStage == null) return null;
        return switch (apiStage) {
            case "LAST_32",   "ROUND_OF_32"                -> MatchStage.ROUND_OF_32;
            case "LAST_16",   "ROUND_OF_16"                -> MatchStage.ROUND_OF_16;
            case "QUARTER_FINALS", "QUARTER_FINAL"         -> MatchStage.QUARTER_FINAL;
            case "SEMI_FINALS",    "SEMI_FINAL"            -> MatchStage.SEMI_FINAL;
            case "FINAL"                                   -> MatchStage.FINAL;
            case "THIRD_PLACE", "PLAY_OFF_FOR_THIRD_PLACE" -> MatchStage.THIRD_PLACE;
            default -> null;
        };
    }

    /**
     * Resolves a team from the API DTO, trying externalId → TLA → name in order.
     * When found via TLA or name, also stamps the externalId so future lookups
     * hit the fast externalId path.
     */
    private Optional<Team> resolveTeam(FootballApiTeamDto dto) {
        if (dto == null) return Optional.empty();

        // 1. Fast path: already linked by externalId
        if (dto.id() != null) {
            Optional<Team> t = teamRepository.findByExternalId(dto.id());
            if (t.isPresent()) return t;
        }

        // 2. TLA match
        if (dto.tla() != null) {
            Optional<Team> t = teamRepository.findByFifaCodeIgnoreCase(dto.tla());
            if (t.isPresent()) {
                linkExternalId(t.get(), dto.id());
                return t;
            }
        }

        // 3. Name match (handles "Ivory Coast" vs "Cote d'Ivoire" etc. where TLA differs)
        if (dto.name() != null) {
            Optional<Team> t = teamRepository.findByNameIgnoreCase(dto.name());
            if (t.isPresent()) {
                linkExternalId(t.get(), dto.id());
                return t;
            }
        }

        log.warn("Cannot resolve team: id={} tla={} name={}", dto.id(), dto.tla(), dto.name());
        return Optional.empty();
    }

    private void linkExternalId(Team team, Long externalId) {
        if (externalId != null && team.getExternalId() == null) {
            team.setExternalId(externalId);
            teamRepository.save(team);
        }
    }

    private LocalDateTime parseUtc(String utcDate) {
        try {
            return OffsetDateTime.parse(utcDate)
                    .atZoneSameInstant(ZoneId.of(timezoneId))
                    .toLocalDateTime();
        } catch (Exception e) {
            log.warn("Cannot parse UTC date '{}', using epoch", utcDate);
            return LocalDateTime.of(2026, 6, 11, 0, 0);
        }
    }

    private MatchStatus mapStatus(String status) {
        if (status == null) return MatchStatus.SCHEDULED;
        return switch (status) {
            case "FINISHED" -> MatchStatus.COMPLETED;
            case "IN_PLAY", "PAUSED" -> MatchStatus.SCHEDULED;
            default -> MatchStatus.SCHEDULED;
        };
    }
}
