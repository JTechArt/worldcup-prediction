package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.super-admin.username:admin}")
    private String username;

    @Value("${app.super-admin.password:changeme123}")
    private String password;

    @Value("${app.super-admin.email:admin@worldcup.local}")
    private String email;

    @Override
    public void run(String... args) {
        List<User> admins = userRepository.findByRole(UserRole.SUPER_ADMIN);

        if (admins.isEmpty()) {
            User superAdmin = User.builder()
                    .email(email)
                    .firstName(username)
                    .lastName("Admin")
                    .passwordHash(passwordEncoder.encode(password))
                    .role(UserRole.SUPER_ADMIN)
                    .status(UserStatus.ACTIVE)
                    .build();
            userRepository.save(superAdmin);
            log.info("Super Admin created: email={}", email);
            return;
        }

        if (admins.size() > 1) {
            log.warn("Multiple Super Admin accounts detected ({}). Only the first will be kept.", admins.size());
            admins.stream().skip(1).forEach(userRepository::delete);
        }

        User superAdmin = admins.getFirst();
        superAdmin.setEmail(email);
        superAdmin.setFirstName(username);
        superAdmin.setPasswordHash(passwordEncoder.encode(password));
        superAdmin.setStatus(UserStatus.ACTIVE);
        userRepository.save(superAdmin);
        log.info("Super Admin credentials synced from environment: email={}", email);
    }
}
