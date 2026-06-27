# Features v4 Design — Play-Off Stage Readiness

**Date:** 2026-06-27  
**Context:** Group stage ends 2026-06-28. Play-off (knockout) stage begins the same day. Five features needed to support the transition.

---

## Overview

Five features, one prerequisite:

1. **Knockout match sync** — prerequisite for features 3 and 5
2. **Standing page: Groups + Play Off tabs**
3. **Leaderboard: collapsible Group Stage section**
4. **Exact Score Heroes: stage filter tabs**
5. **Fixtures: knockout tab** — resolved by feature 1

---

## Feature 1: Knockout Match Sync

### Goal

Load knockout stage matches from the football-data.org API into the DB. Currently only group stage matches are synced. This unblocks the Fixtures knockout tab and the Bracket page.

### New method: `MatchSyncService.syncKnockoutMatches()`

- Calls `FootballApiClient.fetchAllMatches()` (same `MATCHES_URL` already used)
- Filters to matches where `stage != "GROUP_STAGE"`
- Non-destructive: upserts by `externalId` — never touches existing matches or predictions
- For each match:
  - Resolve `homeTeam` / `awayTeam` via the existing `resolveTeam()` method
  - If teams cannot be resolved (TBD / placeholder names), store them in `homeTeamPlaceholder` / `awayTeamPlaceholder` on the `Match` entity (fields already exist)
  - Set `roundLabel` from the API `stage` field (e.g. "Round of 32")
- Logs every distinct `stage` string returned by the API for observability

### Stage mapping (handles both naming conventions)

| API `stage` string | `MatchStage` enum |
|---|---|
| `LAST_32` / `ROUND_OF_32` | `ROUND_OF_32` |
| `LAST_16` / `ROUND_OF_16` | `ROUND_OF_16` |
| `QUARTER_FINALS` / `QUARTER_FINAL` | `QUARTER_FINAL` |
| `SEMI_FINALS` / `SEMI_FINAL` | `SEMI_FINAL` |
| `FINAL` | `FINAL` |
| `THIRD_PLACE` / `PLAY_OFF_FOR_THIRD_PLACE` | `THIRD_PLACE` |
| unknown | skipped + logged as WARN |

WC 2026 format: 32 teams advance from groups → first knockout round is Round of 32 (1/16 finals, 16 games). The `ROUND_OF_32` enum value is already present.

### Admin trigger

New button **"Sync Knockout Matches"** added to the existing admin sync panel (same UI pattern as the other sync buttons). Response shows result count: `"16 knockout matches synced"` or error message.

### Side effects resolved automatically

- **Fixtures page** — knockout filter tab calls `matchService.getKnockoutFixtures()` which filters `stage != GROUP`. Will populate once matches are in DB.
- **Bracket page** — `BracketController` queries by `MatchStage` enum values. Will render once matches are in DB.

---

## Feature 2: Standing Page — Groups + Play Off Tabs

### Route

`/groups` — unchanged. The `/bracket` route also remains unchanged.

### Controller changes (`GroupController`)

Extended to also load bracket data. Delegates to `MatchRepository.findByStageOrderByKickoffTimeAsc()` for each knockout stage (same logic as `BracketController`). Both datasets passed to the template.

New model attributes:
- `bracketByStage` — `Map<String, List<Match>>` (same structure as `BracketController`)
- `defaultTab` — `"groups"` before 2026-06-28, `"playoff"` from 2026-06-28 onwards (set via `LocalDate.now()` comparison in controller)

`gsOpenDefault` (for the leaderboard collapsible GS) is set in `CommunityLeaderboardController`, not here.

### Template changes (`groups.html`)

**Page header:** "GROUP STANDINGS" → "STANDINGS"

**Tab bar** (above the content area):
```
[ Groups ]  [ Play Off ]
```
Alpine.js state: `x-data="{ tab: '[[defaultTab]]' }"`

- Clicking **Groups** sets `tab = 'groups'`
- Clicking **Play Off** sets `tab = 'playoff'`

**Groups tab content:** existing group card grid, `x-show="tab === 'groups'"`

**Play Off tab content:** knockout bracket view, `x-show="tab === 'playoff'"`

### Bracket fragment

Extract the bracket HTML from `bracket.html` into `fragments/bracket-view.html`. Both `groups.html` (Play Off tab) and `bracket.html` include this fragment via `th:replace`. No logic duplication.

---

## Feature 3: Leaderboard — Collapsible Group Stage

### Location

`leaderboard.html` — the horizontal-scrolling phase table.

### Behaviour

- The Group Stage phase header (`.phase-gs`) gets a collapse/expand toggle button (chevron icon, right-aligned in the header cell)
- When collapsed: all GS match columns are hidden (`x-show="gsOpen"`)
- When collapsed: the GS phase header still shows, but with a `"(48 matches — click to expand)"` subtitle
- The GS phase score column (showing each user's total GS points) **remains visible** when collapsed — users can still see their group stage total
- Default state: `gsOpen = false` if `LocalDate.now() >= 2026-06-28`, else `gsOpen = true` (server passes this as a Thymeleaf boolean to Alpine init)

### Implementation

```html
<!-- phase header gets x-data and toggle -->
<div class="phase-hd-cell phase-gs" x-data="{ gsOpen: [[${gsOpenDefault}]] }">
  Group Stage
  <button @click="gsOpen = !gsOpen">...</button>
  <span x-show="!gsOpen" class="..."> (48 matches)</span>
</div>
```

GS match columns use `x-show="gsOpen"` on each column element.

---

## Feature 4: Exact Score Heroes — Stage Filter Tabs

### Location

Community home page (`/community/{slug}`) — the "Exact Score Heroes" widget.

### New HTMX endpoint

`GET /community/{slug}/heroes?stage={all|group|playoff}`

Returns the heroes fragment (just the scroll row content, not the tab bar). Default: `all`.

### New service method

`DailyExactPredictorService.getCumulativeHeroes(Long communityId, String stageFilter)`

- Queries all completed predictions with exact scores (home score == predicted home, away == predicted away)
- Filters by match stage:
  - `"group"` → `match.stage == GROUP`
  - `"playoff"` → `match.stage != GROUP`
  - `"all"` → no stage filter
- Groups by user, sums exact count
- Returns `List<DailyExactPredictorDto>` sorted by `exactCount DESC, displayName ASC`
- Returns top N users (cap at 20, same as today)

### Template changes

Three tab buttons above the heroes scroll row:

```
All  |  Group Stage  |  Play Off
```

- Active tab is styled (green background)
- Each tab has `hx-get="/community/{slug}/heroes?stage=..."` targeting the heroes container div
- On initial page load, `"All"` is active and heroes are rendered server-side (no extra request)
- Section title: "Exact Score Heroes" (was "Last Matchday Heroes")
- Sub-label below title shows active filter: e.g. "All stages · 47 exact predictions"

### Community home controller changes

On initial load, calls `getCumulativeHeroes(communityId, "all")` instead of `getLastMatchdayExactPredictors()`.

---

## Feature 5: Fixtures Knockout Tab

No code changes needed beyond Feature 1. Once `syncKnockoutMatches()` is run, `matchService.getKnockoutFixtures()` returns results and the "Knockouts" filter tab populates automatically.

---

## Data Flow Summary

```
Admin clicks "Sync Knockout Matches"
  → MatchSyncService.syncKnockoutMatches()
  → Matches written to DB with stages ROUND_OF_32 … FINAL
  → Fixtures /knockout tab shows matches ✓
  → /bracket page renders bracket ✓
  → /groups Play Off tab renders bracket ✓
  → Prediction windows open for R32 matches
```

---

## Files Affected

| File | Change |
|---|---|
| `MatchSyncService.java` | Add `syncKnockoutMatches()` |
| `controller/admin/AdminSyncController.java` | Add endpoint for sync trigger |
| `GroupController.java` | Load bracket data + `defaultTab` |
| `CommunityLeaderboardController.java` | Pass `gsOpenDefault` to leaderboard template |
| `groups.html` | Add tabs, Play Off bracket section, rename header |
| `fragments/bracket-view.html` | New — extracted from `bracket.html` |
| `bracket.html` | Use `th:replace` for bracket fragment |
| `leaderboard.html` | Collapsible GS phase section |
| `DailyExactPredictorService.java` | Add `getCumulativeHeroes()` |
| `CommunityHomeController.java` | Switch to cumulative heroes, add HTMX heroes endpoint |
| `community/home.html` | Stage filter tabs on heroes widget |
| Admin sync panel template | Add "Sync Knockout Matches" button |
