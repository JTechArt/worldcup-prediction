package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Email service. When no SMTP host is configured (JavaMailSender not in context),
 * all methods log intent only — no exception is thrown.
 */
@Service
@Slf4j
public class EmailService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEE, MMM d 'at' HH:mm 'UTC'");

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean enabled;

    /**
     * Spring injection.
     * Set MAIL_ENABLED=true in .env (and configure SMTP_* vars) to send real emails.
     * When disabled, all methods log intent only — the app starts without SMTP.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public EmailService(@Value("${app.mail.enabled:false}") boolean mailEnabled,
                        Optional<JavaMailSender> mailSenderOpt,
                        @Value("${app.mail.from:noreply@worldcup.example.com}") String fromAddress) {
        this.fromAddress = fromAddress;
        if (!mailEnabled) {
            this.mailSender = null;
            this.enabled    = false;
            log.info("EmailService: MAIL_ENABLED=false — running in log-only mode");
        } else if (mailSenderOpt.isEmpty()) {
            this.mailSender = null;
            this.enabled    = false;
            log.warn("EmailService: MAIL_ENABLED=true but no JavaMailSender configured (check SPRING_MAIL_HOST)");
        } else {
            this.mailSender = mailSenderOpt.get();
            this.enabled    = true;
            log.info("EmailService: mail sending enabled via {}", fromAddress);
        }
    }

    /** Package-private constructor for unit tests (inject mock directly). */
    EmailService(JavaMailSender mailSender, String fromAddress, boolean enabled) {
        this.mailSender  = mailSender;
        this.fromAddress = fromAddress;
        this.enabled     = enabled;
    }

    public void sendApprovalEmail(User user) {
        String subject = "Welcome to World Cup 2026 Predictions — You're approved!";
        String body = """
                <html><body style="font-family:Inter,sans-serif;background:#f0fdf5;padding:32px;">
                  <h2 style="color:#006b2a;">You're in! 🎉</h2>
                  <p>Hi %s,</p>
                  <p>Your registration for the <strong>World Cup 2026 Prediction Game</strong> has been approved.</p>
                  <p>Sign in with your Google or LinkedIn account to start making predictions.</p>
                  <p style="margin-top:32px;font-size:12px;color:#666;">World Cup 2026 Prediction Game</p>
                </body></html>
                """.formatted(user.getFirstName());
        send(user.getEmail(), subject, body);
    }

    public void sendRejectionEmail(User user) {
        String subject = "World Cup 2026 Predictions — Registration update";
        String body = """
                <html><body style="font-family:Inter,sans-serif;background:#f0fdf5;padding:32px;">
                  <h2 style="color:#006b2a;">Registration Update</h2>
                  <p>Hi %s,</p>
                  <p>Unfortunately your registration was not approved at this time.</p>
                  <p>If you believe this is an error, please contact your administrator.</p>
                  <p style="margin-top:32px;font-size:12px;color:#666;">World Cup 2026 Prediction Game</p>
                </body></html>
                """.formatted(user.getFirstName());
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
                ? match.getKickoffTime().format(DATE_FMT) + " UTC" : "";
        String body = """
                <html><body style="font-family:Inter,sans-serif;background:#f0fdf5;padding:32px;">
                  <h2 style="color:#FF5722;">⏰ Time is running out!</h2>
                  <p>Predictions for <strong>%s</strong> (%s) close in <strong>2 hours</strong>.</p>
                  <p>Log in now to submit your predictions before the window closes.</p>
                  <p style="margin-top:32px;font-size:12px;color:#666;">World Cup 2026 Prediction Game</p>
                </body></html>
                """.formatted(matchLabel, kickoffStr);
        users.forEach(user -> send(user.getEmail(), subject, body));
    }

    public void sendResultsPublished(List<User> users, Match match) {
        String matchLabel = matchLabel(match);
        String subject = "📊 Results published — " + matchLabel;
        String body = """
                <html><body style="font-family:Inter,sans-serif;background:#f0fdf5;padding:32px;">
                  <h2 style="color:#006b2a;">Results are in!</h2>
                  <p>The results for <strong>%s</strong> have been published and the leaderboard updated.</p>
                  <p>Check the leaderboard to see where you stand.</p>
                  <p style="margin-top:32px;font-size:12px;color:#666;">World Cup 2026 Prediction Game</p>
                </body></html>
                """.formatted(matchLabel);
        users.forEach(user -> send(user.getEmail(), subject, body));
    }

    private void send(String to, String subject, String htmlBody) {
        if (!enabled || mailSender == null) {
            log.info("[EMAIL LOG-ONLY] To: {} | Subject: {}", to, subject);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.debug("Email sent to {} — {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }

    private String matchLabel(Match match) {
        String home = match.getHomeTeam() != null ? match.getHomeTeam().getName() : "TBD";
        String away = match.getAwayTeam() != null ? match.getAwayTeam().getName() : "TBD";
        return home + " vs " + away;
    }
}
