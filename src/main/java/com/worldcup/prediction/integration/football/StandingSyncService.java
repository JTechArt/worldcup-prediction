package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.Group;
import com.worldcup.prediction.domain.GroupStanding;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.integration.football.dto.*;
import com.worldcup.prediction.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class StandingSyncService {

    private final FootballApiClient client;
    private final FootballApiRateLimiter rateLimiter;
    private final GroupStandingRepository standingRepository;
    private final GroupRepository groupRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;

    public SyncResult syncStandings() {
        LocalDateTime lastUpdate = standingRepository.findMostRecentUpdateTime()
                .orElse(LocalDateTime.MIN);
        long recentCompletions = matchRepository.countByStatusAndUpdatedAtAfter(
                MatchStatus.COMPLETED, lastUpdate);
        if (recentCompletions == 0 && standingRepository.count() > 0) {
            return SyncResult.skipped("No matches completed since last standings update");
        }

        FootballApiStandingsResponseDto response = rateLimiter.call(client::fetchStandings);
        if (response == null || response.standings() == null) {
            return SyncResult.skipped("No API response");
        }

        int upserted = 0;
        for (FootballApiStandingGroupDto groupDto : response.standings()) {
            if (!"TOTAL".equals(groupDto.type()) || groupDto.group() == null) continue;

            String groupName = groupDto.group().replace("GROUP_", "");
            Optional<Group> groupOpt = groupRepository.findByNameIgnoreCase(groupName);
            if (groupOpt.isEmpty()) {
                log.warn("Group not found for name={}", groupName);
                continue;
            }
            Group group = groupOpt.get();

            if (groupDto.table() == null) continue;
            for (FootballApiStandingEntryDto entry : groupDto.table()) {
                if (entry.team() == null) continue;
                Optional<Team> teamOpt = Optional.empty();
                if (entry.team().id() != null) {
                    teamOpt = teamRepository.findByExternalId(entry.team().id());
                }
                if (teamOpt.isEmpty()) {
                    teamOpt = teamRepository.findByFifaCodeIgnoreCase(entry.team().tla());
                }
                if (teamOpt.isEmpty()) {
                    log.warn("Team not found for tla={}", entry.team().tla());
                    continue;
                }

                GroupStanding standing = standingRepository
                        .findByGroupIdAndTeamId(group.getId(), teamOpt.get().getId())
                        .orElse(GroupStanding.builder().group(group).team(teamOpt.get()).build());

                standing.setPosition(entry.position() != null ? entry.position() : 0);
                standing.setPlayed(entry.playedGames() != null ? entry.playedGames() : 0);
                standing.setWon(entry.won() != null ? entry.won() : 0);
                standing.setDrawn(entry.draw() != null ? entry.draw() : 0);
                standing.setLost(entry.lost() != null ? entry.lost() : 0);
                standing.setPoints(entry.points() != null ? entry.points() : 0);
                standing.setGoalsFor(entry.goalsFor() != null ? entry.goalsFor() : 0);
                standing.setGoalsAgainst(entry.goalsAgainst() != null ? entry.goalsAgainst() : 0);
                standing.setGoalDifference(entry.goalDifference() != null ? entry.goalDifference() : 0);
                standingRepository.save(standing);
                upserted++;
            }
        }

        return SyncResult.success(upserted + " standing rows upserted");
    }
}
