# Round-Level Prediction Window Control

## Problem

The prediction window (open/close) is currently managed per-match, but predictions are submitted per-round (all-or-nothing). This creates a mismatch: admins must click "Open Window" on every individual match in a round, which is tedious, error-prone, and semantically wrong.

## Solution

Replace per-match prediction window fields with a `RoundWindow` entity keyed by `roundLabel`. Rounds open/close automatically based on kickoff times, with admin override capability.

## Data Model

### New Entity: `RoundWindow`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT PK | Auto-increment |
| `round_label` | VARCHAR(50) UNIQUE NOT NULL | Matches `Match.roundLabel` |
| `override_status` | VARCHAR(20) NULL | `FORCE_OPEN`, `FORCE_CLOSED`, or NULL (automatic) |
| `auto_opens_at` | TIMESTAMP | First match kickoff - 24h (computed on sync/creation) |
| `auto_closes_at` | TIMESTAMP | Last match kickoff - 1h (computed on sync/creation) |
| `created_at` | TIMESTAMP NOT NULL | |
| `updated_at` | TIMESTAMP NOT NULL | |

### New Enum: `RoundOverrideStatus`

```java
public enum RoundOverrideStatus {
    FORCE_OPEN,
    FORCE_CLOSED
}
```

### Remove from `Match`

- `prediction_window_open` (column + field)
- `prediction_window_opens_at` (column + field)
- `prediction_window_closes_at` (column + field)

### DB Migration (V7)

Uses the SQLite table-rebuild pattern (consistent with V6) since SQLite doesn't support `ALTER TABLE DROP COLUMN`.

```sql
-- 1. Create round_windows table
CREATE TABLE round_windows (
    id                 INTEGER PRIMARY KEY,
    round_label        VARCHAR(50)  NOT NULL UNIQUE,
    override_status    VARCHAR(20),
    auto_opens_at      TIMESTAMP,
    auto_closes_at     TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Populate from existing match data
INSERT INTO round_windows (round_label, auto_opens_at, auto_closes_at)
SELECT m.round_label,
       DATETIME(MIN(m.kickoff_time), '-24 hours'),
       DATETIME(MAX(m.kickoff_time), '-1 hours')
FROM matches m
WHERE m.round_label IS NOT NULL
GROUP BY m.round_label;

-- 3. Rebuild matches table without prediction window columns (SQLite pattern)
CREATE TABLE matches_new (
    -- all existing columns EXCEPT prediction_window_open,
    -- prediction_window_opens_at, prediction_window_closes_at
    -- (full column list in implementation)
);

INSERT INTO matches_new (...) SELECT ... FROM matches;
DROP TABLE matches;
ALTER TABLE matches_new RENAME TO matches;

-- 4. Recreate all matches indexes (excluding the dropped prediction_window_idx)
```

## Round Window Logic

### `RoundWindowService`

Central service for determining round open/closed status:

```java
public boolean isRoundOpen(String roundLabel, LocalDateTime now) {
    RoundWindow rw = roundWindowRepository.findByRoundLabel(roundLabel)
            .orElse(null);
    if (rw == null) return false;
    if (rw.getOverrideStatus() == RoundOverrideStatus.FORCE_OPEN) return true;
    if (rw.getOverrideStatus() == RoundOverrideStatus.FORCE_CLOSED) return false;
    return !now.isBefore(rw.getAutoOpensAt()) && now.isBefore(rw.getAutoClosesAt());
}
```

### Prediction Submission Guard

Two-level check:
1. Round must be open (via `RoundWindowService.isRoundOpen`)
2. Individual match must not have kicked off yet (kickoff - 1h cutoff, existing logic)

## Affected Components

### Service Layer

| File | Changes |
|------|---------|
| **New: `RoundWindowService`** | `isRoundOpen()`, `openRound()`, `closeRound()`, `resetOverride()`, `recalculateAutoTimes()` |
| **New: `RoundWindowRepository`** | `findByRoundLabel()`, `findAll()` |
| `MatchAdminService` | Remove `setPredictionWindowOpen()`, `findByPredictionWindowOpen()`. Add round-level admin methods that delegate to `RoundWindowService`. |
| `PredictionService` | `isWindowOpen()` delegates to `RoundWindowService.isRoundOpen(match.getRoundLabel())` |
| `PredictionViewService` | `getRoundSummaries()` uses `RoundWindowService` for OPEN status. `submitPredictionsForRound()` checks round open + per-match kickoff cutoff. |
| `MatchServiceImpl` | `getNextPredictableMatch()` and `getOpenMatchCount()` query matches in open rounds |

### Controller Layer

| File | Changes |
|------|---------|
| `AdminMatchController` | Remove `/{id}/open-window` and `/{id}/close-window` endpoints. Add `POST /admin/matches/rounds/{roundLabel}/open`, `/close`, `/reset`. Restructure `listMatches()` to group by round. |

### Repository Layer

| File | Changes |
|------|---------|
| `MatchRepository` | Remove `findOpenPredictionWindows()`, `findMatchesWhereWindowShouldOpen()`, `findMatchesWhereWindowShouldClose()`, `findByPredictionWindowOpen()` |
| `PredictionRepository` | `countPendingForOpenWindows` query rewritten to join `RoundWindow` |

### Domain

| File | Changes |
|------|---------|
| `Match.java` | Remove `predictionWindowOpen`, `predictionWindowOpensAt`, `predictionWindowClosesAt` fields |
| **New: `RoundWindow.java`** | JPA entity for `round_windows` table |
| **New: `RoundOverrideStatus.java`** | Enum: `FORCE_OPEN`, `FORCE_CLOSED` |

### Scheduler

| File | Changes |
|------|---------|
| `NotificationScheduler` | `checkPredictionWindowOpen()` uses `RoundWindowService` to detect newly-open rounds instead of per-match queries. `checkPredictionDeadline()` checks round open status instead of `match.isPredictionWindowOpen()`. |

### Templates

| File | Changes |
|------|---------|
| `admin/matches.html` | Restructure from flat table to grouped-by-round with collapsible sections. Each section header has round status badge + Open/Close/Reset buttons. Per-match rows keep Enter Result and Reminder actions. |
| `admin/dashboard.html` | Replace per-match window badge with round-level status |

### Tests

| File | Changes |
|------|---------|
| `MatchRepositoryTest` | Update `findOpenPredictionWindows` test to use `RoundWindow` |
| `PredictionRepositoryTest` | Remove `predictionWindowOpen` from test match builders |
| `PredictionServiceTest` | Update `isWindowOpen` tests to use `RoundWindowService` |
| `AdminMatchControllerTest` | Update open/close window tests to round-level |
| `NotificationSchedulerTest` | Update to use round-level window checks |
| **New: `RoundWindowServiceTest`** | Unit tests for auto/override logic |

### Audit Actions

Add to `AuditAction` enum:
- `ROUND_WINDOW_OPENED` (admin force-opened a round)
- `ROUND_WINDOW_CLOSED` (admin force-closed a round)
- `ROUND_WINDOW_RESET` (admin reset to automatic)

Remove or deprecate:
- `PREDICTION_WINDOW_OPENED`
- `PREDICTION_WINDOW_CLOSED`

## Admin UI Design

Matches page grouped by round with collapsible sections (Alpine.js):

```
[v] Group Stage MD1  (12 matches)  [OPEN]  [Close Round]  [Send Reminder]
  -----------------------------------------------------------------------
  | Match               | Kickoff       | Result  | Actions             |
  | Germany vs Scotland | 14 Jun 21:00  | Pending | [Enter Result]      |
  | Hungary vs Switz.   | 15 Jun 15:00  | Pending | [Enter Result]      |
  ...

[>] Group Stage MD2  (12 matches)  [FUTURE]  [Open Round]
  (collapsed)

[>] Round of 32  (8 matches)  [FUTURE]  [Open Round]
  (collapsed)
```

Round status badge logic:
- `PAST`: all matches completed -> gray badge
- `OPEN`: round window is open (auto or override) -> green badge
- `FUTURE`: round window not yet open -> blue badge
- `FORCE_OPEN`/`FORCE_CLOSED`: show override indicator (e.g., yellow border)

## Edge Cases

1. **Match added to existing round after sync**: `RoundWindowService.recalculateAutoTimes(roundLabel)` must be called after match sync to update `autoOpensAt`/`autoClosesAt`.
2. **Round with all matches completed**: Status is PAST regardless of override. Override is ignored.
3. **Empty round** (all matches cancelled): Round is not shown.
4. **Admin closes round while users are mid-submission**: Submission fails with clear error message.
5. **Send Reminder**: Moves to round-level in the UI. Sends a single reminder per user listing all unpredicted matches in the round, rather than one reminder per match.
