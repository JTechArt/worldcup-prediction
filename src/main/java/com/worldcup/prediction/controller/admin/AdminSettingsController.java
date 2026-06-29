package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.enums.WindowMode;
import com.worldcup.prediction.security.SuperAdminAuthenticationProvider;
import com.worldcup.prediction.service.RoundWindowService;
import com.worldcup.prediction.service.TournamentSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final PasswordEncoder passwordEncoder;
    private final TournamentSettingsService tournamentSettingsService;
    private final RoundWindowService roundWindowService;

    @GetMapping
    public String settings(Model model) {
        model.addAttribute("tournamentSettings", tournamentSettingsService.getSettings());
        model.addAttribute("windowModes", WindowMode.values());
        return "admin/settings";
    }

    @PostMapping("/tournament-mode")
    public String updateTournamentMode(@RequestParam WindowMode windowMode,
                                       @RequestParam int dailyWindowCloseOffsetMinutes,
                                       @RequestParam int roundLockOffsetMinutes,
                                       RedirectAttributes redirectAttributes) {
        tournamentSettingsService.updateMode(windowMode);
        tournamentSettingsService.updateCloseOffset(dailyWindowCloseOffsetMinutes);
        tournamentSettingsService.updateRoundLockOffset(roundLockOffsetMinutes);
        roundWindowService.recalculateAllRoundWindows();
        redirectAttributes.addFlashAttribute("successMessage", "Tournament window mode updated.");
        return "redirect:/admin/settings";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirectAttributes) {
        if (newPassword == null || newPassword.length() < 8) {
            redirectAttributes.addFlashAttribute("errorMessage", "Password must be at least 8 characters.");
            return "redirect:/admin/settings";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Passwords do not match.");
            return "redirect:/admin/settings";
        }
        // Note: actual password update would require access to the admin properties or database
        // For now, this is a placeholder
        redirectAttributes.addFlashAttribute("successMessage", "Password change is not yet implemented for environment-based admin.");
        return "redirect:/admin/settings";
    }
}
