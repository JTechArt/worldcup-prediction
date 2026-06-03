package com.worldcup.prediction.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LeaderboardController {

    @GetMapping("/leaderboard")
    public String leaderboardPage() {
        return "leaderboard";
    }
}
