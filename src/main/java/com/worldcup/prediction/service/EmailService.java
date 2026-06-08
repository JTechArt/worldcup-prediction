package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Email service. When MAIL_ENABLED=false (default), all methods log intent only.
 * When enabled with an API key, uses Resend's HTTP API (port 443 — never blocked).
 * Falls back to SMTP when no API key is set (legacy path, used by unit tests via mock).
 */
@Service
@Slf4j
public class EmailService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEE, MMM d 'at' HH:mm 'UTC'");

    private final JavaMailSender mailSender;
    private final RestClient resendClient;
    private final String fromAddress;
    private final boolean enabled;
    private final FreemarkerEmailRenderer renderer;

    @Value("${app.base-url:http://localhost:8888}")
    private String appUrl;

    @org.springframework.beans.factory.annotation.Autowired
    public EmailService(@Value("${app.mail.enabled:false}") boolean mailEnabled,
                        Optional<JavaMailSender> mailSenderOpt,
                        @Value("${app.mail.from:noreply@worldcup.example.com}") String fromAddress,
                        @Value("${spring.mail.password:}") String apiKey,
                        Optional<FreemarkerEmailRenderer> rendererOpt) {
        this.fromAddress = fromAddress;
        this.renderer = rendererOpt.orElse(null);
        if (!mailEnabled) {
            this.mailSender   = null;
            this.resendClient = null;
            this.enabled      = false;
            log.info("EmailService: MAIL_ENABLED=false — running in log-only mode");
        } else if (!apiKey.isBlank()) {
            // Prefer HTTP API — bypasses SMTP port restrictions entirely
            this.mailSender   = null;
            this.resendClient = RestClient.builder()
                    .baseUrl("https://api.resend.com")
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .build();
            this.enabled = true;
            log.info("EmailService: mail sending enabled via Resend HTTP API (from={})", fromAddress);
        } else {
            // Legacy SMTP path (no API key configured)
            this.resendClient = null;
            this.mailSender   = mailSenderOpt.orElse(null);
            this.enabled      = this.mailSender != null;
            if (this.enabled) {
                log.info("EmailService: mail sending enabled via SMTP (from={})", fromAddress);
            } else {
                log.warn("EmailService: MAIL_ENABLED=true but neither RESEND API key nor JavaMailSender is configured");
            }
        }
    }

    /** Package-private constructor for unit tests (inject mock JavaMailSender directly). */
    EmailService(JavaMailSender mailSender, String fromAddress, boolean enabled,
                 FreemarkerEmailRenderer renderer, String appUrl) {
        this.mailSender   = mailSender;
        this.resendClient = null;
        this.fromAddress  = fromAddress;
        this.enabled      = enabled;
        this.renderer     = renderer;
        this.appUrl       = appUrl;
    }

    public void sendApprovalEmail(User user) {
        String subject = "Welcome to World Cup 2026 Predictions — You're approved!";
        String body = renderOrFallback("approval.ftlh", Map.of(
                "title", "Welcome",
                "firstName", user.getFirstName(),
                "appUrl", appUrl
        ), subject);
        send(user.getEmail(), subject, body);
    }

    public void sendRejectionEmail(User user) {
        String subject = "World Cup 2026 Predictions — Registration update";
        Map<String, Object> model = new java.util.HashMap<>();
        model.put("title", "Registration Update");
        model.put("firstName", user.getFirstName());
        model.put("appUrl", appUrl);
        String body = renderOrFallback("rejection.ftlh", model, subject);
        send(user.getEmail(), subject, body);
    }

    public void sendPredictionReminder(User user, String matchInfo) {
        String subject = "⚽ Predictions close soon — " + matchInfo;
        String body = """
                <html><body style="font-family:Inter,sans-serif;background:#f0fdf5;padding:32px;">
                  <h2 style="color:#FF5722;">⏰ Time is running out!</h2>
                  <p>Predictions for <strong>%s</strong> close soon.</p>
                  <p>Log in now to submit your predictions before the window closes.</p>
                  <p style="margin-top:32px;font-size:12px;color:#666;">World Cup 2026 Prediction Game</p>
                </body></html>
                """.formatted(matchInfo);
        send(user.getEmail(), subject, body);
    }

    public void sendPredictionReminder(List<User> users, Match match) {
        String matchLabel = matchLabel(match);
        String subject = "⚽ Predictions close in 2 hours — " + matchLabel;
        String kickoffStr = match.getKickoffTime() != null
                ? match.getKickoffTime().format(DATE_FMT) : "";
        List<Map<String, String>> matches = List.of(
                Map.of("label", matchLabel, "kickoff", kickoffStr)
        );
        users.forEach(user -> {
            String body = renderOrFallback("prediction-reminder.ftlh", Map.of(
                    "title", "Reminder",
                    "firstName", user.getFirstName(),
                    "matches", matches,
                    "hoursLeft", "2",
                    "appUrl", appUrl
            ), subject);
            send(user.getEmail(), subject, body);
        });
    }

    public void sendResultsPublished(List<User> users, Match match) {
        String matchLabel = matchLabel(match);
        String score = (match.getHomeScore() != null && match.getAwayScore() != null)
                ? match.getHomeScore() + " - " + match.getAwayScore() : "N/A";
        String subject = "📊 Results published — " + matchLabel;
        users.forEach(user -> {
            String body = renderOrFallback("results-published.ftlh", Map.of(
                    "title", "Results Published",
                    "firstName", user.getFirstName(),
                    "matchLabel", matchLabel,
                    "score", score,
                    "appUrl", appUrl
            ), subject);
            send(user.getEmail(), subject, body);
        });
    }

    public void sendPredictionWindowOpen(List<User> users, List<Match> matchList) {
        List<Map<String, String>> matches = matchList.stream()
                .map(m -> {
                    String kickoffStr = m.getKickoffTime() != null
                            ? m.getKickoffTime().format(DATE_FMT) : "";
                    return Map.of("label", matchLabel(m), "kickoff", kickoffStr);
                })
                .collect(Collectors.toList());
        String matchLabels = matchList.stream()
                .map(this::matchLabel)
                .collect(Collectors.joining(", "));
        String subject = "Predictions are open! " + matchLabels;
        users.forEach(user -> {
            String body = renderOrFallback("prediction-window-open.ftlh", Map.of(
                    "title", "Predictions Open",
                    "firstName", user.getFirstName(),
                    "matches", matches,
                    "appUrl", appUrl
            ), subject);
            send(user.getEmail(), subject, body);
        });
    }

    public void sendLeaderboardDigest(User user, int rank, int points,
                                      List<Map<String, Object>> topEntries,
                                      List<Map<String, Object>> matchResults) {
        String subject = "Leaderboard Update — You're #" + rank;
        Map<String, Object> model = new HashMap<>();
        model.put("title", "Leaderboard Update");
        model.put("firstName", user.getFirstName());
        model.put("rank", rank);
        model.put("points", points);
        model.put("topEntries", topEntries);
        model.put("matchResults", matchResults);
        model.put("appUrl", appUrl);
        String body = renderOrFallback("leaderboard-digest.ftlh", model, subject);
        send(user.getEmail(), subject, body);
    }

    public void sendPredictionConfirmation(User user, String roundLabel,
                                            List<Map<String, String>> predictions,
                                            LocalDateTime submittedAt) {
        String subject = "Your predictions for " + roundLabel + " have been submitted";
        Map<String, Object> model = new HashMap<>();
        model.put("title", "Prediction Confirmation");
        model.put("firstName", user.getFirstName());
        model.put("roundLabel", roundLabel);
        model.put("submittedAt", submittedAt.format(DATE_FMT));
        model.put("predictions", predictions);
        model.put("appUrl", appUrl);
        String body = renderOrFallback("prediction-confirmation.ftlh", model, subject);
        send(user.getEmail(), subject, body);
    }

    public void sendTestPredictionConfirmation(String to, String firstName, String roundLabel) {
        String subject = "Your predictions for " + roundLabel + " have been submitted";
        List<Map<String, String>> predictions = List.of(
                Map.of("homeTeam", "Mexico", "awayTeam", "Canada",
                        "predictedHome", "2", "predictedAway", "1",
                        "kickoff", "Wed, Jun 11 at 19:00 UTC"),
                Map.of("homeTeam", "USA", "awayTeam", "Brazil",
                        "predictedHome", "1", "predictedAway", "3",
                        "kickoff", "Thu, Jun 12 at 21:00 UTC"),
                Map.of("homeTeam", "France", "awayTeam", "Germany",
                        "predictedHome", "0", "predictedAway", "0",
                        "kickoff", "Fri, Jun 13 at 18:00 UTC")
        );
        Map<String, Object> model = new HashMap<>();
        model.put("title", "Prediction Confirmation");
        model.put("firstName", firstName);
        model.put("roundLabel", roundLabel);
        model.put("submittedAt", LocalDateTime.now().format(DATE_FMT));
        model.put("predictions", predictions);
        model.put("appUrl", appUrl);
        String body = renderOrFallback("prediction-confirmation.ftlh", model, subject);
        send(to, subject, body);
    }

    public void sendInvitation(String email, User inviter) {
        String subject = "You're invited to World Cup 2026 Predictions!";
        String body = renderOrFallback("invitation.ftlh", Map.of(
                "title", "You're Invited",
                "inviterName", inviter.getFullName(),
                "appUrl", appUrl
        ), subject);
        send(email, subject, body);
    }

    // ── Test-only helpers (bypass entity requirements, no deduplication) ──────────

    public void sendTestInvitation(String to, String inviterName) {
        String subject = "You're invited to World Cup 2026 Predictions!";
        String body = renderOrFallback("invitation.ftlh", Map.of(
                "title", "You're Invited",
                "inviterName", inviterName,
                "appUrl", appUrl
        ), subject);
        send(to, subject, body);
    }

    public void sendTestWindowOpen(String to, String firstName, String matchLabel, String kickoff) {
        String subject = "Predictions are open! " + matchLabel;
        String body = renderOrFallback("prediction-window-open.ftlh", Map.of(
                "title", "Predictions Open",
                "firstName", firstName,
                "matches", List.of(Map.of("label", matchLabel, "kickoff", kickoff)),
                "appUrl", appUrl
        ), subject);
        send(to, subject, body);
    }

    public void sendTestReminder(String to, String firstName, String matchLabel, String kickoff, String hoursLeft) {
        String subject = "⚽ Predictions close in " + hoursLeft + "h — " + matchLabel;
        String body = renderOrFallback("prediction-reminder.ftlh", Map.of(
                "title", "Reminder",
                "firstName", firstName,
                "matches", List.of(Map.of("label", matchLabel, "kickoff", kickoff)),
                "hoursLeft", hoursLeft,
                "appUrl", appUrl
        ), subject);
        send(to, subject, body);
    }

    public void sendTestResults(String to, String firstName, String matchLabel, String score) {
        String subject = "📊 Results published — " + matchLabel;
        String body = renderOrFallback("results-published.ftlh", Map.of(
                "title", "Results Published",
                "firstName", firstName,
                "matchLabel", matchLabel,
                "score", score,
                "appUrl", appUrl
        ), subject);
        send(to, subject, body);
    }

    private String renderOrFallback(String templateName, Map<String, Object> model, String subject) {
        if (renderer != null) {
            return renderer.render(templateName, model);
        }
        return subject;
    }

    private void send(String to, String subject, String htmlBody) {
        if (!enabled) {
            log.info("[EMAIL LOG-ONLY] To: {} | Subject: {}", to, subject);
            return;
        }
        if (resendClient != null) {
            sendViaResendApi(to, subject, htmlBody);
        } else {
            sendViaSmtp(to, subject, htmlBody);
        }
    }

    private void sendViaResendApi(String to, String subject, String htmlBody) {
        try {
            Map<String, Object> payload = Map.of(
                    "from", fromAddress,
                    "to", List.of(to),
                    "subject", subject,
                    "html", htmlBody
            );
            resendClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Email sent to {} via Resend API — {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {} via Resend API: {}", to, e.getMessage(), e);
        }
    }

    private void sendViaSmtp(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.debug("Email sent to {} via SMTP — {}", to, subject);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }

    private String matchLabel(Match match) {
        String home = match.getHomeTeam() != null ? match.getHomeTeam().getName() : "TBD";
        String away = match.getAwayTeam() != null ? match.getAwayTeam().getName() : "TBD";
        return home + " vs " + away;
    }
}
