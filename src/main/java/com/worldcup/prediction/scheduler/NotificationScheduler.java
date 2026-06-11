package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.LeaderboardService;
import com.worldcup.prediction.service.NotificationService;
import com.worldcup.prediction.service.RoundWindowService;
import com.worldcup.prediction.service.SchedulerLogService;
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
    private final CommunityRepository communityRepository;
    private final RoundWindowService roundWindowService;
    private final SchedulerLogService logService;

    @Value("${app.notification.reminder-hours-before:3}")
    private int reminderHoursBefore;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM HH:mm");

    @Scheduled(fixedDelay = 300_000)
    public void checkPredictionWindowOpen() {
        SchedulerLog entry = logService.start(SchedulerJobType.NOTIF_WINDOW_OPEN.name());
        try {
            LocalDateTime now = LocalDateTime.now();
            List<RoundWindow> allRounds = roundWindowService.findAll();
            List<RoundWindow> openRounds = allRounds.stream()
                    .filter(rw -> roundWindowService.isRoundOpen(rw.getRoundLabel(), now))
                    .filter(rw -> rw.getAutoOpensAt() != null
                            && !now.isBefore(rw.getAutoOpensAt())
                            && now.isBefore(rw.getAutoOpensAt().plusMinutes(10)))
                    .toList();

            if (openRounds.isEmpty()) {
                log.debug("NotificationScheduler: no newly-open rounds — skipping");
                logService.complete(entry, SchedulerJobStatus.SKIPPED, 0, "No newly-open rounds");
                return;
            }

            List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
            List<Community> communities = communityRepository.findAll();
            int sent = 0;
            for (RoundWindow rw : openRounds) {
                List<Match> matches = matchRepository.findByRoundLabelWithTeams(rw.getRoundLabel());
                if (matches.isEmpty()) continue;
                Match firstMatch = matches.get(0);
                for (Community community : communities) {
                    boolean ok = notificationService.sendPredictionWindowOpen(activeUsers, firstMatch, community.getId());
                    if (ok) {
                        log.info("Sent prediction-window-open notification for round {} in community {}", rw.getRoundLabel(), community.getId());
                        sent++;
                    }
                }
            }
            logService.complete(entry, SchedulerJobStatus.SUCCESS, sent, sent + " window-open notification(s) sent");
        } catch (Exception e) {
            log.error("NotificationScheduler.checkPredictionWindowOpen error", e);
            logService.fail(entry, e.getMessage(), stackTraceString(e));
        }
    }

    @Scheduled(fixedDelay = 900_000)
    public void checkPredictionDeadline() {
        SchedulerLog entry = logService.start(SchedulerJobType.NOTIF_DEADLINE.name());
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime deadlineFrom = now.plusHours(1);
            LocalDateTime deadlineTo = now.plusHours(reminderHoursBefore);
            List<Match> approachingMatches = matchRepository.findByKickoffTimeBetween(deadlineFrom, deadlineTo);
            LocalDateTime deadlineNow = LocalDateTime.now();
            approachingMatches = approachingMatches.stream()
                    .filter(m -> m.getStatus() == MatchStatus.SCHEDULED
                            && roundWindowService.isRoundOpen(m.getRoundLabel(), deadlineNow))
                    .collect(Collectors.toList());
            if (approachingMatches.isEmpty()) {
                log.debug("NotificationScheduler: no approaching deadlines — skipping");
                logService.complete(entry, SchedulerJobStatus.SKIPPED, 0, "No approaching deadlines");
                return;
            }
            List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
            List<Community> communities = communityRepository.findAll();
            int totalSent = 0;
            for (Match match : approachingMatches) {
                List<User> usersWithoutPredictions = activeUsers.stream()
                        .filter(u -> !predictionRepository.existsByUserIdAndMatchId(u.getId(), match.getId()))
                        .collect(Collectors.toList());
                if (usersWithoutPredictions.isEmpty()) continue;
                for (Community community : communities) {
                    int sentCount = notificationService.sendPredictionReminders(usersWithoutPredictions, match, community.getId());
                    if (sentCount > 0) {
                        log.info("Sent {} prediction reminders for match {} in community {}", sentCount, match.getId(), community.getId());
                        totalSent += sentCount;
                    }
                }
            }
            logService.complete(entry, SchedulerJobStatus.SUCCESS, totalSent, totalSent + " reminder(s) sent");
        } catch (Exception e) {
            log.error("NotificationScheduler.checkPredictionDeadline error", e);
            logService.fail(entry, e.getMessage(), stackTraceString(e));
        }
    }

    @Scheduled(fixedDelay = 1_800_000)
    public void checkLeaderboardDigest() {
        SchedulerLog entry = logService.start(SchedulerJobType.NOTIF_DIGEST.name());
        try {
            LocalDate today = LocalDate.now();
            LocalDateTime dayStart = today.atStartOfDay();
            LocalDateTime dayEnd = today.atTime(LocalTime.MAX);
            List<Match> todayMatches = matchRepository.findByKickoffTimeBetween(dayStart, dayEnd);
            if (todayMatches.isEmpty()) {
                log.debug("NotificationScheduler: no matches today — skipping digest");
                logService.complete(entry, SchedulerJobStatus.SKIPPED, 0, "No matches today");
                return;
            }
            boolean allCompleted = todayMatches.stream().allMatch(m -> m.getStatus() == MatchStatus.COMPLETED);
            if (!allCompleted) {
                log.debug("NotificationScheduler: not all today's matches completed — skipping digest");
                logService.complete(entry, SchedulerJobStatus.SKIPPED, 0, "Not all today's matches completed");
                return;
            }
            String dateKey = today.toString();
            List<Community> communities = communityRepository.findAll();
            int sent = 0;
            for (Community community : communities) {
                List<LeaderboardEntryDto> top10 = leaderboardService.getTopN(10, community.getId());
                if (top10.isEmpty()) continue;
                List<User> topUsers = new ArrayList<>();
                List<Map<String, Object>> topEntries = new ArrayList<>();
                for (LeaderboardEntryDto entry2 : top10) {
                    userRepository.findById(entry2.getUserId()).ifPresent(topUsers::add);
                    topEntries.add(Map.of("rank", entry2.getRank(), "name", entry2.getDisplayName(), "points", entry2.getTotalPoints()));
                }
                List<Map<String, Object>> matchResults = todayMatches.stream()
                        .filter(Match::isCompleted)
                        .map(m -> Map.<String, Object>of("label", matchLabel(m), "score", m.getHomeScore() + " - " + m.getAwayScore()))
                        .collect(Collectors.toList());
                boolean ok = notificationService.sendLeaderboardDigest(dateKey, topUsers, topEntries, matchResults, community.getId());
                if (ok) {
                    log.info("Sent leaderboard digest for {} in community {}", dateKey, community.getId());
                    sent++;
                }
            }
            logService.complete(entry, SchedulerJobStatus.SUCCESS, sent, sent + " digest(s) sent");
        } catch (Exception e) {
            log.error("NotificationScheduler.checkLeaderboardDigest error", e);
            logService.fail(entry, e.getMessage(), stackTraceString(e));
        }
    }

    private static String stackTraceString(Throwable e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }

    private String matchLabel(Match match) {
        String home = match.getHomeTeam() != null ? match.getHomeTeam().getName() : "TBD";
        String away = match.getAwayTeam() != null ? match.getAwayTeam().getName() : "TBD";
        return home + " vs " + away;
    }
}
