package com.worldcup.prediction.controller;

import com.worldcup.prediction.dto.FixtureViewDto;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.LeaderboardService;
import com.worldcup.prediction.service.MatchService;
import com.worldcup.prediction.service.UserStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final LeaderboardService leaderboardService;
    private final MatchService matchService;
    private final UserStatsService userStatsService;

    @GetMapping("/home")
    public String home(@AuthenticationPrincipal CustomOAuth2User currentUser, Model model) {
        Long userId = currentUser.getUserId();

        List<LeaderboardEntryDto> topTen = leaderboardService.getTopN(10);
        FixtureViewDto nextMatch = matchService.getNextPredictableMatch(userId);

        int userRank       = leaderboardService.getEntryForUser(userId).map(LeaderboardEntryDto::getRank).orElse(0);
        int userPoints     = userStatsService.getTotalPoints(userId);
        int userExact      = userStatsService.getExactScoreCount(userId);
        int totalPredicted = userStatsService.getTotalPredicted(userId);
        int openMatchCount = matchService.getOpenMatchCount();

        model.addAttribute("topTen",         topTen);
        model.addAttribute("nextMatch",      nextMatch);
        model.addAttribute("userRank",       userRank);
        model.addAttribute("userPoints",     userPoints);
        model.addAttribute("userExact",      userExact);
        model.addAttribute("totalPredicted", totalPredicted);
        model.addAttribute("openMatchCount", openMatchCount);
        model.addAttribute("pageTitle",      "Home");

        return "home";
    }
}
