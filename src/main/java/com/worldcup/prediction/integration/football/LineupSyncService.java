package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.GoalType;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.integration.football.dto.*;
import com.worldcup.prediction.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class LineupSyncService {

    private final FootballApiClient client;
    private final FootballApiRateLimiter rateLimiter;
    private final MatchRepository matchRepository;
    private final MatchLineupRepository lineupRepository;
    private final MatchGoalRepository goalRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    public SyncResult syncLineups() {
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
            FootballApiMatchDetailDto detail = rateLimiter.call(() -> client.fetchMatchDetail(extId));
            if (detail == null) continue;

            persistLineups(match, detail);
            persistGoals(match, detail);
            match.setLineupFetched(true);
            matchRepository.save(match);
            fetched++;
        }

        return SyncResult.success(fetched + " match lineups fetched");
    }

    private void persistLineups(Match match, FootballApiMatchDetailDto detail) {
        if (detail.lineups() == null) return;
        for (FootballApiLineupDto lineupDto : detail.lineups()) {
            if (lineupDto.team() == null) continue;
            Optional<Team> teamOpt = resolveTeam(lineupDto.team());
            if (teamOpt.isEmpty()) continue;
            Team team = teamOpt.get();

            if (lineupDto.startXI() != null) {
                for (FootballApiLineupPlayerDto lp : lineupDto.startXI()) {
                    saveLineupEntry(match, team, lp, true);
                }
            }
            if (lineupDto.substitutes() != null) {
                for (FootballApiLineupPlayerDto lp : lineupDto.substitutes()) {
                    saveLineupEntry(match, team, lp, false);
                }
            }
        }
    }

    private void saveLineupEntry(Match match, Team team, FootballApiLineupPlayerDto lp, boolean starting) {
        if (lp.player() == null || lp.player().id() == null) return;
        playerRepository.findByExternalId(lp.player().id()).ifPresent(player -> {
            MatchLineup entry = MatchLineup.builder()
                    .match(match).team(team).player(player)
                    .starting(starting)
                    .shirtNumber(lp.shirtNumber())
                    .formationPosition(lp.position())
                    .build();
            lineupRepository.save(entry);
        });
    }

    private void persistGoals(Match match, FootballApiMatchDetailDto detail) {
        if (detail.goals() == null) return;
        for (FootballApiGoalDto goalDto : detail.goals()) {
            if (goalDto.team() == null || goalDto.minute() == null) continue;
            Optional<Team> teamOpt = resolveTeam(goalDto.team());
            if (teamOpt.isEmpty()) continue;

            Player scorer = null;
            if (goalDto.scorer() != null && goalDto.scorer().id() != null) {
                scorer = playerRepository.findByExternalId(goalDto.scorer().id()).orElse(null);
            }

            GoalType type = parseGoalType(goalDto.type());
            MatchGoal goal = MatchGoal.builder()
                    .match(match).team(teamOpt.get()).player(scorer)
                    .minute(goalDto.minute()).type(type).build();
            goalRepository.save(goal);
        }
    }

    private GoalType parseGoalType(String raw) {
        if ("OWN_GOAL".equals(raw)) return GoalType.OWN_GOAL;
        if ("PENALTY".equals(raw)) return GoalType.PENALTY;
        return GoalType.REGULAR;
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
}
