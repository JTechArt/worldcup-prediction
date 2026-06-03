package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.AuditAction;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.AuditLogService;
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.service.MatchAdminService;
import com.worldcup.prediction.service.UserService;
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
@RequestMapping("/admin/matches")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminMatchController {

    private final MatchAdminService matchAdminService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final UserService userService;

    @GetMapping
    public String listMatches(Model model) {
        model.addAttribute("matches", matchAdminService.findAllOrderByKickoffAsc());
        return "admin/matches";
    }

    @PostMapping("/{id}/result")
    public String enterResult(@PathVariable Long id,
                              @RequestParam @Min(0) int homeScore,
                              @RequestParam @Min(0) int awayScore,
                              @AuthenticationPrincipal CustomOAuth2User admin,
                              RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        Match match = matchAdminService.setResult(id, homeScore, awayScore);
        matchAdminService.scoreAllPredictions(id);
        auditLogService.log(adminId, AuditAction.MATCH_RESULT_ENTERED, "MATCH", id,
                match.getHomeTeam().getName() + " " + homeScore + "-" + awayScore + " " + match.getAwayTeam().getName());
        redirectAttributes.addFlashAttribute("successMessage",
                "Result entered: " + match.getHomeTeam().getName() + " " + homeScore + "–" + awayScore + " " + match.getAwayTeam().getName());
        return "redirect:/admin/matches";
    }

    @PostMapping("/{id}/open-window")
    public String openWindow(@PathVariable Long id,
                             @AuthenticationPrincipal CustomOAuth2User admin,
                             RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        Match match = matchAdminService.setPredictionWindowOpen(id, true);
        auditLogService.log(adminId, AuditAction.PREDICTION_WINDOW_OPENED, "MATCH", id,
                "Window opened: " + match.getHomeTeam().getName() + " vs " + match.getAwayTeam().getName());
        redirectAttributes.addFlashAttribute("successMessage",
                "Prediction window opened for " + match.getHomeTeam().getName() + " vs " + match.getAwayTeam().getName());
        return "redirect:/admin/matches";
    }

    @PostMapping("/{id}/close-window")
    public String closeWindow(@PathVariable Long id,
                              @AuthenticationPrincipal CustomOAuth2User admin,
                              RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        Match match = matchAdminService.setPredictionWindowOpen(id, false);
        auditLogService.log(adminId, AuditAction.PREDICTION_WINDOW_CLOSED, "MATCH", id,
                "Window closed: " + match.getHomeTeam().getName() + " vs " + match.getAwayTeam().getName());
        redirectAttributes.addFlashAttribute("successMessage",
                "Prediction window closed for " + match.getHomeTeam().getName() + " vs " + match.getAwayTeam().getName());
        return "redirect:/admin/matches";
    }

    @PostMapping("/{id}/send-reminder")
    public String sendReminder(@PathVariable Long id,
                               @AuthenticationPrincipal CustomOAuth2User admin,
                               RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        Match match = matchAdminService.findById(id);
        String matchInfo = match.getHomeTeam().getName() + " vs " + match.getAwayTeam().getName();
        List<User> activeUsers = userService.findByStatus(UserStatus.ACTIVE);
        activeUsers.forEach(u -> emailService.sendPredictionReminder(u, matchInfo));
        auditLogService.log(adminId, AuditAction.REMINDER_SENT, "MATCH", id,
                "Reminder for: " + matchInfo + " (" + activeUsers.size() + " recipients)");
        redirectAttributes.addFlashAttribute("successMessage",
                "Reminder logged for " + activeUsers.size() + " participants (stub — no email sent yet).");
        return "redirect:/admin/matches";
    }
}
