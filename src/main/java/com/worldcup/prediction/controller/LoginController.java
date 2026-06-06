package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.security.CustomOAuth2User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    private final AuthenticationManager authenticationManager;

    public LoginController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "disabled", required = false) String disabled,
            @RequestParam(value = "emailError", required = false) String emailError,
            Model model) {
        if (error != null) {
            model.addAttribute("error", "Authentication failed. Please try again.");
        } else if (disabled != null) {
            model.addAttribute("error", "Your account has been disabled. Please contact an administrator.");
        }
        if (emailError != null) {
            model.addAttribute("emailError", "Invalid email or password. Please try again.");
        }
        return "login";
    }

    @PostMapping("/login/email")
    public String loginWithEmail(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            HttpServletRequest request) {
        try {
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(email.toLowerCase().trim(), password);
            Authentication auth = authenticationManager.authenticate(token);
            if (auth == null || !auth.isAuthenticated()) {
                return "redirect:/login?emailError";
            }

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            CustomOAuth2User principal = (CustomOAuth2User) auth.getPrincipal();
            UserStatus status = principal.getStatus();

            if (status == UserStatus.PENDING) {
                return "redirect:/pending";
            } else if (status == UserStatus.DISABLED) {
                return "redirect:/login?disabled";
            } else {
                return "redirect:/communities";
            }
        } catch (AuthenticationException ex) {
            return "redirect:/login?emailError";
        }
    }
}
