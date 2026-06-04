# Features V2 — Design Spec
**Date:** 2026-06-04  
**Status:** Pending Review  
**Scope:** Email notifications (Freemarker templates, scheduler, admin triggers, invitations) + Enhanced national team detail page

---

## 1. Overview

Two feature areas from `features_v2.md`:

1. **Email Notifications** — automated and admin-triggered notifications using Freemarker templates: prediction window opening, deadline reminders for users who haven't submitted, daily leaderboard digest for top 10, and admin invitation emails with auto-approve on OAuth2 login.
2. **Enhanced National Team Detail Page** — full-width hero header with team/player images, group standing mini-card, improved match results section with win/draw/loss coloring.

---

## 2. Data Model Changes

### New table: `notification_log`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | auto-generated |
| `type` | VARCHAR(50) | `PREDICTION_WINDOW_OPEN`, `PREDICTION_REMINDER`, `LEADERBOARD_DIGEST`, `INVITATION`, `RESULTS_PUBLISHED` |
| `recipient_id` | BIGINT FK nullable | -> users (null for not-yet-registered invitees) |
| `recipient_email` | VARCHAR(255) | denormalized, always populated |
| `match_id` | BIGINT FK nullable | -> matches (contextual reference) |
| `sent_at` | TIMESTAMP | |
| `reference_key` | VARCHAR(100) UNIQUE | dedup key, e.g. `PREDICTION_REMINDER:user:42:match:15` |

Unique constraint on `reference_key` prevents double-sends.

### New table: `invitations`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | auto-generated |
| `email` | VARCHAR(255) UNIQUE | invited email address |
| `invited_by_id` | BIGINT FK | -> users (admin who invited) |
| `invited_at` | TIMESTAMP | |
| `accepted_at` | TIMESTAMP nullable | set when user completes OAuth2 registration |

### No schema changes to `teams`

Hero and star player images are resolved by file convention (see Section 7).

---

## 3. Email Templating — Freemarker

### Dependency

Add `spring-boot-starter-freemarker` to `pom.xml`.

### Configuration

Freemarker configured as a secondary template engine for email rendering only. Thymeleaf continues to handle all web pages. A dedicated `freemarker.Configuration` bean is created manually (not relying on Spring Boot auto-config, which would conflict with Thymeleaf's template resolver). This bean points to `classpath:/templates/email/` as its template directory. Freemarker templates use `.ftlh` extension.

### Templates

| Template | Variables | Purpose |
|---|---|---|
| `_header.ftlh` | `title` | Shared email header partial (logo, green banner) |
| `_footer.ftlh` | — | Shared email footer partial (app name, unsubscribe hint) |
| `prediction-window-open.ftlh` | `firstName`, `matches` (label + kickoff), `appUrl` | Prediction window just opened |
| `prediction-reminder.ftlh` | `firstName`, `matches` (label + kickoff), `hoursLeft`, `appUrl` | Deadline approaching, user hasn't submitted |
| `leaderboard-digest.ftlh` | `firstName`, `rank`, `points`, `topEntries` (top 10), `matchResults` (today's completed), `appUrl` | Daily digest after last match of day |
| `invitation.ftlh` | `inviterName`, `appUrl` | Admin invitation to join |
| `approval.ftlh` | `firstName`, `appUrl` | Registration approved (migrated from inline HTML) |
| `rejection.ftlh` | `firstName` | Registration rejected (migrated from inline HTML) |
| `results-published.ftlh` | `firstName`, `matchLabel`, `score`, `appUrl` | Match results published (migrated from inline HTML) |

### Email design consistency

All templates share inline CSS matching the app design system:
- Background: `#f0fdf5`
- Header: `#006b2a` dark green
- Accent: `#00c853` green
- Urgency: `#FF5722` orange
- Font: Inter (with web-safe fallbacks)
- Shared `_header.ftlh` and `_footer.ftlh` partials for consistency

### Refactored `EmailService`

- New `FreemarkerEmailRenderer` component: takes template name + model map, returns rendered HTML string
- `EmailService` delegates HTML rendering to `FreemarkerEmailRenderer`, keeps SMTP send logic and log-only mode
- Existing methods (`sendApprovalEmail`, `sendRejectionEmail`, `sendPredictionReminder`, `sendResultsPublished`) refactored to use Freemarker templates
- New methods: `sendPredictionWindowOpen(List<User>, List<Match>)`, `sendLeaderboardDigest(User, LeaderboardDigestModel)`, `sendInvitation(String email, User inviter)`

---

## 4. Notification Scheduler

### New class: `NotificationScheduler`

Follows the existing project pattern (`MatchResultScheduler`, `LineupSyncScheduler`, etc.).

### Scheduled jobs

| Job | Cron | Logic | Dedup reference_key |
|---|---|---|---|
| `checkPredictionWindowOpen` | Every 5 min | Find matches with `kickoffTime` between 24h and 23h55m from now (window just opened). Send to all active participants. | `PREDICTION_WINDOW_OPEN:match:{matchId}` |
| `checkPredictionDeadline` | Every 15 min | Find matches with `kickoffTime` between 1h and configurable threshold from now. Find active users who have NOT submitted predictions for those matches. Send reminder. | `PREDICTION_REMINDER:user:{userId}:match:{matchId}` |
| `checkLeaderboardDigest` | Every 30 min | Check if all matches scheduled today have status `COMPLETED`. If yes, compute current top 10 leaderboard (at time of generation) and send digest to those 10 users with today's results + updated standings. | `LEADERBOARD_DIGEST:date:{yyyy-MM-dd}` |

### Skip logic

Before each job runs, check `notification_log` for existing `reference_key`. If found, skip. Each job logs `"Skipped — already sent"` or `"Sent N notifications"`.

### Configuration

```properties
app.notification.reminder-hours-before=3
app.notification.enabled=true
```

When `app.notification.enabled=false`, scheduler jobs are disabled (no-op). `EmailService` log-only mode is a separate concern (SMTP not configured).

---

## 5. Admin Notification Controller

### New class: `AdminNotificationController`

New admin controller with dedicated page and menu item.

### Routes

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/admin/notifications` | Notification dashboard page |
| `POST` | `/admin/notifications/invite` | Send invitation email |
| `POST` | `/admin/notifications/reminder/{matchId}` | Manual prediction reminder for specific match |
| `POST` | `/admin/notifications/leaderboard-digest` | Manual leaderboard digest send |
| `POST` | `/admin/notifications/window-open/{matchId}` | Manual window-open notification |

### Admin notification page (`admin/notifications.html`)

- **Invite section:** email input field + "Send Invitation" button. Validation: reject if email already in `invitations` or `users` table. Success/error feedback via HTMX.
- **Manual trigger section:** buttons for each notification type. Reminder and window-open buttons show a dropdown to pick a match. Leaderboard digest is a single button.
- **Recent notifications table:** last 50 entries from `notification_log` showing type, recipient email, sent_at. Styled consistently with other admin tables.

### Admin nav update

Add "Notifications" menu item to admin sidebar/nav, alongside existing: Dashboard, Users, Matches, Predictions, Sync.

---

## 6. Invitation Flow

1. Admin enters email on `/admin/notifications`, clicks "Send Invitation"
2. System creates row in `invitations` table, sends invitation email (Freemarker template with link to landing page)
3. Validation: if email already exists in `invitations` or `users` table, return error message
4. Invitee receives email, clicks link, lands on app landing page, signs in via Google/LinkedIn
5. `CustomOAuth2UserService` (existing) modified: after OAuth2 callback, before setting status to `PENDING`, check if incoming email exists in `invitations` table
6. If match found: set user status to `ACTIVE` (skip pending), set `invitations.accepted_at = now()`
7. If no match: normal flow — status = `PENDING`, admin reviews manually

---

## 7. Enhanced National Team Detail Page

### Route

`/teams/{id}` (existing, enhanced)

### Controller changes (`TeamController`)

- Resolve hero image path: check if `/images/teams/{fifaCode}-hero.jpg` exists, otherwise use `/images/teams/default-hero.jpg`
- Resolve star player image: check if `/images/teams/{fifaCode}-star.png` exists, pass path or null
- Fetch group info: which group the team belongs to, current `GroupStanding` row for this team
- Split matches into completed and upcoming lists
- Compute match result status for completed matches (win/draw/loss from this team's perspective)
- Existing player loading unchanged

### Image conventions

| File | Path | Size recommendation |
|---|---|---|
| Team hero | `/images/teams/{fifaCode}-hero.jpg` | ~1200x400px, team photo or stadium |
| Star player | `/images/teams/{fifaCode}-star.png` | ~400x500px, transparent background cutout |
| Default hero | `/images/teams/default-hero.jpg` | ~1200x400px, generic WC2026 tournament image |

Images placed manually in `src/main/resources/static/images/teams/`. Missing custom images fall back to defaults. Default hero image is always present and shipped with the app.

### Page layout (`team.html`)

**1. Hero header (full-width):**
- Full-width background image (hero) with dark gradient overlay (`rgba(0,80,30,0.6)` -> transparent) for text readability
- Team flag (existing circle style, larger ~80px) on the left
- Team name in Bebas Neue, large (tracking-widest, white text over the overlay)
- Confederation + Group badge (e.g., "UEFA · Group A") below team name
- If star player image exists: positioned on the right side of the hero, absolute positioned, slight `fadeUp` animation on load, partial overflow allowed (player image extends slightly above the hero container for visual depth)
- `fadeUp` entrance animation consistent with rest of app

**2. Group standing mini-card:**
- White `rounded-2xl shadow-sm border border-gray-100` card (matches existing card style)
- Single table row showing this team's standing: P, W, D, L, GF, GA, GD, Pts
- Team's row highlighted with light green background
- Link to full `/groups` page

**3. Matches section (enhanced):**
- White rounded card, split into "Results" and "Upcoming" sub-headings
- **Completed matches:** opponent flag + name, score in `font-mono font-bold`, result indicator (green dot = win, yellow = draw, red = loss), link to `/matches/{id}`
- **Upcoming matches:** opponent flag + name, kickoff date/time, venue
- Styled consistently with existing match-preview card style

**4. Squad section (existing, unchanged):**
- Grouped by position (GK/DEF/MID/FWD)
- Shirt number, name, tournament goals with football emoji
- No changes needed

### Design consistency

All new elements use:
- `rounded-2xl shadow-sm border border-gray-100` cards
- `font-display` (Bebas Neue) for headings
- `fadeUp` animation with stagger delays
- Green/dark-green/orange color palette from `tailwind.config`
- Same spacing patterns (`space-y-8`, `px-4 py-10`, `max-w-4xl mx-auto`) as match-preview.html

---

## 8. Flyway Migrations

Two new migration files:

- `V{next}__create_notification_log.sql` — creates `notification_log` table with unique index on `reference_key`
- `V{next}__create_invitations.sql` — creates `invitations` table with unique index on `email`

Migration version numbers follow the existing sequence in the project.

---

## 9. Testing

- **Unit tests:** `NotificationScheduler` skip logic, `FreemarkerEmailRenderer` template rendering, invitation auto-approve logic in `CustomOAuth2UserService`
- **Integration tests:** `AdminNotificationController` endpoints (invite, manual trigger), `NotificationLog` dedup behavior
- **Manual verification:** team page with and without custom images, email rendering in log-only mode
