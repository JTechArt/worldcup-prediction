package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final CommunityMembershipRepository communityMembershipRepository;

    @GetMapping
    public String list(Model model) {
        List<Community> communities = communityService.findAll();
        List<User> allUsers = userRepository.findByRoleNotOrderByFirstNameAscLastNameAsc(UserRole.SUPER_ADMIN);
        model.addAttribute("communities", communities);
        model.addAttribute("allUsers", allUsers);
        return "admin/communities";
    }

    @PostMapping("/create")
    public String create(@RequestParam String name,
                         @RequestParam String slug,
                         @RequestParam(required = false) String description,
                         @AuthenticationPrincipal CustomOAuth2User principal,
                         RedirectAttributes redirectAttributes) {
        try {
            communityService.createCommunity(name, slug, description, principal.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Community '" + name + "' created.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/communities";
    }

    @PostMapping("/{communityId}/members/add")
    public String addMember(@PathVariable Long communityId,
                            @RequestParam Long userId,
                            @RequestParam(defaultValue = "MEMBER") CommunityRole role,
                            RedirectAttributes redirectAttributes) {
        try {
            communityService.addOrActivateMember(communityId, userId, role);
            redirectAttributes.addFlashAttribute("successMessage", "User added to community successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to add user: " + e.getMessage());
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
