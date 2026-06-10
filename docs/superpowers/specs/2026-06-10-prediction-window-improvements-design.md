# Prediction Window Improvements — Design Spec

**Date:** 2026-06-10  
**Status:** Approved

---

## Overview

Four related improvements to the prediction-window experience:

1. A new `round_submissions` table that explicitly tracks per-user, per-community, per-matchday submission state (rather than deriving it from individual match predictions).
2. A countdown/confirmation banner in the community layout header visible whenever a prediction window is open.
3. A Super Admin page to view and remind participants per community per matchday.
4. A Community Admin page with the same capability scoped to their community.

---

## 1. Data Layer — `round_submissions` Table

### Schema

```sql
CREATE TABLE round_submissions (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    community_id  BIGINT       NOT NULL,
    round_label   VARCHAR(50)  NOT NULL,
    submitted_at  TIMESTAMP    NOT NULL,
    CONSTRAINT fk_rs_user      FOREIGN KEY (user_id)      REFERENCES users(id),
    CONSTRAINT fk_rs_community FOREIGN KEY (community_id) REFERENCES communities(id),
    CONSTRAINT uq_rs UNIQUE (user_id, community_id, round_label)
);
```

### Backfill (same migration, `V9__round_submissions.sql`)

```sql
INSERT INTO round_submissions (user_id, community_id, round_label, submitted_at)
SELECT user_id, community_id, round_label, MIN(created_at)
FROM predictions
GROUP BY user_id, community_id, round_label;
```

This is idempotent as a one-shot Flyway migration. Any user who already submitted Matchday 1 predictions in production will receive a correctly-dated row.

### JPA Entity

`RoundSubmission { id, userId, communityId, roundLabel, submittedAt }`  
Unique constraint on `(userId, communityId, roundLabel)`.

### Write path

`PredictionViewService.submitPredictionsForRound` upserts a `RoundSubmission` row immediately after saving match predictions. On re-submit (user overwrites predictions while window is still open) the existing row's `submittedAt` is updated.

---

## 2. Countdown / Confirmation Banner in Community Header

### Behaviour

Shown between the community nav and main content on every community page (`community-base.html`).

| Condition | Banner |
|---|---|
| Window open + user **not** submitted | Amber: "⏱ Matchday X window closes in H:MM:SS — Submit predictions" (link to predictions page) |
| Window open + user **already** submitted | Green: "✅ Predictions submitted for Matchday X" |
| Window closed | No banner |

The countdown is a client-side Alpine.js timer driven by an ISO-8601 `autoClosesAt` timestamp rendered into the page.

### Data injection

A new `@ControllerAdvice` (`CommunityWindowBannerAdvice`) annotated with `@ModelAttribute` populates `windowBanner` into every model for community controllers (`/c/**`). It:

1. Finds the currently open `RoundWindow` (if any).
2. Checks `RoundSubmissionRepository.existsByUserIdAndCommunityIdAndRoundLabel(...)`.
3. Returns a `WindowBannerDto { roundLabel, autoClosesAt, submitted }` or `null` if no open window.

The `@ControllerAdvice` only fires for authenticated users with an active community membership (derived from `SecurityContextHolder`).

### Super Admin in community pages

The `CommunityInterceptor` already lets SUPER_ADMIN through with `communityMembership = null`. The advice must handle this case — SUPER_ADMIN viewing a community page sees no banner (they are not a participant).

---

## 3. Super Admin — Matchday Submission Status Page

### URL

`GET /admin/communities/{communityId}/matchday-status?round={roundLabel}`

Reachable via a new "Matchday Status" link on the existing `/admin/communities` list page (one link per community row).

### Controller

`AdminMatchdayStatusController` (`@PreAuthorize("hasRole('SUPER_ADMIN')")`).

- If `round` param is absent, defaults to the currently open round (or the first matchday if none open).
- Loads all ACTIVE members of the community.
- Loads all `RoundSubmission` rows for `(communityId, roundLabel)`.
- Builds a `List<MemberSubmissionStatusDto> { userId, displayName, email, avatarUrl, submitted, submittedAt }`.

### Template (`admin/matchday-status.html`)

- Community name header.
- Round selector (`<select>` → GET with `?round=` param).
- Countdown widget (same Alpine.js component, rendered only when the selected round's window is currently open).
- Member table: Name | Email | Status badge (✅ Submitted / ⏳ Pending) | Submitted At | [Remind] button.

### Remind action

`POST /admin/communities/{communityId}/matchday-status/{userId}/remind`

Sends the existing `prediction-reminder` email (already in `EmailService`) to that user for that round. Returns redirect back to the status page with a flash message. Does not check submission status server-side — admin decides who to remind.

---

## 4. Community Admin — Submission Status Page

### URL

`GET /c/{slug}/admin/submission-status?round={roundLabel}`

New link in the community admin navigation alongside Members and Predictions.

### Controller

`CommunityAdminSubmissionController` (community admin role required, already enforced by `CommunityInterceptor`).

Identical logic to the Super Admin page but:
- Community is resolved from the URL slug (already in request attribute).
- No community selector needed.
- Admin can only see their own community's data.

### Template (`community/admin/submission-status.html`)

Same layout as the Super Admin template: round selector, countdown, member table, per-user [Remind] button.

### Remind action

`POST /c/{slug}/admin/submission-status/{userId}/remind`

Same email trigger as the Super Admin remind action.

---

## Visibility rules (unchanged)

Predictions remain hidden from other community members until the window closes — this is already enforced in `PredictionViewService`. The leaderboard continues to show points (which are calculated from submitted predictions) regardless of window state. No changes needed here.

---

## Files affected

| File | Change |
|---|---|
| `V9__round_submissions.sql` | New Flyway migration: DDL + backfill |
| `RoundSubmission.java` | New entity |
| `RoundSubmissionRepository.java` | New repository (`existsBy...`, `findByCommunityIdAndRoundLabel`) |
| `PredictionViewService.java` | Upsert `RoundSubmission` on submit |
| `WindowBannerDto.java` | New DTO |
| `CommunityWindowBannerAdvice.java` | New `@ControllerAdvice` |
| `community-base.html` | Add banner slot + Alpine countdown |
| `MemberSubmissionStatusDto.java` | New DTO |
| `AdminMatchdayStatusController.java` | New controller |
| `admin/matchday-status.html` | New template |
| `admin/communities.html` | Add "Matchday Status" link per row |
| `CommunityAdminSubmissionController.java` | New controller |
| `community/admin/submission-status.html` | New template |
| `community-base.html` (nav) | Add "Submission Status" link in community admin nav |
