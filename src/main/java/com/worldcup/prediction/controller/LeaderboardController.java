package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Public leaderboard page — no authentication required.
 * SecurityConfig permits /leaderboard/** without auth.
 */
@Controller
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final PredictionRepository predictionRepository;

    /**
     * GET /leaderboard
     *
     * Model attributes:
     *   entries           — List<LeaderboardEntryDto>
     *   stages            — MatchStage[] for phase header row
     *   totalParticipants — int
     *   phasePoints       — Map<Long userId, Map<MatchStage, Integer sumPoints>>
     *   currentUserEntry  — LeaderboardEntryDto or null
     */
    @GetMapping("/leaderboard")
    public String leaderboard(Model model, Authentication authentication) {
        List<LeaderboardEntryDto> entries = leaderboardService.getFullLeaderboard();

        // Phase breakdown: userId → (stage → sumPoints)
        Map<Long, Map<MatchStage, Integer>> phasePoints = new HashMap<>();
        predictionRepository.sumPointsByUserAndStage().forEach(row -> {
            Long userId   = (Long)      row[0];
            MatchStage st = (MatchStage) row[1];
            int pts       = ((Number)    row[2]).intValue();
            phasePoints.computeIfAbsent(userId, k -> new EnumMap<>(MatchStage.class)).put(st, pts);
        });

        // Current user entry for KPI strip
        LeaderboardEntryDto currentUserEntry = null;
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomOAuth2User customUser) {
            currentUserEntry = leaderboardService.getEntryForUser(customUser.getUserId()).orElse(null);
        }

        model.addAttribute("entries", entries);
        model.addAttribute("stages", MatchStage.values());
        model.addAttribute("totalParticipants", entries.size());
        model.addAttribute("phasePoints", phasePoints);
        model.addAttribute("currentUserEntry", currentUserEntry);

        return "leaderboard";
    }
}
