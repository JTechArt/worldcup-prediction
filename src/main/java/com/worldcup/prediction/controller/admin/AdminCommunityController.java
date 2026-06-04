package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/communities")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminCommunityController {

    private final CommunityService communityService;

    @GetMapping
    public String list(Model model) {
        List<Community> communities = communityService.findAll();
        model.addAttribute("communities", communities);
        return "admin/communities";
    }

    @PostMapping("/create")
    public String create(@RequestParam String name,
                         @RequestParam String slug,
                         @RequestParam(required = false) String description,
                         @AuthenticationPrincipal CustomOAuth2User principal,
                         RedirectAttributes redirectAttributes) {
        try {
            Community community = communityService.createCommunity(name, slug, description, principal.getUserId());
            // Auto-add creator as community admin
            communityService.addMember(community.getId(), principal.getUserId(), CommunityRole.ADMIN, MembershipStatus.ACTIVE);
            redirectAttributes.addFlashAttribute("successMessage", "Community '" + name + "' created.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/communities";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            communityService.deleteCommunity(id);
            redirectAttributes.addFlashAttribute("successMessage", "Community deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete: " + e.getMessage());
        }
        return "redirect:/admin/communities";
    }
}
