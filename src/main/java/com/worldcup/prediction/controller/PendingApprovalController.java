package com.worldcup.prediction.controller;

import com.worldcup.prediction.security.CustomOAuth2User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PendingApprovalController {

    @GetMapping("/pending")
    public String pendingPage(
            @AuthenticationPrincipal CustomOAuth2User currentUser,
            Model model
    ) {
        if (currentUser != null) {
            model.addAttribute("firstName", currentUser.getUser().getFirstName());
            model.addAttribute("email", currentUser.getEmail());
        }
        return "pending";
    }
}
