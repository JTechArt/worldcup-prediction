package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.Invitation;
import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.NotificationLog;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.repository.*;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.LeaderboardService;
import com.worldcup.prediction.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;
    private final NotificationLogRepository notificationLogRepository;
    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final LeaderboardService leaderboardService;

    @GetMapping
    public String notificationsPage(Model model) {
        List<NotificationLog> recentLogs = notificationLogRepository.findTop50ByOrderBySentAtDesc();
        model.addAttribute("recentLogs", recentLogs);

        List<Match> upcomingMatches = matchRepository.findByStatus(MatchStatus.SCHEDULED);
        model.addAttribute("upcomingMatches", upcomingMatches);

        return "admin/notifications";
    }

    @PostMapping("/invite")
    public String sendInvitation(@RequestParam String email,
                                  @AuthenticationPrincipal CustomOAuth2User principal,
                                  RedirectAttributes redirectAttributes) {
        String normalizedEmail = email.toLowerCase().trim();
        if (invitationRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invitation already sent to " + normalizedEmail);
            return "redirect:/admin/notifications";
        }
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            redirectAttributes.addFlashAttribute("errorMessage", "User already registered with " + normalizedEmail);
            return "redirect:/admin/notifications";
        }

        User admin = userRepository.findById(principal.getUserId()).orElseThrow();
        Invitation invitation = Invitation.builder()
                .email(normalizedEmail)
                .invitedBy(admin)
                .build();
        invitationRepository.save(invitation);
        notificationService.sendInvitation(normalizedEmail, admin);

        redirectAttributes.addFlashAttribute("successMessage", "Invitation sent to " + normalizedEmail);
        return "redirect:/admin/notifications";
    }

    @PostMapping("/reminder/{matchId}")
    public String sendReminder(@PathVariable Long matchId, RedirectAttributes redirectAttributes) {
        Match match = matchRepository.findByIdWithTeams(matchId).orElseThrow();
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
        List<User> usersWithoutPredictions = activeUsers.stream()
                .filter(u -> !predictionRepository.existsByUserIdAndMatchId(u.getId(), matchId))
                .collect(Collectors.toList());

        int sent = notificationService.sendPredictionReminders(usersWithoutPredictions, match);
        redirectAttributes.addFlashAttribute("successMessage",
                "Sent " + sent + " prediction reminders for " + matchLabel(match));
        return "redirect:/admin/notifications";
    }

    @PostMapping("/window-open/{matchId}")
    public String sendWindowOpen(@PathVariable Long matchId, RedirectAttributes redirectAttributes) {
        Match match = matchRepository.findByIdWithTeams(matchId).orElseThrow();
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
        boolean sent = notificationService.sendPredictionWindowOpen(activeUsers, match);
        String msg = sent ? "Window-open notification sent" : "Already sent (skipped)";
        redirectAttributes.addFlashAttribute("successMessage", msg);
        return "redirect:/admin/notifications";
    }

    @PostMapping("/leaderboard-digest")
    public String sendLeaderboardDigest(RedirectAttributes redirectAttributes) {
        LocalDate today = LocalDate.now();
        List<LeaderboardEntryDto> top10 = leaderboardService.getTopN(10);
        if (top10.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No leaderboard entries");
            return "redirect:/admin/notifications";
        }

        List<User> topUsers = new ArrayList<>();
        List<Map<String, Object>> topEntries = new ArrayList<>();
        for (LeaderboardEntryDto entry : top10) {
            userRepository.findById(entry.getUserId()).ifPresent(topUsers::add);
            topEntries.add(Map.of(
                    "rank", entry.getRank(),
                    "name", entry.getDisplayName(),
                    "points", entry.getTotalPoints()
            ));
        }

        var dayStart = today.atStartOfDay();
        var dayEnd = today.atTime(LocalTime.MAX);
        List<Match> todayMatches = matchRepository.findByKickoffTimeBetween(dayStart, dayEnd);
        List<Map<String, Object>> matchResults = todayMatches.stream()
                .filter(Match::isCompleted)
                .map(m -> Map.<String, Object>of(
                        "label", matchLabel(m),
                        "score", m.getHomeScore() + " - " + m.getAwayScore()
                ))
                .collect(Collectors.toList());

        boolean sent = notificationService.sendLeaderboardDigest(today.toString(), topUsers, topEntries, matchResults);
        String msg = sent ? "Leaderboard digest sent to top 10" : "Already sent today (skipped)";
        redirectAttributes.addFlashAttribute("successMessage", msg);
        return "redirect:/admin/notifications";
    }

    private String matchLabel(Match match) {
        String home = match.getHomeTeam() != null ? match.getHomeTeam().getName() : "TBD";
        String away = match.getAwayTeam() != null ? match.getAwayTeam().getName() : "TBD";
        return home + " vs " + away;
    }
}
