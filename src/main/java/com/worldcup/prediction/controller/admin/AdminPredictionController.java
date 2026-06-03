package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.enums.AuditAction;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.AuditLogService;
import com.worldcup.prediction.service.MatchAdminService;
import com.worldcup.prediction.service.PredictionService;
import com.worldcup.prediction.service.ScoringService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/predictions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPredictionController {

    private final PredictionService predictionService;
    private final MatchAdminService matchAdminService;
    private final AuditLogService auditLogService;
    private final ScoringService scoringService;

    @GetMapping
    public String listPredictions(@RequestParam(required = false) Long matchId, Model model) {
        model.addAttribute("allMatches", matchAdminService.findAllOrderByKickoffAsc());
        if (matchId != null) {
            Match selectedMatch = matchAdminService.findById(matchId);
            List<Prediction> predictions = predictionService.findAllByMatchId(matchId);
            model.addAttribute("selectedMatch", selectedMatch);
            model.addAttribute("predictions", predictions);
        }
        model.addAttribute("matchId", matchId);
        return "admin/predictions";
    }

    @PostMapping("/{id}/override")
    public String overridePrediction(@PathVariable Long id,
                                     @RequestParam @Min(0) int homeScore,
                                     @RequestParam @Min(0) int awayScore,
                                     @AuthenticationPrincipal CustomOAuth2User admin,
                                     RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        Prediction prediction = predictionService.overridePrediction(id, homeScore, awayScore, scoringService);
        auditLogService.log(adminId, AuditAction.PREDICTION_EDITED_BY_ADMIN, "PREDICTION", id,
                "Override: " + homeScore + "–" + awayScore
                        + " userId=" + prediction.getUser().getId()
                        + " matchId=" + prediction.getMatch().getId());
        redirectAttributes.addFlashAttribute("successMessage",
                "Prediction #" + id + " overridden to " + homeScore + "–" + awayScore);
        return "redirect:/admin/predictions?matchId=" + prediction.getMatch().getId();
    }
}
