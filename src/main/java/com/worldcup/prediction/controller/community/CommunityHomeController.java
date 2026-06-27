package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.dto.DailyExactPredictorDto;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.DailyExactPredictorService;
import com.worldcup.prediction.service.LeaderboardService;
import com.worldcup.prediction.service.MatchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/c/{slug}")
@RequiredArgsConstructor
public class CommunityHomeController {

    private final LeaderboardService leaderboardService;
    private final MatchService matchService;
    private final DailyExactPredictorService dailyExactPredictorService;

    @GetMapping("/home")
    public String home(@PathVariable String slug,
                       @AuthenticationPrincipal CustomOAuth2User currentUser,
                       HttpServletRequest request, Model model) {
        Community community = (Community) request.getAttribute("community");
        Long userId = currentUser.getUserId();
        Long communityId = community.getId();

        List<LeaderboardEntryDto> topTen = leaderboardService.getTopN(10, communityId);
        int userRank = leaderboardService.getEntryForUser(userId, communityId)
                .map(LeaderboardEntryDto::getRank).orElse(0);
        int openMatchCount = matchService.getOpenMatchCount();

        List<DailyExactPredictorDto> exactPredictors =
                dailyExactPredictorService.getCumulativeHeroes(communityId, "all");

        model.addAttribute("community", community);
        model.addAttribute("slug", slug);
        model.addAttribute("topTen", topTen);
        model.addAttribute("userRank", userRank);
        model.addAttribute("openMatchCount", openMatchCount);
        model.addAttribute("exactPredictors", exactPredictors);
        model.addAttribute("heroStage", "all");
        model.addAttribute("heroTotalCount", exactPredictors.stream()
                .mapToInt(DailyExactPredictorDto::getExactCount).sum());
        model.addAttribute("pageTitle", community.getName() + " · Home");
        return "community/home";
    }

    @GetMapping("/heroes")
    public String heroes(@PathVariable String slug,
                         @RequestParam(defaultValue = "all") String stage,
                         HttpServletRequest request, Model model) {
        Community community = (Community) request.getAttribute("community");
        Long communityId = community.getId();

        List<DailyExactPredictorDto> exactPredictors =
                dailyExactPredictorService.getCumulativeHeroes(communityId, stage);

        model.addAttribute("slug", slug);
        model.addAttribute("exactPredictors", exactPredictors);
        model.addAttribute("heroStage", stage);
        model.addAttribute("heroTotalCount", exactPredictors.stream()
                .mapToInt(DailyExactPredictorDto::getExactCount).sum());
        return "fragments/heroes-content :: heroes-content";
    }
}
