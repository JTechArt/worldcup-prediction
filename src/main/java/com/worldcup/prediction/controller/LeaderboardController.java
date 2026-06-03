package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Public leaderboard page — accessible to guests and authenticated users alike.
 * SecurityConfig permits /leaderboard/** without authentication.
 */
@Controller
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    /**
     * GET /leaderboard
     *
     * Model attributes:
     *   entries           — List<LeaderboardEntryDto> sorted by rank
     *   stages            — MatchStage[] for phase header row
     *   totalParticipants — int
     *   currentUserEntry  — LeaderboardEntryDto or null (authenticated users only)
     */
    @GetMapping("/leaderboard")
    public String leaderboard(Model model, Authentication authentication) {
        List<LeaderboardEntryDto> entries = leaderboardService.getFullLeaderboard();

        LeaderboardEntryDto currentUserEntry = null;
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomOAuth2User customUser) {
            currentUserEntry = leaderboardService.getEntryForUser(customUser.getUserId()).orElse(null);
        }

        model.addAttribute("entries", entries);
        model.addAttribute("stages", MatchStage.values());
        model.addAttribute("totalParticipants", entries.size());
        model.addAttribute("currentUserEntry", currentUserEntry);

        return "leaderboard";
    }
}
