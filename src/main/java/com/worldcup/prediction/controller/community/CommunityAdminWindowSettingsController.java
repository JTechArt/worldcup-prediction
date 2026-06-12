package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.enums.WindowMode;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.service.PredictionWindowService;
import com.worldcup.prediction.service.TournamentSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/c/{slug}/admin/window-settings")
@RequiredArgsConstructor
public class CommunityAdminWindowSettingsController {

    private final CommunityRepository communityRepository;
    private final TournamentSettingsService tournamentSettingsService;
    private final PredictionWindowService predictionWindowService;

    @GetMapping
    public String settingsPage(@PathVariable String slug, HttpServletRequest request, Model model) {
        Community community = (Community) request.getAttribute("community");
        model.addAttribute("community", community);
        model.addAttribute("globalSettings", tournamentSettingsService.getSettings());
        model.addAttribute("effectiveMode", tournamentSettingsService.getEffectiveMode(community.getId()));
        model.addAttribute("globalWindows", predictionWindowService.findAllGlobal());
        model.addAttribute("windowModes", WindowMode.values());
        return "community/admin/window-settings";
    }

    @PostMapping("/mode")
    public String updateMode(@PathVariable String slug,
                             HttpServletRequest request,
                             @RequestParam(required = false) WindowMode windowModeOverride,
                             RedirectAttributes ra) {
        Community community = (Community) request.getAttribute("community");
        community.setWindowModeOverride(windowModeOverride);
        communityRepository.save(community);
        ra.addFlashAttribute("successMessage", "Window mode updated.");
        return "redirect:/c/" + slug + "/admin/window-settings";
    }

    @PostMapping("/windows/{windowId}/force-open")
    public String forceOpen(@PathVariable String slug, @PathVariable Long windowId, RedirectAttributes ra) {
        predictionWindowService.forceOpen(windowId);
        ra.addFlashAttribute("successMessage", "Window forced OPEN.");
        return "redirect:/c/" + slug + "/admin/window-settings";
    }

    @PostMapping("/windows/{windowId}/force-close")
    public String forceClose(@PathVariable String slug, @PathVariable Long windowId, RedirectAttributes ra) {
        predictionWindowService.forceClose(windowId);
        ra.addFlashAttribute("successMessage", "Window forced CLOSED.");
        return "redirect:/c/" + slug + "/admin/window-settings";
    }

    @PostMapping("/windows/{windowId}/reset-override")
    public String resetOverride(@PathVariable String slug, @PathVariable Long windowId, RedirectAttributes ra) {
        predictionWindowService.resetOverride(windowId);
        ra.addFlashAttribute("successMessage", "Override cleared.");
        return "redirect:/c/" + slug + "/admin/window-settings";
    }
}
