package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Community;
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
    public boolean sendPredictionWindowOpen(List<User> users, Match match, Long communityId) {
        List<User> toNotify = users.stream()
                .filter(u -> {
                    String refKey = "PREDICTION_WINDOW_OPEN:community:" + communityId + ":match:" + match.getId() + ":user:" + u.getId();
                    return !notificationLogRepository.existsByReferenceKey(refKey);
                })
                .toList();
        if (toNotify.isEmpty()) {
            log.debug("Window-open notification already sent for match {} in community {}", match.getId(), communityId);
            return false;
        }
        emailService.sendPredictionWindowOpen(toNotify, List.of(match));
        for (User user : toNotify) {
            String refKey = "PREDICTION_WINDOW_OPEN:community:" + communityId + ":match:" + match.getId() + ":user:" + user.getId();
            logNotification(NotificationType.PREDICTION_WINDOW_OPEN, user, user.getEmail(), match, refKey, null);
        }
        return true;
    }

    @Transactional
    public int sendPredictionReminders(List<User> users, Match match, Long communityId) {
        int sent = 0;
        for (User user : users) {
            String refKey = "PREDICTION_REMINDER:community:" + communityId + ":user:" + user.getId() + ":match:" + match.getId();
            if (notificationLogRepository.existsByReferenceKey(refKey)) {
                continue;
            }
            emailService.sendPredictionReminder(List.of(user), match);
            logNotification(NotificationType.PREDICTION_REMINDER, user, user.getEmail(), match, refKey, null);
            sent++;
        }
        return sent;
    }

    @Transactional
    public boolean sendLeaderboardDigest(String dateKey, List<User> topUsers,
                                         List<Map<String, Object>> topEntries,
                                         List<Map<String, Object>> matchResults,
                                         Long communityId) {
        String refKey = "LEADERBOARD_DIGEST:community:" + communityId + ":date:" + dateKey;
        if (notificationLogRepository.existsByReferenceKey(refKey)) {
            log.debug("Leaderboard digest already sent for {} in community {}", dateKey, communityId);
            return false;
        }
        for (int i = 0; i < topUsers.size(); i++) {
            User user = topUsers.get(i);
            Map<String, Object> entry = topEntries.get(i);
            int rank = (int) entry.get("rank");
            int points = (int) entry.get("points");
            emailService.sendLeaderboardDigest(user, rank, points, topEntries, matchResults);
        }
        logNotification(NotificationType.LEADERBOARD_DIGEST, null, "top10", null, refKey, null);
        return true;
    }

    @Transactional
    public void sendInvitation(String email, User inviter, Long communityId) {
        String refKey = "INVITATION:community:" + communityId + ":email:" + email.toLowerCase();
        if (notificationLogRepository.existsByReferenceKey(refKey)) {
            log.debug("Invitation already sent to {} for community {}", email, communityId);
            return;
        }
        emailService.sendInvitation(email, inviter);
        logNotification(NotificationType.INVITATION, null, email, null, refKey, null);
    }

    private void logNotification(NotificationType type, User recipient, String email,
                                  Match match, String refKey, Community community) {
        NotificationLog entry = NotificationLog.builder()
                .type(type)
                .recipient(recipient)
                .recipientEmail(email)
                .match(match)
                .community(community)
                .sentAt(LocalDateTime.now())
                .referenceKey(refKey)
                .build();
        notificationLogRepository.save(entry);
    }
}
