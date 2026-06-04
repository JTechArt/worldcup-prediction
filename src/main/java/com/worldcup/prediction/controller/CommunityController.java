package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/communities")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @GetMapping
    public String myCommunities(@AuthenticationPrincipal CustomOAuth2User principal, Model model) {
        List<CommunityMembership> active = communityService.getActiveMembershipsForUser(principal.getUserId());
        model.addAttribute("memberships", active);
        model.addAttribute("pageTitle", "My Communities");
        return "communities";
    }
}
