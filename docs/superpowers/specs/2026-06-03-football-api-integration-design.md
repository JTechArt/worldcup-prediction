# Football API Integration — Design Spec
**Date:** 2026-06-03  
**Status:** Approved

---

## Overview

Revise the football API integration to collect all static WC2026 data (teams, squads, matches, standings) via a one-time bootstrap script, then maintain it with incremental cron jobs for results, lineups, goal scorers, and standings. All data is stored locally; the API is only called when there is work to do.

**API:** football-data.org v4  
**Rate limit:** 10 calls/minute (enforced via shared Guava `RateLimiter` bean)

---

## 1. Data Model

### Modified: `teams`
- Add `external_id` (long, nullable, unique) — football-data.org team ID
- All other existing fields unchanged

### New: `players`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `external_id` | BIGINT | unique, not null |
| `team_id` | BIGINT FK | → teams |
| `name` | VARCHAR(100) | |
| `position` | VARCHAR(20) | GOALKEEPER / DEFENDER / MIDFIELDER / FORWARD |
| `nationality` | VARCHAR(100) | |
| `date_of_birth` | DATE | nullable |
| `shirt_number` | INTEGER | nullable |

### New: `group_standings`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `group_id` | BIGINT FK | → groups |
| `team_id` | BIGINT FK | → teams |
| `position` | INTEGER | |
| `points` | INTEGER | |
| `played` | INTEGER | |
| `won` | INTEGER | |
| `drawn` | INTEGER | |
| `lost` | INTEGER | |
| `goals_for` | INTEGER | |
| `goals_against` | INTEGER | |
| `goal_difference` | INTEGER | |
| `updated_at` | TIMESTAMP | |
| UNIQUE | (group_id, team_id) | upsert key |

### New: `match_lineups`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `match_id` | BIGINT FK | → matches |
| `team_id` | BIGINT FK | → teams |
| `player_id` | BIGINT FK | → players |
| `starting` | BOOLEAN | true = starter, false = sub |
| `shirt_number` | INTEGER | nullable |
| `formation_position` | VARCHAR(50) | e.g. "Goalkeeper", "Left Back" |

### New: `match_goals`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `match_id` | BIGINT FK | → matches |
| `team_id` | BIGINT FK | → teams |
| `player_id` | BIGINT FK, nullable | null for own goals |
| `minute` | INTEGER | |
| `type` | VARCHAR(20) | REGULAR / OWN_GOAL / PENALTY |

### Modified: `matches`
- Add `lineup_fetched` (BOOLEAN, default false) — avoids expensive join in scheduler skip check

### Modified: `players`
- Add `tournament_goals` (INTEGER, default 0) — updated by `ScorersSyncScheduler`

---

## 2. API Client & Rate Limiting

### `FootballApiRateLimiter` (new `@Component`)
```java
RateLimiter.create(10.0 / 60.0)  // max 10 tokens per 60 seconds

public <T> T call(Supplier<T> apiCall)  // acquires token, then executes
```
Single shared bean. All sync services inject it. Handles concurrent scheduler bursts naturally.

### `FootballApiClient` — methods

| Method | Endpoint | Calls |
|---|---|---|
| `fetchTeamsWithSquads()` | `GET /competitions/WC/teams` | 1 — all 48 teams + full squads |
| `fetchAllMatches()` | `GET /competitions/WC/matches` | 1 — all matches (renamed from `fetchMatches`) |
| `fetchStandings()` | `GET /competitions/WC/standings` | 1 — all 12 groups |
| `fetchMatchDetail(long externalId)` | `GET /matches/{id}` | 1 per match — lineup + goals |
| `fetchTopScorers()` | `GET /competitions/WC/scorers?limit=20` | 1 |

### New DTOs
- `FootballApiTeamsResponseDto` — wraps `List<FootballApiTeamWithSquadDto>`
- `FootballApiTeamWithSquadDto` — team fields + `List<FootballApiPlayerDto>`
- `FootballApiPlayerDto` — id, name, position, nationality, dateOfBirth, shirtNumber
- `FootballApiStandingsResponseDto` — wraps standings per group
- `FootballApiMatchDetailDto` — extends match with `lineups` + `goals` arrays
- `FootballApiGoalDto` — minute, type, scorer (id, name), team id
- `FootballApiLineupDto` — team id, startXI, substitutes (each with player id, name, shirtNumber, position)

---

## 3. Sync Services

| Service | Responsibility |
|---|---|
| `TeamSyncService` | Upsert teams (`externalId`, name, TLA) + upsert players per team |
| `MatchSyncService` | Upsert group stage matches with `externalId`, link to `Team` and `Group` by `external_id` |
| `StandingSyncService` | Upsert all `GroupStanding` rows; resolve group by API stage/group name |
| `LineupSyncService` | For each `COMPLETED` match with `lineup_fetched = false`: fetch detail, persist lineups + goals, set flag |
| `ScorersService` | Fetch top scorers, update `players.tournament_goals` |

All services inject `FootballApiRateLimiter` and `FootballApiClient`.

---

## 4. Cron Schedule & Skip Logic

| Scheduler | Cron | API calls/run | Skip condition |
|---|---|---|---|
| `MatchResultScheduler` (existing) | every 5 min | 1 | No SCHEDULED matches with `kickoffTime < now` |
| `LineupSyncScheduler` | every 30 min | 0–N | No COMPLETED matches with `lineup_fetched = false` |
| `StandingSyncScheduler` | every 6 hours | 1 | No matches completed since `group_standings.updated_at` |
| `ScorersSyncScheduler` | daily 02:00 | 1 | Any player's `tournament_goals` updated within last 24h |

Skip check = single `COUNT` query. Zero API calls when nothing to do. Logs `"Skipped — nothing to do"`.

**Worst-case rate budget:** matchday with 4 games finishing simultaneously → `LineupSyncScheduler` makes 4 calls + 1 results + 1 standings = 6 calls. Within the 10/min limit.

---

## 5. Admin Panel — Manual Triggers

New `AdminSyncController` with one endpoint per service:

```
POST /admin/sync/teams
POST /admin/sync/matches
POST /admin/sync/standings
POST /admin/sync/lineups
POST /admin/sync/scorers
```

Admin UI gets a **"Data Sync"** panel section:
- One button per job
- Last-run timestamp
- Result summary ("3 lineups fetched", "Skipped — nothing to do")
- Optional `?force=true` parameter to override skip logic for manual re-sync

Same service methods used by schedulers and admin — no duplication.

---

## 6. Bootstrap Runner

`FootballApiBootstrapRunner` implements `CommandLineRunner`. Activated only with `--spring.profiles.active=bootstrap`. **Never runs in production.**

**Execution sequence (3 API calls):**
1. `fetchTeamsWithSquads()` → upsert teams with `externalId` + upsert all players
2. `fetchAllMatches()` → upsert matches with `externalId`, linked to teams/groups by `external_id`
3. `fetchStandings()` → seed initial `GroupStanding` rows

After running, export the populated DB to SQL and replace `R__wc2026_data.sql` with the full dataset. All upserts use `ON CONFLICT (external_id) DO UPDATE` — safe to re-run if interrupted.

**Failure handling:** log and continue on any single call failure. Partial data is acceptable; admin sync panel fills gaps.

---

## 7. UI Views

### Groups Page (enhanced)
- Each group card: standings table (P W D L GF GA GD Pts) from `group_standings`
- Below table: upcoming matches (kickoff time + teams) + completed matches (final score)
- Links to individual match preview

### Match Preview Page (new — `/matches/{id}`)
- Header: home vs away, score or kickoff time, venue + city, round label
- Two lineup columns: starting XI (shirt number + formation position) then substitutes
- Goals timeline: minute — scorer name — team side — type badge (OG / PEN)

### Team Page (new — `/teams/{id}`)
- Header: flag, name, group, confederation
- Squad: grouped by position (GK / DEF / MID / FWD), name + shirt number + goals
- Matches: all group stage matches — upcoming + completed with scores

### Top Scorers (new — `/scorers`)
- Ranked table: rank, player name, team flag, goals

---

## 8. Key Invariants

1. `teams.external_id`, `matches.external_id`, `players.external_id` must be non-null after bootstrap — cron jobs skip any record missing this field and log a warning.
2. `match_goals.player_id` is nullable — own goals have no attributed scorer.
3. `lineup_fetched` flag is the source of truth for whether a match has been enriched — avoids expensive join in every scheduler cycle.
4. The bootstrap profile is the only place bulk team/squad/match data is written from the API. Post-bootstrap, team and player data is considered static (no scheduled re-sync).
