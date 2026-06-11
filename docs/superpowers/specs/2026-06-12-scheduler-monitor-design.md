# Scheduler Monitor — Admin Page

**Date:** 2026-06-12
**Scope:** New admin page showing scheduler execution history, job status, next-run times, and enabled/disabled state for all 7 scheduled jobs.

## Problem

No visibility into whether scheduled jobs are actually running in production. Failures are only visible in server logs, which are not easily accessible. Need an admin UI to confirm jobs are firing, see errors, and check next scheduled runs.

## Job Inventory

7 distinct job types (5 schedulers, with NotificationScheduler split into 3 sub-jobs):

| Job Name | Scheduler Class | Method | Schedule | Enabled By |
|----------|----------------|--------|----------|------------|
| `MATCH_RESULT` | `MatchResultScheduler` | `syncAndScore` | `fixedDelay = 300_000` (5 min) | `app.football.api.enabled` |
| `LINEUP_SYNC` | `LineupSyncScheduler` | `syncLineups` | `fixedDelay = 1_800_000` (30 min) | `app.football.api.enabled` |
| `STANDING_SYNC` | `StandingSyncScheduler` | `syncStandings` | `cron = 0 0 */6 * * *` (every 6h) | `app.football.api.enabled` |
| `SCORERS_SYNC` | `ScorersSyncScheduler` | `syncScorers` | `cron = 0 0 2 * * *` (daily 2 AM) | `app.football.api.enabled` |
| `NOTIF_WINDOW_OPEN` | `NotificationScheduler` | `checkPredictionWindowOpen` | `fixedDelay = 300_000` (5 min) | `app.notification.enabled` |
| `NOTIF_DEADLINE` | `NotificationScheduler` | `checkPredictionDeadline` | `fixedDelay = 900_000` (15 min) | `app.notification.enabled` |
| `NOTIF_DIGEST` | `NotificationScheduler` | `checkLeaderboardDigest` | `fixedDelay = 1_800_000` (30 min) | `app.notification.enabled` |

## Architecture

### 1. Database Table — `scheduler_log`

Single table storing one row per job execution. Flyway migration `V10__scheduler_log.sql`.

```sql
CREATE TABLE scheduler_log (
    id              BIGINT PRIMARY KEY,
    job_name        VARCHAR(30) NOT NULL,
    status          VARCHAR(10) NOT NULL,
    started_at      TIMESTAMP NOT NULL,
    finished_at     TIMESTAMP,
    items_processed INTEGER DEFAULT 0,
    message         VARCHAR(500),
    error_detail    VARCHAR(2000)
);

CREATE INDEX idx_scheduler_log_job_name ON scheduler_log (job_name);
CREATE INDEX idx_scheduler_log_started_at ON scheduler_log (started_at);
```

**ID generation:** Use `@GeneratedValue(strategy = IDENTITY)` consistent with the rest of the project.

**Status enum:** `SUCCESS`, `SKIPPED`, `FAILED` — stored as string.

**Retention:** A cleanup `@Scheduled` job (`fixedDelay = 86_400_000`, daily) deletes rows where `started_at < now - 7 days`.

### 2. Domain

#### `SchedulerLog` entity

JPA entity mapping to `scheduler_log`. Fields: `id`, `jobName` (String), `status` (enum `SchedulerJobStatus`), `startedAt`, `finishedAt`, `itemsProcessed`, `message`, `errorDetail`.

#### `SchedulerJobStatus` enum

```java
public enum SchedulerJobStatus { SUCCESS, SKIPPED, FAILED }
```

#### `SchedulerJobType` enum

Holds metadata for all 7 jobs: display name, schedule description, schedule expression (for next-run calculation), whether it's fixedDelay or cron, the delay millis (for fixedDelay), and the property name that controls enabled/disabled.

```java
public enum SchedulerJobType {
    MATCH_RESULT("Match Results", "Every 5 min", true, 300_000, null, "app.football.api.enabled"),
    LINEUP_SYNC("Lineup Sync", "Every 30 min", true, 1_800_000, null, "app.football.api.enabled"),
    STANDING_SYNC("Standings", "Every 6 hours", false, 0, "0 0 */6 * * *", "app.football.api.enabled"),
    SCORERS_SYNC("Top Scorers", "Daily at 02:00", false, 0, "0 0 2 * * *", "app.football.api.enabled"),
    NOTIF_WINDOW_OPEN("Window Open Notif", "Every 5 min", true, 300_000, null, "app.notification.enabled"),
    NOTIF_DEADLINE("Deadline Reminder", "Every 15 min", true, 900_000, null, "app.notification.enabled"),
    NOTIF_DIGEST("Leaderboard Digest", "Every 30 min", true, 1_800_000, null, "app.notification.enabled");

    // fields: displayName, scheduleDescription, isFixedDelay, delayMs, cronExpression, enabledProperty
}
```

### 3. Repository

`SchedulerLogRepository extends JpaRepository<SchedulerLog, Long>`:
- `List<SchedulerLog> findByJobNameOrderByStartedAtDesc(String jobName)` — for per-job history
- `Optional<SchedulerLog> findFirstByJobNameOrderByStartedAtDesc(String jobName)` — latest entry per job (for next-run calculation)
- `List<SchedulerLog> findAllByOrderByStartedAtDesc()` — full history for the table
- `List<SchedulerLog> findByJobNameAndStatusOrderByStartedAtDesc(String jobName, SchedulerJobStatus status)` — filtered
- `void deleteByStartedAtBefore(LocalDateTime cutoff)` — cleanup
- Filter queries as needed for the combination filters (job name, status, or both)

### 4. Service — `SchedulerLogService`

Provides two operations used by all schedulers:

```java
@Service
public class SchedulerLogService {
    SchedulerLog start(String jobName);           // creates row with STARTED status, sets startedAt
    void complete(SchedulerLog log, SchedulerJobStatus status, int itemsProcessed, String message);
    void fail(SchedulerLog log, String message, String errorDetail);
    void cleanup();                                // delete rows older than 7 days
    Optional<SchedulerLog> findLatest(String jobName);
    List<SchedulerLog> findAll(String jobNameFilter, SchedulerJobStatus statusFilter);
}
```

The `start()` method persists a row immediately (so we can see "in progress" jobs). The `complete()`/`fail()` methods update `finishedAt`, `status`, `itemsProcessed`, `message`, `errorDetail`.

### 5. Scheduler Modifications

Each of the 5 scheduler classes gets modified to call `SchedulerLogService.start()` at the beginning and `complete()`/`fail()` at the end. Pattern:

```java
public void syncAndScore() {
    SchedulerLog log = logService.start("MATCH_RESULT");
    try {
        if (!syncService.hasActionableMatches()) {
            logService.complete(log, SKIPPED, 0, "No actionable matches");
            return;
        }
        List<Long> finished = syncService.syncResults();
        logService.complete(log, SUCCESS, finished.size(),
                finished.isEmpty() ? "No new results" : finished.size() + " match(es) scored");
    } catch (Exception e) {
        logService.fail(log, e.getMessage(), truncate(stackTrace(e), 2000));
    }
}
```

For `NotificationScheduler`, each of the 3 methods gets its own job name (`NOTIF_WINDOW_OPEN`, `NOTIF_DEADLINE`, `NOTIF_DIGEST`).

### 6. Cleanup Scheduler

Add cleanup logic to `SchedulerLogService` or a dedicated small scheduler:

```java
@Scheduled(fixedDelay = 86_400_000) // daily
public void cleanup() {
    logRepository.deleteByStartedAtBefore(LocalDateTime.now().minusDays(7));
}
```

This can live in a new `SchedulerLogCleanupScheduler` or inside `SchedulerLogService` with `@Scheduled`. The cleanup job itself does NOT log to the scheduler_log table (to avoid infinite recursion).

### 7. Controller — `AdminSchedulerController`

```java
@Controller
@RequestMapping("/admin/schedulers")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminSchedulerController {

    @GetMapping
    public String schedulerPage(
            @RequestParam(required = false) String job,
            @RequestParam(required = false) String status,
            Model model) {
        // 1. Build job summary cards (one per SchedulerJobType)
        // 2. Query filtered log history
        // 3. Add to model
        return "admin/schedulers";
    }
}
```

**Job summary card data** (per job type):
- `displayName` — from enum
- `scheduleDescription` — from enum (e.g. "Every 5 min")
- `enabled` — read from Spring `Environment` using the enum's `enabledProperty`
- `lastRun` — from latest `SchedulerLog` entry
- `lastStatus` — from latest entry's status (for colored dot)
- `nextRun` — computed:
  - For `fixedDelay`: `lastFinishedAt + delayMs`. If never run: "Pending".
  - For cron: `CronExpression.parse(expr).next(lastFinishedAt)` using Spring's `CronExpression`. If never run: next occurrence from now.
  - If disabled: "Disabled".

### 8. Template — `admin/schedulers.html`

Extends `admin/layout`. Two sections:

**Section 1: Job Status Cards**

A responsive grid (2-3 columns) of cards, one per job type. Each card:
- Job display name (bold)
- Enabled/Disabled badge (green/red small pill)
- Schedule description (gray text, e.g. "Every 5 min")
- Next run time (formatted, or "Disabled" in gray)
- Last status colored dot: green = SUCCESS, gray = SKIPPED, red = FAILED, no dot if never run

**Section 2: Execution Log Table**

Filter bar at top:
- Dropdown: job name (All / Match Results / Lineup Sync / ... ) 
- Dropdown: status (All / Success / Skipped / Failed)
- Filters applied via form GET params, server-side filtering

Table columns: Job | Status | Started | Finished | Duration | Items | Message

Row coloring:
- `SUCCESS` with items > 0: light green background (`bg-green-50`)
- `SKIPPED` (items = 0, no error): light gray background (`bg-gray-50`)
- `FAILED`: light red background (`bg-red-50`)

For FAILED rows, the `message` column shows the error message. A click/expand could show `errorDetail` (stack trace) if present — or just show the first line with a title tooltip containing the full detail.

### 9. Sidebar Link

Add "Schedulers" link to `admin/layout.html` under the "System" section, between "DB Backup" and "Communities". Icon: a clock or timer icon (SVG).

URL: `/admin/schedulers`

### 10. Security

The page is protected by `@PreAuthorize("hasRole('SUPER_ADMIN')")` like other admin controllers. The `/admin/schedulers` path is already covered by the existing security config that protects `/admin/**`.

## Files Changed

| File | Change |
|------|--------|
| `src/main/resources/db/migration/V10__scheduler_log.sql` | New migration |
| `src/main/java/.../domain/SchedulerLog.java` | New entity |
| `src/main/java/.../domain/enums/SchedulerJobStatus.java` | New enum |
| `src/main/java/.../domain/enums/SchedulerJobType.java` | New enum with job metadata |
| `src/main/java/.../repository/SchedulerLogRepository.java` | New repository |
| `src/main/java/.../service/SchedulerLogService.java` | New service |
| `src/main/java/.../scheduler/MatchResultScheduler.java` | Add logging calls |
| `src/main/java/.../scheduler/LineupSyncScheduler.java` | Add logging calls |
| `src/main/java/.../scheduler/StandingSyncScheduler.java` | Add logging calls |
| `src/main/java/.../scheduler/ScorersSyncScheduler.java` | Add logging calls |
| `src/main/java/.../scheduler/NotificationScheduler.java` | Add logging calls (3 methods) |
| `src/main/java/.../scheduler/SchedulerLogCleanupScheduler.java` | New — weekly cleanup |
| `src/main/java/.../controller/admin/AdminSchedulerController.java` | New controller |
| `src/main/resources/templates/admin/schedulers.html` | New template |
| `src/main/resources/templates/admin/layout.html` | Add sidebar link |

## Testing

- `SchedulerLogServiceTest` — unit test: start/complete/fail lifecycle, cleanup deletes old rows
- `MatchResultSchedulerTest` — update existing test to verify logging calls
- `AdminSchedulerControllerTest` — mock-mvc test: page loads, filters work
- All existing scheduler tests updated to mock `SchedulerLogService`
