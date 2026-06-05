package com.worldcup.prediction.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminLoginController {

    @GetMapping("/admin/login")
    public String adminLoginPage(
            @RequestParam(value = "error", required = false) String error,
            Model model
    ) {
        if (error != null) {
            model.addAttribute("error", "Invalid credentials. Please try again.");
        }
        return "admin/login";
    }
}
