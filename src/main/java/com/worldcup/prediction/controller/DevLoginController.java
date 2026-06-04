package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.security.CustomOAuth2User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/**
 * Dev-only login bypass — available ONLY in the 'sqlite' (local dev) profile.
 * Creates/reuses a local admin user and programmatically authenticates without OAuth2.
 *
 * NOT AVAILABLE IN PRODUCTION (postgres profile).
 */
@Controller
@RequestMapping("/dev")
@Profile("sqlite")
@Slf4j
@RequiredArgsConstructor
public class DevLoginController {

    private static final String DEV_ADMIN_EMAIL = "devadmin@localhost";

    private final UserRepository userRepository;

    @GetMapping("/login")
    public String devLoginPage() {
        return "dev-login";
    }

    @PostMapping("/login")
    public String devLogin(HttpServletRequest request,
                           HttpServletResponse response,
                           HttpSession session) {

        User admin = userRepository.findByEmail(DEV_ADMIN_EMAIL).orElseGet(() -> {
            log.info("[DEV] Creating local dev admin user: {}", DEV_ADMIN_EMAIL);
            return userRepository.save(User.builder()
                    .email(DEV_ADMIN_EMAIL)
                    .firstName("Dev")
                    .lastName("Admin")
                    .status(UserStatus.ACTIVE)
                    .role(UserRole.SUPER_ADMIN)
                    .build());
        });

        if (admin.getStatus() != UserStatus.ACTIVE) {
            admin.setStatus(UserStatus.ACTIVE);
            admin.setRole(UserRole.SUPER_ADMIN);
            admin = userRepository.save(admin);
        }

        CustomOAuth2User principal = new CustomOAuth2User(admin, Map.of("sub", "dev-admin"));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(auth);
        new HttpSessionSecurityContextRepository()
                .saveContext(SecurityContextHolder.getContext(), request, response);

        log.info("[DEV] Logged in as dev admin: {}", DEV_ADMIN_EMAIL);
        return "redirect:/admin";
    }
}
