package com.worldcup.prediction.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "disabled", required = false) String disabled,
            Model model
    ) {
        if (error != null) {
            model.addAttribute("error", "Authentication failed. Please try again.");
        } else if (disabled != null) {
            model.addAttribute("error", "Your account has been disabled. Please contact an administrator.");
        }
        return "login";
    }
}
