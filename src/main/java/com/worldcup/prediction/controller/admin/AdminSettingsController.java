package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.security.SuperAdminAuthenticationProvider;
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

    @GetMapping
    public String settings(Model model) {
        return "admin/settings";
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
