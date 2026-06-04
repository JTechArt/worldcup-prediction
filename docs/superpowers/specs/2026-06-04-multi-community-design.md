# Multi-Community Architecture — Design Spec

**Date:** 2026-06-04
**Status:** Approved for implementation
**Scope:** Transform single-tenant prediction game into multi-community platform with role hierarchy

---

## 1. Overview

The application currently operates as a single-tenant prediction game — one pool of users, one leaderboard, one set of predictions. This design introduces **Communities** (user-defined prediction groups) so the same application can serve multiple independent groups. Each community has its own leaderboard, predictions, and admin.

**Core concept:** `mydomain.com/c/{slug}/` scopes all community-specific pages. A user has one account but can belong to multiple communities with independent predictions in each.

---

## 2. Key Decisions

| Decision | Choice |
|---|---|
| Naming | "Community" (not pool, league, or group — avoids collision with tournament groups) |
| URL routing | Community in URL path: `/c/{slug}/predictions`, `/c/{slug}/leaderboard` |
| Prediction scope | Per-community — a user submits separate predictions in each community |
| Multi-membership | One account, multiple community memberships, switch via header dropdown |
| Super Admin auth | Username/password form login (no OAuth2) |
| Guest access | Public pages (fixtures, bracket, groups, teams) remain public; leaderboard requires community context |
| Stats storage | Denormalized on `CommunityMembership` (total_points, exact_score_count, etc.) |
| Migration strategy | Truncate user/prediction data, preserve global data (teams, matches, players) |

---

## 3. Role Model

### Global Roles (on `User.role`)

| Role | Assignment | Capabilities |
|---|---|---|
| `SUPER_ADMIN` | Seeded on startup from `application.properties`. Single user. Form-based login. | Create/delete communities. Assign community admins (invite new or pick existing). Sync football API. Publish match results. **Cannot predict or join communities.** |
| `USER` | Default for all OAuth2 registrations. | Join communities via invitation/approval. Submit predictions per community. View community leaderboards. |

### Community Roles (on `CommunityMembership.role`)

| Role | Assignment | Capabilities |
|---|---|---|
| `ADMIN` | Assigned by Super Admin when creating the community, or promoted later. | Invite users to community. Approve/reject join requests. Send community-specific notifications. View/edit all community predictions. Manage members (enable/disable). **Can also predict as a member.** Note: prediction windows are global (per-match), managed by Super Admin. |
| `MEMBER` | Default when a user joins a community. | Submit predictions. View leaderboard. View own predictions and post-lock predictions of others. |

---

## 4. Data Model

### New Tables

**`communities`**

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | auto |
| `name` | VARCHAR(100) NOT NULL | Display name, e.g. "Acme Corp" |
| `slug` | VARCHAR(50) UNIQUE NOT NULL | URL-safe, e.g. "acme-corp" |
| `description` | VARCHAR(500) | Optional |
| `created_by_id` | BIGINT FK -> users | Super Admin who created it |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

**`community_memberships`**

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | auto |
| `community_id` | BIGINT FK -> communities NOT NULL | |
| `user_id` | BIGINT FK -> users NOT NULL | |
| `role` | VARCHAR(50) NOT NULL | `ADMIN` or `MEMBER` |
| `status` | VARCHAR(50) NOT NULL | `ACTIVE`, `PENDING`, `DISABLED` |
| `total_points` | INTEGER NOT NULL DEFAULT 0 | Denormalized stats |
| `exact_score_count` | INTEGER NOT NULL DEFAULT 0 | |
| `correct_winner_count` | INTEGER NOT NULL DEFAULT 0 | |
| `correct_draw_count` | INTEGER NOT NULL DEFAULT 0 | |
| `joined_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |
| UNIQUE(community_id, user_id) | | |

### Modified Tables

**`users`**
- Add: `password_hash VARCHAR(255) nullable` (only populated for SUPER_ADMIN)
- Change `role` enum values: `PARTICIPANT` -> `USER`, `ADMIN` -> removed (replaced by `SUPER_ADMIN` and `USER`)
- Drop columns: `total_points`, `exact_score_count`, `correct_winner_count`, `correct_draw_count` (moved to `community_memberships`)

**`predictions`**
- Add: `community_id BIGINT FK -> communities NOT NULL`
- Drop unique constraint: `(user_id, match_id)`
- Add unique constraint: `(user_id, match_id, community_id)`

**`tournament_winner_predictions`**
- Add: `community_id BIGINT FK -> communities NOT NULL`
- Drop unique constraint: `(user_id)`
- Add unique constraint: `(user_id, community_id)`

**`invitations`**
- Add: `community_id BIGINT FK -> communities NOT NULL`

**`notification_log`**
- Add: `community_id BIGINT FK -> communities nullable`

### Unchanged Tables

`teams`, `players`, `matches`, `groups`, `group_standings`, `group_teams`, `match_goals`, `match_lineups`, `audit_logs`

---

## 5. Authentication

### Two Auth Paths on `/login`

1. **OAuth2 buttons** (Google, LinkedIn) — existing flow, for all regular users
2. **Super Admin form** — collapsible "Super Admin Login" section with username/password fields

### Super Admin Bootstrap

`CommandLineRunner` on startup checks if a `SUPER_ADMIN` user exists. If not, creates one from:

```properties
app.super-admin.username=admin
app.super-admin.password=changeme123
app.super-admin.email=admin@worldcup.local
```

Password stored as BCrypt hash in `User.password_hash`. Super Admin can change password from `/admin/settings`.

### Spring Security Configuration

- `formLogin()` added alongside `oauth2Login()`
- Form login restricted to `SUPER_ADMIN` role (regular users cannot use form login)
- `/admin/**` requires `SUPER_ADMIN`
- `/c/{slug}/admin/**` requires authenticated + community `ADMIN` role (verified via `HandlerInterceptor`)
- `/c/{slug}/**` requires authenticated + community membership
- Public routes unchanged: `/`, `/login`, `/fixtures/**`, `/groups/**`, `/bracket/**`, `/teams/**`

### OAuth2 Flow Changes

- New users get `role=USER` (was `PARTICIPANT`)
- Invitation check resolves `community_id` — invited user auto-added to that community as `MEMBER` with `ACTIVE` status
- Users without any community membership redirect to `/communities` after login

### AccountStatusFilter Changes

- Continues checking global `User.status`
- Additionally resolves community context from URL slug on `/c/{slug}/**` routes and verifies membership
- Super Admin bypasses community checks

---

## 6. URL Routing

### Community-Scoped (authenticated + community membership)

| URL | Purpose |
|---|---|
| `/c/{slug}/home` | Community home with community-specific leaderboard widget |
| `/c/{slug}/predictions` | Predictions for this community |
| `/c/{slug}/leaderboard` | Leaderboard filtered to community members |
| `/c/{slug}/admin/dashboard` | Community admin stats |
| `/c/{slug}/admin/members` | Approve/reject/manage community members |
| `/c/{slug}/admin/predictions` | View/edit community predictions |
| `/c/{slug}/admin/notifications` | Community notifications, invite users |

### Global (public or auth-only)

| URL | Access | Purpose |
|---|---|---|
| `/` | Public | Landing page |
| `/login` | Public | OAuth2 + Super Admin form login |
| `/fixtures/**` | Public | Match schedule |
| `/groups/**` | Public | Tournament groups |
| `/bracket/**` | Public | Knockout bracket |
| `/teams/{id}` | Public | Team detail |
| `/communities` | Authenticated | List my communities, switch between them |
| `/communities/join/{slug}` | Authenticated | Request to join a community |
| `/pending` | Authenticated | Pending approval page |

### Super Admin

| URL | Purpose |
|---|---|
| `/admin/dashboard` | Global stats, community list |
| `/admin/communities` | Create/manage communities |
| `/admin/communities/{id}/admins` | Assign community admins |
| `/admin/sync` | Football API sync |
| `/admin/matches` | Enter/publish match results |
| `/admin/settings` | Change Super Admin password |

### Navigation Behavior

- After login: 1 community -> `/c/{slug}/home`; 0 communities -> `/communities`; multiple -> `/communities`
- Header dropdown shows all user's communities; selecting one navigates to `/c/{new-slug}/{current-page}`
- Super Admin always goes to `/admin/dashboard`

---

## 7. Service Layer Changes

### New: `CommunityService`

- CRUD for communities (create, update, delete)
- Slug resolution (`findBySlug`)
- Membership management (add/remove members, change roles, approve/reject)
- Community listing for a user

### Modified: `LeaderboardService`

- `getFullLeaderboard(Long communityId)` — filters to community members, queries predictions with `community_id`
- `getTopN(int n, Long communityId)`
- `getEntryForUser(Long userId, Long communityId)`
- Tiebreaker chain unchanged; stats read from `CommunityMembership` denormalized columns

### Modified: `PredictionService`

- `submitPredictions()` takes `communityId` — predictions saved with community context
- Unique constraint `(user_id, match_id, community_id)` allows different predictions per community

### Modified: `PredictionViewService`

- All methods take `communityId` as additional parameter
- Round summaries, match predictions, past rounds all filtered by community

### Modified: `ScoringService`

- When a match result is entered, re-scores predictions for ALL communities (same match, different `community_id` values)
- Updates denormalized stats on `CommunityMembership`

### Modified: `UserService`

- `approveUser`/`rejectUser` become community-scoped — toggle `CommunityMembership.status`
- `User.status` remains for global account state (Super Admin can disable globally)

### Modified: `NotificationService`

- Community-scoped: reminders go to members of a specific community who haven't predicted
- Leaderboard digests are per-community

### Modified: `InvitationService` (within `CustomOAuth2UserService`)

- Invitations tied to a community
- On OAuth2 registration, invited user auto-joined to the invitation's community

### Unchanged

- `MatchService`, `MatchAdminService` — matches are global
- `GroupService` — tournament groups, no community context
- `FootballApiSyncService` and related integration services — global data sync

---

## 8. Database Migration

### `V5__multi_community.sql`

**Phase 1: Truncate community-related data**
```sql
DELETE FROM notification_log;
DELETE FROM invitations;
DELETE FROM tournament_winner_predictions;
DELETE FROM predictions;
DELETE FROM oauth_identities;
DELETE FROM users;
```

**Phase 2: Create new tables**
- `communities` (id, name, slug, description, created_by_id, created_at, updated_at)
- `community_memberships` (id, community_id, user_id, role, status, total_points, exact_score_count, correct_winner_count, correct_draw_count, joined_at, updated_at)

**Phase 3: Alter existing tables**
- `users`: add `password_hash` column, drop `total_points`, `exact_score_count`, `correct_winner_count`, `correct_draw_count`
- `predictions`: add `community_id` column (NOT NULL), drop old unique constraint, add new unique `(user_id, match_id, community_id)`
- `tournament_winner_predictions`: add `community_id` column (NOT NULL), drop old unique, add new unique `(user_id, community_id)`
- `invitations`: add `community_id` column (NOT NULL)
- `notification_log`: add `community_id` column (nullable)

**Preserved as-is:** `teams`, `players`, `matches`, `groups`, `group_standings`, `group_teams`, `match_goals`, `match_lineups`, `audit_logs`

---

## 9. UI Changes

### Login Page (`/login`)
- Existing OAuth2 buttons (Google, LinkedIn) unchanged
- Add collapsible "Super Admin Login" section with username/password form below

### New: Community Selector (`/communities`)
- Cards for each community the user belongs to (name, member count, user's role)
- Click to enter -> `/c/{slug}/home`
- Pending join requests shown with status

### Header
- Community name displayed prominently
- Dropdown to switch communities (navigates to `/c/{new-slug}/{current-page}`)
- Badge for Community Admin vs Member role

### Community-Scoped Pages (`/c/{slug}/...`)
- Home, Predictions, Leaderboard — same UI layouts as current, operating on community-scoped data
- Controller injects community context into model; templates render whatever data they receive

### Community Admin Pages (`/c/{slug}/admin/...`)
- Members: approve/reject join requests, invite by email, promote to Admin
- Predictions: same as current admin predictions, filtered to community
- Notifications: community-specific reminders, invitations

### Super Admin Pages (`/admin/...`)
- Dashboard: community list with member counts
- Community CRUD: create (name, slug), delete, assign admins
- Users: global view of all users, assign as community admin
- Sync + Match results: same as current admin panel
- Settings: change password

### Unchanged
- Public pages (fixtures, bracket, groups, teams) — no community context
- Prediction submission UI flow (all-or-nothing per round) — scoped to community
- Leaderboard grid layout — filtered to community members

---

## 10. Security

- Super Admin form login: BCrypt password, session-based
- OAuth2 for all regular users (unchanged)
- CSRF protection on all forms (unchanged)
- Community access enforced via `HandlerInterceptor`: every `/c/{slug}/**` request resolves community from slug and verifies user membership
- Community admin actions verified via membership role check
- Super Admin bypasses all community checks
- Audit log extended with community context where applicable

---

## 11. Enum Changes

### `UserRole` (modified)
```java
public enum UserRole {
    SUPER_ADMIN,
    USER
}
```

### `CommunityRole` (new)
```java
public enum CommunityRole {
    ADMIN,
    MEMBER
}
```

### `MembershipStatus` (new)
```java
public enum MembershipStatus {
    PENDING,
    ACTIVE,
    DISABLED
}
```
