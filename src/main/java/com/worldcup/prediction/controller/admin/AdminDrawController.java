package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/admin/draw")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminDrawController {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;

    @GetMapping
    public String drawPage(Model model) {
        List<MatchStage> knockoutOrder = List.of(
            MatchStage.ROUND_OF_32, MatchStage.ROUND_OF_16,
            MatchStage.QUARTER_FINAL, MatchStage.SEMI_FINAL,
            MatchStage.THIRD_PLACE, MatchStage.FINAL
        );
        Map<MatchStage, List<Match>> matchesByStage = new LinkedHashMap<>();
        Map<MatchStage, Long> assignedCountByStage = new LinkedHashMap<>();
        for (MatchStage stage : knockoutOrder) {
            List<Match> matches = matchRepository.findByStageWithTeams(stage);
            matches.sort(Comparator.comparing(Match::getKickoffTime));
            if (!matches.isEmpty()) {
                matchesByStage.put(stage, matches);
                long assigned = matches.stream()
                    .filter(m -> m.getHomeTeam() != null && m.getAwayTeam() != null)
                    .count();
                assignedCountByStage.put(stage, assigned);
            }
        }
        model.addAttribute("matchesByStage", matchesByStage);
        model.addAttribute("assignedCountByStage", assignedCountByStage);
        model.addAttribute("teams", teamRepository.findAllByOrderByNameAsc());
        return "admin/draw";
    }

    @PostMapping("/match/{id}/teams")
    public String assignTeams(@PathVariable Long id,
                               @RequestParam(required = false) Long homeTeamId,
                               @RequestParam(required = false) Long awayTeamId,
                               RedirectAttributes ra) {
        try {
            Match match = matchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + id));

            if (homeTeamId != null && homeTeamId.equals(awayTeamId)) {
                throw new IllegalArgumentException("Home and away team cannot be the same");
            }

            if (homeTeamId != null) {
                Team home = teamRepository.findById(homeTeamId)
                    .orElseThrow(() -> new IllegalArgumentException("Team not found: " + homeTeamId));
                match.setHomeTeam(home);
            } else {
                match.setHomeTeam(null);
            }

            if (awayTeamId != null) {
                Team away = teamRepository.findById(awayTeamId)
                    .orElseThrow(() -> new IllegalArgumentException("Team not found: " + awayTeamId));
                match.setAwayTeam(away);
            } else {
                match.setAwayTeam(null);
            }

            matchRepository.save(match);
            String homeLabel = match.getHomeTeam() != null ? match.getHomeTeam().getName() : "TBD";
            String awayLabel = match.getAwayTeam() != null ? match.getAwayTeam().getName() : "TBD";
            log.info("Admin assigned draw for match #{}: {} vs {}", match.getMatchNumber(), homeLabel, awayLabel);
            ra.addFlashAttribute("successMessage",
                "Match #" + match.getMatchNumber() + " updated: " + homeLabel + " vs " + awayLabel);
        } catch (Exception e) {
            log.error("Draw assignment error for match {}: {}", id, e.getMessage());
            ra.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
        }
        return "redirect:/admin/draw";
    }
}
