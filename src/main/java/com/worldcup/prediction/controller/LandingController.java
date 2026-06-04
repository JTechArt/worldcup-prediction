package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.security.CustomOAuth2User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingController {

    @GetMapping("/")
    public String landing(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomOAuth2User customUser) {
            if (customUser.getRole() == UserRole.SUPER_ADMIN) {
                return "redirect:/admin";
            }
            return "redirect:/communities";
        }
        return "index";
    }
}
