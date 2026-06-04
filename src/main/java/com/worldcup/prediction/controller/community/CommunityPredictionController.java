package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.TournamentWinnerPrediction;
import com.worldcup.prediction.dto.*;
import com.worldcup.prediction.repository.TeamRepository;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.PredictionViewService;
import com.worldcup.prediction.service.TournamentWinnerPredictionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/c/{slug}/predictions")
@RequiredArgsConstructor
public class CommunityPredictionController {

    private final PredictionViewService predictionViewService;
    private final TournamentWinnerPredictionService tournamentWinnerService;
    private final TeamRepository teamRepository;

    @GetMapping
    public String predictionsPage(@PathVariable String slug,
                                  @AuthenticationPrincipal CustomOAuth2User principal,
                                  HttpServletRequest request, Model model) {
        Community community = (Community) request.getAttribute("community");
        Long userId = principal.getUserId();
        Long communityId = community.getId();

        List<RoundSummaryDto> roundSummaries = predictionViewService.getRoundSummaries(userId, communityId);
        model.addAttribute("roundSummaries", roundSummaries);

        String activeRoundLabel = roundSummaries.stream()
                .filter(r -> "OPEN".equals(r.getStatus()))
                .map(RoundSummaryDto::getRoundLabel)
                .findFirst()
                .orElseGet(() -> roundSummaries.isEmpty() ? null
                        : roundSummaries.get(0).getRoundLabel());

        model.addAttribute("activeRoundLabel", activeRoundLabel);

        if (activeRoundLabel != null) {
            populateRoundModel(userId, activeRoundLabel, communityId, model);
        } else {
            model.addAttribute("roundMatches", List.of());
            model.addAttribute("matchesByDate", Map.of());
            model.addAttribute("filledCount", 0);
            model.addAttribute("totalCount", 0);
            model.addAttribute("roundLocked", false);
            model.addAttribute("roundOpen", false);
            model.addAttribute("allFilled", false);
        }

        model.addAttribute("pastRounds", predictionViewService.getPastRoundsForUser(userId, communityId));

        Optional<TournamentWinnerPrediction> winnerOpt = tournamentWinnerService.getForUser(userId, communityId);
        model.addAttribute("winnerSubmitted", winnerOpt.isPresent());
        model.addAttribute("winnerPick", winnerOpt.map(TournamentWinnerPrediction::getTeam).orElse(null));
        model.addAttribute("allTeams", teamRepository.findAllByOrderByNameAsc());

        model.addAttribute("community", community);
        model.addAttribute("slug", slug);
        model.addAttribute("pageTitle", community.getName() + " · Predictions");

        return "community/predictions";
    }

    @GetMapping("/round")
    public String roundFragment(@RequestParam("label") String roundLabel,
                                @PathVariable String slug,
                                @AuthenticationPrincipal CustomOAuth2User principal,
                                HttpServletRequest request, Model model) {
        Community community = (Community) request.getAttribute("community");
        Long communityId = community.getId();
        populateRoundModel(principal.getUserId(), roundLabel, communityId, model);
        model.addAttribute("activeRoundLabel", roundLabel);
        model.addAttribute("slug", slug);
        return "fragments/predictions-round-content :: roundContent";
    }

    @PostMapping("/submit")
    public String submitPredictions(@PathVariable String slug,
                                    @Valid @ModelAttribute PredictionSubmitDto submitDto,
                                    BindingResult bindingResult,
                                    @AuthenticationPrincipal CustomOAuth2User principal,
                                    HttpServletRequest request,
                                    RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid prediction data. Please check all scores.");
            return "redirect:/c/" + slug + "/predictions";
        }
        Community community = (Community) request.getAttribute("community");
        try {
            int count = predictionViewService.submitPredictionsForRound(principal.getUserId(), submitDto, community.getId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Predictions saved! " + count + " match" + (count == 1 ? "" : "es") + " locked in.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/c/" + slug + "/predictions";
    }

    @PostMapping("/winner")
    public String submitWinner(@PathVariable String slug,
                               @Valid @ModelAttribute TournamentWinnerSubmitDto dto,
                               BindingResult bindingResult,
                               @AuthenticationPrincipal CustomOAuth2User principal,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a team.");
            return "redirect:/c/" + slug + "/predictions";
        }
        Community community = (Community) request.getAttribute("community");
        try {
            var team = teamRepository.findById(dto.getTeamId())
                    .orElseThrow(() -> new IllegalStateException("Team not found."));
            TournamentWinnerPredictionDto predDto = new TournamentWinnerPredictionDto(team.getFlagCode());
            tournamentWinnerService.submitOrUpdate(principal.getUserId(), predDto, community.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Tournament winner prediction saved!");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/c/" + slug + "/predictions";
    }

    private void populateRoundModel(Long userId, String roundLabel, Long communityId, Model model) {
        List<MatchPredictionDto> matches = predictionViewService.getMatchesForRound(userId, roundLabel, communityId);
        Map<String, List<MatchPredictionDto>> matchesByDate = predictionViewService.groupMatchesByDate(matches);
        long filled = matches.stream().filter(MatchPredictionDto::isPredictionSaved).count();
        boolean roundLocked = !matches.isEmpty() && matches.stream().allMatch(MatchPredictionDto::isLocked);
        boolean roundOpen = !roundLocked && matches.stream().anyMatch(m -> !m.isLocked());

        model.addAttribute("roundMatches", matches);
        model.addAttribute("matchesByDate", matchesByDate);
        model.addAttribute("filledCount", filled);
        model.addAttribute("totalCount", matches.size());
        model.addAttribute("roundLocked", roundLocked);
        model.addAttribute("roundOpen", roundOpen);
        model.addAttribute("allFilled", !matches.isEmpty() && filled == matches.size());
    }
}
