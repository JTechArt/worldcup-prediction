package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.NotificationLog;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.enums.NotificationType;
import com.worldcup.prediction.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final EmailService emailService;
    private final NotificationLogRepository notificationLogRepository;

    @Transactional
    public boolean sendPredictionWindowOpen(List<User> users, Match match) {
        String refKey = "PREDICTION_WINDOW_OPEN:match:" + match.getId();
        if (notificationLogRepository.existsByReferenceKey(refKey)) {
            log.debug("Window-open notification already sent for match {}", match.getId());
            return false;
        }
        emailService.sendPredictionWindowOpen(users, List.of(match));
        for (User user : users) {
            logNotification(NotificationType.PREDICTION_WINDOW_OPEN, user, user.getEmail(), match, refKey);
        }
        return true;
    }

    @Transactional
    public int sendPredictionReminders(List<User> users, Match match) {
        int sent = 0;
        for (User user : users) {
            String refKey = "PREDICTION_REMINDER:user:" + user.getId() + ":match:" + match.getId();
            if (notificationLogRepository.existsByReferenceKey(refKey)) {
                continue;
            }
            emailService.sendPredictionReminder(List.of(user), match);
            logNotification(NotificationType.PREDICTION_REMINDER, user, user.getEmail(), match, refKey);
            sent++;
        }
        return sent;
    }

    @Transactional
    public boolean sendLeaderboardDigest(String dateKey, List<User> topUsers,
                                         List<Map<String, Object>> topEntries,
                                         List<Map<String, Object>> matchResults) {
        String refKey = "LEADERBOARD_DIGEST:date:" + dateKey;
        if (notificationLogRepository.existsByReferenceKey(refKey)) {
            log.debug("Leaderboard digest already sent for {}", dateKey);
            return false;
        }
        for (int i = 0; i < topUsers.size(); i++) {
            User user = topUsers.get(i);
            Map<String, Object> entry = topEntries.get(i);
            int rank = (int) entry.get("rank");
            int points = (int) entry.get("points");
            emailService.sendLeaderboardDigest(user, rank, points, topEntries, matchResults);
        }
        logNotification(NotificationType.LEADERBOARD_DIGEST, null, "top10", null, refKey);
        return true;
    }

    @Transactional
    public void sendInvitation(String email, User inviter) {
        String refKey = "INVITATION:email:" + email.toLowerCase();
        if (notificationLogRepository.existsByReferenceKey(refKey)) {
            log.debug("Invitation already sent to {}", email);
            return;
        }
        emailService.sendInvitation(email, inviter);
        logNotification(NotificationType.INVITATION, null, email, null, refKey);
    }

    private void logNotification(NotificationType type, User recipient, String email,
                                  Match match, String refKey) {
        NotificationLog entry = NotificationLog.builder()
                .type(type)
                .recipient(recipient)
                .recipientEmail(email)
                .match(match)
                .sentAt(LocalDateTime.now())
                .referenceKey(refKey)
                .build();
        notificationLogRepository.save(entry);
    }
}
