package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.service.CommunityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/c/{slug}/admin/members")
@RequiredArgsConstructor
public class CommunityAdminMemberController {

    private final CommunityService communityService;

    @GetMapping
    public String members(@PathVariable String slug,
                          HttpServletRequest request,
                          Model model) {
        Community community = (Community) request.getAttribute("community");
        List<CommunityMembership> memberships = communityService.getMembershipsForCommunity(community.getId());
        model.addAttribute("community", community);
        model.addAttribute("slug", slug);
        model.addAttribute("memberships", memberships);
        model.addAttribute("pageTitle", community.getName() + " · Members");
        return "community/admin/members";
    }

    @PostMapping("/{userId}/approve")
    public String approve(@PathVariable String slug,
                          @PathVariable Long userId,
                          HttpServletRequest request,
                          RedirectAttributes redirectAttributes) {
        Community community = (Community) request.getAttribute("community");
        communityService.approveMember(community.getId(), userId);
        redirectAttributes.addFlashAttribute("successMessage", "Member approved.");
        return "redirect:/c/" + slug + "/admin/members";
    }

    @PostMapping("/{userId}/reject")
    public String reject(@PathVariable String slug,
                         @PathVariable Long userId,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        Community community = (Community) request.getAttribute("community");
        communityService.rejectMember(community.getId(), userId);
        redirectAttributes.addFlashAttribute("successMessage", "Member rejected.");
        return "redirect:/c/" + slug + "/admin/members";
    }

    @PostMapping("/{userId}/role")
    public String setRole(@PathVariable String slug,
                          @PathVariable Long userId,
                          @RequestParam CommunityRole role,
                          HttpServletRequest request,
                          RedirectAttributes redirectAttributes) {
        Community community = (Community) request.getAttribute("community");
        communityService.setMemberRole(community.getId(), userId, role);
        redirectAttributes.addFlashAttribute("successMessage", "Role updated.");
        return "redirect:/c/" + slug + "/admin/members";
    }
}
