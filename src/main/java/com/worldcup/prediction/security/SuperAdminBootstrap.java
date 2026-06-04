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
        if (userRepository.findByRole(UserRole.SUPER_ADMIN).isEmpty()) {
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
        } else {
            log.debug("Super Admin already exists, skipping bootstrap");
        }
    }
}
