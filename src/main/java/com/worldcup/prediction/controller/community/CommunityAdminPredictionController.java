package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.MatchAdminService;
import com.worldcup.prediction.service.PredictionService;
import com.worldcup.prediction.service.ScoringService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/c/{slug}/admin/predictions")
@RequiredArgsConstructor
public class CommunityAdminPredictionController {

    private final PredictionService predictionService;
    private final MatchAdminService matchAdminService;
    private final ScoringService scoringService;

    @GetMapping
    public String listPredictions(@PathVariable String slug,
                                  @RequestParam(required = false) Long matchId,
                                  HttpServletRequest request,
                                  Model model) {
        Community community = (Community) request.getAttribute("community");
        model.addAttribute("community", community);
        model.addAttribute("slug", slug);
        model.addAttribute("allMatches", matchAdminService.findAllOrderByKickoffAsc());
        if (matchId != null) {
            Match selectedMatch = matchAdminService.findById(matchId);
            List<Prediction> predictions = predictionService.findAllByMatchId(matchId);
            model.addAttribute("selectedMatch", selectedMatch);
            model.addAttribute("predictions", predictions);
        }
        model.addAttribute("matchId", matchId);
        model.addAttribute("pageTitle", community.getName() + " · Predictions");
        return "community/admin/predictions";
    }

    @PostMapping("/{id}/override")
    public String overridePrediction(@PathVariable String slug,
                                     @PathVariable Long id,
                                     @RequestParam @Min(0) int homeScore,
                                     @RequestParam @Min(0) int awayScore,
                                     @AuthenticationPrincipal CustomOAuth2User admin,
                                     RedirectAttributes redirectAttributes) {
        Prediction prediction = predictionService.overridePrediction(id, homeScore, awayScore, scoringService);
        redirectAttributes.addFlashAttribute("successMessage",
                "Prediction #" + id + " overridden to " + homeScore + "–" + awayScore);
        return "redirect:/c/" + slug + "/admin/predictions?matchId=" + prediction.getMatch().getId();
    }
}
