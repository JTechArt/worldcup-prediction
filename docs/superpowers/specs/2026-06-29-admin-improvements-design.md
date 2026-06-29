# Admin Improvements — Design Spec

**Date:** 2026-06-29  
**Status:** Approved

---

## 1. Admin Predictions Page — Missing Predictions by Community

### Goal
Allow the super admin to see which community members have not submitted a prediction for a given match and create predictions on their behalf (backdoor for members who emailed predictions manually).

### Current State
- `GET /admin/predictions?matchId=X` loads all predictions globally via `predictionService.findAllByMatchId(matchId)`
- No community context; cannot see who is missing
- Existing predictions can be overridden via `POST /admin/predictions/{id}/override`

### Changes

#### Repository — `CommunityMembershipRepository`
Add query to find active members without a prediction for a specific match in a specific community:
```jpql
SELECT cm FROM CommunityMembership cm
JOIN FETCH cm.user u
WHERE cm.community.id = :communityId
  AND cm.status = 'ACTIVE'
  AND cm.user.id NOT IN (
      SELECT p.user.id FROM Prediction p
      WHERE p.match.id = :matchId AND p.community.id = :communityId
  )
ORDER BY u.firstName ASC
```

#### Service — `PredictionService`
New method:
```java
createOnBehalfOf(Long adminUserId, Long userId, Long matchId, Long communityId, int homeScore, int awayScore): Prediction
```
- Bypasses window lock checks (intentional admin backdoor)
- Sets `editedByAdmin = true`, `adminEditNote = "Created on behalf of user by admin"`
- Logs to AuditLog with `AuditAction.PREDICTION_EDITED_BY_ADMIN`
- Throws if prediction already exists for that user+match+community (use override instead)
- Must look up and assign the User, Match, and Community entities

#### Controller — `AdminPredictionController`
- `GET /admin/predictions?matchId=X&communityId=Y`
  - Pass `allCommunities` to model (from `CommunityRepository.findAll()`)
  - When both matchId and communityId present: load predictions via `findByMatchIdAndCommunityId`, load missing members via new repo query
  - When only matchId (no community): current behavior (all predictions, no missing section)
  - Model attributes: `allMatches`, `allCommunities`, `selectedMatch`, `selectedCommunity`, `predictions`, `missingMembers`
- `POST /admin/predictions/create` with params: `matchId`, `communityId`, `userId`, `homeScore`, `awayScore`
  - Calls `createOnBehalfOf`, logs audit, redirects to `?matchId=X&communityId=Y`

#### Template — `admin/predictions.html`
- Add community dropdown next to match selector (optional — if not selected, community-specific features hidden)
- When community selected, show a second table below existing predictions titled "Missing Predictions" listing members with no submission and an inline "Create" form (same UX as Override)
- Existing Override rows unchanged

---

## 2. Database Restore Fix

### Root Cause
The restore form in `backup.html` uses Alpine.js `@submit.prevent="if(confirmed) $el.submit()"`. Calling `form.submit()` programmatically can drop file inputs in some browser/Alpine combinations — the server receives an empty upload, returns a redirect, and the user sees the backup HTML page again with no change applied.

A secondary issue: Spring Boot's default multipart max is 1 MB, which is smaller than most real databases.

### Changes

#### `backup.html`
Remove `@submit.prevent="if(confirmed) $el.submit()"` from the form element. The `:disabled="!confirmed || !filename"` on the submit button is sufficient to gate submission. Without the interceptor, the form submits natively (POST, multipart/form-data) which reliably carries file inputs.

Before:
```html
<form ... @submit.prevent="if(confirmed) $el.submit()">
```
After:
```html
<form ...>
```

#### `application.properties`
```properties
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB
```

No controller or service changes needed — `AdminBackupController` and `DatabaseBackupService` are correct.

---

## 3. Configurable Round Lock Offset

### Goal
Make the "minutes before first kickoff to lock round predictions" configurable from admin settings instead of hardcoded to 60 minutes.

### Current State
`RoundWindowService.recalculateAutoTimes()` line 92:
```java
if (lastKickoff != null) rw.setAutoClosesAt(lastKickoff.minusHours(1));
```

### Changes

#### Migration — `V10__round_lock_offset.sql`
```sql
ALTER TABLE tournament_settings ADD COLUMN round_lock_offset_minutes INTEGER NOT NULL DEFAULT 60;
```

#### Entity — `TournamentSettings`
Add field:
```java
@Column(name = "round_lock_offset_minutes", nullable = false)
private int roundLockOffsetMinutes;
```
Update builder default in `TournamentSettingsService.getSettings()` to include `roundLockOffsetMinutes(60)`.

#### Service — `TournamentSettingsService`
New method:
```java
@Transactional
public TournamentSettings updateRoundLockOffset(int minutes) {
    TournamentSettings s = getSettings();
    s.setRoundLockOffsetMinutes(minutes);
    return settingsRepository.save(s);
}
```

#### Service — `RoundWindowService`
- Inject `TournamentSettingsService`
- Replace `lastKickoff.minusHours(1)` with `lastKickoff.minusMinutes(tournamentSettingsService.getSettings().getRoundLockOffsetMinutes())`
- Add new method `recalculateAllRoundWindows()` that calls `recalculateAutoTimes()` for every existing round window (used when the setting changes)

#### Controller — `AdminSettingsController`
- `updateTournamentMode` POST handler: add `@RequestParam int roundLockOffsetMinutes` parameter
- Call `tournamentSettingsService.updateRoundLockOffset(roundLockOffsetMinutes)`
- After saving, call `roundWindowService.recalculateAllRoundWindows()` so all existing windows update immediately
- Inject `RoundWindowService` into controller

#### Template — `admin/settings.html`
Add within the Prediction Window Mode form:
```html
<div class="flex items-center gap-3">
  <label class="text-sm text-gray-600">Round lock offset (minutes before last match kickoff):</label>
  <input type="number" name="roundLockOffsetMinutes" min="1" max="360"
         th:value="${tournamentSettings != null ? tournamentSettings.roundLockOffsetMinutes : 60}"
         class="border border-gray-300 rounded px-2 py-1 w-20 text-sm"/>
</div>
```

---

## Summary of Files Changed

| File | Change |
|------|--------|
| `CommunityMembershipRepository` | New query: active members without prediction for match |
| `PredictionService` | New `createOnBehalfOf()` method |
| `AdminPredictionController` | Add `communityId` param, new `/create` POST endpoint |
| `templates/admin/predictions.html` | Community dropdown + missing members table |
| `templates/admin/backup.html` | Remove `@submit.prevent` from restore form |
| `application.properties` | Multipart size limits |
| `db/migration/V10__round_lock_offset.sql` | New migration |
| `TournamentSettings` | New `roundLockOffsetMinutes` field |
| `TournamentSettingsService` | New `updateRoundLockOffset()` method + builder default |
| `RoundWindowService` | Read offset from settings, new `recalculateAllRoundWindows()` |
| `AdminSettingsController` | Accept new param, call recalculate after save |
| `templates/admin/settings.html` | Round lock offset input field |
