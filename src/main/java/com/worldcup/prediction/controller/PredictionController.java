package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.TournamentWinnerPrediction;
import com.worldcup.prediction.dto.*;
import com.worldcup.prediction.repository.TeamRepository;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.PredictionViewService;
import com.worldcup.prediction.service.TournamentWinnerPredictionService;
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
@RequestMapping("/predictions")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionViewService predictionViewService;
    private final TournamentWinnerPredictionService tournamentWinnerService;
    private final TeamRepository teamRepository;

    @GetMapping
    public String predictionsPage(@AuthenticationPrincipal CustomOAuth2User principal, Model model) {
        Long userId = principal.getUserId();

        List<RoundSummaryDto> roundSummaries = predictionViewService.getRoundSummaries(userId);
        model.addAttribute("roundSummaries", roundSummaries);

        String activeRoundLabel = roundSummaries.stream()
                .filter(r -> "OPEN".equals(r.getStatus()))
                .map(RoundSummaryDto::getRoundLabel)
                .findFirst()
                .orElseGet(() -> roundSummaries.isEmpty() ? null
                        : roundSummaries.get(0).getRoundLabel());

        model.addAttribute("activeRoundLabel", activeRoundLabel);

        if (activeRoundLabel != null) {
            populateRoundModel(userId, activeRoundLabel, model);
        } else {
            model.addAttribute("roundMatches", List.of());
            model.addAttribute("matchesByDate", Map.of());
            model.addAttribute("filledCount", 0);
            model.addAttribute("totalCount", 0);
            model.addAttribute("roundLocked", false);
            model.addAttribute("roundOpen", false);
            model.addAttribute("allFilled", false);
        }

        model.addAttribute("pastRounds", predictionViewService.getPastRoundsForUser(userId));

        Optional<TournamentWinnerPrediction> winnerOpt = tournamentWinnerService.getForUser(userId);
        model.addAttribute("winnerSubmitted", winnerOpt.isPresent());
        model.addAttribute("winnerPick", winnerOpt.map(TournamentWinnerPrediction::getTeam).orElse(null));
        model.addAttribute("allTeams", teamRepository.findAllByOrderByNameAsc());
        model.addAttribute("pageTitle", "My Predictions");

        return "predictions";
    }

    @GetMapping("/round")
    public String roundFragment(@RequestParam("label") String roundLabel,
                                @AuthenticationPrincipal CustomOAuth2User principal,
                                Model model) {
        populateRoundModel(principal.getUserId(), roundLabel, model);
        model.addAttribute("activeRoundLabel", roundLabel);
        return "fragments/predictions-round-content :: roundContent";
    }

    @PostMapping("/submit")
    public String submitPredictions(@Valid @ModelAttribute PredictionSubmitDto submitDto,
                                    BindingResult bindingResult,
                                    @AuthenticationPrincipal CustomOAuth2User principal,
                                    RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid prediction data. Please check all scores.");
            return "redirect:/predictions";
        }
        try {
            int count = predictionViewService.submitPredictionsForRound(principal.getUserId(), submitDto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Predictions saved! " + count + " match" + (count == 1 ? "" : "es") + " locked in.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/predictions";
    }

    @PostMapping("/winner")
    public String submitWinner(@Valid @ModelAttribute TournamentWinnerSubmitDto dto,
                               BindingResult bindingResult,
                               @AuthenticationPrincipal CustomOAuth2User principal,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a team.");
            return "redirect:/predictions";
        }
        try {
            Team team = teamRepository.findById(dto.getTeamId())
                    .orElseThrow(() -> new IllegalStateException("Team not found."));
            TournamentWinnerPredictionDto predDto = new TournamentWinnerPredictionDto(team.getFlagCode());
            tournamentWinnerService.submitOrUpdate(principal.getUserId(), predDto);
            redirectAttributes.addFlashAttribute("successMessage", "Tournament winner prediction saved!");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/predictions";
    }

    private void populateRoundModel(Long userId, String roundLabel, Model model) {
        List<MatchPredictionDto> matches = predictionViewService.getMatchesForRound(userId, roundLabel);
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
