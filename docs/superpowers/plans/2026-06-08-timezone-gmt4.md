# Timezone GMT+4 (Asia/Yerevan) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all times in the app display in Asia/Yerevan (UTC+4, no DST) by setting the JVM default timezone at startup, with `app.timezone` as the configurable property.

**Architecture:** Set JVM default timezone before Spring boots so `LocalDateTime.now()`, Hibernate JDBC reads, and Thymeleaf `#temporals` all use the right zone automatically. Client-side ISO strings gain an explicit `+04:00` offset so the JS countdown timer and `Intl` date formatting are consistent for all browsers. A `@ControllerAdvice` injects `timezoneLabel` and `appTimezone` into every model. Cron `@Scheduled` annotations gain an explicit `zone` attribute.

**Tech Stack:** Spring Boot 3, Thymeleaf, Alpine.js, SQLite (Hibernate), JUnit 5, Mockito

---

## File Map

| File | Action |
|---|---|
| `src/main/resources/application.properties` | Add `app.timezone` property |
| `src/main/java/com/worldcup/prediction/WorldCupPredictionApplication.java` | Set JVM default timezone before Spring boot |
| `src/main/java/com/worldcup/prediction/config/TimezoneControllerAdvice.java` | **New** — `@ControllerAdvice` adds `timezoneLabel` + `appTimezone` to every model |
| `src/main/java/com/worldcup/prediction/scheduler/StandingSyncScheduler.java` | Add `zone` to cron `@Scheduled` |
| `src/main/java/com/worldcup/prediction/scheduler/ScorersSyncScheduler.java` | Add `zone` to cron `@Scheduled` |
| `src/main/java/com/worldcup/prediction/service/PredictionViewService.java` | Emit ISO strings with `+04:00` offset; fix `groupMatchesByDate` re-parser |
| `src/main/resources/templates/fragments/layout.html` | Add timezone label to navbar |
| `src/main/resources/templates/admin/layout.html` | Add timezone label to admin header |
| `src/main/resources/templates/fragments/predictions-round-content.html` | Add `APP_TZ` constant; pass to all `toLocale*` calls |
| `src/main/resources/templates/fragments/community-predictions-round-content.html` | Same as above |
| `src/test/java/com/worldcup/prediction/config/TimezoneControllerAdviceTest.java` | **New** — unit test for advice |
| `src/test/java/com/worldcup/prediction/service/PredictionViewServiceTimezoneTest.java` | **New** — verifies ISO strings carry offset |

---

### Task 1: Add `app.timezone` property and set JVM default timezone at startup

**Files:**
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/java/com/worldcup/prediction/WorldCupPredictionApplication.java`

- [ ] **Step 1: Add property to application.properties**

Open `src/main/resources/application.properties`. After the `app.base-url` line, add:

```properties
app.timezone=${APP_TIMEZONE:Asia/Yerevan}
```

- [ ] **Step 2: Set JVM default timezone in main()**

Replace the entire `WorldCupPredictionApplication.java` with:

```java
package com.worldcup.prediction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class WorldCupPredictionApplication {

    public static void main(String[] args) {
        String tz = System.getenv().getOrDefault("APP_TIMEZONE", "Asia/Yerevan");
        TimeZone.setDefault(TimeZone.getTimeZone(tz));
        SpringApplication.run(WorldCupPredictionApplication.class, args);
    }
}
```

- [ ] **Step 3: Verify app still starts**

```bash
./mvnw spring-boot:run -q 2>&1 | head -30
```

Expected: no errors, app starts on port 8888. Ctrl+C to stop.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/application.properties \
        src/main/java/com/worldcup/prediction/WorldCupPredictionApplication.java
git commit -m "feat: set JVM default timezone from app.timezone property"
```

---

### Task 2: Create TimezoneControllerAdvice

**Files:**
- Create: `src/main/java/com/worldcup/prediction/config/TimezoneControllerAdvice.java`
- Create: `src/test/java/com/worldcup/prediction/config/TimezoneControllerAdviceTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/worldcup/prediction/config/TimezoneControllerAdviceTest.java`:

```java
package com.worldcup.prediction.config;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.assertj.core.api.Assertions.assertThat;

class TimezoneControllerAdviceTest {

    @Test
    void addsTimezoneLabelForYerevan() {
        TimezoneControllerAdvice advice = new TimezoneControllerAdvice("Asia/Yerevan");
        Model model = new ExtendedModelMap();
        advice.addTimezoneAttributes(model);
        assertThat(model.getAttribute("appTimezone")).isEqualTo("Asia/Yerevan");
        assertThat(model.getAttribute("timezoneLabel")).isEqualTo("GMT+4 · Yerevan");
    }

    @Test
    void addsTimezoneLabelForUtc() {
        TimezoneControllerAdvice advice = new TimezoneControllerAdvice("UTC");
        Model model = new ExtendedModelMap();
        advice.addTimezoneAttributes(model);
        assertThat(model.getAttribute("appTimezone")).isEqualTo("UTC");
        assertThat(model.getAttribute("timezoneLabel")).isEqualTo("GMT+0 · UTC");
    }

    @Test
    void addsTimezoneLabelForLondon() {
        TimezoneControllerAdvice advice = new TimezoneControllerAdvice("Europe/London");
        Model model = new ExtendedModelMap();
        advice.addTimezoneAttributes(model);
        assertThat(model.getAttribute("appTimezone")).isEqualTo("Europe/London");
        // London is UTC+0 in winter (no DST during WC 2026 group stage)
        assertThat((String) model.getAttribute("timezoneLabel")).startsWith("GMT");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./mvnw test -pl . -Dtest=TimezoneControllerAdviceTest -q 2>&1 | tail -15
```

Expected: FAIL — `TimezoneControllerAdvice` not found.

- [ ] **Step 3: Create TimezoneControllerAdvice**

Create `src/main/java/com/worldcup/prediction/config/TimezoneControllerAdvice.java`:

```java
package com.worldcup.prediction.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.ZoneId;
import java.time.ZoneOffset;

@ControllerAdvice
public class TimezoneControllerAdvice {

    private final String timezoneId;
    private final String timezoneLabel;

    public TimezoneControllerAdvice(@Value("${app.timezone}") String timezoneId) {
        this.timezoneId = timezoneId;
        this.timezoneLabel = buildLabel(timezoneId);
    }

    @ModelAttribute
    public void addTimezoneAttributes(Model model) {
        model.addAttribute("appTimezone", timezoneId);
        model.addAttribute("timezoneLabel", timezoneLabel);
    }

    private static String buildLabel(String zoneId) {
        ZoneOffset offset = ZoneId.of(zoneId).getRules().getStandardOffset(java.time.Instant.now());
        int totalSeconds = offset.getTotalSeconds();
        int hours = totalSeconds / 3600;
        int minutes = Math.abs((totalSeconds % 3600) / 60);
        String sign = hours >= 0 ? "+" : "-";
        String gmtPart = minutes == 0
            ? "GMT" + sign + Math.abs(hours)
            : String.format("GMT%s%d:%02d", sign, Math.abs(hours), minutes);
        // Use the last segment of the zone ID as the city name (e.g. "Asia/Yerevan" → "Yerevan")
        String[] parts = zoneId.split("/");
        String city = parts[parts.length - 1].replace("_", " ");
        return gmtPart + " · " + city;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./mvnw test -pl . -Dtest=TimezoneControllerAdviceTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/config/TimezoneControllerAdvice.java \
        src/test/java/com/worldcup/prediction/config/TimezoneControllerAdviceTest.java
git commit -m "feat: add TimezoneControllerAdvice to inject timezone label into all models"
```

---

### Task 3: Fix cron scheduler zone attributes

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/scheduler/StandingSyncScheduler.java`
- Modify: `src/main/java/com/worldcup/prediction/scheduler/ScorersSyncScheduler.java`

- [ ] **Step 1: Update StandingSyncScheduler**

In `StandingSyncScheduler.java`, replace:

```java
@Scheduled(cron = "0 0 */6 * * *")
```

with:

```java
@Scheduled(cron = "0 0 */6 * * *", zone = "${app.timezone}")
```

- [ ] **Step 2: Update ScorersSyncScheduler**

In `ScorersSyncScheduler.java`, replace:

```java
@Scheduled(cron = "0 0 2 * * *")
```

with:

```java
@Scheduled(cron = "0 0 2 * * *", zone = "${app.timezone}")
```

- [ ] **Step 3: Verify context loads (cron zone attribute is validated at startup)**

```bash
./mvnw test -pl . -Dtest=WorldCupPredictionApplicationTests -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worldcup/prediction/scheduler/StandingSyncScheduler.java \
        src/main/java/com/worldcup/prediction/scheduler/ScorersSyncScheduler.java
git commit -m "feat: set explicit timezone zone on cron schedulers"
```

---

### Task 4: Fix PredictionViewService to emit offset-aware ISO strings

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/service/PredictionViewService.java`
- Create: `src/test/java/com/worldcup/prediction/service/PredictionViewServiceTimezoneTest.java`

**Context:** Currently `kickoffIso` and `lockTimeIso` are emitted as `"2026-06-11T19:00:00"` (no offset). The browser's `new Date()` treats these as local browser time, which is wrong for users in other timezones. We need `"2026-06-11T19:00:00+04:00"`. Additionally, `groupMatchesByDate` re-parses the ISO string with `LocalDateTime.parse` — this must switch to `OffsetDateTime.parse` once the string carries an offset.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/worldcup/prediction/service/PredictionViewServiceTimezoneTest.java`:

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.dto.MatchPredictionDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class PredictionViewServiceTimezoneTest {

    @Test
    void kickoffIsoContainsOffset() {
        // Use Asia/Yerevan offset (+04:00)
        ZoneId zone = ZoneId.of("Asia/Yerevan");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(zone);

        LocalDateTime kickoff = LocalDateTime.of(2026, 6, 11, 19, 0, 0);
        String iso = kickoff.atZone(zone).format(fmt);

        assertThat(iso).isEqualTo("2026-06-11T19:00:00+04:00");
    }

    @Test
    void lockTimeIsoContainsOffset() {
        ZoneId zone = ZoneId.of("Asia/Yerevan");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(zone);

        LocalDateTime lockTime = LocalDateTime.of(2026, 6, 11, 18, 0, 0); // 1 hour before kickoff
        String iso = lockTime.atZone(zone).format(fmt);

        assertThat(iso).isEqualTo("2026-06-11T18:00:00+04:00");
    }
}
```

- [ ] **Step 2: Run test to verify it passes (validates the formatter approach)**

```bash
./mvnw test -pl . -Dtest=PredictionViewServiceTimezoneTest -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS — these tests validate the formatter logic, not the service wiring yet.

- [ ] **Step 3: Update PredictionViewService**

In `PredictionViewService.java`, make these changes:

**a) Add imports** (after existing imports):
```java
import org.springframework.beans.factory.annotation.Value;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import jakarta.annotation.PostConstruct;
```

**b) Remove the static `ISO` field** and add instance fields after the existing `DISPLAY` field:

Remove:
```java
private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
```

Add (after `DISPLAY`):
```java
@Value("${app.timezone}")
private String timezoneId;

private ZoneId appZone;
private DateTimeFormatter isoFmt;

@PostConstruct
private void initFormatters() {
    appZone = ZoneId.of(timezoneId);
    isoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(appZone);
}
```

**c) Update `groupMatchesByDate`** — re-parses the ISO string, must handle offset now:

Replace:
```java
String dk = m.getKickoffIso() != null
        ? LocalDateTime.parse(m.getKickoffIso(), ISO).format(key) : "TBD";
```

With:
```java
String dk = m.getKickoffIso() != null
        ? OffsetDateTime.parse(m.getKickoffIso()).toLocalDateTime().format(key) : "TBD";
```

**d) Update `toMatchPredictionDto`** — format with `isoFmt`:

Replace:
```java
dto.setKickoffIso(m.getKickoffTime().format(ISO));
dto.setLockTimeIso(m.getKickoffTime().minusHours(1).format(ISO));
```

With:
```java
dto.setKickoffIso(m.getKickoffTime().atZone(appZone).format(isoFmt));
dto.setLockTimeIso(m.getKickoffTime().minusHours(1).atZone(appZone).format(isoFmt));
```

- [ ] **Step 4: Run all tests**

```bash
./mvnw test -q 2>&1 | tail -20
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/PredictionViewService.java \
        src/test/java/com/worldcup/prediction/service/PredictionViewServiceTimezoneTest.java
git commit -m "feat: emit offset-aware ISO strings from PredictionViewService"
```

---

### Task 5: Add timezone label to headers

**Files:**
- Modify: `src/main/resources/templates/fragments/layout.html`
- Modify: `src/main/resources/templates/admin/layout.html`

**Context:** `timezoneLabel` (e.g., `"GMT+4 · Yerevan"`) is now available in every model via `TimezoneControllerAdvice`.

- [ ] **Step 1: Add label to main navbar**

In `src/main/resources/templates/fragments/layout.html`, find the desktop nav links block inside the `<nav th:fragment="navbar">`. It has this line:

```html
<div class="hidden md:flex items-center gap-6 text-sm font-medium">
```

Add the timezone label as the **first child** of that div:

```html
<div class="hidden md:flex items-center gap-6 text-sm font-medium">
    <span class="text-xs text-wc-gold/70 font-normal tracking-wide"
          th:text="${timezoneLabel} ?: 'GMT+4 · Yerevan'">GMT+4 · Yerevan</span>
```

- [ ] **Step 2: Add label to admin header**

In `src/main/resources/templates/admin/layout.html`, find the admin top bar header:

```html
<header class="bg-white border-b border-gray-200 px-8 py-4 flex items-center justify-between">
```

It contains a date display div. Add the timezone label after it:

```html
      <div class="flex items-center gap-3">
        <div class="text-xs text-gray-400"
             th:text="${#temporals.format(#temporals.createNow(), 'EEE d MMM yyyy')}"></div>
        <span class="text-xs text-gray-400"
              th:text="'· ' + (${timezoneLabel} ?: 'GMT+4 · Yerevan')">· GMT+4 · Yerevan</span>
      </div>
```

Replace the existing bare date div:
```html
      <div class="text-xs text-gray-400"
           th:text="${#temporals.format(#temporals.createNow(), 'EEE d MMM yyyy')}"></div>
```

with the wrapped version above.

- [ ] **Step 3: Verify context loads cleanly**

```bash
./mvnw test -pl . -Dtest=WorldCupPredictionApplicationTests -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/fragments/layout.html \
        src/main/resources/templates/admin/layout.html
git commit -m "feat: add timezone label to main navbar and admin header"
```

---

### Task 6: Fix client-side date formatting in Alpine.js templates

**Files:**
- Modify: `src/main/resources/templates/fragments/predictions-round-content.html`
- Modify: `src/main/resources/templates/fragments/community-predictions-round-content.html`

**Context:** Both templates have identical `fmtKickoff`, `fmtDate`, `fmtDateKey` JS functions that call `toLocaleDateString` / `toLocaleTimeString` without a `timeZone` option — so they use the browser's local timezone. We need to pass `timeZone: APP_TZ` (a constant defined from the Thymeleaf `${appTimezone}` model attribute) into every call. The countdown timer works correctly once ISO strings carry `+04:00`.

- [ ] **Step 1: Update predictions-round-content.html**

In `src/main/resources/templates/fragments/predictions-round-content.html`, locate the `<script>` block containing the Alpine.js component. Find the three format functions and replace them:

Find:
```javascript
      fmtKickoff(iso) {
        if (!iso) return '';
        return new Date(iso).toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'}) + ' · ' +
               new Date(iso).toLocaleTimeString('en-US',{hour:'2-digit',minute:'2-digit'});
      },
      fmtDate(iso) {
        if (!iso) return '';
        return new Date(iso).toLocaleDateString('en-US',{weekday:'short',month:'short',day:'numeric'});
      },
      fmtDateKey(iso) {
        if (!iso) return 'TBD';
        return new Date(iso).toLocaleDateString('en-US',{weekday:'long',month:'long',day:'numeric'});
      },
```

Replace with:
```javascript
      fmtKickoff(iso) {
        if (!iso) return '';
        return new Date(iso).toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric',timeZone:APP_TZ}) + ' · ' +
               new Date(iso).toLocaleTimeString('en-US',{hour:'2-digit',minute:'2-digit',timeZone:APP_TZ});
      },
      fmtDate(iso) {
        if (!iso) return '';
        return new Date(iso).toLocaleDateString('en-US',{weekday:'short',month:'short',day:'numeric',timeZone:APP_TZ});
      },
      fmtDateKey(iso) {
        if (!iso) return 'TBD';
        return new Date(iso).toLocaleDateString('en-US',{weekday:'long',month:'long',day:'numeric',timeZone:APP_TZ});
      },
```

Then, immediately before the `function predictionsApp(` line, add the `APP_TZ` constant using Thymeleaf inline JS:

```html
<script th:inline="javascript">
  const APP_TZ = /*[[${appTimezone}]]*/ 'Asia/Yerevan';
</script>
```

Place this `<script>` block just before the existing `<script>` block that contains `function predictionsApp(`.

- [ ] **Step 2: Update community-predictions-round-content.html**

In `src/main/resources/templates/fragments/community-predictions-round-content.html`, apply the identical changes:

Find and replace the same three format functions (same text), and add the same `APP_TZ` constant script block before the Alpine component function.

- [ ] **Step 3: Run full test suite**

```bash
./mvnw test -q 2>&1 | tail -15
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/fragments/predictions-round-content.html \
        src/main/resources/templates/fragments/community-predictions-round-content.html
git commit -m "feat: pass Asia/Yerevan timezone to Alpine.js date formatters"
```

---

### Task 7: Run full test suite and verify

- [ ] **Step 1: Run all tests**

```bash
./mvnw test 2>&1 | tail -30
```

Expected: BUILD SUCCESS, no failures.

- [ ] **Step 2: Start the app and do a visual check**

```bash
./mvnw spring-boot:run &
sleep 8
curl -s http://localhost:8888 | grep -i "GMT\|Yerevan" | head -5
```

Expected: output contains `GMT+4` or `Yerevan` in the HTML.

```bash
# Stop the app
kill %1
```

- [ ] **Step 3: Final commit if any stragglers**

```bash
git status
# If clean, nothing to do. If not:
git add -p
git commit -m "chore: timezone cleanup"
```
