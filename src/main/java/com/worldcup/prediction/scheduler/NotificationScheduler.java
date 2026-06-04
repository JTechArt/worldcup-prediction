package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.LeaderboardService;
import com.worldcup.prediction.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notification.enabled", havingValue = "true")
public class NotificationScheduler {

    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final PredictionRepository predictionRepository;
    private final NotificationService notificationService;
    private final LeaderboardService leaderboardService;

    @Value("${app.notification.reminder-hours-before:3}")
    private int reminderHoursBefore;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM HH:mm");

    @Scheduled(fixedDelay = 300_000)
    public void checkPredictionWindowOpen() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Match> matches = matchRepository.findMatchesWhereWindowShouldOpen(now);
            if (matches.isEmpty()) {
                log.debug("NotificationScheduler: no new prediction windows — skipping");
                return;
            }
            List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
            for (Match match : matches) {
                boolean sent = notificationService.sendPredictionWindowOpen(activeUsers, match);
                if (sent) {
                    log.info("Sent prediction-window-open notification for match {}", match.getId());
                }
            }
        } catch (Exception e) {
            log.error("NotificationScheduler.checkPredictionWindowOpen error", e);
        }
    }

    @Scheduled(fixedDelay = 900_000)
    public void checkPredictionDeadline() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime deadlineFrom = now.plusHours(1);
            LocalDateTime deadlineTo = now.plusHours(reminderHoursBefore);
            List<Match> approachingMatches = matchRepository.findByKickoffTimeBetween(deadlineFrom, deadlineTo);
            approachingMatches = approachingMatches.stream()
                    .filter(m -> m.getStatus() == MatchStatus.SCHEDULED && m.isPredictionWindowOpen())
                    .collect(Collectors.toList());
            if (approachingMatches.isEmpty()) {
                log.debug("NotificationScheduler: no approaching deadlines — skipping");
                return;
            }
            List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
            for (Match match : approachingMatches) {
                List<User> usersWithoutPredictions = activeUsers.stream()
                        .filter(u -> !predictionRepository.existsByUserIdAndMatchId(u.getId(), match.getId()))
                        .collect(Collectors.toList());
                if (usersWithoutPredictions.isEmpty()) continue;
                int sent = notificationService.sendPredictionReminders(usersWithoutPredictions, match);
                if (sent > 0) {
                    log.info("Sent {} prediction reminders for match {}", sent, match.getId());
                }
            }
        } catch (Exception e) {
            log.error("NotificationScheduler.checkPredictionDeadline error", e);
        }
    }

    @Scheduled(fixedDelay = 1_800_000)
    public void checkLeaderboardDigest() {
        try {
            LocalDate today = LocalDate.now();
            LocalDateTime dayStart = today.atStartOfDay();
            LocalDateTime dayEnd = today.atTime(LocalTime.MAX);
            List<Match> todayMatches = matchRepository.findByKickoffTimeBetween(dayStart, dayEnd);
            if (todayMatches.isEmpty()) {
                log.debug("NotificationScheduler: no matches today — skipping digest");
                return;
            }
            boolean allCompleted = todayMatches.stream()
                    .allMatch(m -> m.getStatus() == MatchStatus.COMPLETED);
            if (!allCompleted) {
                log.debug("NotificationScheduler: not all today's matches completed — skipping digest");
                return;
            }
            String dateKey = today.toString();
            List<LeaderboardEntryDto> top10 = leaderboardService.getTopN(10);
            if (top10.isEmpty()) return;

            List<User> topUsers = new ArrayList<>();
            List<Map<String, Object>> topEntries = new ArrayList<>();
            for (LeaderboardEntryDto entry : top10) {
                userRepository.findById(entry.getUserId()).ifPresent(topUsers::add);
                topEntries.add(Map.of(
                        "rank", entry.getRank(),
                        "name", entry.getDisplayName(),
                        "points", entry.getTotalPoints()
                ));
            }

            List<Map<String, Object>> matchResults = todayMatches.stream()
                    .filter(Match::isCompleted)
                    .map(m -> Map.<String, Object>of(
                            "label", matchLabel(m),
                            "score", m.getHomeScore() + " - " + m.getAwayScore()
                    ))
                    .collect(Collectors.toList());

            boolean sent = notificationService.sendLeaderboardDigest(dateKey, topUsers, topEntries, matchResults);
            if (sent) {
                log.info("Sent leaderboard digest for {}", dateKey);
            }
        } catch (Exception e) {
            log.error("NotificationScheduler.checkLeaderboardDigest error", e);
        }
    }

    private String matchLabel(Match match) {
        String home = match.getHomeTeam() != null ? match.getHomeTeam().getName() : "TBD";
        String away = match.getAwayTeam() != null ? match.getAwayTeam().getName() : "TBD";
        return home + " vs " + away;
    }
}
