package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/matches")
@RequiredArgsConstructor
public class MatchPreviewController {

    private final MatchRepository matchRepository;
    private final MatchLineupRepository lineupRepository;
    private final MatchGoalRepository goalRepository;

    @GetMapping("/{id}")
    public String matchPreview(@PathVariable Long id, Model model) {
        Match match = matchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("match", match);
        model.addAttribute("goals", goalRepository.findByMatchIdOrderByMinuteAsc(id));
        model.addAttribute("hasLineup", lineupRepository.existsByMatchId(id));

        if (match.getHomeTeam() != null && match.getAwayTeam() != null) {
            model.addAttribute("homeLineup",
                    lineupRepository.findByMatchIdAndTeamIdOrderByStartingDescShirtNumberAsc(
                            id, match.getHomeTeam().getId()));
            model.addAttribute("awayLineup",
                    lineupRepository.findByMatchIdAndTeamIdOrderByStartingDescShirtNumberAsc(
                            id, match.getAwayTeam().getId()));
        }

        return "match-preview";
    }
}
