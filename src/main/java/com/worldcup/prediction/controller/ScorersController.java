package com.worldcup.prediction.controller;

import com.worldcup.prediction.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/scorers")
@RequiredArgsConstructor
public class ScorersController {

    private final PlayerRepository playerRepository;

    @GetMapping
    public String scorers(Model model) {
        model.addAttribute("scorers",
                playerRepository.findByTournamentGoalsGreaterThanOrderByTournamentGoalsDesc(0));
        return "scorers";
    }
}
