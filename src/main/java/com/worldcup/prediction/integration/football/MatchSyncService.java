package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.Group;
import com.worldcup.prediction.domain.Match;
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
import com.worldcup.prediction.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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

        // Clear in FK order: child rows first
        lineupRepository.deleteAllLineups();
        goalRepository.deleteAllGoals();
        predictionRepository.deleteAllPredictions();
        matchRepository.deleteAllMatches();
        groupRepository.deleteAllGroupTeams();
        log.info("Cleared all existing match and group-team data");

        AtomicInteger matchNumber = new AtomicInteger(1);
        int created = 0;
        int skipped = 0;

        for (FootballApiMatchDto apiMatch : groupMatches) {
            String groupName = apiMatch.group().replace("GROUP_", "");
            Optional<Group> groupOpt = groupRepository.findByNameIgnoreCase(groupName);
            if (groupOpt.isEmpty()) {
                log.warn("Group '{}' not found — skipping match id={}", groupName, apiMatch.id());
                skipped++;
                continue;
            }

            Optional<Team> homeOpt = resolveTeam(apiMatch.homeTeam());
            Optional<Team> awayOpt = resolveTeam(apiMatch.awayTeam());
            if (homeOpt.isEmpty() || awayOpt.isEmpty()) {
                log.warn("Cannot resolve teams for match id={} ({} vs {})",
                        apiMatch.id(), apiMatch.homeTeam().tla(), apiMatch.awayTeam().tla());
                skipped++;
                continue;
            }

            Group group = groupOpt.get();
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

        return SyncResult.success(created + " group stage matches created" +
                (skipped > 0 ? ", " + skipped + " skipped" : ""));
    }

    private Optional<Team> resolveTeam(FootballApiTeamDto dto) {
        if (dto == null) return Optional.empty();
        if (dto.id() != null) {
            Optional<Team> t = teamRepository.findByExternalId(dto.id());
            if (t.isPresent()) return t;
        }
        if (dto.tla() != null) return teamRepository.findByFifaCodeIgnoreCase(dto.tla());
        return Optional.empty();
    }

    private LocalDateTime parseUtc(String utcDate) {
        try {
            return OffsetDateTime.parse(utcDate).toLocalDateTime();
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
