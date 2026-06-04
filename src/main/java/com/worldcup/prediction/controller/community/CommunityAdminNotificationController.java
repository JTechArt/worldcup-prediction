package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.Community;
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
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/c/{slug}/admin/notifications")
@RequiredArgsConstructor
public class CommunityAdminNotificationController {

    private final NotificationService notificationService;
    private final NotificationLogRepository notificationLogRepository;
    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final LeaderboardService leaderboardService;
    private final CommunityRepository communityRepository;

    @GetMapping
    public String notificationsPage(@PathVariable String slug,
                                    HttpServletRequest request,
                                    Model model) {
        Community community = (Community) request.getAttribute("community");
        List<NotificationLog> recentLogs = notificationLogRepository.findTop50ByOrderBySentAtDesc();
        model.addAttribute("recentLogs", recentLogs);
        model.addAttribute("community", community);
        model.addAttribute("slug", slug);

        List<Match> upcomingMatches = matchRepository.findByStatus(MatchStatus.SCHEDULED);
        model.addAttribute("upcomingMatches", upcomingMatches);
        model.addAttribute("pageTitle", community.getName() + " · Notifications");

        return "community/admin/notifications";
    }

    @PostMapping("/invite")
    public String sendInvitation(@PathVariable String slug,
                                 @RequestParam String email,
                                 @AuthenticationPrincipal CustomOAuth2User principal,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        Community community = (Community) request.getAttribute("community");
        String normalizedEmail = email.toLowerCase().trim();
        if (invitationRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invitation already sent to " + normalizedEmail);
            return "redirect:/c/" + slug + "/admin/notifications";
        }
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            redirectAttributes.addFlashAttribute("errorMessage", "User already registered with " + normalizedEmail);
            return "redirect:/c/" + slug + "/admin/notifications";
        }
        User admin = userRepository.findById(principal.getUserId()).orElseThrow();
        Invitation invitation = Invitation.builder()
                .email(normalizedEmail)
                .invitedBy(admin)
                .community(community)
                .build();
        invitationRepository.save(invitation);
        notificationService.sendInvitation(normalizedEmail, admin, community.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Invitation sent to " + normalizedEmail);
        return "redirect:/c/" + slug + "/admin/notifications";
    }

    @PostMapping("/reminder/{matchId}")
    public String sendReminder(@PathVariable String slug,
                               @PathVariable Long matchId,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        Community community = (Community) request.getAttribute("community");
        Match match = matchRepository.findByIdWithTeams(matchId).orElseThrow();
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
        List<User> usersWithoutPredictions = activeUsers.stream()
                .filter(u -> !predictionRepository.existsByUserIdAndMatchId(u.getId(), matchId))
                .collect(Collectors.toList());
        int sent = notificationService.sendPredictionReminders(usersWithoutPredictions, match, community.getId());
        redirectAttributes.addFlashAttribute("successMessage",
                "Sent " + sent + " prediction reminders for " + matchLabel(match));
        return "redirect:/c/" + slug + "/admin/notifications";
    }

    @PostMapping("/window-open/{matchId}")
    public String sendWindowOpen(@PathVariable String slug,
                                 @PathVariable Long matchId,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        Community community = (Community) request.getAttribute("community");
        Match match = matchRepository.findByIdWithTeams(matchId).orElseThrow();
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
        boolean sent = notificationService.sendPredictionWindowOpen(activeUsers, match, community.getId());
        String msg = sent ? "Window-open notification sent" : "Already sent (skipped)";
        redirectAttributes.addFlashAttribute("successMessage", msg);
        return "redirect:/c/" + slug + "/admin/notifications";
    }

    @PostMapping("/leaderboard-digest")
    public String sendLeaderboardDigest(@PathVariable String slug,
                                        HttpServletRequest request,
                                        RedirectAttributes redirectAttributes) {
        Community community = (Community) request.getAttribute("community");
        LocalDate today = LocalDate.now();
        List<LeaderboardEntryDto> top10 = leaderboardService.getTopN(10, community.getId());
        if (top10.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No leaderboard entries");
            return "redirect:/c/" + slug + "/admin/notifications";
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
        boolean sent = notificationService.sendLeaderboardDigest(today.toString(), topUsers, topEntries, matchResults, community.getId());
        String msg = sent ? "Leaderboard digest sent to top 10" : "Already sent today (skipped)";
        redirectAttributes.addFlashAttribute("successMessage", msg);
        return "redirect:/c/" + slug + "/admin/notifications";
    }

    private String matchLabel(Match match) {
        String home = match.getHomeTeam() != null ? match.getHomeTeam().getName() : "TBD";
        String away = match.getAwayTeam() != null ? match.getAwayTeam().getName() : "TBD";
        return home + " vs " + away;
    }
}
