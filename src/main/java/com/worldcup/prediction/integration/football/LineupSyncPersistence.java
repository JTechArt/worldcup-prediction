package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.GoalType;
import com.worldcup.prediction.integration.football.dto.*;
import com.worldcup.prediction.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
class LineupSyncPersistence {

    private final MatchRepository matchRepository;
    private final MatchLineupRepository lineupRepository;
    private final MatchGoalRepository goalRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    void persistMatchResults(Long matchId, FootballApiMatchDetailDto detail) {
        Match match = matchRepository.findById(matchId).orElse(null);
        if (match == null) return;
        persistLineups(match, detail);
        persistGoals(match, detail);
        match.setLineupFetched(true);
        matchRepository.save(match);
    }

    private void persistLineups(Match match, FootballApiMatchDetailDto detail) {
        persistTeamLineup(match, detail.homeTeam());
        persistTeamLineup(match, detail.awayTeam());
    }

    private void persistTeamLineup(Match match, FootballApiTeamDto teamDto) {
        if (teamDto == null) return;
        Optional<Team> teamOpt = resolveTeam(teamDto);
        if (teamOpt.isEmpty()) return;
        Team team = teamOpt.get();

        if (teamDto.lineup() != null) {
            for (FootballApiInlinePlayerDto p : teamDto.lineup()) {
                saveInlineLineupEntry(match, team, p, true);
            }
        }
        if (teamDto.bench() != null) {
            for (FootballApiInlinePlayerDto p : teamDto.bench()) {
                saveInlineLineupEntry(match, team, p, false);
            }
        }
    }

    private void saveInlineLineupEntry(Match match, Team team, FootballApiInlinePlayerDto p, boolean starting) {
        if (p == null || p.id() == null) return;
        playerRepository.findByExternalId(p.id()).ifPresent(player ->
            lineupRepository.save(MatchLineup.builder()
                .match(match).team(team).player(player)
                .starting(starting)
                .shirtNumber(p.shirtNumber())
                .formationPosition(p.position())
                .build())
        );
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
