package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.enums.AuditAction;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.AuditLogService;
import com.worldcup.prediction.service.MatchAdminService;
import com.worldcup.prediction.service.PredictionService;
import com.worldcup.prediction.service.ScoringService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/predictions")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminPredictionController {

    private final PredictionService predictionService;
    private final MatchAdminService matchAdminService;
    private final AuditLogService auditLogService;
    private final ScoringService scoringService;
    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository communityMembershipRepository;

    @GetMapping
    public String listPredictions(@RequestParam(required = false) Long matchId,
                                   @RequestParam(required = false) Long communityId,
                                   Model model) {
        model.addAttribute("allMatches", matchAdminService.findAllOrderByKickoffAsc());
        model.addAttribute("allCommunities", communityRepository.findAll(Sort.by("name")));
        model.addAttribute("matchId", matchId);
        model.addAttribute("communityId", communityId);

        if (matchId != null) {
            Match selectedMatch = matchAdminService.findById(matchId);
            model.addAttribute("selectedMatch", selectedMatch);

            if (communityId != null) {
                Community selectedCommunity = communityRepository.findById(communityId).orElse(null);
                List<Prediction> predictions = predictionService.findAllByMatchIdAndCommunityId(matchId, communityId);
                var missingMembers = communityMembershipRepository
                        .findActiveMembersWithoutPrediction(communityId, matchId);
                model.addAttribute("selectedCommunity", selectedCommunity);
                model.addAttribute("predictions", predictions);
                model.addAttribute("missingMembers", missingMembers);
            } else {
                List<Prediction> predictions = predictionService.findAllByMatchId(matchId);
                model.addAttribute("predictions", predictions);
            }
        }
        return "admin/predictions";
    }

    @PostMapping("/{id}/override")
    public String overridePrediction(@PathVariable Long id,
                                     @RequestParam @Min(0) int homeScore,
                                     @RequestParam @Min(0) int awayScore,
                                     @RequestParam(required = false) Long communityId,
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
        String redirect = "redirect:/admin/predictions?matchId=" + prediction.getMatch().getId();
        if (communityId != null) redirect += "&communityId=" + communityId;
        return redirect;
    }

    @PostMapping("/create")
    public String createPrediction(@RequestParam Long matchId,
                                    @RequestParam Long communityId,
                                    @RequestParam Long userId,
                                    @RequestParam @Min(0) int homeScore,
                                    @RequestParam @Min(0) int awayScore,
                                    @AuthenticationPrincipal CustomOAuth2User admin,
                                    RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        try {
            Prediction prediction = predictionService.createOnBehalfOf(
                    userId, matchId, communityId, homeScore, awayScore, scoringService);
            auditLogService.log(adminId, AuditAction.PREDICTION_EDITED_BY_ADMIN, "PREDICTION",
                    prediction.getId(),
                    "Created on behalf: " + homeScore + "–" + awayScore
                            + " userId=" + userId
                            + " matchId=" + matchId
                            + " communityId=" + communityId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Prediction created for " + prediction.getUser().getFullName()
                            + ": " + homeScore + "–" + awayScore);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/predictions?matchId=" + matchId + "&communityId=" + communityId;
    }
}
