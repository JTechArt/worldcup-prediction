# Daily Prediction Windows Design

**Date:** 2026-06-12  
**Status:** Approved  

## Problem

The current system uses a single `RoundWindow` per matchday (e.g., "Matchday 1"). With 8+ matches spread across multiple days, participants must predict all of them at once or miss the window entirely. Participants want to predict only the next batch of matches — e.g., today's 3 matches — so they can give each match proper thought.

## Goals

- Support a new **DAILY mode** where prediction windows cover a smaller batch of matches (typically one day's worth)
- Keep the existing **ROUND mode** fully functional and unchanged
- Let super admins switch between modes at any time during the tournament
- Allow community-level overrides of the global mode
- Auto-generate daily windows with a preview UI; admin confirms before going live
- Community admins can create per-community window overrides with different times

## Non-Goals

- Replacing or breaking existing round window logic
- Per-user or per-match granularity (windows are still batch-level)
- Automatic window generation without admin confirmation

---

## Architecture Overview

The system adds a first-class `PredictionWindow` entity for DAILY mode alongside the existing `RoundWindow` for ROUND mode. A new `TournamentSettings` singleton controls the active mode globally. `isWindowOpen()` becomes mode-aware, delegating to the appropriate service.

```
TournamentSettings.windowMode
        │
        ├─ ROUND → RoundWindowService (unchanged)
        │
        └─ DAILY → PredictionWindowService
                        │
                        ├─ find PredictionWindow containing match
                        ├─ check community override first
                        └─ apply overrideStatus → time range
```

---

## Data Model

### New table: `tournament_settings` (singleton)

| Column | Type | Notes |
|---|---|---|
| `id` | PK | always 1 row |
| `window_mode` | enum `ROUND/DAILY` | global default mode |
| `daily_window_close_offset_minutes` | int | default 30 — subtracted from first match kickoff when no explicit close time set |
| `updated_at` | timestamp | |

### Extended: `community`

| Column | Type | Notes |
|---|---|---|
| `window_mode_override` | enum `ROUND/DAILY` nullable | null = inherit global setting |

### New table: `prediction_window`

| Column | Type | Notes |
|---|---|---|
| `id` | PK | |
| `label` | varchar | display name, e.g. "June 14 Matches" |
| `open_at` | timestamp | when window opens |
| `close_at` | timestamp nullable | explicit close time; null = auto-calculate |
| `effective_close_at` | timestamp nullable | computed and stored when window transitions to OPEN |
| `override_status` | enum `FORCE_OPEN/FORCE_CLOSED` nullable | manual admin override |
| `status` | enum `DRAFT/SCHEDULED/OPEN/CLOSED` | lifecycle state |
| `community_id` | FK nullable | null = global; non-null = community override |
| `created_by_id` | FK user | |
| `created_at` | timestamp | |
| `updated_at` | timestamp | |

### New table: `prediction_window_match` (join)

| Column | Type | Notes |
|---|---|---|
| `window_id` | FK `prediction_window.id` | |
| `match_id` | FK `match.id` | |
| PK on `(window_id, match_id)` | | |

### Extended: `round_submission`

| Column | Type | Notes |
|---|---|---|
| `prediction_window_id` | FK `prediction_window.id` nullable | null in ROUND mode; set in DAILY mode |

`round_label` remains for ROUND mode. Both columns coexist; the active mode determines which is used for validation and grouping.

---

## Window Lifecycle

```
DRAFT → SCHEDULED → OPEN → CLOSED
```

| State | Meaning |
|---|---|
| `DRAFT` | Created, not yet published. Invisible to participants. |
| `SCHEDULED` | Published. Will auto-open at `open_at`. |
| `OPEN` | Accepting predictions. `effective_close_at` computed and stored on transition. |
| `CLOSED` | No more predictions. Predictions become visible to participants. |

**`effective_close_at` calculation** (computed at SCHEDULED → OPEN transition):
- If `close_at` is set → use it directly
- Otherwise → `MIN(kickoffTime of included matches) - daily_window_close_offset_minutes`

**Override behavior:**
- `FORCE_OPEN` → window treated as OPEN regardless of times
- `FORCE_CLOSED` → window treated as CLOSED regardless of times
- Reset → clears override, reverts to time-based logic

Community override windows follow the same lifecycle independently.

---

## `isWindowOpen()` Logic

```java
// pseudocode
boolean isWindowOpen(Match match, Instant now, Long communityId) {
    WindowMode mode = getEffectiveMode(communityId);  // override ?? global
    
    if (mode == ROUND) {
        return roundWindowService.isRoundOpen(match.getRoundLabel(), now);  // unchanged
    }
    
    // DAILY mode
    PredictionWindow window = findEffectiveWindow(match, communityId);
    // community override first, fallback to global
    
    if (window == null) return false;  // no window configured — block gracefully
    if (window.overrideStatus == FORCE_OPEN) return true;
    if (window.overrideStatus == FORCE_CLOSED) return false;
    if (window.status != OPEN) return false;
    return now >= window.openAt && now < window.effectiveCloseAt;
}
```

---

## Submission Validation

**ROUND mode** (unchanged): all submitted matches must belong to the same `roundLabel`. `RoundSubmission` populated with `round_label`.

**DAILY mode**: all submitted matches must belong to the same `PredictionWindow` (by ID). `RoundSubmission` populated with `prediction_window_id`. If no active window exists for the matches, submission is blocked with a clear error message.

Switching modes mid-tournament has no effect on existing `RoundSubmission` records — each record retains whichever field was populated at submission time. No data migration required on mode switch.

---

## Scheduler

### New: `PredictionWindowScheduler`

Follows the same pattern as existing schedulers. Runs every 5 minutes (`@Scheduled(fixedDelay = 300_000)`). Controlled by a new `app.prediction-window.enabled` feature flag (same pattern as existing schedulers).

**Job 1: `activateScheduledWindows()`**
- Finds all `SCHEDULED` global windows where `open_at <= now`
- Transitions each to `OPEN`
- Computes and stores `effective_close_at`
- Repeats for community override windows

**Job 2: `closeExpiredWindows()`**
- Finds all `OPEN` windows where `effective_close_at <= now` and `override_status != FORCE_OPEN`
- Transitions each to `CLOSED`

**Notification integration:** `NotificationScheduler.checkPredictionWindowOpen()` is extended to also detect newly-opened `PredictionWindow` records in DAILY mode, triggering the same participant email notifications as today.

---

## Admin UI

### Super Admin: Tournament Settings (`/admin/settings`)

- Toggle global `windowMode` (ROUND / DAILY) — takes effect immediately
- Set `daily_window_close_offset_minutes` (default 30, shown as "Auto-close X minutes before first match")

### Super Admin: Daily Windows (`/admin/prediction-windows`)

**List page** — table of all global `PredictionWindow` records showing:
- Label, status badge, open/close times, match count, actions (Edit, Force Open, Force Close, Delete)

**Generate window form:**
- Admin inputs a date range (date + time start/end)
- System queries matches with `kickoffTime` in range
- Pre-fills:
  - `label` (auto-suggested from date)
  - `openAt` (configurable default, e.g. 10:00 in system timezone)
  - `closeAt` (left empty → will auto-calculate)
- **Match timeline preview:** shows all matches included in the window, plus the nearest match before and after the range boundary — so admin can see if times need adjusting before saving
- Save as DRAFT → admin publishes separately (DRAFT → SCHEDULED)

**Edit window form:** same form + timeline preview; window must be in DRAFT or SCHEDULED state to edit times and matches.

**Override controls:** FORCE_OPEN / FORCE_CLOSED / Reset — same UX as existing round window overrides.

### Community Admin: Window Settings (`/c/{slug}/admin/window-settings`)

- Shows current effective mode with option to override (Inherit global / ROUND / DAILY)
- Shows global `PredictionWindow` list (read-only reference)
- "Create override" — same form as super admin generate, but scoped to this community; produces a `PredictionWindow` record with `community_id` set
- Can edit or delete community-scoped override windows

---

## Community Predictions UI Changes

**DAILY mode:** predictions page groups matches by `PredictionWindow` instead of `roundLabel`. Only OPEN and SCHEDULED windows shown as actionable. CLOSED windows collapse into "Past predictions" (same as today).

**ROUND mode:** unchanged — groups by `roundLabel`.

**Countdown banner** (`community-base.html`): sources `effectiveCloseAt` from `PredictionWindow` in DAILY mode instead of `RoundWindow.autoClosesAt`. No structural change to the banner itself.

**Submission status admin pages** (super admin matchday-status, community admin submission-status): In DAILY mode, groups submissions by `prediction_window_id` (displaying window label for readability) instead of `round_label`.

---

## Database Migrations

| Migration | Content |
|---|---|
| V10 | `tournament_settings` table with default row (`window_mode = ROUND`, `offset = 30`) |
| V11 | `prediction_window` and `prediction_window_match` tables |
| V12 | Add `window_mode_override` to `community`; add `prediction_window_id` FK to `round_submission` |

---

## New Service Classes

| Class | Responsibility |
|---|---|
| `TournamentSettingsService` | Read/write `TournamentSettings`; `getEffectiveMode(communityId)` |
| `PredictionWindowService` | CRUD for `PredictionWindow`; `isWindowOpen()`; `findEffectiveWindow()`; generate preview |
| `PredictionWindowScheduler` | Auto-activate and auto-close windows every 5 min |

### Modified Classes

| Class | Change |
|---|---|
| `PredictionService` | `isWindowOpen()` delegates to `TournamentSettingsService` → appropriate service |
| `PredictionViewService` | Groups by window ID in DAILY mode |
| `RoundSubmissionService` | Populates `prediction_window_id` in DAILY mode; validates by ID |
| `NotificationScheduler` | Extends window-open detection to `PredictionWindow` in DAILY mode |
| `CommunityWindowBannerAdvice` | Sources close time from `PredictionWindow` in DAILY mode |
| `AdminWindowBannerAdvice` | Same |

---

## Key Invariants

1. Switching `windowMode` never modifies or deletes existing `RoundWindow` or `PredictionWindow` records
2. A match can appear in at most one global `PredictionWindow` and at most one community-scoped `PredictionWindow` per community — enforced by unique constraints on `(match_id)` for global windows and `(match_id, community_id)` for community-scoped windows
3. `effective_close_at` is always set before a window becomes OPEN — never null on an OPEN window
4. Community override windows are evaluated before global windows; if no community override exists, the global window applies
5. In DAILY mode, if no `PredictionWindow` contains a given match, `isWindowOpen()` returns false (safe default)
