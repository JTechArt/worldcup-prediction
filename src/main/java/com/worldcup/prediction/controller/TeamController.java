package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;

    @GetMapping("/{id}")
    public String teamPage(@PathVariable Long id, Model model) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("team", team);
        model.addAttribute("players", playerRepository.findByTeamIdOrderByShirtNumberAsc(id));
        model.addAttribute("matches", matchRepository.findByTeamIdOrderByKickoffTimeAsc(id));
        return "team";
    }
}
