package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.CommunityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/seed-data")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminSeedDataController {

    private final UserRepository userRepository;
    private final CommunityService communityService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public String show(Model model) {
        List<Community> allCommunities = communityService.findAll();
        model.addAttribute("allCommunities", allCommunities);
        return "admin/seed-data";
    }

    @PostMapping("/generate")
    public String generate(RedirectAttributes redirectAttributes) {
        String hash = passwordEncoder.encode("user");
        int generated = 0;

        for (int i = 1; i <= 100; i++) {
            String email = "user" + i + "@fifaworldcup2026prediction.win";
            if (userRepository.existsByEmailIgnoreCase(email)) {
                continue;
            }
            User user = User.builder()
                    .firstName("User")
                    .lastName(String.valueOf(i))
                    .email(email)
                    .passwordHash(hash)
                    .status(UserStatus.ACTIVE)
                    .role(UserRole.USER)
                    .build();
            userRepository.save(user);
            generated++;
        }

        log.info("Seed data: generated {} test users", generated);
        redirectAttributes.addFlashAttribute("successMessage",
                "Generated " + generated + " users and assigned to communities.");
        return "redirect:/admin/seed-data";
    }
}
