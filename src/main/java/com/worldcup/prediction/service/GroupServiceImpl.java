package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.dto.GroupStandingDto;
import com.worldcup.prediction.repository.GroupRepository;
import com.worldcup.prediction.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final MatchRepository matchRepository;

    @Override
    public Map<String, List<GroupStandingDto>> getAllGroupStandings() {
        List<com.worldcup.prediction.domain.Group> groups = groupRepository.findAllWithTeams();
        List<Match> groupMatches = matchRepository.findByStageWithTeams(MatchStage.GROUP);

        Map<String, List<GroupStandingDto>> result = new LinkedHashMap<>();
        for (com.worldcup.prediction.domain.Group g : groups) {
            List<Match> gMatches = groupMatches.stream()
                    .filter(m -> m.getGroup() != null && m.getGroup().getId().equals(g.getId()))
                    .toList();
            result.put("Group " + g.getName(), computeStandings(g, gMatches));
        }
        return result;
    }

    @Override
    public List<String> getQualifiedThirdPlaceGroups() {
        return List.of();
    }

    @Override
    public Map<String, List<Match>> getMatchesByGroup() {
        List<com.worldcup.prediction.domain.Group> groups = groupRepository.findAllWithTeams();
        List<Match> groupMatches = matchRepository.findByStageWithTeams(MatchStage.GROUP);
        Map<String, List<Match>> result = new LinkedHashMap<>();
        for (com.worldcup.prediction.domain.Group g : groups) {
            result.put("Group " + g.getName(), groupMatches.stream()
                    .filter(m -> m.getGroup() != null && m.getGroup().getId().equals(g.getId()))
                    .toList());
        }
        return result;
    }

    private List<GroupStandingDto> computeStandings(
            com.worldcup.prediction.domain.Group group, List<Match> matches) {

        Map<Long, GroupStandingDto> byTeamId = new LinkedHashMap<>();

        for (Team t : group.getTeams()) {
            GroupStandingDto dto = new GroupStandingDto();
            dto.setTeamName(t.getName());
            dto.setTeamCode(t.getFlagCode());
            dto.setQualificationStatus("PENDING");
            byTeamId.put(t.getId(), dto);
        }

        for (Match m : matches) {
            if (m.getStatus() != MatchStatus.COMPLETED) continue;
            if (m.getHomeTeam() == null || m.getAwayTeam() == null) continue;
            if (m.getHomeScore() == null || m.getAwayScore() == null) continue;

            GroupStandingDto home = byTeamId.get(m.getHomeTeam().getId());
            GroupStandingDto away = byTeamId.get(m.getAwayTeam().getId());
            if (home == null || away == null) continue;

            int hs = m.getHomeScore();
            int as = m.getAwayScore();

            home.setPlayed(home.getPlayed() + 1);
            away.setPlayed(away.getPlayed() + 1);
            home.setGoalsFor(home.getGoalsFor() + hs);
            home.setGoalsAgainst(home.getGoalsAgainst() + as);
            away.setGoalsFor(away.getGoalsFor() + as);
            away.setGoalsAgainst(away.getGoalsAgainst() + hs);

            if (hs > as) {
                home.setWon(home.getWon() + 1);
                home.setPoints(home.getPoints() + 3);
                away.setLost(away.getLost() + 1);
            } else if (hs == as) {
                home.setDrawn(home.getDrawn() + 1);
                home.setPoints(home.getPoints() + 1);
                away.setDrawn(away.getDrawn() + 1);
                away.setPoints(away.getPoints() + 1);
            } else {
                away.setWon(away.getWon() + 1);
                away.setPoints(away.getPoints() + 3);
                home.setLost(home.getLost() + 1);
            }
        }

        List<GroupStandingDto> sorted = new ArrayList<>(byTeamId.values());
        sorted.forEach(dto -> dto.setGoalDifference(dto.getGoalsFor() - dto.getGoalsAgainst()));
        sorted.sort(Comparator.comparingInt(GroupStandingDto::getPoints).reversed()
                .thenComparingInt(GroupStandingDto::getGoalDifference).reversed()
                .thenComparingInt(GroupStandingDto::getGoalsFor).reversed());

        boolean allPlayed = sorted.stream().allMatch(dto -> dto.getPlayed() == 3);
        for (int i = 0; i < sorted.size(); i++) {
            GroupStandingDto dto = sorted.get(i);
            dto.setPosition(i + 1);
            if (allPlayed) {
                if (i < 2) dto.setQualificationStatus("QUALIFIED");
                else if (i == 2) dto.setQualificationStatus("THIRD");
                else dto.setQualificationStatus("ELIMINATED");
            }
        }

        return sorted;
    }
}
