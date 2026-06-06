package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/communities")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @GetMapping
    public String communities(@AuthenticationPrincipal CustomOAuth2User principal,
                              @RequestParam(required = false) String joinSlug,
                              Model model) {
        Long userId = principal.getUserId();

        List<Community> allCommunities = communityService.findAll();
        List<CommunityMembership> userMemberships = communityService.getAllMembershipsForUser(userId);

        // communityId -> status map for template lookups
        Map<Long, MembershipStatus> statusMap = userMemberships.stream()
                .collect(Collectors.toMap(m -> m.getCommunity().getId(), CommunityMembership::getStatus));

        // active memberships for "My Communities" section
        List<CommunityMembership> activeMemberships = userMemberships.stream()
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .toList();

        model.addAttribute("allCommunities", allCommunities);
        model.addAttribute("statusMap", statusMap);
        model.addAttribute("activeMemberships", activeMemberships);
        model.addAttribute("joinSlug", joinSlug);
        return "communities";
    }

    @PostMapping("/{communityId}/join")
    public String requestJoin(@PathVariable Long communityId,
                              @AuthenticationPrincipal CustomOAuth2User principal,
                              RedirectAttributes redirectAttributes) {
        CommunityMembership membership = communityService.requestJoin(communityId, principal.getUserId());
        switch (membership.getStatus()) {
            case ACTIVE   -> redirectAttributes.addFlashAttribute("successMessage", "You are already a member of this community.");
            case PENDING  -> redirectAttributes.addFlashAttribute("successMessage", "Join request submitted — waiting for approval from the community admin.");
            default       -> redirectAttributes.addFlashAttribute("errorMessage", "Your previous request was rejected. Contact the community admin.");
        }
        return "redirect:/communities";
    }
}
