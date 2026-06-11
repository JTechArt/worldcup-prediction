package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.dto.SchedulerCardDto;
import com.worldcup.prediction.repository.SchedulerLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SchedulerLogService {

    private final SchedulerLogRepository repository;
    private final Environment environment;

    @Value("${app.timezone:Asia/Yerevan}")
    private String appTimezone;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("d MMM HH:mm");

    @Transactional
    public SchedulerLog start(String jobName) {
        SchedulerLog log = SchedulerLog.builder()
                .jobName(jobName)
                .status(SchedulerJobStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();
        return repository.save(log);
    }

    @Transactional
    public void complete(SchedulerLog log, SchedulerJobStatus status, int itemsProcessed, String message) {
        log.setStatus(status);
        log.setFinishedAt(LocalDateTime.now());
        log.setItemsProcessed(itemsProcessed);
        log.setMessage(message);
        repository.save(log);
    }

    @Transactional
    public void fail(SchedulerLog log, String message, String errorDetail) {
        log.setStatus(SchedulerJobStatus.FAILED);
        log.setFinishedAt(LocalDateTime.now());
        log.setMessage(message != null ? truncate(message, 500) : "Unknown error");
        log.setErrorDetail(errorDetail);
        repository.save(log);
    }

    @Transactional
    public void cleanup() {
        repository.deleteByStartedAtBefore(LocalDateTime.now().minusDays(7));
    }

    public Optional<SchedulerLog> findLatest(String jobName) {
        return repository.findFirstByJobNameOrderByStartedAtDesc(jobName);
    }

    public List<SchedulerLog> findAll(String jobNameFilter, SchedulerJobStatus statusFilter) {
        if (jobNameFilter != null && statusFilter != null) {
            return repository.findTop200ByJobNameAndStatusOrderByStartedAtDesc(jobNameFilter, statusFilter);
        } else if (jobNameFilter != null) {
            return repository.findTop200ByJobNameOrderByStartedAtDesc(jobNameFilter);
        } else if (statusFilter != null) {
            return repository.findTop200ByStatusOrderByStartedAtDesc(statusFilter);
        } else {
            return repository.findTop200ByOrderByStartedAtDesc();
        }
    }

    @Transactional(readOnly = true)
    public List<SchedulerCardDto> buildCards() {
        ZoneId zone = ZoneId.of(appTimezone);
        List<SchedulerCardDto> cards = new ArrayList<>();
        for (SchedulerJobType jobType : SchedulerJobType.values()) {
            boolean enabled = environment.getProperty(jobType.getEnabledProperty(), Boolean.class, false);
            Optional<SchedulerLog> latest = repository.findFirstByJobNameOrderByStartedAtDesc(jobType.name());
            SchedulerJobStatus lastStatus = latest.map(SchedulerLog::getStatus).orElse(null);
            String lastFinishedAt = latest.flatMap(l -> Optional.ofNullable(l.getFinishedAt()))
                    .map(t -> t.format(FORMATTER)).orElse(null);
            String nextRun = computeNextRun(jobType, latest.orElse(null), enabled, zone);
            cards.add(new SchedulerCardDto(
                    jobType.name(), jobType.getDisplayName(), jobType.getScheduleDescription(),
                    enabled, nextRun, lastStatus, lastFinishedAt));
        }
        return cards;
    }

    private String computeNextRun(SchedulerJobType jobType, SchedulerLog latest, boolean enabled, ZoneId zone) {
        if (!enabled) return "Disabled";
        LocalDateTime ref = latest != null
                ? (latest.getFinishedAt() != null ? latest.getFinishedAt() : latest.getStartedAt())
                : null;
        if (jobType.isFixedDelay()) {
            if (ref == null) return "Pending first run";
            return ref.plusNanos(jobType.getDelayMs() * 1_000_000L).format(FORMATTER);
        } else {
            ZonedDateTime base = ref != null ? ref.atZone(zone) : ZonedDateTime.now(zone);
            ZonedDateTime next = CronExpression.parse(jobType.getCronExpression()).next(base);
            return next != null ? next.toLocalDateTime().format(FORMATTER) : "Unknown";
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
