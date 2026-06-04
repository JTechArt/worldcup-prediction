package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final GroupRepository groupRepository;
    private final GroupStandingRepository groupStandingRepository;

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public String teamPage(@PathVariable Long id, Model model) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        model.addAttribute("team", team);
        model.addAttribute("players", playerRepository.findByTeamIdOrderByShirtNumberAsc(id));

        List<Match> allMatches = matchRepository.findByTeamIdOrderByKickoffTimeAsc(id);
        List<Match> completedMatches = allMatches.stream()
                .filter(m -> m.getStatus() == MatchStatus.COMPLETED)
                .collect(Collectors.toList());
        List<Match> upcomingMatches = allMatches.stream()
                .filter(m -> m.getStatus() != MatchStatus.COMPLETED)
                .collect(Collectors.toList());
        model.addAttribute("completedMatches", completedMatches);
        model.addAttribute("upcomingMatches", upcomingMatches);

        // Compute match results from this team's perspective
        Map<Long, String> matchResults = completedMatches.stream()
                .collect(Collectors.toMap(Match::getId, m -> computeResult(m, team)));
        model.addAttribute("matchResults", matchResults);

        // Group + standing
        var groups = groupRepository.findAllWithTeams();
        var teamGroup = groups.stream()
                .filter(g -> g.getTeams().stream().anyMatch(t -> t.getId().equals(id)))
                .findFirst().orElse(null);
        model.addAttribute("teamGroup", teamGroup);

        if (teamGroup != null) {
            var standing = groupStandingRepository.findByGroupIdAndTeamId(teamGroup.getId(), id)
                    .orElse(null);
            model.addAttribute("standing", standing);
        }

        // Hero image: check if team-specific exists, otherwise default
        String heroPath = resolveImagePath("images/teams/" + team.getFifaCode().toLowerCase() + "-hero.jpg",
                                           "images/teams/default-hero.jpg");
        model.addAttribute("heroImage", "/" + heroPath);

        // Star player image: check if exists
        String starPath = "images/teams/" + team.getFifaCode().toLowerCase() + "-star.png";
        boolean hasStarImage = new ClassPathResource("static/" + starPath).exists();
        model.addAttribute("hasStarImage", hasStarImage);
        if (hasStarImage) {
            model.addAttribute("starImage", "/" + starPath);
        }

        return "team";
    }

    private String resolveImagePath(String teamSpecific, String fallback) {
        if (new ClassPathResource("static/" + teamSpecific).exists()) {
            return teamSpecific;
        }
        return fallback;
    }

    private String computeResult(Match match, Team team) {
        if (match.getHomeScore() == null || match.getAwayScore() == null) return "pending";
        int teamScore, opponentScore;
        if (match.getHomeTeam() != null && match.getHomeTeam().getId().equals(team.getId())) {
            teamScore = match.getHomeScore();
            opponentScore = match.getAwayScore();
        } else {
            teamScore = match.getAwayScore();
            opponentScore = match.getHomeScore();
        }
        if (teamScore > opponentScore) return "win";
        if (teamScore < opponentScore) return "loss";
        return "draw";
    }
}
