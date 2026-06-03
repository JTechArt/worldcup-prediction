package com.worldcup.prediction.integration;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.security.CustomOAuth2User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OAuth2LoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void loginPage_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void homeRoute_redirectsToLogin_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void pendingRoute_redirectsWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/pending"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void leaderboardRoute_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/leaderboard"))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUser_withActiveStatus_canAccessHome() throws Exception {
        User activeUser = userRepository.save(User.builder()
                .email("active@test.com").firstName("Active").lastName("User")
                .status(UserStatus.ACTIVE).role(UserRole.PARTICIPANT).build());

        CustomOAuth2User principal = new CustomOAuth2User(activeUser, Map.of("sub", "id-active"));

        mockMvc.perform(get("/home").with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUser_withPendingStatus_canAccessPendingPage() throws Exception {
        User pendingUser = userRepository.save(User.builder()
                .email("pending@test.com").firstName("Pending").lastName("User")
                .status(UserStatus.PENDING).role(UserRole.PARTICIPANT).build());

        CustomOAuth2User principal = new CustomOAuth2User(pendingUser, Map.of("sub", "id-pending"));

        mockMvc.perform(get("/pending").with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("pending"));
    }

    @Test
    void adminRoute_isForbidden_forParticipant() throws Exception {
        User participant = userRepository.save(User.builder()
                .email("part@test.com").firstName("Part").lastName("Icipant")
                .status(UserStatus.ACTIVE).role(UserRole.PARTICIPANT).build());

        CustomOAuth2User principal = new CustomOAuth2User(participant, Map.of("sub", "id-part"));

        mockMvc.perform(get("/admin").with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoute_isNotForbidden_forAdmin() throws Exception {
        User admin = userRepository.save(User.builder()
                .email("admin@test.com").firstName("Admin").lastName("User")
                .status(UserStatus.ACTIVE).role(UserRole.ADMIN).build());

        CustomOAuth2User principal = new CustomOAuth2User(admin, Map.of("sub", "id-admin"));

        // Admin gets past security (not 403) — may be 200, 302, or 404 depending on Part 7 completion
        mockMvc.perform(get("/admin").with(oauth2Login().oauth2User(principal)))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }
}
