package com.worldcup.prediction.controller;

import com.worldcup.prediction.security.CustomOAuth2User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/home")
    public String homePage(
            @AuthenticationPrincipal CustomOAuth2User currentUser,
            Model model
    ) {
        if (currentUser != null) {
            model.addAttribute("displayName", currentUser.getDisplayName());
        }
        return "home";
    }
}
