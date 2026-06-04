package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.LeaderboardService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/c/{slug}")
@RequiredArgsConstructor
public class CommunityLeaderboardController {

    private final LeaderboardService leaderboardService;
    private final PredictionRepository predictionRepository;

    @GetMapping("/leaderboard")
    public String leaderboard(@PathVariable String slug,
                              HttpServletRequest request,
                              Authentication authentication,
                              Model model) {
        Community community = (Community) request.getAttribute("community");
        Long communityId = community.getId();

        List<LeaderboardEntryDto> entries = leaderboardService.getFullLeaderboard(communityId);

        // Phase breakdown: userId → (stage → sumPoints)
        Map<Long, Map<MatchStage, Integer>> phasePoints = new HashMap<>();
        predictionRepository.sumPointsByUserAndStageAndCommunityId(communityId).forEach(row -> {
            Long userId   = (Long)       row[0];
            MatchStage st = (MatchStage) row[1];
            int pts       = ((Number)    row[2]).intValue();
            phasePoints.computeIfAbsent(userId, k -> new EnumMap<>(MatchStage.class)).put(st, pts);
        });

        // Current user entry for KPI strip
        LeaderboardEntryDto currentUserEntry = null;
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomOAuth2User customUser) {
            currentUserEntry = leaderboardService.getEntryForUser(customUser.getUserId(), communityId).orElse(null);
        }

        model.addAttribute("community", community);
        model.addAttribute("slug", slug);
        model.addAttribute("entries", entries);
        model.addAttribute("stages", MatchStage.values());
        model.addAttribute("totalParticipants", entries.size());
        model.addAttribute("phasePoints", phasePoints);
        model.addAttribute("currentUserEntry", currentUserEntry);
        model.addAttribute("pageTitle", community.getName() + " · Leaderboard");

        return "community/leaderboard";
    }
}
