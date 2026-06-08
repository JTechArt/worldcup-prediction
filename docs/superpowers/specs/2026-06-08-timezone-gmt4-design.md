# Timezone: GMT+4 (Asia/Yerevan) — Design Spec

**Date:** 2026-06-08  
**Status:** Approved

## Summary

All users are in Armenia (UTC+4, no DST). Every time visible in the UI must display in `Asia/Yerevan` timezone. The timezone is configurable via `app.timezone` / `APP_TIMEZONE` env var. Schedulers that fire at specific times of day must also respect the configured zone. A timezone label is shown in the header.

---

## 1. Configuration

### `application.properties`

Add:
```
app.timezone=${APP_TIMEZONE:Asia/Yerevan}
```

### `WorldCupPredictionApplication.main()`

Set JVM default timezone before Spring boots:
```java
public static void main(String[] args) {
    String tz = System.getenv().getOrDefault("APP_TIMEZONE", "Asia/Yerevan");
    TimeZone.setDefault(TimeZone.getTimeZone(tz));
    SpringApplication.run(WorldCupPredictionApplication.class, args);
}
```

**Why this works:** SQLite stores `kickoff_time` as epoch milliseconds (UTC). Hibernate reads those into `LocalDateTime` using the JVM's default timezone. With JVM set to `Asia/Yerevan`, all reads, `LocalDateTime.now()` calls, `LocalDate.now()` calls, and Thymeleaf `#temporals.format()` rendering automatically use Armenia time — zero changes needed in services or schedulers for "now" calculations.

---

## 2. Cron Schedulers

Two schedulers use cron expressions (time-of-day sensitive). Add explicit `zone` attribute to survive any future JVM timezone misconfiguration:

| Scheduler | Current cron | Change |
|---|---|---|
| `StandingSyncScheduler` | `"0 0 */6 * * *"` | Add `zone = "${app.timezone}"` |
| `ScorersSyncScheduler` | `"0 0 2 * * *"` | Add `zone = "${app.timezone}"` |

`fixedDelay` schedulers (`MatchResultScheduler`, `LineupSyncScheduler`, `NotificationScheduler`) are interval-based, not time-of-day — no change needed.

---

## 3. Client-side ISO Strings

### Problem

`PredictionViewService` emits kickoff ISO strings without offset (e.g. `"2026-06-11T19:00:00"`). The browser's `new Date()` treats offset-less strings as **local time**, so users in different zones see wrong times.

### Fix in `PredictionViewService`

Inject the configured `ZoneId` and emit strings with explicit offset:

```java
@Value("${app.timezone}")
private String timezoneId;

// Replace static formatter with:
private DateTimeFormatter isoFormatter() {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
            .withZone(ZoneId.of(timezoneId));
}
```

Apply `isoFormatter()` when formatting `kickoffTime` and `lockTimeIso` in `MatchPredictionDto`. Result: `"2026-06-11T19:00:00+04:00"`.

### Fix in Alpine.js templates

Both `predictions-round-content.html` and `community-predictions-round-content.html` have identical `fmtKickoff`, `fmtDate`, `fmtDateKey` functions.

Pass the configured timezone string from the controller into a Thymeleaf variable `appTimezone`, then use it in the JS:

```js
const APP_TZ = /*[[${appTimezone}]]*/ 'Asia/Yerevan';

fmtKickoff(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('en-US', {month:'short', day:'numeric', year:'numeric', timeZone: APP_TZ})
       + ' · '
       + new Date(iso).toLocaleTimeString('en-US', {hour:'2-digit', minute:'2-digit', timeZone: APP_TZ});
},
fmtDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('en-US', {weekday:'short', month:'short', day:'numeric', timeZone: APP_TZ});
},
fmtDateKey(iso) {
  if (!iso) return 'TBD';
  return new Date(iso).toLocaleDateString('en-US', {weekday:'long', month:'long', day:'numeric', timeZone: APP_TZ});
},
```

The countdown timer (`new Date(lockTimeIso) - new Date()`) already works correctly once the ISO string carries the `+04:00` offset.

### Controller change

`appTimezone` and `timezoneLabel` are added to every model via a `@ControllerAdvice` (see Section 4), so no per-controller change is needed for the templates to receive these values.

---

## 4. Header Timezone Label

### Main layout (`fragments/layout.html`)

Add a small label visible on all pages:
```html
<span class="text-xs text-gray-400" th:text="'All times: GMT+4 · Yerevan'">All times: GMT+4 · Yerevan</span>
```

Use a model attribute for the display string, set via a `HandlerInterceptor` or `@ControllerAdvice` that reads `app.timezone` and computes a human-readable label (e.g., `"GMT+4 · Yerevan"` for `Asia/Yerevan`).

**Implementation:** Create `TimezoneControllerAdvice` (annotated `@ControllerAdvice`) with a `@ModelAttribute` method that adds `timezoneLabel` and `appTimezone` to every model in the app. No `WebMvcConfig` registration needed. The label format: `"GMT+4 · Yerevan"` (derived from the ZoneId offset and zone ID short name).

### Admin layout (`admin/layout.html`)

Same label in the admin header bar.

---

## 5. Files Changed

| File | Change |
|---|---|
| `WorldCupPredictionApplication.java` | Set JVM default timezone before Spring boot |
| `application.properties` | Add `app.timezone` property |
| `scheduler/StandingSyncScheduler.java` | Add `zone` to `@Scheduled` |
| `scheduler/ScorersSyncScheduler.java` | Add `zone` to `@Scheduled` |
| `service/PredictionViewService.java` | Inject timezone; emit ISO strings with `+04:00` offset |
| `config/TimezoneControllerAdvice.java` | **New:** `@ControllerAdvice` adds `timezoneLabel` + `appTimezone` to all models |
| `templates/fragments/layout.html` | Add timezone label in header using `${timezoneLabel}` |
| `templates/admin/layout.html` | Add timezone label in admin header using `${timezoneLabel}` |
| `templates/fragments/predictions-round-content.html` | Add `APP_TZ` const; pass to all date format calls |
| `templates/fragments/community-predictions-round-content.html` | Same as above |

---

## 6. Testing

- Unit test `PredictionViewService`: assert ISO strings contain `+04:00` offset.
- Scheduler tests: verify cron `zone` attribute is set (via Spring integration test).
- Template smoke test: start app, open fixtures/predictions — times show Armenia local time.
- Verify `FixtureViewDto.isToday()` returns correct result when JVM is set to `Asia/Yerevan`.
- Verify countdown timer counts down to the correct UTC moment.

---

## Out of Scope

- Per-user timezone preferences
- DST handling (Armenia has no DST since 2012)
- Migrating existing DB data (epoch ms is timezone-neutral; no migration needed)
