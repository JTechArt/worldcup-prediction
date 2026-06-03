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

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock MimeMessage mimeMessage;

    EmailService emailService;
    User testUser;
    Match testMatch;

    @BeforeEach
    void setUp() {
        lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService = new EmailService(mailSender, "noreply@worldcup.example.com", true);

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
        EmailService disabledService = new EmailService(mailSender, "noreply@worldcup.example.com", false);
        disabledService.sendApprovalEmail(testUser);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
