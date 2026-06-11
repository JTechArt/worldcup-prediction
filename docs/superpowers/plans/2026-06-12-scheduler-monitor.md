# Scheduler Monitor — Admin Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an admin page at `/admin/schedulers` that shows execution history, status, next-run time, and enabled/disabled state for all 7 scheduled jobs, with per-job "Run Now" manual trigger buttons.

**Architecture:** A `scheduler_log` DB table (Flyway V10) captures each job execution via a `SchedulerLogService` that all 5 schedulers call. A `SchedulerRunnerService` dispatches manual runs by calling scheduler bean methods directly. `AdminSchedulerController` serves the page with cards (one per job type) and a filterable log table.

**Tech Stack:** Spring Boot 3.3.x, Spring Data JPA, Thymeleaf, Flyway, Tailwind CSS (CDN), Lombok, Mockito, `@WebMvcTest` + `MockMvc`

---

## File Map

| File | Action |
|------|--------|
| `src/main/resources/db/migration/V10__scheduler_log.sql` | Create |
| `src/main/java/com/worldcup/prediction/domain/enums/SchedulerJobStatus.java` | Create |
| `src/main/java/com/worldcup/prediction/domain/enums/SchedulerJobType.java` | Create |
| `src/main/java/com/worldcup/prediction/domain/SchedulerLog.java` | Create |
| `src/main/java/com/worldcup/prediction/dto/SchedulerCardDto.java` | Create |
| `src/main/java/com/worldcup/prediction/repository/SchedulerLogRepository.java` | Create |
| `src/main/java/com/worldcup/prediction/service/SchedulerLogService.java` | Create |
| `src/main/java/com/worldcup/prediction/scheduler/SchedulerLogCleanupScheduler.java` | Create |
| `src/main/java/com/worldcup/prediction/scheduler/MatchResultScheduler.java` | Modify |
| `src/main/java/com/worldcup/prediction/scheduler/LineupSyncScheduler.java` | Modify |
| `src/main/java/com/worldcup/prediction/scheduler/StandingSyncScheduler.java` | Modify |
| `src/main/java/com/worldcup/prediction/scheduler/ScorersSyncScheduler.java` | Modify |
| `src/main/java/com/worldcup/prediction/scheduler/NotificationScheduler.java` | Modify |
| `src/main/java/com/worldcup/prediction/service/SchedulerRunnerService.java` | Create |
| `src/main/java/com/worldcup/prediction/controller/admin/AdminSchedulerController.java` | Create |
| `src/main/resources/templates/admin/schedulers.html` | Create |
| `src/main/resources/templates/admin/layout.html` | Modify |
| `src/test/java/com/worldcup/prediction/service/SchedulerLogServiceTest.java` | Create |
| `src/test/java/com/worldcup/prediction/scheduler/MatchResultSchedulerTest.java` | Modify |
| `src/test/java/com/worldcup/prediction/scheduler/NotificationSchedulerTest.java` | Modify |
| `src/test/java/com/worldcup/prediction/service/SchedulerRunnerServiceTest.java` | Create |
| `src/test/java/com/worldcup/prediction/controller/admin/AdminSchedulerControllerTest.java` | Create |

---

## Task 1: Flyway Migration V10

**Files:**
- Create: `src/main/resources/db/migration/V10__scheduler_log.sql`

- [ ] **Step 1: Create the migration**

```sql
-- V10__scheduler_log.sql
-- ANSI SQL — compatible with SQLite and PostgreSQL (matches V1 pattern)

CREATE TABLE scheduler_log (
    id              INTEGER PRIMARY KEY,
    job_name        VARCHAR(30)   NOT NULL,
    status          VARCHAR(15)   NOT NULL,
    started_at      TIMESTAMP     NOT NULL,
    finished_at     TIMESTAMP,
    items_processed INTEGER       NOT NULL DEFAULT 0,
    message         VARCHAR(500),
    error_detail    VARCHAR(2000)
);

CREATE INDEX idx_scheduler_log_job_name   ON scheduler_log (job_name);
CREATE INDEX idx_scheduler_log_started_at ON scheduler_log (started_at);
```

- [ ] **Step 2: Verify migration runs**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw flyway:migrate -q 2>&1 | tail -5
```

Expected: no errors, mentions V10.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V10__scheduler_log.sql
git commit -m "feat: add scheduler_log migration V10"
```

---

## Task 2: Domain Layer — Enums + Entity

**Files:**
- Create: `src/main/java/com/worldcup/prediction/domain/enums/SchedulerJobStatus.java`
- Create: `src/main/java/com/worldcup/prediction/domain/enums/SchedulerJobType.java`
- Create: `src/main/java/com/worldcup/prediction/domain/SchedulerLog.java`

- [ ] **Step 1: Create `SchedulerJobStatus`**

```java
package com.worldcup.prediction.domain.enums;

public enum SchedulerJobStatus {
    IN_PROGRESS, SUCCESS, SKIPPED, FAILED;

    public String getDisplayLabel() {
        return switch (this) {
            case IN_PROGRESS -> "Running";
            case SUCCESS     -> "Success";
            case SKIPPED     -> "Skipped";
            case FAILED      -> "Failed";
        };
    }

    public String getBadgeCss() {
        return switch (this) {
            case IN_PROGRESS -> "bg-blue-100 text-blue-700";
            case SUCCESS     -> "bg-green-100 text-green-800";
            case SKIPPED     -> "bg-gray-100 text-gray-600";
            case FAILED      -> "bg-red-100 text-red-700";
        };
    }
}
```

- [ ] **Step 2: Create `SchedulerJobType`**

```java
package com.worldcup.prediction.domain.enums;

public enum SchedulerJobType {
    MATCH_RESULT    ("Match Results",       "Every 5 min",    true,  300_000L,     null,              "app.football.api.enabled"),
    LINEUP_SYNC     ("Lineup Sync",         "Every 30 min",   true,  1_800_000L,   null,              "app.football.api.enabled"),
    STANDING_SYNC   ("Standings",           "Every 6 hours",  false, 0L,           "0 0 */6 * * *",   "app.football.api.enabled"),
    SCORERS_SYNC    ("Top Scorers",         "Daily at 02:00", false, 0L,           "0 0 2 * * *",     "app.football.api.enabled"),
    NOTIF_WINDOW_OPEN("Window Open Notif", "Every 5 min",    true,  300_000L,     null,              "app.notification.enabled"),
    NOTIF_DEADLINE  ("Deadline Reminder",   "Every 15 min",   true,  900_000L,     null,              "app.notification.enabled"),
    NOTIF_DIGEST    ("Leaderboard Digest",  "Every 30 min",   true,  1_800_000L,   null,              "app.notification.enabled");

    private final String displayName;
    private final String scheduleDescription;
    private final boolean fixedDelay;
    private final long delayMs;
    private final String cronExpression;
    private final String enabledProperty;

    SchedulerJobType(String displayName, String scheduleDescription, boolean fixedDelay,
                     long delayMs, String cronExpression, String enabledProperty) {
        this.displayName         = displayName;
        this.scheduleDescription = scheduleDescription;
        this.fixedDelay          = fixedDelay;
        this.delayMs             = delayMs;
        this.cronExpression      = cronExpression;
        this.enabledProperty     = enabledProperty;
    }

    public String getDisplayName()          { return displayName; }
    public String getScheduleDescription()  { return scheduleDescription; }
    public boolean isFixedDelay()           { return fixedDelay; }
    public long getDelayMs()                { return delayMs; }
    public String getCronExpression()       { return cronExpression; }
    public String getEnabledProperty()      { return enabledProperty; }
}
```

- [ ] **Step 3: Create `SchedulerLog` entity**

```java
package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "scheduler_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SchedulerLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "job_name", nullable = false, length = 30)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SchedulerJobStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "items_processed", nullable = false)
    @Builder.Default
    private int itemsProcessed = 0;

    @Column(length = 500)
    private String message;

    @Column(name = "error_detail", length = 2000)
    private String errorDetail;

    @Transient
    public String getDurationFormatted() {
        if (startedAt == null || finishedAt == null) return "—";
        long seconds = Duration.between(startedAt, finishedAt).getSeconds();
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    @Transient
    public String getRowCssClass() {
        if (status == null) return "";
        return switch (status) {
            case FAILED      -> "bg-red-50";
            case IN_PROGRESS -> "bg-blue-50";
            case SUCCESS     -> itemsProcessed > 0 ? "bg-green-50" : "bg-gray-50";
            case SKIPPED     -> "bg-gray-50";
        };
    }
}
```

- [ ] **Step 4: Compile check**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/domain/enums/SchedulerJobStatus.java \
        src/main/java/com/worldcup/prediction/domain/enums/SchedulerJobType.java \
        src/main/java/com/worldcup/prediction/domain/SchedulerLog.java
git commit -m "feat: add SchedulerLog entity and job enums"
```

---

## Task 3: Repository + SchedulerCardDto

**Files:**
- Create: `src/main/java/com/worldcup/prediction/repository/SchedulerLogRepository.java`
- Create: `src/main/java/com/worldcup/prediction/dto/SchedulerCardDto.java`

- [ ] **Step 1: Create `SchedulerLogRepository`**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SchedulerLogRepository extends JpaRepository<SchedulerLog, Long> {

    Optional<SchedulerLog> findFirstByJobNameOrderByStartedAtDesc(String jobName);

    List<SchedulerLog> findTop200ByOrderByStartedAtDesc();

    List<SchedulerLog> findTop200ByJobNameOrderByStartedAtDesc(String jobName);

    List<SchedulerLog> findTop200ByStatusOrderByStartedAtDesc(SchedulerJobStatus status);

    List<SchedulerLog> findTop200ByJobNameAndStatusOrderByStartedAtDesc(String jobName, SchedulerJobStatus status);

    void deleteByStartedAtBefore(LocalDateTime cutoff);
}
```

- [ ] **Step 2: Create `SchedulerCardDto`**

```java
package com.worldcup.prediction.dto;

import com.worldcup.prediction.domain.enums.SchedulerJobStatus;

public record SchedulerCardDto(
        String jobName,
        String displayName,
        String scheduleDescription,
        boolean enabled,
        String nextRun,
        SchedulerJobStatus lastStatus,
        String lastFinishedAt
) {}
```

- [ ] **Step 3: Compile check**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worldcup/prediction/repository/SchedulerLogRepository.java \
        src/main/java/com/worldcup/prediction/dto/SchedulerCardDto.java
git commit -m "feat: add SchedulerLogRepository and SchedulerCardDto"
```

---

## Task 4: SchedulerLogService + Test

**Files:**
- Create: `src/main/java/com/worldcup/prediction/service/SchedulerLogService.java`
- Create: `src/test/java/com/worldcup/prediction/service/SchedulerLogServiceTest.java`

- [ ] **Step 1: Write the failing test first**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.dto.SchedulerCardDto;
import com.worldcup.prediction.repository.SchedulerLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerLogServiceTest {

    @Mock SchedulerLogRepository repository;
    @Mock Environment environment;
    @InjectMocks SchedulerLogService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "appTimezone", "Asia/Yerevan");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void start_persistsInProgressRow() {
        service.start("MATCH_RESULT");
        ArgumentCaptor<SchedulerLog> captor = ArgumentCaptor.forClass(SchedulerLog.class);
        verify(repository).save(captor.capture());
        SchedulerLog saved = captor.getValue();
        assertThat(saved.getJobName()).isEqualTo("MATCH_RESULT");
        assertThat(saved.getStatus()).isEqualTo(SchedulerJobStatus.IN_PROGRESS);
        assertThat(saved.getStartedAt()).isNotNull();
    }

    @Test
    void complete_updatesStatusAndFinishedAt() {
        SchedulerLog log = SchedulerLog.builder().id(1L).jobName("LINEUP_SYNC")
                .status(SchedulerJobStatus.IN_PROGRESS).startedAt(LocalDateTime.now()).build();
        service.complete(log, SchedulerJobStatus.SUCCESS, 3, "3 lineups fetched");
        assertThat(log.getStatus()).isEqualTo(SchedulerJobStatus.SUCCESS);
        assertThat(log.getFinishedAt()).isNotNull();
        assertThat(log.getItemsProcessed()).isEqualTo(3);
        assertThat(log.getMessage()).isEqualTo("3 lineups fetched");
        verify(repository).save(log);
    }

    @Test
    void fail_marksFailedWithErrorDetail() {
        SchedulerLog log = SchedulerLog.builder().id(2L).jobName("STANDING_SYNC")
                .status(SchedulerJobStatus.IN_PROGRESS).startedAt(LocalDateTime.now()).build();
        service.fail(log, "timeout", "java.net.SocketTimeoutException...");
        assertThat(log.getStatus()).isEqualTo(SchedulerJobStatus.FAILED);
        assertThat(log.getMessage()).isEqualTo("timeout");
        assertThat(log.getErrorDetail()).isEqualTo("java.net.SocketTimeoutException...");
        verify(repository).save(log);
    }

    @Test
    void cleanup_deletesRowsOlderThan7Days() {
        service.cleanup();
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByStartedAtBefore(captor.capture());
        assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusDays(6));
    }

    @Test
    void findAll_noFilters_callsTop200() {
        when(repository.findTop200ByOrderByStartedAtDesc()).thenReturn(List.of());
        service.findAll(null, null);
        verify(repository).findTop200ByOrderByStartedAtDesc();
    }

    @Test
    void findAll_jobFilter_callsJobQuery() {
        when(repository.findTop200ByJobNameOrderByStartedAtDesc("MATCH_RESULT")).thenReturn(List.of());
        service.findAll("MATCH_RESULT", null);
        verify(repository).findTop200ByJobNameOrderByStartedAtDesc("MATCH_RESULT");
    }

    @Test
    void findAll_statusFilter_callsStatusQuery() {
        when(repository.findTop200ByStatusOrderByStartedAtDesc(SchedulerJobStatus.FAILED)).thenReturn(List.of());
        service.findAll(null, SchedulerJobStatus.FAILED);
        verify(repository).findTop200ByStatusOrderByStartedAtDesc(SchedulerJobStatus.FAILED);
    }

    @Test
    void findAll_bothFilters_callsCombinedQuery() {
        when(repository.findTop200ByJobNameAndStatusOrderByStartedAtDesc("LINEUP_SYNC", SchedulerJobStatus.SUCCESS))
                .thenReturn(List.of());
        service.findAll("LINEUP_SYNC", SchedulerJobStatus.SUCCESS);
        verify(repository).findTop200ByJobNameAndStatusOrderByStartedAtDesc("LINEUP_SYNC", SchedulerJobStatus.SUCCESS);
    }

    @Test
    void buildCards_returnsOneCardPerJobType() {
        when(environment.getProperty(anyString(), eq(Boolean.class), eq(false))).thenReturn(false);
        when(repository.findFirstByJobNameOrderByStartedAtDesc(anyString())).thenReturn(Optional.empty());
        List<SchedulerCardDto> cards = service.buildCards();
        assertThat(cards).hasSize(SchedulerJobType.values().length);
        assertThat(cards.get(0).jobName()).isEqualTo("MATCH_RESULT");
    }

    @Test
    void buildCards_enabledJobShowsNextRun() {
        when(environment.getProperty("app.football.api.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("app.notification.enabled", Boolean.class, false)).thenReturn(false);
        LocalDateTime lastFinished = LocalDateTime.now().minusMinutes(2);
        SchedulerLog recent = SchedulerLog.builder().jobName("MATCH_RESULT")
                .status(SchedulerJobStatus.SUCCESS).startedAt(lastFinished.minusSeconds(1))
                .finishedAt(lastFinished).build();
        when(repository.findFirstByJobNameOrderByStartedAtDesc("MATCH_RESULT")).thenReturn(Optional.of(recent));
        when(repository.findFirstByJobNameOrderByStartedAtDesc(argThat(s -> !s.equals("MATCH_RESULT")))).thenReturn(Optional.empty());
        List<SchedulerCardDto> cards = service.buildCards();
        SchedulerCardDto matchResultCard = cards.stream().filter(c -> c.jobName().equals("MATCH_RESULT")).findFirst().orElseThrow();
        assertThat(matchResultCard.enabled()).isTrue();
        assertThat(matchResultCard.nextRun()).isNotEqualTo("Disabled");
        assertThat(matchResultCard.nextRun()).isNotEqualTo("Pending first run");
    }

    @Test
    void buildCards_disabledJobShowsDisabled() {
        when(environment.getProperty(anyString(), eq(Boolean.class), eq(false))).thenReturn(false);
        when(repository.findFirstByJobNameOrderByStartedAtDesc(anyString())).thenReturn(Optional.empty());
        List<SchedulerCardDto> cards = service.buildCards();
        assertThat(cards).allMatch(c -> c.nextRun().equals("Disabled"));
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw test -Dtest=SchedulerLogServiceTest -q 2>&1 | tail -15
```

Expected: FAIL — `SchedulerLogService` not found.

- [ ] **Step 3: Implement `SchedulerLogService`**

```java
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
        log.setErrorDetail(errorDetail != null ? truncate(errorDetail, 2000) : null);
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

    public List<SchedulerCardDto> buildCards() {
        ZoneId zone = ZoneId.of(environment.getProperty("app.timezone", "Asia/Yerevan"));
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
```

- [ ] **Step 4: Run tests and confirm they pass**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw test -Dtest=SchedulerLogServiceTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/SchedulerLogService.java \
        src/test/java/com/worldcup/prediction/service/SchedulerLogServiceTest.java
git commit -m "feat: add SchedulerLogService with start/complete/fail/buildCards"
```

---

## Task 5: Cleanup Scheduler

**Files:**
- Create: `src/main/java/com/worldcup/prediction/scheduler/SchedulerLogCleanupScheduler.java`

- [ ] **Step 1: Create `SchedulerLogCleanupScheduler`**

```java
package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.service.SchedulerLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SchedulerLogCleanupScheduler {

    private final SchedulerLogService logService;

    @Scheduled(fixedDelay = 86_400_000) // daily
    public void cleanup() {
        try {
            logService.cleanup();
            log.debug("SchedulerLogCleanup: purged entries older than 7 days");
        } catch (Exception e) {
            log.error("SchedulerLogCleanup: error during cleanup", e);
        }
    }
}
```

- [ ] **Step 2: Compile check**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worldcup/prediction/scheduler/SchedulerLogCleanupScheduler.java
git commit -m "feat: add SchedulerLogCleanupScheduler — daily 7-day retention"
```

---

## Task 6: Instrument Football API Schedulers

Update `MatchResultScheduler`, `LineupSyncScheduler`, `StandingSyncScheduler`, `ScorersSyncScheduler` to log via `SchedulerLogService`. Update `MatchResultSchedulerTest`.

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/scheduler/MatchResultScheduler.java`
- Modify: `src/main/java/com/worldcup/prediction/scheduler/LineupSyncScheduler.java`
- Modify: `src/main/java/com/worldcup/prediction/scheduler/StandingSyncScheduler.java`
- Modify: `src/main/java/com/worldcup/prediction/scheduler/ScorersSyncScheduler.java`
- Modify: `src/test/java/com/worldcup/prediction/scheduler/MatchResultSchedulerTest.java`

- [ ] **Step 1: Update `MatchResultScheduler`**

Replace the entire file:

```java
package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.integration.football.FootballApiSyncService;
import com.worldcup.prediction.service.SchedulerLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class MatchResultScheduler {

    private final FootballApiSyncService syncService;
    private final SchedulerLogService logService;

    @Scheduled(fixedDelay = 300_000)
    public void syncAndScore() {
        SchedulerLog entry = logService.start(SchedulerJobType.MATCH_RESULT.name());
        try {
            if (!syncService.hasActionableMatches()) {
                log.debug("MatchResultScheduler: no actionable matches — skipping");
                logService.complete(entry, SchedulerJobStatus.SKIPPED, 0, "No actionable matches");
                return;
            }
            List<Long> finished = syncService.syncResults();
            String msg = finished.isEmpty() ? "No new results" : finished.size() + " match(es) scored";
            if (!finished.isEmpty()) log.info("Scheduler: {} match(es) newly finished and scored: {}", finished.size(), finished);
            logService.complete(entry, SchedulerJobStatus.SUCCESS, finished.size(), msg);
        } catch (Exception e) {
            log.error("Scheduler: unexpected error — will retry next cycle", e);
            logService.fail(entry, e.getMessage(), stackTraceString(e));
        }
    }

    private static String stackTraceString(Throwable e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
```

- [ ] **Step 2: Update `LineupSyncScheduler`**

Replace the entire file:

```java
package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.integration.football.LineupSyncService;
import com.worldcup.prediction.integration.football.SyncResult;
import com.worldcup.prediction.service.SchedulerLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LineupSyncScheduler {

    private final LineupSyncService syncService;
    private final SchedulerLogService logService;

    @Scheduled(fixedDelay = 1_800_000)
    public void syncLineups() {
        SchedulerLog entry = logService.start(SchedulerJobType.LINEUP_SYNC.name());
        try {
            SyncResult result = syncService.syncLineups();
            if (!result.skipped()) log.info("LineupSync: {}", result.message());
            else log.debug("LineupSync: {}", result.message());
            SchedulerJobStatus status = result.skipped() ? SchedulerJobStatus.SKIPPED : SchedulerJobStatus.SUCCESS;
            logService.complete(entry, status, 0, result.message());
        } catch (Exception e) {
            log.error("LineupSyncScheduler: unexpected error — will retry next cycle", e);
            logService.fail(entry, e.getMessage(), stackTraceString(e));
        }
    }

    private static String stackTraceString(Throwable e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
```

- [ ] **Step 3: Update `StandingSyncScheduler`**

Replace the entire file:

```java
package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.integration.football.StandingSyncService;
import com.worldcup.prediction.integration.football.SyncResult;
import com.worldcup.prediction.service.SchedulerLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class StandingSyncScheduler {

    private final StandingSyncService syncService;
    private final SchedulerLogService logService;

    @Scheduled(cron = "0 0 */6 * * *", zone = "${app.timezone}")
    public void syncStandings() {
        SchedulerLog entry = logService.start(SchedulerJobType.STANDING_SYNC.name());
        try {
            SyncResult result = syncService.syncStandings();
            if (!result.skipped()) log.info("StandingSync: {}", result.message());
            else log.debug("StandingSync: {}", result.message());
            SchedulerJobStatus status = result.skipped() ? SchedulerJobStatus.SKIPPED : SchedulerJobStatus.SUCCESS;
            logService.complete(entry, status, 0, result.message());
        } catch (Exception e) {
            log.error("StandingSyncScheduler: unexpected error — will retry next cycle", e);
            logService.fail(entry, e.getMessage(), stackTraceString(e));
        }
    }

    private static String stackTraceString(Throwable e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
```

- [ ] **Step 4: Update `ScorersSyncScheduler`**

Replace the entire file:

```java
package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.integration.football.ScorersService;
import com.worldcup.prediction.integration.football.SyncResult;
import com.worldcup.prediction.service.SchedulerLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ScorersSyncScheduler {

    private final ScorersService scorersService;
    private final SchedulerLogService logService;

    @Scheduled(cron = "0 0 2 * * *", zone = "${app.timezone}")
    public void syncScorers() {
        SchedulerLog entry = logService.start(SchedulerJobType.SCORERS_SYNC.name());
        try {
            SyncResult result = scorersService.syncScorers();
            if (!result.skipped()) log.info("ScorersSync: {}", result.message());
            else log.debug("ScorersSync: {}", result.message());
            SchedulerJobStatus status = result.skipped() ? SchedulerJobStatus.SKIPPED : SchedulerJobStatus.SUCCESS;
            logService.complete(entry, status, 0, result.message());
        } catch (Exception e) {
            log.error("ScorersSyncScheduler: unexpected error — will retry next cycle", e);
            logService.fail(entry, e.getMessage(), stackTraceString(e));
        }
    }

    private static String stackTraceString(Throwable e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
```

- [ ] **Step 5: Update `MatchResultSchedulerTest`**

Replace the entire file:

```java
package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.integration.football.FootballApiSyncService;
import com.worldcup.prediction.service.SchedulerLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchResultSchedulerTest {

    @Mock FootballApiSyncService syncService;
    @Mock SchedulerLogService logService;
    @InjectMocks MatchResultScheduler scheduler;

    private final SchedulerLog stubLog = SchedulerLog.builder()
            .id(1L).jobName("MATCH_RESULT").status(SchedulerJobStatus.IN_PROGRESS)
            .startedAt(LocalDateTime.now()).build();

    @BeforeEach
    void setUp() {
        when(logService.start(anyString())).thenReturn(stubLog);
    }

    @Test
    void syncAndScore_callsSyncService() {
        when(syncService.hasActionableMatches()).thenReturn(true);
        when(syncService.syncResults()).thenReturn(List.of(1L, 2L));
        scheduler.syncAndScore();
        verify(syncService).syncResults();
        verify(logService).complete(stubLog, SchedulerJobStatus.SUCCESS, 2, "2 match(es) scored");
    }

    @Test
    void syncAndScore_whenSyncReturnsEmpty_logsSuccess() {
        when(syncService.hasActionableMatches()).thenReturn(true);
        when(syncService.syncResults()).thenReturn(List.of());
        scheduler.syncAndScore();
        verify(logService).complete(stubLog, SchedulerJobStatus.SUCCESS, 0, "No new results");
    }

    @Test
    void syncAndScore_whenSyncThrows_logsFailed() {
        when(syncService.hasActionableMatches()).thenReturn(true);
        when(syncService.syncResults()).thenThrow(new RuntimeException("network failure"));
        scheduler.syncAndScore();
        verify(logService).fail(eq(stubLog), eq("network failure"), anyString());
    }

    @Test
    void syncAndScore_whenNoActionableMatches_logsSkipped() {
        when(syncService.hasActionableMatches()).thenReturn(false);
        scheduler.syncAndScore();
        verify(syncService, never()).syncResults();
        verify(logService).complete(stubLog, SchedulerJobStatus.SKIPPED, 0, "No actionable matches");
    }
}
```

- [ ] **Step 6: Run scheduler tests**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw test -Dtest=MatchResultSchedulerTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, 4 tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/worldcup/prediction/scheduler/MatchResultScheduler.java \
        src/main/java/com/worldcup/prediction/scheduler/LineupSyncScheduler.java \
        src/main/java/com/worldcup/prediction/scheduler/StandingSyncScheduler.java \
        src/main/java/com/worldcup/prediction/scheduler/ScorersSyncScheduler.java \
        src/test/java/com/worldcup/prediction/scheduler/MatchResultSchedulerTest.java
git commit -m "feat: instrument football API schedulers with SchedulerLogService"
```

---

## Task 7: Instrument NotificationScheduler

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/scheduler/NotificationScheduler.java`
- Modify: `src/test/java/com/worldcup/prediction/scheduler/NotificationSchedulerTest.java`

- [ ] **Step 1: Add `SchedulerLogService` to `NotificationScheduler`**

Add `private final SchedulerLogService logService;` to the existing fields (Lombok `@RequiredArgsConstructor` handles injection). Then wrap each of the 3 methods with start/complete/fail calls.

Replace `checkPredictionWindowOpen()`:

```java
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
```

Replace `checkPredictionDeadline()`:

```java
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
                int sent = notificationService.sendPredictionReminders(usersWithoutPredictions, match, community.getId());
                if (sent > 0) {
                    log.info("Sent {} prediction reminders for match {} in community {}", sent, match.getId(), community.getId());
                    totalSent += sent;
                }
            }
        }
        logService.complete(entry, SchedulerJobStatus.SUCCESS, totalSent, totalSent + " reminder(s) sent");
    } catch (Exception e) {
        log.error("NotificationScheduler.checkPredictionDeadline error", e);
        logService.fail(entry, e.getMessage(), stackTraceString(e));
    }
}
```

Replace `checkLeaderboardDigest()`:

```java
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
```

Also add the imports at the top of `NotificationScheduler.java`:

```java
import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.service.SchedulerLogService;
```

And add the `stackTraceString` helper at the bottom of the class (before the `matchLabel` method):

```java
private static String stackTraceString(Throwable e) {
    java.io.StringWriter sw = new java.io.StringWriter();
    e.printStackTrace(new java.io.PrintWriter(sw));
    String s = sw.toString();
    return s.length() > 2000 ? s.substring(0, 2000) : s;
}
```

- [ ] **Step 2: Update `NotificationSchedulerTest`**

Replace the entire file:

```java
package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.LeaderboardService;
import com.worldcup.prediction.service.NotificationService;
import com.worldcup.prediction.service.RoundWindowService;
import com.worldcup.prediction.service.SchedulerLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock MatchRepository matchRepository;
    @Mock UserRepository userRepository;
    @Mock PredictionRepository predictionRepository;
    @Mock NotificationService notificationService;
    @Mock LeaderboardService leaderboardService;
    @Mock CommunityRepository communityRepository;
    @Mock RoundWindowService roundWindowService;
    @Mock SchedulerLogService logService;
    @InjectMocks NotificationScheduler scheduler;

    private final SchedulerLog stubLog = SchedulerLog.builder()
            .id(1L).status(SchedulerJobStatus.IN_PROGRESS).startedAt(LocalDateTime.now()).build();

    @BeforeEach
    void setUp() {
        when(logService.start(anyString())).thenReturn(stubLog);
    }

    @Test
    void checkPredictionWindowOpen_noMatches_logsSkipped() {
        when(roundWindowService.findAll()).thenReturn(List.of());
        scheduler.checkPredictionWindowOpen();
        verify(notificationService, never()).sendPredictionWindowOpen(anyList(), any(), anyLong());
        verify(logService).complete(stubLog, SchedulerJobStatus.SKIPPED, 0, "No newly-open rounds");
    }

    @Test
    void checkPredictionDeadline_noApproachingMatches_logsSkipped() {
        when(matchRepository.findByKickoffTimeBetween(any(), any())).thenReturn(List.of());
        scheduler.checkPredictionDeadline();
        verify(notificationService, never()).sendPredictionReminders(anyList(), any(), anyLong());
        verify(logService).complete(stubLog, SchedulerJobStatus.SKIPPED, 0, "No approaching deadlines");
    }

    @Test
    void checkLeaderboardDigest_noMatchesToday_logsSkipped() {
        when(matchRepository.findByKickoffTimeBetween(any(), any())).thenReturn(List.of());
        scheduler.checkLeaderboardDigest();
        verify(notificationService, never()).sendLeaderboardDigest(anyString(), anyList(), anyList(), anyList(), anyLong());
        verify(logService).complete(stubLog, SchedulerJobStatus.SKIPPED, 0, "No matches today");
    }

    @Test
    void checkLeaderboardDigest_notAllCompleted_logsSkipped() {
        Match incomplete = Match.builder().id(1L).status(MatchStatus.SCHEDULED)
                .kickoffTime(LocalDateTime.now()).stage(MatchStage.GROUP).matchNumber(1).build();
        when(matchRepository.findByKickoffTimeBetween(any(), any())).thenReturn(List.of(incomplete));
        scheduler.checkLeaderboardDigest();
        verify(notificationService, never()).sendLeaderboardDigest(anyString(), anyList(), anyList(), anyList(), anyLong());
        verify(logService).complete(stubLog, SchedulerJobStatus.SKIPPED, 0, "Not all today's matches completed");
    }
}
```

- [ ] **Step 3: Run tests**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw test -Dtest=NotificationSchedulerTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, 4 tests pass.

- [ ] **Step 4: Compile check all schedulers**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw compile -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/scheduler/NotificationScheduler.java \
        src/test/java/com/worldcup/prediction/scheduler/NotificationSchedulerTest.java
git commit -m "feat: instrument NotificationScheduler with SchedulerLogService"
```

---

## Task 8: SchedulerRunnerService + Test

**Files:**
- Create: `src/main/java/com/worldcup/prediction/service/SchedulerRunnerService.java`
- Create: `src/test/java/com/worldcup/prediction/service/SchedulerRunnerServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.scheduler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerRunnerServiceTest {

    @Mock MatchResultScheduler matchResultScheduler;
    @Mock LineupSyncScheduler lineupSyncScheduler;
    @Mock StandingSyncScheduler standingSyncScheduler;
    @Mock ScorersSyncScheduler scorersSyncScheduler;
    @Mock SchedulerLogService logService;
    @InjectMocks SchedulerRunnerService service;

    @Test
    void run_matchResult_callsSyncAndScore() {
        SchedulerLog log = SchedulerLog.builder().status(SchedulerJobStatus.SUCCESS).message("2 match(es) scored").build();
        when(logService.findLatest("MATCH_RESULT")).thenReturn(Optional.of(log));
        String result = service.run(SchedulerJobType.MATCH_RESULT);
        verify(matchResultScheduler).syncAndScore();
        assertThat(result).contains("SUCCESS");
    }

    @Test
    void run_lineupSync_callsSyncLineups() {
        when(logService.findLatest("LINEUP_SYNC")).thenReturn(Optional.empty());
        service.run(SchedulerJobType.LINEUP_SYNC);
        verify(lineupSyncScheduler).syncLineups();
    }

    @Test
    void run_standingSync_callsSyncStandings() {
        when(logService.findLatest("STANDING_SYNC")).thenReturn(Optional.empty());
        service.run(SchedulerJobType.STANDING_SYNC);
        verify(standingSyncScheduler).syncStandings();
    }

    @Test
    void run_scorersSync_callsSyncScorers() {
        when(logService.findLatest("SCORERS_SYNC")).thenReturn(Optional.empty());
        service.run(SchedulerJobType.SCORERS_SYNC);
        verify(scorersSyncScheduler).syncScorers();
    }

    @Test
    void run_notifWindowOpen_whenNotificationSchedulerNull_logsSkipped() {
        // notificationScheduler is null (not injected — disabled via @ConditionalOnProperty)
        SchedulerLog stubLog = SchedulerLog.builder().id(1L).status(SchedulerJobStatus.IN_PROGRESS).startedAt(LocalDateTime.now()).build();
        when(logService.start("NOTIF_WINDOW_OPEN")).thenReturn(stubLog);
        String result = service.run(SchedulerJobType.NOTIF_WINDOW_OPEN);
        verify(logService).complete(stubLog, SchedulerJobStatus.SKIPPED, 0, "Notification scheduler disabled (app.notification.enabled=false)");
        assertThat(result).contains("SKIPPED");
    }

    @Test
    void run_notifWindowOpen_whenNotificationSchedulerPresent_callsMethod() {
        NotificationScheduler mockNotif = mock(NotificationScheduler.class);
        ReflectionTestUtils.setField(service, "notificationScheduler", mockNotif);
        SchedulerLog log = SchedulerLog.builder().status(SchedulerJobStatus.SKIPPED).message("No newly-open rounds").build();
        when(logService.findLatest("NOTIF_WINDOW_OPEN")).thenReturn(Optional.of(log));
        service.run(SchedulerJobType.NOTIF_WINDOW_OPEN);
        verify(mockNotif).checkPredictionWindowOpen();
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw test -Dtest=SchedulerRunnerServiceTest -q 2>&1 | tail -10
```

Expected: FAIL — `SchedulerRunnerService` not found.

- [ ] **Step 3: Implement `SchedulerRunnerService`**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.scheduler.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SchedulerRunnerService {

    private final MatchResultScheduler matchResultScheduler;
    private final LineupSyncScheduler lineupSyncScheduler;
    private final StandingSyncScheduler standingSyncScheduler;
    private final ScorersSyncScheduler scorersSyncScheduler;
    private final SchedulerLogService logService;

    @Autowired(required = false)
    private NotificationScheduler notificationScheduler;

    public String run(SchedulerJobType jobType) {
        switch (jobType) {
            case MATCH_RESULT    -> matchResultScheduler.syncAndScore();
            case LINEUP_SYNC     -> lineupSyncScheduler.syncLineups();
            case STANDING_SYNC   -> standingSyncScheduler.syncStandings();
            case SCORERS_SYNC    -> scorersSyncScheduler.syncScorers();
            case NOTIF_WINDOW_OPEN -> {
                if (notificationScheduler == null) return logDisabled(jobType);
                notificationScheduler.checkPredictionWindowOpen();
            }
            case NOTIF_DEADLINE  -> {
                if (notificationScheduler == null) return logDisabled(jobType);
                notificationScheduler.checkPredictionDeadline();
            }
            case NOTIF_DIGEST    -> {
                if (notificationScheduler == null) return logDisabled(jobType);
                notificationScheduler.checkLeaderboardDigest();
            }
        }
        return logService.findLatest(jobType.name())
                .map(l -> l.getStatus() + ": " + (l.getMessage() != null ? l.getMessage() : ""))
                .orElse("Triggered");
    }

    private String logDisabled(SchedulerJobType jobType) {
        SchedulerLog log = logService.start(jobType.name());
        logService.complete(log, SchedulerJobStatus.SKIPPED, 0, "Notification scheduler disabled (app.notification.enabled=false)");
        return "SKIPPED: Notification scheduler disabled";
    }
}
```

- [ ] **Step 4: Run tests**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw test -Dtest=SchedulerRunnerServiceTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/SchedulerRunnerService.java \
        src/test/java/com/worldcup/prediction/service/SchedulerRunnerServiceTest.java
git commit -m "feat: add SchedulerRunnerService for manual job dispatch"
```

---

## Task 9: AdminSchedulerController + Test

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/admin/AdminSchedulerController.java`
- Create: `src/test/java/com/worldcup/prediction/controller/admin/AdminSchedulerControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import com.worldcup.prediction.service.SchedulerLogService;
import com.worldcup.prediction.service.SchedulerRunnerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminSchedulerController.class)
class AdminSchedulerControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SchedulerLogService logService;
    @MockBean SchedulerRunnerService runnerService;
    @MockBean UserRepository userRepository;
    @MockBean CommunityRepository communityRepository;
    @MockBean CommunityMembershipRepository communityMembershipRepository;
    @MockBean RoundWindowService roundWindowService;
    @MockBean RoundSubmissionService roundSubmissionService;

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void schedulerPage_returnsView() throws Exception {
        when(logService.buildCards()).thenReturn(List.of());
        when(logService.findAll(null, null)).thenReturn(List.of());
        mockMvc.perform(get("/admin/schedulers"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/schedulers"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void schedulerPage_withFilters_passesFiltersToService() throws Exception {
        when(logService.buildCards()).thenReturn(List.of());
        when(logService.findAll("MATCH_RESULT", SchedulerJobStatus.FAILED)).thenReturn(List.of());
        mockMvc.perform(get("/admin/schedulers").param("job", "MATCH_RESULT").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/schedulers"));
        verify(logService).findAll("MATCH_RESULT", SchedulerJobStatus.FAILED);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void runJob_triggersRunnerAndRedirects() throws Exception {
        when(runnerService.run(SchedulerJobType.MATCH_RESULT)).thenReturn("SUCCESS: 2 match(es) scored");
        mockMvc.perform(post("/admin/schedulers/run/MATCH_RESULT").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/schedulers"));
        verify(runnerService).run(SchedulerJobType.MATCH_RESULT);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void runJob_unknownJobName_redirectsWithError() throws Exception {
        mockMvc.perform(post("/admin/schedulers/run/UNKNOWN_JOB").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/schedulers"));
        verify(runnerService, never()).run(any());
    }

    @Test
    void schedulerPage_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/schedulers"))
                .andExpect(status().is3xxRedirection());
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw test -Dtest=AdminSchedulerControllerTest -q 2>&1 | tail -10
```

Expected: FAIL — `AdminSchedulerController` not found.

- [ ] **Step 3: Implement `AdminSchedulerController`**

```java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.service.SchedulerLogService;
import com.worldcup.prediction.service.SchedulerRunnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/schedulers")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminSchedulerController {

    private final SchedulerLogService logService;
    private final SchedulerRunnerService runnerService;

    @GetMapping
    public String schedulerPage(
            @RequestParam(required = false) String job,
            @RequestParam(required = false) String status,
            Model model) {
        SchedulerJobStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try { statusFilter = SchedulerJobStatus.valueOf(status); } catch (IllegalArgumentException ignored) {}
        }
        model.addAttribute("cards", logService.buildCards());
        model.addAttribute("logs", logService.findAll(job, statusFilter));
        model.addAttribute("jobTypes", SchedulerJobType.values());
        model.addAttribute("statuses", SchedulerJobStatus.values());
        model.addAttribute("jobFilter", job);
        model.addAttribute("statusFilter", status);
        return "admin/schedulers";
    }

    @PostMapping("/run/{jobName}")
    public String runJob(@PathVariable String jobName, RedirectAttributes ra) {
        try {
            SchedulerJobType jobType = SchedulerJobType.valueOf(jobName);
            String result = runnerService.run(jobType);
            ra.addFlashAttribute("successMessage", "Triggered " + jobType.getDisplayName() + " — " + result);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", "Unknown job: " + jobName);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Job failed: " + e.getMessage());
        }
        return "redirect:/admin/schedulers";
    }
}
```

- [ ] **Step 4: Run tests**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw test -Dtest=AdminSchedulerControllerTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/admin/AdminSchedulerController.java \
        src/test/java/com/worldcup/prediction/controller/admin/AdminSchedulerControllerTest.java
git commit -m "feat: add AdminSchedulerController with GET page and POST run-job"
```

---

## Task 10: Template + Sidebar Link

**Files:**
- Create: `src/main/resources/templates/admin/schedulers.html`
- Modify: `src/main/resources/templates/admin/layout.html`

- [ ] **Step 1: Create `admin/schedulers.html`**

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{admin/layout}">
<head><title>Schedulers</title></head>
<body>
<th:block layout:fragment="page-title">Schedulers</th:block>
<th:block layout:fragment="content">

  <!-- Job Cards -->
  <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 mb-8">
    <div th:each="card : ${cards}"
         class="bg-white rounded-xl border border-gray-100 shadow-sm p-4 flex flex-col gap-2">

      <!-- Header row: name + enabled badge -->
      <div class="flex items-center justify-between gap-2">
        <span class="font-semibold text-gray-800 text-sm" th:text="${card.displayName}">Job Name</span>
        <span th:if="${card.enabled}"
              class="px-2 py-0.5 rounded text-xs font-semibold bg-green-100 text-green-700">Enabled</span>
        <span th:unless="${card.enabled}"
              class="px-2 py-0.5 rounded text-xs font-semibold bg-red-100 text-red-700">Disabled</span>
      </div>

      <!-- Schedule -->
      <p class="text-xs text-gray-400" th:text="${card.scheduleDescription}">Every 5 min</p>

      <!-- Last status -->
      <div class="flex items-center gap-1.5 text-xs">
        <span class="text-gray-500">Last:</span>
        <span th:if="${card.lastStatus == null}" class="text-gray-400">Never run</span>
        <span th:unless="${card.lastStatus == null}"
              th:class="'inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium ' + ${card.lastStatus.badgeCss}"
              th:text="${card.lastStatus.displayLabel}">Status</span>
        <span th:if="${card.lastFinishedAt != null}" class="text-gray-400"
              th:text="${card.lastFinishedAt}"></span>
      </div>

      <!-- Next run -->
      <div class="text-xs text-gray-500">
        <span class="font-medium">Next:</span>
        <span th:text="${card.nextRun}" class="text-gray-700">—</span>
      </div>

      <!-- Run Now button -->
      <form th:action="@{'/admin/schedulers/run/' + ${card.jobName}}" method="post" class="mt-1">
        <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
        <button type="submit"
                class="w-full text-xs font-semibold px-3 py-1.5 rounded-lg bg-admin-dark text-white hover:bg-admin-mid transition-colors duration-150">
          Run Now
        </button>
      </form>
    </div>
  </div>

  <!-- Filter bar -->
  <form method="get" action="/admin/schedulers" class="flex flex-wrap gap-3 mb-4 items-end">
    <div>
      <label class="block text-xs text-gray-500 mb-1">Job</label>
      <select name="job"
              class="rounded-lg border border-gray-300 text-sm px-3 py-1.5 bg-white focus:outline-none focus:ring-1 focus:ring-admin-mid">
        <option value="" th:selected="${jobFilter == null or jobFilter == ''}">All jobs</option>
        <option th:each="jt : ${jobTypes}"
                th:value="${jt.name()}"
                th:text="${jt.displayName}"
                th:selected="${jt.name() == jobFilter}">Job</option>
      </select>
    </div>
    <div>
      <label class="block text-xs text-gray-500 mb-1">Status</label>
      <select name="status"
              class="rounded-lg border border-gray-300 text-sm px-3 py-1.5 bg-white focus:outline-none focus:ring-1 focus:ring-admin-mid">
        <option value="" th:selected="${statusFilter == null or statusFilter == ''}">All statuses</option>
        <option th:each="s : ${statuses}"
                th:value="${s.name()}"
                th:text="${s.displayLabel}"
                th:selected="${s.name() == statusFilter}">Status</option>
      </select>
    </div>
    <button type="submit"
            class="px-4 py-1.5 text-sm font-semibold bg-admin-dark text-white rounded-lg hover:bg-admin-mid transition-colors">
      Filter
    </button>
    <a href="/admin/schedulers" class="px-4 py-1.5 text-sm text-gray-500 hover:text-gray-700 rounded-lg border border-gray-300 hover:border-gray-400 transition-colors">
      Clear
    </a>
  </form>

  <!-- Log table -->
  <div class="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
    <table class="w-full text-sm">
      <thead>
        <tr class="bg-gray-50 border-b border-gray-100 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
          <th class="px-4 py-3">Job</th>
          <th class="px-4 py-3">Status</th>
          <th class="px-4 py-3">Started</th>
          <th class="px-4 py-3">Finished</th>
          <th class="px-4 py-3">Duration</th>
          <th class="px-4 py-3">Items</th>
          <th class="px-4 py-3">Message</th>
        </tr>
      </thead>
      <tbody>
        <tr th:if="${#lists.isEmpty(logs)}">
          <td colspan="7" class="px-4 py-8 text-center text-gray-400 text-sm">No logs yet</td>
        </tr>
        <tr th:each="log : ${logs}"
            class="border-b last:border-0 hover:brightness-95 transition-all"
            th:classappend="${log.rowCssClass}">
          <td class="px-4 py-2.5 font-mono text-xs text-gray-600" th:text="${log.jobName}">JOB</td>
          <td class="px-4 py-2.5">
            <span th:if="${log.status != null}"
                  th:class="'inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium ' + ${log.status.badgeCss}"
                  th:text="${log.status.displayLabel}">—</span>
          </td>
          <td class="px-4 py-2.5 text-xs text-gray-600 whitespace-nowrap"
              th:text="${log.startedAt != null ? #temporals.format(log.startedAt, 'd MMM HH:mm:ss') : '—'}">—</td>
          <td class="px-4 py-2.5 text-xs text-gray-600 whitespace-nowrap"
              th:text="${log.finishedAt != null ? #temporals.format(log.finishedAt, 'd MMM HH:mm:ss') : '—'}">—</td>
          <td class="px-4 py-2.5 text-xs text-gray-500" th:text="${log.durationFormatted}">—</td>
          <td class="px-4 py-2.5 text-xs text-gray-500" th:text="${log.itemsProcessed}">0</td>
          <td class="px-4 py-2.5 text-xs text-gray-600 max-w-xs truncate"
              th:text="${log.message}"
              th:title="${log.errorDetail}">—</td>
        </tr>
      </tbody>
    </table>
  </div>

</th:block>
</body>
</html>
```

- [ ] **Step 2: Add sidebar link in `admin/layout.html`**

In `admin/layout.html`, locate the "System" section (the `<div class="mt-4 mb-2 px-3 ...">System</div>` block). Insert the Schedulers link between the `DB Backup` entry and the `Communities` entry:

```html
      <a th:href="@{/admin/schedulers}"
         th:classappend="${currentUri.startsWith('/admin/schedulers')} ? ' bg-admin-mid text-white' : ' text-green-200 hover:bg-admin-mid hover:text-white'"
         class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors duration-150">
        <svg class="w-5 h-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/>
        </svg>
        Schedulers
      </a>
```

- [ ] **Step 3: Run full test suite**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw test -q 2>&1 | tail -20
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/admin/schedulers.html \
        src/main/resources/templates/admin/layout.html
git commit -m "feat: scheduler monitor admin page — cards, log table, Run Now buttons, sidebar link"
```

---

## Final Verification

- [ ] **Run all tests one more time**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw test -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS

- [ ] **Verify app compiles and starts (quick smoke test)**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw compile -q && echo "Compile OK"
```

Expected: `Compile OK`
