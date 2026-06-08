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
