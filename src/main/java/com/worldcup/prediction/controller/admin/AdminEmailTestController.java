package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/email-test")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminEmailTestController {

    private final EmailService emailService;

    @GetMapping
    public String emailTestPage() {
        return "admin/email-test";
    }

    @PostMapping("/invitation")
    public String testInvitation(@RequestParam String to,
                                 @RequestParam String inviterName,
                                 RedirectAttributes ra) {
        try {
            emailService.sendTestInvitation(to, inviterName);
            ra.addFlashAttribute("successMessage", "Invitation test sent to " + to);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/email-test";
    }

    @PostMapping("/approval")
    public String testApproval(@RequestParam String to,
                               @RequestParam String firstName,
                               RedirectAttributes ra) {
        try {
            emailService.sendApprovalEmail(fakeUser(to, firstName));
            ra.addFlashAttribute("successMessage", "Approval test sent to " + to);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/email-test";
    }

    @PostMapping("/rejection")
    public String testRejection(@RequestParam String to,
                                @RequestParam String firstName,
                                RedirectAttributes ra) {
        try {
            emailService.sendRejectionEmail(fakeUser(to, firstName));
            ra.addFlashAttribute("successMessage", "Rejection test sent to " + to);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/email-test";
    }

    @PostMapping("/window-open")
    public String testWindowOpen(@RequestParam String to,
                                 @RequestParam String firstName,
                                 @RequestParam String matchLabel,
                                 @RequestParam String kickoff,
                                 RedirectAttributes ra) {
        try {
            emailService.sendTestWindowOpen(to, firstName, matchLabel, kickoff);
            ra.addFlashAttribute("successMessage", "Window-open test sent to " + to);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/email-test";
    }

    @PostMapping("/reminder")
    public String testReminder(@RequestParam String to,
                               @RequestParam String firstName,
                               @RequestParam String matchLabel,
                               @RequestParam String kickoff,
                               @RequestParam String hoursLeft,
                               RedirectAttributes ra) {
        try {
            emailService.sendTestReminder(to, firstName, matchLabel, kickoff, hoursLeft);
            ra.addFlashAttribute("successMessage", "Reminder test sent to " + to);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/email-test";
    }

    @PostMapping("/results")
    public String testResults(@RequestParam String to,
                              @RequestParam String firstName,
                              @RequestParam String matchLabel,
                              @RequestParam String score,
                              RedirectAttributes ra) {
        try {
            emailService.sendTestResults(to, firstName, matchLabel, score);
            ra.addFlashAttribute("successMessage", "Results test sent to " + to);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/email-test";
    }

    @PostMapping("/leaderboard")
    public String testLeaderboard(@RequestParam String to,
                                  @RequestParam String firstName,
                                  @RequestParam int rank,
                                  @RequestParam int points,
                                  RedirectAttributes ra) {
        if (rank < 1 || rank > 10) {
            ra.addFlashAttribute("errorMessage", "Rank must be between 1 and 10");
            return "redirect:/admin/email-test";
        }
        try {
            List<Map<String, Object>> topEntries = buildSyntheticLeaderboard(firstName, rank, points);
            List<Map<String, Object>> matchResults = List.of(
                    Map.of("label", "France vs Brazil", "score", "2 - 1"),
                    Map.of("label", "Germany vs Argentina", "score", "1 - 1")
            );
            emailService.sendLeaderboardDigest(fakeUser(to, firstName), rank, points, topEntries, matchResults);
            ra.addFlashAttribute("successMessage", "Leaderboard digest test sent to " + to);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/email-test";
    }

    @PostMapping("/prediction-confirmation")
    public String testPredictionConfirmation(@RequestParam String to,
                                              @RequestParam String firstName,
                                              @RequestParam String roundLabel,
                                              RedirectAttributes ra) {
        try {
            emailService.sendTestPredictionConfirmation(to, firstName, roundLabel);
            ra.addFlashAttribute("successMessage", "Prediction confirmation test sent to " + to);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/email-test";
    }

    private User fakeUser(String email, String firstName) {
        return User.builder()
                .email(email)
                .firstName(firstName)
                .lastName("")
                .build();
    }

    private List<Map<String, Object>> buildSyntheticLeaderboard(String firstName, int rank, int points) {
        String[] dummyNames = {"Carlos M.", "Sophie L.", "Luca R.", "Emma K.",
                "Rafael T.", "Yuki N.", "Omar S.", "Priya D.", "Jonas B.", "Ana C."};
        List<Map<String, Object>> entries = new ArrayList<>();
        int dummyIdx = 0;
        for (int i = 1; i <= 10; i++) {
            if (i == rank) {
                entries.add(Map.of("rank", i, "name", firstName, "points", points));
            } else {
                int pts = Math.max(0, points + (rank - i) * 7);
                entries.add(Map.of("rank", i, "name", dummyNames[dummyIdx++], "points", pts));
            }
        }
        return entries;
    }
}
