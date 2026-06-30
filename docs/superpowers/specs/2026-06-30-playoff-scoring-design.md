# Playoff Scoring & Manual Result Protection — Design Spec

**Date:** 2026-06-30  
**Status:** Approved  
**Scope:** Full — manual result protection, 90-min/final result split, playoff winner prediction

---

## Problem Statement

Two related problems in the current system:

1. **API overwrite**: When an admin manually enters a 90-min result for a knockout match (e.g., 1-1 after 90 mins), the football-data.org API sync overwrites it with the full-time score including extra time (e.g., 2-1 after ET). Predictions then get scored against the wrong number.

2. **Playoff scoring gap**: The `homeScore90`/`awayScore90` and `resultEnteredAt`/`resultEnteredBy` fields already exist on `Match` but are never written. There is no mechanism for users to predict the playoff winner when their predicted score is a draw.

---

## Solution Overview

- **Source tracking**: A new `resultSource` enum on `Match` marks whether 90-min scores came from the API or were admin-entered. API sync respects this flag and never overwrites manually-entered 90-min scores.
- **Dual result storage**: API populates full-time result (`homeScore`/`awayScore`); 90-min result (`homeScore90`/`awayScore90`) comes from API's `score.regularTime` field or admin entry. Scoring uses 90-min for knockout matches.
- **Playoff winner prediction**: When a user predicts a draw on a knockout match, a dynamic winner picker appears. A correct pick earns +1 bonus point (max 3 pts total: 2 for exact draw + 1 for winner).
- **Runtime config**: A super admin toggle controls whether knockout scoring uses 90-min or full-time result.

---

## Data Model

### Match — new fields

| Field | Type | Description |
|-------|------|-------------|
| `resultSource` | `ResultSource` enum (nullable) | `MANUAL` when admin entered 90-min scores; `API` when API last set them. Null = not yet completed. |
| `playoffWinner` | `PlayoffWinner` enum (nullable) | Who won after ET/penalties: `HOME_WIN` or `AWAY_WIN`. Set by API from `score.winner`, or by admin. Only relevant for knockout draws. |

**Existing fields activated (previously written by neither path):**
- `homeScore90` / `awayScore90` — 90-min result. Now written by API from `score.regularTime` (when available and `resultSource != MANUAL`) or by admin.
- `resultEnteredAt` / `resultEnteredBy` — populated on admin 90-min result entry.

**New enums:**
- `ResultSource`: `MANUAL`, `API`
- `PlayoffWinner`: `HOME_WIN`, `AWAY_WIN`

### Prediction — new field

| Field | Type | Description |
|-------|------|-------------|
| `predictedPlayoffWinner` | `PlayoffWinnerPick` enum (nullable) | `HOME` or `AWAY`. Set when user predicts a draw on a knockout match and picks a winner. Null otherwise. |

**New enum:** `PlayoffWinnerPick`: `HOME`, `AWAY`

### PredictionScore enum — new value

| Value | Points | Description |
|-------|--------|-------------|
| `EXACT_DRAW_WINNER` | 3 | Exact draw score + correct playoff winner pick |

Existing values unchanged: `EXACT` (3), `CORRECT_DRAW` (2), `CORRECT_WINNER` (1), `WRONG` (0), `PENDING` (0).

### DB Migrations

- **V18**: Add `result_source VARCHAR` and `playoff_winner VARCHAR` to `matches`
- **V19**: Add `predicted_playoff_winner VARCHAR` to `predictions`

---

## API Sync Behavior

`FootballApiSyncService.syncResults()` updated logic for each finished match:

1. **Always update** `homeScore` / `awayScore` from `score.fullTime` (final result with ET)
2. **Always update** `playoffWinner` from `score.winner` (`HOME_TEAM` → `HOME_WIN`, `AWAY_TEAM` → `AWAY_WIN`)
3. **Update `homeScore90` / `awayScore90`** only when `resultSource != MANUAL`:
   - If `score.regularTime` is non-null → use those values
   - If `score.regularTime` is null (match decided in 90 mins, no ET) → copy from `homeScore` / `awayScore`
4. When API updates 90-min scores, set `resultSource = API`

---

## Admin UI

### Knockout match result entry form

**Section A — 90-min Result** (drives prediction scoring):
- Home score input / Away score input
- If home == away (draw): **Playoff Winner** radio appears (`[Home Team]` / `[Away Team]`)
- Submit writes: `homeScore90`, `awayScore90`, `playoffWinner`, `resultSource = MANUAL`, `resultEnteredAt`, `resultEnteredBy`
- Triggers `scoreAllPredictions(matchId)`
- **Unlock button**: clears `resultSource` so API can re-sync 90-min scores

**Section B — Final Result** (read-only, informational):
- Displays `homeScore` / `awayScore` as populated by API
- Shows context label if applicable: "After extra time" / "After penalties"

### Group stage matches
Form unchanged — single score pair, no 90-min/final split.

### Service change
`MatchAdminService.setResult()` replaced by `set90MinResult(matchId, home90, away90, playoffWinner, adminUser)`.

---

## User Prediction UI

### Knockout match prediction form

- Score inputs unchanged
- **Winner picker**: shown dynamically when `predictedHome == predictedAway`
  - Label: `"Who wins? [Home Team] / [Away Team]"`
  - Two radio buttons (HOME / AWAY)
  - Hides and clears `predictedPlayoffWinner` when scores change to non-draw
  - Implemented via small JS `input` event listener — no page reload
- On submit: `predictedPlayoffWinner` included in form payload (null if not shown)

### Group stage
Unchanged — no winner picker.

### Prediction display (post-match)

For knockout matches, prediction cards show the winner pick outcome:
- `EXACT_DRAW_WINNER`: "1-1 + Real Madrid ✓" — 3 pts
- `CORRECT_DRAW` (with wrong/no pick): "1-1 + Barcelona ✗" or "1-1" — 2 pts
- All other outcomes: existing display unchanged

---

## Scoring Logic

### Updated `ScoringService`

New method: `calculatePlayoffWinnerBonus(match, prediction)` → returns 0 or 1.

Bonus is awarded when ALL conditions are true:
1. Match is a knockout stage
2. `match.homeScore90 == match.awayScore90` (90-min draw)
3. `prediction.predictedHome == prediction.predictedAway` (user predicted draw)
4. `prediction.predictedHome == match.homeScore90` (exact score match)
5. `prediction.predictedPlayoffWinner` matches `match.playoffWinner`

### Updated scoring flow (`MatchAdminService.scoreAllPredictions`)

1. Determine effective scores based on config (90-min or full-time — see Configuration)
2. Call existing `calculatePoints()` → base points (0–3)
3. If base points == 2 (CORRECT_DRAW): call `calculatePlayoffWinnerBonus()` → add 0 or 1
4. Determine `scoreResult`:
   - 3 pts from exact score → `EXACT`
   - 3 pts from draw + winner → `EXACT_DRAW_WINNER`
   - 2 pts → `CORRECT_DRAW`
   - 1 pt → `CORRECT_WINNER`
   - 0 pts → `WRONG`

### Point totals
| Scenario | Points | scoreResult |
|----------|--------|-------------|
| Exact non-draw score (e.g., 2-1 predicted, 2-1 result) | 3 | `EXACT` |
| Exact draw score + correct winner | 3 | `EXACT_DRAW_WINNER` |
| Exact draw score + wrong/no winner pick | 2 | `CORRECT_DRAW` |
| Correct winner (non-exact) | 1 | `CORRECT_WINNER` |
| Wrong | 0 | `WRONG` |

---

## Configuration

A new toggle on the **super admin settings page**:

- **Label**: "Knockout scoring mode"
- **Options**: `90-min result (default)` / `Full-time result (incl. ET)`
- **Stored**: in the existing app settings DB table (same pattern as round lock offset)
- **Read by**: `ScoringService` at scoring time — runtime change, no restart
- When full-time mode is active: playoff winner bonus is disabled (noted in admin UI)

No per-community granularity.

---

## Pages to Update

All pages that display prediction outcomes or scoring must handle `EXACT_DRAW_WINNER`:

| Page | Change |
|------|--------|
| **Leaderboard** | `EXACT_DRAW_WINNER` counts as 3 pts — already correct by points; add badge/label distinction |
| **Exact score heroes** | Include `EXACT_DRAW_WINNER` alongside `EXACT` |
| **Playoff bracket/standings** | Show draw result + winner pick badge per user |
| **Rules page** | New section: knockout scoring explanation — 90-min only, max 3 pts (exact OR draw+winner) |
| **Prediction history/detail** | Show `predictedPlayoffWinner` and whether it was correct |

---

## Out of Scope

- Per-community knockout scoring mode
- Predicting method of victory (ET vs penalties)
- Retroactive rescoring of historical matches when config mode is toggled
- Automatic rescoring when admin unlocks a match — if unlocked and API re-syncs 90-min scores, admin must manually re-trigger scoring from the match admin page
