package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.MatchStage;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock MimeMessage mimeMessage;

    EmailService emailService;
    FreemarkerEmailRenderer renderer;
    User testUser;
    Match testMatch;

    @BeforeEach
    void setUp() {
        lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        freemarker.template.Configuration cfg =
                new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_33);
        cfg.setClassForTemplateLoading(EmailServiceTest.class, "/templates/email");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLogTemplateExceptions(false);
        renderer = new FreemarkerEmailRenderer(cfg);

        emailService = new EmailService(mailSender, "noreply@worldcup.example.com", true,
                renderer, "http://localhost:8888");

        testUser = new User();
        testUser.setEmail("player@example.com");
        testUser.setFirstName("Alice");
        testUser.setLastName("Smith");

        Team home = Team.builder().name("Mexico").fifaCode("MEX").flagCode("mx").build();
        Team away = Team.builder().name("Canada").fifaCode("CAN").flagCode("ca").build();

        testMatch = Match.builder()
                .homeTeam(home)
                .awayTeam(away)
                .kickoffTime(LocalDateTime.of(2026, 6, 11, 19, 0))
                .stage(MatchStage.GROUP)
                .matchNumber(1)
                .roundLabel("Group Stage MD1")
                .build();
    }

    @Test
    void sendApprovalEmail_invokesMailSender() {
        emailService.sendApprovalEmail(testUser);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendRejectionEmail_invokesMailSender() {
        emailService.sendRejectionEmail(testUser);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendPredictionReminderList_invokesMailSenderForEachUser() {
        User user2 = new User();
        user2.setEmail("bob@example.com");
        user2.setFirstName("Bob");
        user2.setLastName("Jones");

        emailService.sendPredictionReminder(List.of(testUser, user2), testMatch);

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void sendResultsPublished_invokesMailSenderForEachUser() {
        emailService.sendResultsPublished(List.of(testUser), testMatch);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void whenMailDisabled_noMailSent() {
        // Pass null for renderer since this is log-only mode
        EmailService disabledService = new EmailService(null, "noreply@worldcup.example.com",
                false, null, "http://localhost:8888");
        disabledService.sendApprovalEmail(testUser);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendPredictionWindowOpen_invokesMailSenderForEachUser() {
        User user2 = new User();
        user2.setEmail("bob@example.com");
        user2.setFirstName("Bob");
        user2.setLastName("Jones");

        emailService.sendPredictionWindowOpen(List.of(testUser, user2), List.of(testMatch));

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void sendLeaderboardDigest_invokesMailSender() {
        List<Map<String, Object>> topEntries = List.of(
                Map.of("rank", 1, "name", "Alice", "points", 25),
                Map.of("rank", 2, "name", "Bob", "points", 20)
        );
        List<Map<String, Object>> matchResults = List.of(
                Map.of("label", "Mexico vs Canada", "score", "2 - 1")
        );

        emailService.sendLeaderboardDigest(testUser, 1, 25, topEntries, matchResults);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendInvitation_invokesMailSender() {
        User inviter = new User();
        inviter.setFirstName("Admin");
        inviter.setLastName("Jane");

        emailService.sendInvitation("newplayer@example.com", inviter);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendPredictionConfirmation_invokesMailSender() {
        List<Map<String, String>> predictions = List.of(
                Map.of("homeTeam", "Mexico", "awayTeam", "Canada",
                        "predictedHome", "2", "predictedAway", "1",
                        "kickoff", "Wed, Jun 11 at 19:00 UTC"),
                Map.of("homeTeam", "USA", "awayTeam", "Brazil",
                        "predictedHome", "1", "predictedAway", "3",
                        "kickoff", "Wed, Jun 11 at 21:00 UTC")
        );

        emailService.sendPredictionConfirmation(testUser, "Group Stage MD1", predictions,
                LocalDateTime.of(2026, 6, 10, 14, 30));

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }
}
