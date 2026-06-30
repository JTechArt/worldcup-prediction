package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.AuditAction;
import com.worldcup.prediction.domain.enums.PlayoffWinner;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.AuditLogService;
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.service.MatchAdminService;
import com.worldcup.prediction.service.RoundWindowService;
import com.worldcup.prediction.service.UserService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/matches")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminMatchController {

    private final MatchAdminService matchAdminService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final UserService userService;
    private final RoundWindowService roundWindowService;

    @GetMapping
    public String listMatches(Model model) {
        List<Match> allMatches = matchAdminService.findAllOrderByKickoffAsc();
        List<RoundWindow> roundWindows = roundWindowService.findAll();
        LocalDateTime now = LocalDateTime.now();

        Map<String, RoundWindow> windowMap = roundWindows.stream()
                .collect(Collectors.toMap(RoundWindow::getRoundLabel, rw -> rw));

        Map<String, List<Match>> matchesByRound = new LinkedHashMap<>();
        for (Match m : allMatches) {
            matchesByRound.computeIfAbsent(m.getRoundLabel(), k -> new ArrayList<>()).add(m);
        }

        Map<String, String> roundStatuses = new LinkedHashMap<>();
        Map<String, Boolean> roundOverridden = new LinkedHashMap<>();
        for (String roundLabel : matchesByRound.keySet()) {
            List<Match> matches = matchesByRound.get(roundLabel);
            boolean allCompleted = matches.stream().allMatch(Match::isCompleted);
            if (allCompleted) {
                roundStatuses.put(roundLabel, "PAST");
            } else if (roundWindowService.isRoundOpen(roundLabel, now)) {
                roundStatuses.put(roundLabel, "OPEN");
            } else {
                roundStatuses.put(roundLabel, "FUTURE");
            }
            RoundWindow rw = windowMap.get(roundLabel);
            roundOverridden.put(roundLabel, rw != null && rw.getOverrideStatus() != null);
        }

        model.addAttribute("matchesByRound", matchesByRound);
        model.addAttribute("roundStatuses", roundStatuses);
        model.addAttribute("roundOverridden", roundOverridden);
        return "admin/matches";
    }

    @PostMapping("/{id}/result")
    public String enterResult(@PathVariable Long id,
                              @RequestParam(required = false) @Min(0) Integer homeScore,
                              @RequestParam(required = false) @Min(0) Integer awayScore,
                              @RequestParam(required = false) @Min(0) Integer home90,
                              @RequestParam(required = false) @Min(0) Integer away90,
                              @RequestParam(required = false) String playoffWinner,
                              @AuthenticationPrincipal CustomOAuth2User admin,
                              RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        Match match = matchAdminService.findById(id);

        if (match.isKnockout()) {
            if (home90 == null || away90 == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "90-min scores are required for knockout matches.");
                return "redirect:/admin/matches";
            }
            PlayoffWinner winner = "HOME".equals(playoffWinner) ? PlayoffWinner.HOME_WIN
                    : "AWAY".equals(playoffWinner) ? PlayoffWinner.AWAY_WIN : null;
            // Clear winner if not a draw (radio may have been submitted while hidden)
            if (home90 != away90) winner = null;
            User adminUser = admin != null ? admin.getUser() : null;
            matchAdminService.set90MinResult(id, home90, away90, winner, adminUser);
        } else {
            if (homeScore == null || awayScore == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Home and away scores are required.");
                return "redirect:/admin/matches";
            }
            matchAdminService.setResult(id, homeScore, awayScore);
        }

        matchAdminService.scoreAllPredictions(id);
        auditLogService.log(adminId, AuditAction.MATCH_RESULT_ENTERED, "MATCH", id,
                match.getHomeTeam().getName() + " result entered");
        redirectAttributes.addFlashAttribute("successMessage", "Result saved successfully.");
        return "redirect:/admin/matches";
    }

    @PostMapping("/{id}/unlock-result")
    public String unlockResult(@PathVariable Long id,
                               @AuthenticationPrincipal CustomOAuth2User admin,
                               RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        matchAdminService.unlockResult(id);
        auditLogService.log(adminId, AuditAction.MATCH_RESULT_RESET, "MATCH", id,
                "Result source unlocked for API re-sync");
        redirectAttributes.addFlashAttribute("successMessage", "Result unlocked. API will re-sync 90-min scores.");
        return "redirect:/admin/matches";
    }

    @PostMapping("/{id}/reset-result")
    public String resetResult(@PathVariable Long id,
                              @AuthenticationPrincipal CustomOAuth2User admin,
                              RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        Match match = matchAdminService.resetResult(id);
        auditLogService.log(adminId, AuditAction.MATCH_RESULT_RESET, "MATCH", id,
                match.getHomeTeam().getName() + " vs " + match.getAwayTeam().getName());
        redirectAttributes.addFlashAttribute("successMessage",
                "Result reset for: " + match.getHomeTeam().getName() + " vs " + match.getAwayTeam().getName());
        return "redirect:/admin/matches";
    }

    @PostMapping("/rounds/{roundLabel}/open")
    public String openRound(@PathVariable String roundLabel,
                            @AuthenticationPrincipal CustomOAuth2User admin,
                            RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        roundWindowService.openRound(roundLabel);
        auditLogService.log(adminId, AuditAction.ROUND_WINDOW_OPENED, "ROUND", null,
                "Round opened: " + roundLabel);
        redirectAttributes.addFlashAttribute("successMessage",
                "Prediction window opened for " + roundLabel);
        return "redirect:/admin/matches";
    }

    @PostMapping("/rounds/{roundLabel}/close")
    public String closeRound(@PathVariable String roundLabel,
                             @AuthenticationPrincipal CustomOAuth2User admin,
                             RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        roundWindowService.closeRound(roundLabel);
        auditLogService.log(adminId, AuditAction.ROUND_WINDOW_CLOSED, "ROUND", null,
                "Round closed: " + roundLabel);
        redirectAttributes.addFlashAttribute("successMessage",
                "Prediction window closed for " + roundLabel);
        return "redirect:/admin/matches";
    }

    @PostMapping("/rounds/{roundLabel}/reset")
    public String resetRound(@PathVariable String roundLabel,
                             @AuthenticationPrincipal CustomOAuth2User admin,
                             RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        roundWindowService.resetOverride(roundLabel);
        auditLogService.log(adminId, AuditAction.ROUND_WINDOW_RESET, "ROUND", null,
                "Round reset to auto: " + roundLabel);
        redirectAttributes.addFlashAttribute("successMessage",
                "Prediction window reset to automatic for " + roundLabel);
        return "redirect:/admin/matches";
    }

    @PostMapping("/rounds/{roundLabel}/send-reminder")
    public String sendRoundReminder(@PathVariable String roundLabel,
                                    @AuthenticationPrincipal CustomOAuth2User admin,
                                    RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        List<User> activeUsers = userService.findByStatus(UserStatus.ACTIVE);
        activeUsers.forEach(u -> emailService.sendPredictionReminder(u, roundLabel));
        auditLogService.log(adminId, AuditAction.REMINDER_SENT, "ROUND", null,
                "Reminder for: " + roundLabel + " (" + activeUsers.size() + " recipients)");
        redirectAttributes.addFlashAttribute("successMessage",
                "Reminder sent for " + roundLabel + " (" + activeUsers.size() + " participants).");
        return "redirect:/admin/matches";
    }
}
