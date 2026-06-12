# Daily Prediction Windows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add DAILY mode alongside existing ROUND mode so admins can open prediction windows for a subset of matches (typically one day's worth), switchable globally or per community.

**Architecture:** New `PredictionWindow` entity groups matches under an open/close window. `TournamentSettings` singleton stores the active mode (ROUND/DAILY). `isWindowOpen()` in `PredictionService` becomes mode-aware, delegating to `RoundWindowService` (existing) or new `PredictionWindowService`. Communities can override the global mode.

**Tech Stack:** Spring Boot 3, JPA/Hibernate, Flyway (SQLite dev / PostgreSQL prod), Thymeleaf, Alpine.js, Lombok, JUnit 5 + Mockito

**Spec:** `docs/superpowers/specs/2026-06-12-daily-prediction-windows-design.md`

---

## File Map

**New files:**
- `src/main/java/.../domain/enums/WindowMode.java`
- `src/main/java/.../domain/enums/PredictionWindowStatus.java`
- `src/main/java/.../domain/TournamentSettings.java`
- `src/main/java/.../domain/PredictionWindow.java`
- `src/main/java/.../repository/TournamentSettingsRepository.java`
- `src/main/java/.../repository/PredictionWindowRepository.java`
- `src/main/java/.../service/TournamentSettingsService.java`
- `src/main/java/.../service/PredictionWindowService.java`
- `src/main/java/.../scheduler/PredictionWindowScheduler.java`
- `src/main/java/.../controller/admin/AdminPredictionWindowController.java`
- `src/main/java/.../controller/community/CommunityAdminWindowSettingsController.java`
- `src/main/resources/db/migration/V11__tournament_settings.sql`
- `src/main/resources/db/migration/V12__prediction_windows.sql`
- `src/main/resources/db/migration/V13__community_window_mode_and_submission_window_fk.sql`
- `src/main/resources/templates/admin/prediction-windows.html`
- `src/main/resources/templates/admin/prediction-window-form.html`
- `src/main/resources/templates/community/admin/window-settings.html`
- `src/test/java/.../repository/PredictionWindowRepositoryTest.java`
- `src/test/java/.../service/TournamentSettingsServiceTest.java`
- `src/test/java/.../service/PredictionWindowServiceTest.java`
- `src/test/java/.../scheduler/PredictionWindowSchedulerTest.java`

**Modified files:**
- `src/main/java/.../domain/Community.java` — add `windowModeOverride`
- `src/main/java/.../domain/RoundSubmission.java` — add `predictionWindowId`
- `src/main/java/.../repository/RoundSubmissionRepository.java` — add window-based queries
- `src/main/java/.../repository/MatchRepository.java` — add ordered + adjacent-match queries
- `src/main/java/.../service/PredictionService.java` — mode-aware `isWindowOpen`
- `src/main/java/.../service/PredictionViewService.java` — DAILY mode support
- `src/main/java/.../service/RoundSubmissionService.java` — DAILY mode upsert/query
- `src/main/java/.../web/CommunityWindowBannerAdvice.java` — DAILY mode close time
- `src/main/java/.../web/AdminWindowBannerAdvice.java` — DAILY mode close time
- `src/main/java/.../scheduler/NotificationScheduler.java` — detect new PredictionWindow opens
- `src/main/java/.../controller/admin/AdminSettingsController.java` — add tournament mode endpoint
- `src/main/resources/templates/admin/settings.html` — add tournament mode section
- `src/main/resources/templates/layout/community-base.html` — add Window Settings nav link

---

## Task 1: Enums, TournamentSettings entity, V11 migration

**Files:**
- Create: `src/main/java/com/worldcup/prediction/domain/enums/WindowMode.java`
- Create: `src/main/java/com/worldcup/prediction/domain/enums/PredictionWindowStatus.java`
- Create: `src/main/resources/db/migration/V11__tournament_settings.sql`
- Create: `src/main/java/com/worldcup/prediction/domain/TournamentSettings.java`
- Create: `src/main/java/com/worldcup/prediction/repository/TournamentSettingsRepository.java`

- [ ] **Step 1: Create WindowMode enum**

```java
// src/main/java/com/worldcup/prediction/domain/enums/WindowMode.java
package com.worldcup.prediction.domain.enums;

public enum WindowMode {
    ROUND,
    DAILY
}
```

- [ ] **Step 2: Create PredictionWindowStatus enum**

```java
// src/main/java/com/worldcup/prediction/domain/enums/PredictionWindowStatus.java
package com.worldcup.prediction.domain.enums;

public enum PredictionWindowStatus {
    DRAFT,
    SCHEDULED,
    OPEN,
    CLOSED
}
```

- [ ] **Step 3: Write V11 migration**

```sql
-- src/main/resources/db/migration/V11__tournament_settings.sql
CREATE TABLE tournament_settings (
    id                                 INTEGER PRIMARY KEY,
    window_mode                        VARCHAR(10)  NOT NULL DEFAULT 'ROUND',
    daily_window_close_offset_minutes  INTEGER      NOT NULL DEFAULT 30,
    updated_at                         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO tournament_settings (id, window_mode, daily_window_close_offset_minutes)
VALUES (1, 'ROUND', 30);
```

- [ ] **Step 4: Create TournamentSettings entity**

```java
// src/main/java/com/worldcup/prediction/domain/TournamentSettings.java
package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.WindowMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tournament_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TournamentSettings {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "window_mode", nullable = false, length = 10)
    private WindowMode windowMode;

    @Column(name = "daily_window_close_offset_minutes", nullable = false)
    private int dailyWindowCloseOffsetMinutes;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 5: Create TournamentSettingsRepository**

```java
// src/main/java/com/worldcup/prediction/repository/TournamentSettingsRepository.java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.TournamentSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TournamentSettingsRepository extends JpaRepository<TournamentSettings, Long> {
}
```

- [ ] **Step 6: Run the app to verify migration applies cleanly**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | grep -E "Flyway|ERROR" | head -20
```
Expected: `Successfully applied 1 migration to schema` (V11)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/worldcup/prediction/domain/enums/WindowMode.java \
        src/main/java/com/worldcup/prediction/domain/enums/PredictionWindowStatus.java \
        src/main/java/com/worldcup/prediction/domain/TournamentSettings.java \
        src/main/java/com/worldcup/prediction/repository/TournamentSettingsRepository.java \
        src/main/resources/db/migration/V11__tournament_settings.sql
git commit -m "feat: add WindowMode enum, TournamentSettings entity, V11 migration"
```

---

## Task 2: PredictionWindow entity, V12 migration, Repository

**Files:**
- Create: `src/main/resources/db/migration/V12__prediction_windows.sql`
- Create: `src/main/java/com/worldcup/prediction/domain/PredictionWindow.java`
- Create: `src/main/java/com/worldcup/prediction/repository/PredictionWindowRepository.java`
- Create: `src/test/java/com/worldcup/prediction/repository/PredictionWindowRepositoryTest.java`

- [ ] **Step 1: Write V12 migration**

```sql
-- src/main/resources/db/migration/V12__prediction_windows.sql
CREATE TABLE prediction_window (
    id                  INTEGER PRIMARY KEY,
    label               VARCHAR(100) NOT NULL,
    open_at             TIMESTAMP    NOT NULL,
    close_at            TIMESTAMP,
    effective_close_at  TIMESTAMP,
    override_status     VARCHAR(20),
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    community_id        INTEGER      REFERENCES communities(id) ON DELETE CASCADE,
    created_by_id       INTEGER      REFERENCES users(id),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX prediction_window_status_idx    ON prediction_window(status);
CREATE INDEX prediction_window_community_idx ON prediction_window(community_id);

CREATE TABLE prediction_window_match (
    window_id  INTEGER NOT NULL REFERENCES prediction_window(id) ON DELETE CASCADE,
    match_id   INTEGER NOT NULL REFERENCES matches(id)           ON DELETE CASCADE,
    PRIMARY KEY (window_id, match_id)
);

CREATE UNIQUE INDEX pw_match_global_unique_idx
    ON prediction_window_match(match_id)
    WHERE (SELECT community_id FROM prediction_window WHERE id = window_id) IS NULL;
```

- [ ] **Step 2: Create PredictionWindow entity**

```java
// src/main/java/com/worldcup/prediction/domain/PredictionWindow.java
package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.PredictionWindowStatus;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "prediction_window")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PredictionWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "open_at", nullable = false)
    private LocalDateTime openAt;

    @Column(name = "close_at")
    private LocalDateTime closeAt;

    @Column(name = "effective_close_at")
    private LocalDateTime effectiveCloseAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_status", length = 20)
    private RoundOverrideStatus overrideStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PredictionWindowStatus status = PredictionWindowStatus.DRAFT;

    @Column(name = "community_id")
    private Long communityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "prediction_window_match",
            joinColumns = @JoinColumn(name = "window_id"),
            inverseJoinColumns = @JoinColumn(name = "match_id"))
    @Builder.Default
    private Set<Match> matches = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: Create PredictionWindowRepository**

```java
// src/main/java/com/worldcup/prediction/repository/PredictionWindowRepository.java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.enums.PredictionWindowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PredictionWindowRepository extends JpaRepository<PredictionWindow, Long> {

    List<PredictionWindow> findByCommunityIdIsNullOrderByOpenAtAsc();

    List<PredictionWindow> findByCommunityIdOrderByOpenAtAsc(Long communityId);

    List<PredictionWindow> findByStatusAndOpenAtLessThanEqual(PredictionWindowStatus status, LocalDateTime now);

    @Query("SELECT pw FROM PredictionWindow pw WHERE pw.status = :status " +
           "AND pw.effectiveCloseAt <= :now AND pw.overrideStatus <> com.worldcup.prediction.domain.enums.RoundOverrideStatus.FORCE_OPEN")
    List<PredictionWindow> findExpiredOpenWindows(PredictionWindowStatus status, LocalDateTime now);

    @Query("SELECT pw FROM PredictionWindow pw JOIN pw.matches m " +
           "WHERE m.id = :matchId AND pw.communityId IS NULL AND pw.status = 'OPEN'")
    Optional<PredictionWindow> findOpenGlobalWindowForMatch(Long matchId);

    @Query("SELECT pw FROM PredictionWindow pw JOIN pw.matches m " +
           "WHERE m.id = :matchId AND pw.communityId = :communityId AND pw.status = 'OPEN'")
    Optional<PredictionWindow> findOpenCommunityWindowForMatch(Long matchId, Long communityId);

    @Query("SELECT pw FROM PredictionWindow pw JOIN pw.matches m " +
           "WHERE m.id = :matchId AND pw.communityId IS NULL AND pw.overrideStatus = 'FORCE_OPEN'")
    Optional<PredictionWindow> findForceOpenGlobalWindowForMatch(Long matchId);

    @Query("SELECT pw FROM PredictionWindow pw JOIN pw.matches m " +
           "WHERE m.id = :matchId AND pw.communityId = :communityId AND pw.overrideStatus = 'FORCE_OPEN'")
    Optional<PredictionWindow> findForceOpenCommunityWindowForMatch(Long matchId, Long communityId);
}
```

- [ ] **Step 4: Write failing repository test**

```java
// src/test/java/com/worldcup/prediction/repository/PredictionWindowRepositoryTest.java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.domain.enums.PredictionWindowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PredictionWindowRepositoryTest {

    @Autowired PredictionWindowRepository windowRepository;
    @Autowired MatchRepository matchRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private Match match;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM prediction_window_match");
        jdbcTemplate.execute("DELETE FROM prediction_window");
        jdbcTemplate.execute("DELETE FROM predictions");
        jdbcTemplate.execute("DELETE FROM matches");

        match = matchRepository.save(Match.builder()
                .matchNumber(1).stage(MatchStage.GROUP).roundLabel("Matchday 1")
                .kickoffTime(LocalDateTime.now().plusHours(3))
                .status(MatchStatus.SCHEDULED).build());
    }

    @Test
    void findOpenGlobalWindowForMatch_returnsWindowContainingMatch() {
        PredictionWindow window = windowRepository.save(PredictionWindow.builder()
                .label("June 14")
                .openAt(LocalDateTime.now().minusHours(1))
                .status(PredictionWindowStatus.OPEN)
                .matches(Set.of(match))
                .build());

        Optional<PredictionWindow> found = windowRepository.findOpenGlobalWindowForMatch(match.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(window.getId());
    }

    @Test
    void findOpenGlobalWindowForMatch_returnsEmpty_whenWindowNotOpen() {
        windowRepository.save(PredictionWindow.builder()
                .label("June 14")
                .openAt(LocalDateTime.now().minusHours(1))
                .status(PredictionWindowStatus.DRAFT)
                .matches(Set.of(match))
                .build());

        Optional<PredictionWindow> found = windowRepository.findOpenGlobalWindowForMatch(match.getId());
        assertThat(found).isEmpty();
    }

    @Test
    void findByCommunityIdIsNull_returnsOnlyGlobalWindows() {
        windowRepository.save(PredictionWindow.builder()
                .label("Global").openAt(LocalDateTime.now())
                .status(PredictionWindowStatus.DRAFT).build());
        windowRepository.save(PredictionWindow.builder()
                .label("Community").openAt(LocalDateTime.now())
                .communityId(42L).status(PredictionWindowStatus.DRAFT).build());

        List<PredictionWindow> globals = windowRepository.findByCommunityIdIsNullOrderByOpenAtAsc();
        assertThat(globals).hasSize(1);
        assertThat(globals.get(0).getLabel()).isEqualTo("Global");
    }

    @Test
    void findByStatusAndOpenAtLessThanEqual_returnsScheduledReadyToActivate() {
        LocalDateTime now = LocalDateTime.now();
        windowRepository.save(PredictionWindow.builder()
                .label("Ready").openAt(now.minusMinutes(5))
                .status(PredictionWindowStatus.SCHEDULED).build());
        windowRepository.save(PredictionWindow.builder()
                .label("Future").openAt(now.plusHours(2))
                .status(PredictionWindowStatus.SCHEDULED).build());

        List<PredictionWindow> ready = windowRepository.findByStatusAndOpenAtLessThanEqual(
                PredictionWindowStatus.SCHEDULED, now);
        assertThat(ready).hasSize(1);
        assertThat(ready.get(0).getLabel()).isEqualTo("Ready");
    }
}
```

- [ ] **Step 5: Run test — expect it to pass (JPA entity scan picks up new entity)**

```bash
./mvnw test -pl . -Dtest=PredictionWindowRepositoryTest -q
```
Expected: `Tests run: 4, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V12__prediction_windows.sql \
        src/main/java/com/worldcup/prediction/domain/PredictionWindow.java \
        src/main/java/com/worldcup/prediction/repository/PredictionWindowRepository.java \
        src/test/java/com/worldcup/prediction/repository/PredictionWindowRepositoryTest.java
git commit -m "feat: add PredictionWindow entity, repository, V12 migration"
```

---

## Task 3: V13 migration — extend Community + RoundSubmission

**Files:**
- Create: `src/main/resources/db/migration/V13__community_window_mode_and_submission_window_fk.sql`
- Modify: `src/main/java/com/worldcup/prediction/domain/Community.java`
- Modify: `src/main/java/com/worldcup/prediction/domain/RoundSubmission.java`
- Modify: `src/main/java/com/worldcup/prediction/repository/RoundSubmissionRepository.java`

- [ ] **Step 1: Write V13 migration**

```sql
-- src/main/resources/db/migration/V13__community_window_mode_and_submission_window_fk.sql
ALTER TABLE communities ADD COLUMN window_mode_override VARCHAR(10);

ALTER TABLE round_submissions
    ADD COLUMN prediction_window_id INTEGER REFERENCES prediction_window(id) ON DELETE SET NULL;

CREATE INDEX round_submissions_window_idx
    ON round_submissions(community_id, prediction_window_id)
    WHERE prediction_window_id IS NOT NULL;
```

- [ ] **Step 2: Add windowModeOverride to Community entity**

In `Community.java`, add after the `slug` field:

```java
import com.worldcup.prediction.domain.enums.WindowMode;

@Enumerated(EnumType.STRING)
@Column(name = "window_mode_override", length = 10)
private WindowMode windowModeOverride;
```

- [ ] **Step 3: Add predictionWindowId to RoundSubmission entity**

In `RoundSubmission.java`, add after `roundLabel` and update the `@Table` annotation:

```java
// Replace @Table annotation:
@Table(name = "round_submissions",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_round_submissions",
               columnNames = {"user_id", "community_id", "round_label"}))

// Add field after roundLabel:
@Column(name = "prediction_window_id")
private Long predictionWindowId;
```

- [ ] **Step 4: Add window-based queries to RoundSubmissionRepository**

Open `src/main/java/com/worldcup/prediction/repository/RoundSubmissionRepository.java` and add:

```java
Optional<RoundSubmission> findByUserIdAndCommunityIdAndPredictionWindowId(
        Long userId, Long communityId, Long predictionWindowId);

boolean existsByUserIdAndCommunityIdAndPredictionWindowId(
        Long userId, Long communityId, Long predictionWindowId);

List<RoundSubmission> findByCommunityIdAndPredictionWindowId(
        Long communityId, Long predictionWindowId);
```

- [ ] **Step 5: Run existing tests to confirm no regressions**

```bash
./mvnw test -q
```
Expected: all previously passing tests still pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V13__community_window_mode_and_submission_window_fk.sql \
        src/main/java/com/worldcup/prediction/domain/Community.java \
        src/main/java/com/worldcup/prediction/domain/RoundSubmission.java \
        src/main/java/com/worldcup/prediction/repository/RoundSubmissionRepository.java
git commit -m "feat: V13 migration — community window mode override, submission window FK"
```

---

## Task 4: TournamentSettingsService

**Files:**
- Create: `src/main/java/com/worldcup/prediction/service/TournamentSettingsService.java`
- Create: `src/test/java/com/worldcup/prediction/service/TournamentSettingsServiceTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/worldcup/prediction/service/TournamentSettingsServiceTest.java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.TournamentSettings;
import com.worldcup.prediction.domain.enums.WindowMode;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.TournamentSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TournamentSettingsServiceTest {

    @Mock TournamentSettingsRepository settingsRepository;
    @Mock CommunityRepository communityRepository;
    @InjectMocks TournamentSettingsService service;

    private TournamentSettings defaultSettings;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        defaultSettings = TournamentSettings.builder()
                .id(1L).windowMode(WindowMode.ROUND).dailyWindowCloseOffsetMinutes(30).build();
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(defaultSettings));
    }

    @Test
    void getEffectiveMode_returnsCommunityOverrideWhenSet() {
        Community c = new Community();
        c.setWindowModeOverride(WindowMode.DAILY);
        when(communityRepository.findById(5L)).thenReturn(Optional.of(c));

        assertThat(service.getEffectiveMode(5L)).isEqualTo(WindowMode.DAILY);
    }

    @Test
    void getEffectiveMode_fallsBackToGlobalWhenNoOverride() {
        Community c = new Community();
        c.setWindowModeOverride(null);
        when(communityRepository.findById(5L)).thenReturn(Optional.of(c));

        assertThat(service.getEffectiveMode(5L)).isEqualTo(WindowMode.ROUND);
    }

    @Test
    void getEffectiveMode_returnsGlobalWhenCommunityIdNull() {
        assertThat(service.getEffectiveMode(null)).isEqualTo(WindowMode.ROUND);
        verifyNoInteractions(communityRepository);
    }

    @Test
    void updateMode_savesNewMode() {
        when(settingsRepository.save(any())).thenReturn(defaultSettings);
        service.updateMode(WindowMode.DAILY);
        verify(settingsRepository).save(argThat(s -> s.getWindowMode() == WindowMode.DAILY));
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./mvnw test -Dtest=TournamentSettingsServiceTest -q 2>&1 | tail -5
```
Expected: compile error — `TournamentSettingsService` doesn't exist yet.

- [ ] **Step 3: Implement TournamentSettingsService**

```java
// src/main/java/com/worldcup/prediction/service/TournamentSettingsService.java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.TournamentSettings;
import com.worldcup.prediction.domain.enums.WindowMode;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.TournamentSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TournamentSettingsService {

    private final TournamentSettingsRepository settingsRepository;
    private final CommunityRepository communityRepository;

    public TournamentSettings getSettings() {
        return settingsRepository.findById(1L)
                .orElseGet(() -> settingsRepository.save(
                        TournamentSettings.builder()
                                .id(1L).windowMode(WindowMode.ROUND)
                                .dailyWindowCloseOffsetMinutes(30).build()));
    }

    public WindowMode getEffectiveMode(Long communityId) {
        if (communityId != null) {
            communityRepository.findById(communityId).ifPresent(c -> {
            });
            var override = communityRepository.findById(communityId)
                    .map(c -> c.getWindowModeOverride()).orElse(null);
            if (override != null) return override;
        }
        return getSettings().getWindowMode();
    }

    @Transactional
    public TournamentSettings updateMode(WindowMode mode) {
        TournamentSettings s = getSettings();
        s.setWindowMode(mode);
        return settingsRepository.save(s);
    }

    @Transactional
    public TournamentSettings updateCloseOffset(int minutes) {
        TournamentSettings s = getSettings();
        s.setDailyWindowCloseOffsetMinutes(minutes);
        return settingsRepository.save(s);
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
./mvnw test -Dtest=TournamentSettingsServiceTest -q
```
Expected: `Tests run: 4, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/TournamentSettingsService.java \
        src/test/java/com/worldcup/prediction/service/TournamentSettingsServiceTest.java
git commit -m "feat: add TournamentSettingsService with mode-aware getEffectiveMode"
```

---

## Task 5: PredictionWindowService — CRUD, preview generation, lifecycle

**Files:**
- Create: `src/main/java/com/worldcup/prediction/service/PredictionWindowService.java`
- Modify: `src/main/java/com/worldcup/prediction/repository/MatchRepository.java`
- Create: `src/test/java/com/worldcup/prediction/service/PredictionWindowServiceTest.java`

- [ ] **Step 1: Add adjacent-match queries to MatchRepository**

Add to `MatchRepository.java`:

```java
List<Match> findByKickoffTimeBetweenOrderByKickoffTimeAsc(LocalDateTime from, LocalDateTime to);

Optional<Match> findFirstByKickoffTimeLessThanOrderByKickoffTimeDesc(LocalDateTime before);

Optional<Match> findFirstByKickoffTimeGreaterThanOrderByKickoffTimeAsc(LocalDateTime after);
```

- [ ] **Step 2: Write failing service tests**

```java
// src/test/java/com/worldcup/prediction/service/PredictionWindowServiceTest.java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.enums.*;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionWindowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PredictionWindowServiceTest {

    @Mock PredictionWindowRepository windowRepository;
    @Mock MatchRepository matchRepository;
    @Mock TournamentSettingsService tournamentSettingsService;
    @InjectMocks PredictionWindowService service;

    @BeforeEach
    void setUp() { MockitoAnnotations.openMocks(this); }

    @Test
    void generatePreview_returnsMatchesInRangeAndAdjacent() {
        LocalDateTime from = LocalDateTime.of(2026, 6, 14, 0, 0);
        LocalDateTime to   = LocalDateTime.of(2026, 6, 14, 23, 59);
        Match m1 = matchWithKickoff(LocalDateTime.of(2026, 6, 14, 10, 0));
        Match prev = matchWithKickoff(LocalDateTime.of(2026, 6, 13, 20, 0));
        Match next = matchWithKickoff(LocalDateTime.of(2026, 6, 15, 10, 0));

        when(matchRepository.findByKickoffTimeBetweenOrderByKickoffTimeAsc(from, to))
                .thenReturn(List.of(m1));
        when(matchRepository.findFirstByKickoffTimeLessThanOrderByKickoffTimeDesc(from))
                .thenReturn(Optional.of(prev));
        when(matchRepository.findFirstByKickoffTimeGreaterThanOrderByKickoffTimeAsc(to))
                .thenReturn(Optional.of(next));

        var preview = service.generatePreview(from, to);

        assertThat(preview.includedMatches()).containsExactly(m1);
        assertThat(preview.prevMatch()).contains(prev);
        assertThat(preview.nextMatch()).contains(next);
    }

    @Test
    void publish_changesDraftToScheduled() {
        PredictionWindow window = PredictionWindow.builder()
                .id(1L).label("June 14").openAt(LocalDateTime.now().plusHours(2))
                .status(PredictionWindowStatus.DRAFT).build();
        when(windowRepository.findById(1L)).thenReturn(Optional.of(window));
        when(windowRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PredictionWindow result = service.publish(1L);
        assertThat(result.getStatus()).isEqualTo(PredictionWindowStatus.SCHEDULED);
    }

    @Test
    void publish_throwsWhenWindowNotDraft() {
        PredictionWindow window = PredictionWindow.builder()
                .id(1L).status(PredictionWindowStatus.OPEN).build();
        when(windowRepository.findById(1L)).thenReturn(Optional.of(window));

        assertThatThrownBy(() -> service.publish(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only DRAFT");
    }

    @Test
    void activateWindow_computesEffectiveCloseAtFromOffset_whenNoExplicitCloseAt() {
        LocalDateTime kickoff = LocalDateTime.of(2026, 6, 14, 10, 0);
        Match m = matchWithKickoff(kickoff);
        PredictionWindow window = PredictionWindow.builder()
                .id(1L).status(PredictionWindowStatus.SCHEDULED)
                .openAt(LocalDateTime.now().minusMinutes(1))
                .matches(Set.of(m)).build();
        when(windowRepository.findById(1L)).thenReturn(Optional.of(window));
        when(windowRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tournamentSettingsService.getSettings()).thenReturn(
                com.worldcup.prediction.domain.TournamentSettings.builder()
                        .dailyWindowCloseOffsetMinutes(30).build());

        PredictionWindow result = service.activateWindow(1L);

        assertThat(result.getStatus()).isEqualTo(PredictionWindowStatus.OPEN);
        assertThat(result.getEffectiveCloseAt()).isEqualTo(kickoff.minusMinutes(30));
    }

    @Test
    void activateWindow_usesExplicitCloseAtWhenSet() {
        LocalDateTime explicitClose = LocalDateTime.of(2026, 6, 14, 18, 0);
        PredictionWindow window = PredictionWindow.builder()
                .id(1L).status(PredictionWindowStatus.SCHEDULED)
                .openAt(LocalDateTime.now().minusMinutes(1))
                .closeAt(explicitClose)
                .matches(Set.of(matchWithKickoff(LocalDateTime.of(2026, 6, 14, 20, 0)))).build();
        when(windowRepository.findById(1L)).thenReturn(Optional.of(window));
        when(windowRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PredictionWindow result = service.activateWindow(1L);

        assertThat(result.getEffectiveCloseAt()).isEqualTo(explicitClose);
    }

    private Match matchWithKickoff(LocalDateTime kickoff) {
        Match m = new Match();
        m.setKickoffTime(kickoff);
        return m;
    }
}
```

- [ ] **Step 3: Run test — expect compile failure**

```bash
./mvnw test -Dtest=PredictionWindowServiceTest -q 2>&1 | tail -5
```
Expected: compile error — `PredictionWindowService` not yet created.

- [ ] **Step 4: Implement PredictionWindowService**

```java
// src/main/java/com/worldcup/prediction/service/PredictionWindowService.java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.enums.PredictionWindowStatus;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionWindowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PredictionWindowService {

    private final PredictionWindowRepository windowRepository;
    private final MatchRepository matchRepository;
    private final TournamentSettingsService tournamentSettingsService;

    // ---- Preview ----

    public record WindowPreview(
            List<Match> includedMatches,
            Optional<Match> prevMatch,
            Optional<Match> nextMatch) {}

    public WindowPreview generatePreview(LocalDateTime from, LocalDateTime to) {
        List<Match> included = matchRepository.findByKickoffTimeBetweenOrderByKickoffTimeAsc(from, to);
        Optional<Match> prev = matchRepository.findFirstByKickoffTimeLessThanOrderByKickoffTimeDesc(from);
        Optional<Match> next = matchRepository.findFirstByKickoffTimeGreaterThanOrderByKickoffTimeAsc(to);
        return new WindowPreview(included, prev, next);
    }

    // ---- CRUD ----

    public List<PredictionWindow> findAllGlobal() {
        return windowRepository.findByCommunityIdIsNullOrderByOpenAtAsc();
    }

    public List<PredictionWindow> findByCommunity(Long communityId) {
        return windowRepository.findByCommunityIdOrderByOpenAtAsc(communityId);
    }

    public PredictionWindow findById(Long id) {
        return windowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PredictionWindow not found: " + id));
    }

    @Transactional
    public PredictionWindow save(PredictionWindow window) {
        return windowRepository.save(window);
    }

    @Transactional
    public void delete(Long id) {
        PredictionWindow w = findById(id);
        if (w.getStatus() == PredictionWindowStatus.OPEN) {
            throw new IllegalStateException("Cannot delete an OPEN window — close it first.");
        }
        windowRepository.delete(w);
    }

    // ---- Lifecycle ----

    @Transactional
    public PredictionWindow publish(Long id) {
        PredictionWindow w = findById(id);
        if (w.getStatus() != PredictionWindowStatus.DRAFT) {
            throw new IllegalStateException("Can only DRAFT windows — current status: " + w.getStatus());
        }
        w.setStatus(PredictionWindowStatus.SCHEDULED);
        return windowRepository.save(w);
    }

    @Transactional
    public PredictionWindow activateWindow(Long id) {
        PredictionWindow w = findById(id);
        w.setEffectiveCloseAt(computeEffectiveCloseAt(w));
        w.setStatus(PredictionWindowStatus.OPEN);
        return windowRepository.save(w);
    }

    @Transactional
    public PredictionWindow closeWindow(Long id) {
        PredictionWindow w = findById(id);
        w.setStatus(PredictionWindowStatus.CLOSED);
        return windowRepository.save(w);
    }

    @Transactional
    public PredictionWindow forceOpen(Long id) {
        PredictionWindow w = findById(id);
        w.setOverrideStatus(RoundOverrideStatus.FORCE_OPEN);
        return windowRepository.save(w);
    }

    @Transactional
    public PredictionWindow forceClose(Long id) {
        PredictionWindow w = findById(id);
        w.setOverrideStatus(RoundOverrideStatus.FORCE_CLOSED);
        return windowRepository.save(w);
    }

    @Transactional
    public PredictionWindow resetOverride(Long id) {
        PredictionWindow w = findById(id);
        w.setOverrideStatus(null);
        return windowRepository.save(w);
    }

    // ---- isWindowOpen ----

    public boolean isWindowOpen(Match match, LocalDateTime now, Long communityId) {
        PredictionWindow window = findEffectiveWindow(match, communityId);
        if (window == null) return false;
        if (window.getOverrideStatus() == RoundOverrideStatus.FORCE_OPEN) return true;
        if (window.getOverrideStatus() == RoundOverrideStatus.FORCE_CLOSED) return false;
        if (window.getStatus() != PredictionWindowStatus.OPEN) return false;
        if (window.getEffectiveCloseAt() == null) return false;
        return !now.isBefore(window.getOpenAt()) && now.isBefore(window.getEffectiveCloseAt());
    }

    public PredictionWindow findEffectiveWindow(Match match, Long communityId) {
        if (communityId != null) {
            Optional<PredictionWindow> communityForceOpen =
                    windowRepository.findForceOpenCommunityWindowForMatch(match.getId(), communityId);
            if (communityForceOpen.isPresent()) return communityForceOpen.get();

            Optional<PredictionWindow> communityOpen =
                    windowRepository.findOpenCommunityWindowForMatch(match.getId(), communityId);
            if (communityOpen.isPresent()) return communityOpen.get();
        }

        Optional<PredictionWindow> globalForceOpen =
                windowRepository.findForceOpenGlobalWindowForMatch(match.getId());
        if (globalForceOpen.isPresent()) return globalForceOpen.get();

        return windowRepository.findOpenGlobalWindowForMatch(match.getId()).orElse(null);
    }

    // ---- Scheduler support ----

    public List<PredictionWindow> findScheduledReadyToActivate(LocalDateTime now) {
        return windowRepository.findByStatusAndOpenAtLessThanEqual(PredictionWindowStatus.SCHEDULED, now);
    }

    public List<PredictionWindow> findExpiredOpenWindows(LocalDateTime now) {
        return windowRepository.findExpiredOpenWindows(PredictionWindowStatus.OPEN, now);
    }

    // ---- Helpers ----

    private LocalDateTime computeEffectiveCloseAt(PredictionWindow w) {
        if (w.getCloseAt() != null) return w.getCloseAt();
        int offset = tournamentSettingsService.getSettings().getDailyWindowCloseOffsetMinutes();
        return w.getMatches().stream()
                .map(Match::getKickoffTime)
                .min(LocalDateTime::compareTo)
                .map(min -> min.minusMinutes(offset))
                .orElseThrow(() -> new IllegalStateException(
                        "Window has no matches — cannot compute effective close time."));
    }
}
```

- [ ] **Step 5: Run tests — expect pass**

```bash
./mvnw test -Dtest=PredictionWindowServiceTest -q
```
Expected: `Tests run: 5, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/PredictionWindowService.java \
        src/main/java/com/worldcup/prediction/repository/MatchRepository.java \
        src/test/java/com/worldcup/prediction/service/PredictionWindowServiceTest.java
git commit -m "feat: add PredictionWindowService (CRUD, lifecycle, isWindowOpen, preview)"
```

---

## Task 6: PredictionWindowScheduler

**Files:**
- Create: `src/main/java/com/worldcup/prediction/scheduler/PredictionWindowScheduler.java`
- Create: `src/test/java/com/worldcup/prediction/scheduler/PredictionWindowSchedulerTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/worldcup/prediction/scheduler/PredictionWindowSchedulerTest.java
package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.enums.PredictionWindowStatus;
import com.worldcup.prediction.service.PredictionWindowService;
import com.worldcup.prediction.service.SchedulerLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

class PredictionWindowSchedulerTest {

    @Mock PredictionWindowService windowService;
    @Mock SchedulerLogService logService;
    @InjectMocks PredictionWindowScheduler scheduler;

    @BeforeEach
    void setUp() { MockitoAnnotations.openMocks(this); }

    @Test
    void activateScheduledWindows_activatesEachReadyWindow() {
        PredictionWindow w1 = PredictionWindow.builder().id(1L)
                .status(PredictionWindowStatus.SCHEDULED).build();
        PredictionWindow w2 = PredictionWindow.builder().id(2L)
                .status(PredictionWindowStatus.SCHEDULED).build();
        when(windowService.findScheduledReadyToActivate(any())).thenReturn(List.of(w1, w2));
        when(logService.start(any())).thenReturn(new com.worldcup.prediction.domain.SchedulerLog());

        scheduler.activateScheduledWindows();

        verify(windowService).activateWindow(1L);
        verify(windowService).activateWindow(2L);
    }

    @Test
    void closeExpiredWindows_closesEachExpiredWindow() {
        PredictionWindow w = PredictionWindow.builder().id(3L)
                .status(PredictionWindowStatus.OPEN).build();
        when(windowService.findExpiredOpenWindows(any())).thenReturn(List.of(w));
        when(logService.start(any())).thenReturn(new com.worldcup.prediction.domain.SchedulerLog());

        scheduler.closeExpiredWindows();

        verify(windowService).closeWindow(3L);
    }

    @Test
    void activateScheduledWindows_skipsWhenNoneReady() {
        when(windowService.findScheduledReadyToActivate(any())).thenReturn(List.of());
        when(logService.start(any())).thenReturn(new com.worldcup.prediction.domain.SchedulerLog());

        scheduler.activateScheduledWindows();

        verify(windowService, never()).activateWindow(any());
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```bash
./mvnw test -Dtest=PredictionWindowSchedulerTest -q 2>&1 | tail -5
```

- [ ] **Step 3: Implement PredictionWindowScheduler**

```java
// src/main/java/com/worldcup/prediction/scheduler/PredictionWindowScheduler.java
package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.service.PredictionWindowService;
import com.worldcup.prediction.service.SchedulerLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.prediction-window.enabled", havingValue = "true")
public class PredictionWindowScheduler {

    private final PredictionWindowService windowService;
    private final SchedulerLogService logService;

    @Scheduled(fixedDelay = 300_000)
    public void activateScheduledWindows() {
        SchedulerLog entry = logService.start("PREDICTION_WINDOW_ACTIVATE");
        try {
            List<PredictionWindow> ready = windowService.findScheduledReadyToActivate(LocalDateTime.now());
            if (ready.isEmpty()) {
                logService.complete(entry, SchedulerJobStatus.SKIPPED, 0, "No windows ready to activate");
                return;
            }
            for (PredictionWindow w : ready) {
                windowService.activateWindow(w.getId());
                log.info("Activated prediction window id={} label={}", w.getId(), w.getLabel());
            }
            logService.complete(entry, SchedulerJobStatus.SUCCESS, ready.size(),
                    ready.size() + " window(s) activated");
        } catch (Exception e) {
            log.error("PredictionWindowScheduler.activateScheduledWindows error", e);
            logService.fail(entry, e.getMessage(), SchedulerLogService.stackTraceString(e));
        }
    }

    @Scheduled(fixedDelay = 300_000)
    public void closeExpiredWindows() {
        SchedulerLog entry = logService.start("PREDICTION_WINDOW_CLOSE");
        try {
            List<PredictionWindow> expired = windowService.findExpiredOpenWindows(LocalDateTime.now());
            if (expired.isEmpty()) {
                logService.complete(entry, SchedulerJobStatus.SKIPPED, 0, "No expired windows");
                return;
            }
            for (PredictionWindow w : expired) {
                windowService.closeWindow(w.getId());
                log.info("Closed expired prediction window id={} label={}", w.getId(), w.getLabel());
            }
            logService.complete(entry, SchedulerJobStatus.SUCCESS, expired.size(),
                    expired.size() + " window(s) closed");
        } catch (Exception e) {
            log.error("PredictionWindowScheduler.closeExpiredWindows error", e);
            logService.fail(entry, e.getMessage(), SchedulerLogService.stackTraceString(e));
        }
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
./mvnw test -Dtest=PredictionWindowSchedulerTest -q
```
Expected: `Tests run: 3, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/scheduler/PredictionWindowScheduler.java \
        src/test/java/com/worldcup/prediction/scheduler/PredictionWindowSchedulerTest.java
git commit -m "feat: add PredictionWindowScheduler (auto-activate and auto-close)"
```

---

## Task 7: Update PredictionService + RoundSubmissionService (mode-aware)

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/service/PredictionService.java`
- Modify: `src/main/java/com/worldcup/prediction/service/RoundSubmissionService.java`

- [ ] **Step 1: Update PredictionService — mode-aware isWindowOpen**

Add `TournamentSettingsService` and `PredictionWindowService` to the field list. Replace the `isWindowOpen` and `getPredictionsForMatch` methods:

```java
// Add to existing fields in PredictionService:
private final TournamentSettingsService tournamentSettingsService;
private final PredictionWindowService predictionWindowService;

// Replace isWindowOpen:
public boolean isWindowOpen(Match match, LocalDateTime now, Long communityId) {
    return switch (tournamentSettingsService.getEffectiveMode(communityId)) {
        case ROUND -> roundWindowService.isRoundOpen(match.getRoundLabel(), now);
        case DAILY -> predictionWindowService.isWindowOpen(match, now, communityId);
    };
}

// Keep old signature as delegate for callers that don't have communityId:
public boolean isWindowOpen(Match match, LocalDateTime now) {
    return roundWindowService.isRoundOpen(match.getRoundLabel(), now);
}

// Replace getPredictionsForMatch:
public List<Prediction> getPredictionsForMatch(Match match, LocalDateTime now, boolean isAdmin, Long communityId) {
    boolean windowClosed = !isWindowOpen(match, now, communityId);
    if (!isAdmin && !windowClosed) return List.of();
    return predictionRepository.findByMatchIdAndCommunityId(match.getId(), communityId);
}
```

Update `submitPredictions` — change the `isWindowOpen` call to pass `communityId`:

```java
// Line ~87 in submitPredictions, replace:
if (!isWindowOpen(matchMap.get(id), now)) {
// with:
if (!isWindowOpen(matchMap.get(id), now, communityId)) {
```

- [ ] **Step 2: Update RoundSubmissionService — add DAILY mode methods**

Add to `RoundSubmissionService.java`:

```java
@Transactional
public void upsertForWindow(Long userId, Long communityId, Long windowId, String windowLabel) {
    repository.findByUserIdAndCommunityIdAndPredictionWindowId(userId, communityId, windowId)
            .ifPresentOrElse(
                    rs -> rs.setSubmittedAt(LocalDateTime.now()),
                    () -> repository.save(RoundSubmission.builder()
                            .userId(userId)
                            .communityId(communityId)
                            .roundLabel(windowLabel)
                            .predictionWindowId(windowId)
                            .submittedAt(LocalDateTime.now())
                            .build())
            );
}

public boolean hasSubmittedForWindow(Long userId, Long communityId, Long windowId) {
    return repository.existsByUserIdAndCommunityIdAndPredictionWindowId(userId, communityId, windowId);
}

public Map<Long, RoundSubmission> findStatusesForCommunityWindow(Long communityId, Long windowId) {
    return repository.findByCommunityIdAndPredictionWindowId(communityId, windowId)
            .stream()
            .collect(Collectors.toMap(RoundSubmission::getUserId, rs -> rs));
}
```

- [ ] **Step 3: Run all tests to verify no regressions**

```bash
./mvnw test -q
```
Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/PredictionService.java \
        src/main/java/com/worldcup/prediction/service/RoundSubmissionService.java
git commit -m "feat: mode-aware isWindowOpen in PredictionService, DAILY upsert in RoundSubmissionService"
```

---

## Task 8: Update PredictionViewService — DAILY mode support

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/service/PredictionViewService.java`
- Modify: `src/main/java/com/worldcup/prediction/dto/PredictionSubmitDto.java`

- [ ] **Step 1: Add windowId to PredictionSubmitDto**

Open `PredictionSubmitDto.java` and add:

```java
private Long windowId;  // non-null in DAILY mode, null in ROUND mode
```

- [ ] **Step 2: Add DAILY-mode window summaries to PredictionViewService**

Add the following fields and method to `PredictionViewService.java`:

```java
// Add to field injections:
private final TournamentSettingsService tournamentSettingsService;
private final PredictionWindowService predictionWindowService;
```

Add after `getRoundSummaries`:

```java
public List<RoundSummaryDto> getWindowSummaries(Long userId, Long communityId) {
    List<PredictionWindow> windows = predictionWindowService.findAllGlobal();
    List<RoundSummaryDto> summaries = new ArrayList<>();
    LocalDateTime now = LocalDateTime.now();

    for (PredictionWindow pw : windows) {
        List<Match> matches = new ArrayList<>(pw.getMatches());
        if (matches.isEmpty()) continue;

        List<Long> matchIds = matches.stream().map(Match::getId).toList();
        boolean allComplete = matches.stream().allMatch(m -> m.getStatus() == MatchStatus.COMPLETED);

        RoundSummaryDto dto = new RoundSummaryDto();
        dto.setRoundLabel(pw.getLabel());
        dto.setDisplayLabel(pw.getLabel());
        dto.setTotalMatches(matches.size());

        if (allComplete) {
            dto.setStatus("PAST");
            int pts = (int) predictionRepository
                    .findByUserIdAndMatchIdInAndCommunityId(userId, matchIds, communityId)
                    .stream().mapToInt(Prediction::getPointsAwarded).sum();
            dto.setPointsEarned(pts);
        } else if (predictionWindowService.isWindowOpen(matches.get(0), now, communityId)) {
            dto.setStatus("OPEN");
            long predicted = predictionRepository.countByUserIdAndMatchIdInAndCommunityId(userId, matchIds, communityId);
            dto.setPredictedCount((int) predicted);
        } else {
            dto.setStatus("CLOSED");
            long predicted = predictionRepository.countByUserIdAndMatchIdInAndCommunityId(userId, matchIds, communityId);
            dto.setPredictedCount((int) predicted);
        }
        summaries.add(dto);
    }
    return summaries;
}
```

- [ ] **Step 3: Update submitPredictionsForRound to dispatch by mode**

In `PredictionViewService.submitPredictionsForRound`, add a mode check at the top:

```java
@Transactional
public int submitPredictionsForRound(Long userId, PredictionSubmitDto dto, Long communityId) {
    WindowMode mode = tournamentSettingsService.getEffectiveMode(communityId);
    if (mode == WindowMode.DAILY) {
        return submitPredictionsForWindow(userId, dto, communityId);
    }
    // ... existing ROUND logic unchanged ...
}

@Transactional
private int submitPredictionsForWindow(Long userId, PredictionSubmitDto dto, Long communityId) {
    if (dto.getWindowId() == null) {
        throw new IllegalStateException("windowId required in DAILY mode");
    }
    PredictionWindow pw = predictionWindowService.findById(dto.getWindowId());
    LocalDateTime now = LocalDateTime.now();

    List<Match> windowMatches = new ArrayList<>(pw.getMatches());
    if (!predictionWindowService.isWindowOpen(windowMatches.get(0), now, communityId)) {
        throw new IllegalStateException("The prediction window '" + pw.getLabel() + "' is not open.");
    }

    Set<Long> openIds = windowMatches.stream().map(Match::getId).collect(Collectors.toSet());
    Set<Long> submittedIds = dto.getPredictions().stream()
            .map(PredictionSubmitDto.SinglePrediction::getMatchId).collect(Collectors.toSet());
    if (!submittedIds.equals(openIds)) {
        throw new IllegalStateException(
                "You must predict all " + openIds.size() + " matches in this window (all-or-nothing).");
    }

    Map<Long, Match> matchMap = windowMatches.stream().collect(Collectors.toMap(Match::getId, m -> m));
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

    for (PredictionSubmitDto.SinglePrediction sp : dto.getPredictions()) {
        Match match = matchMap.get(sp.getMatchId());
        Optional<Prediction> existing = predictionRepository
                .findByUserIdAndMatchIdAndCommunityId(userId, sp.getMatchId(), communityId);
        Prediction prediction;
        if (existing.isPresent()) {
            prediction = existing.get();
            prediction.setPredictedHome(sp.getHomeScore());
            prediction.setPredictedAway(sp.getAwayScore());
        } else {
            prediction = new Prediction();
            prediction.setUser(user);
            prediction.setMatch(match);
            prediction.setCommunity(communityRepository.findById(communityId).orElseThrow());
            prediction.setPredictedHome(sp.getHomeScore());
            prediction.setPredictedAway(sp.getAwayScore());
        }
        predictionRepository.save(prediction);
    }

    roundSubmissionService.upsertForWindow(userId, communityId, pw.getId(), pw.getLabel());
    return windowMatches.size();
}
```

- [ ] **Step 4: Add import for WindowMode**

Add to imports in `PredictionViewService.java`:
```java
import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.enums.WindowMode;
```

- [ ] **Step 5: Run all tests**

```bash
./mvnw test -q
```
Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/PredictionViewService.java \
        src/main/java/com/worldcup/prediction/dto/PredictionSubmitDto.java
git commit -m "feat: DAILY mode window summaries and submission in PredictionViewService"
```

---

## Task 9: Update banner advice classes + NotificationScheduler

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/web/CommunityWindowBannerAdvice.java`
- Modify: `src/main/java/com/worldcup/prediction/web/AdminWindowBannerAdvice.java`
- Modify: `src/main/java/com/worldcup/prediction/scheduler/NotificationScheduler.java`

- [ ] **Step 1: Update CommunityWindowBannerAdvice**

Add new fields and update `windowBanner` method:

```java
// Add fields:
private final TournamentSettingsService tournamentSettingsService;
private final PredictionWindowService predictionWindowService;
```

Replace the `windowBanner` method body:

```java
@ModelAttribute("windowBanner")
public WindowBannerDto windowBanner(HttpServletRequest request, Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) return null;
    if (!(authentication.getPrincipal() instanceof CustomOAuth2User user)) return null;

    Community community = (Community) request.getAttribute("community");
    if (community == null) return null;

    LocalDateTime now = LocalDateTime.now();
    WindowMode mode = tournamentSettingsService.getEffectiveMode(community.getId());

    if (mode == WindowMode.DAILY) {
        return predictionWindowService.findAllGlobal().stream()
                .filter(pw -> pw.getStatus() == PredictionWindowStatus.OPEN
                        || pw.getOverrideStatus() == RoundOverrideStatus.FORCE_OPEN)
                .findFirst()
                .map(pw -> {
                    String closesAtIso = pw.getEffectiveCloseAt() != null
                            ? pw.getEffectiveCloseAt().atZone(appZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            : null;
                    boolean submitted = user.getRole() != UserRole.SUPER_ADMIN
                            && roundSubmissionService.hasSubmittedForWindow(
                                    user.getUserId(), community.getId(), pw.getId());
                    return new WindowBannerDto(pw.getLabel(), closesAtIso, submitted);
                })
                .orElse(null);
    }

    // ROUND mode — existing logic
    return roundWindowService.findAll().stream()
            .filter(rw -> isOpen(rw, now))
            .findFirst()
            .map(rw -> {
                String closesAtIso = rw.getAutoClosesAt() != null
                        ? rw.getAutoClosesAt().atZone(appZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        : null;
                boolean submitted = user.getRole() != UserRole.SUPER_ADMIN
                        && roundSubmissionService.hasSubmitted(user.getUserId(), community.getId(), rw.getRoundLabel());
                return new WindowBannerDto(rw.getRoundLabel(), closesAtIso, submitted);
            })
            .orElse(null);
}
```

Add required imports:
```java
import com.worldcup.prediction.domain.enums.PredictionWindowStatus;
import com.worldcup.prediction.domain.enums.WindowMode;
import com.worldcup.prediction.service.PredictionWindowService;
import com.worldcup.prediction.service.TournamentSettingsService;
```

- [ ] **Step 2: Apply same pattern to AdminWindowBannerAdvice**

Open `AdminWindowBannerAdvice.java` and apply the same dual-mode logic (DAILY: use `predictionWindowService.findAllGlobal()`, ROUND: existing). The `submitted` field is always `false` for admins in both modes.

- [ ] **Step 3: Extend NotificationScheduler to detect newly-opened PredictionWindows**

Add fields to `NotificationScheduler.java`:

```java
private final PredictionWindowService predictionWindowService;
private final TournamentSettingsService tournamentSettingsService;
```

At the end of `checkPredictionWindowOpen()`, after the existing round-window notification block, add:

```java
// DAILY mode: notify on newly-opened PredictionWindows
List<PredictionWindow> newlyOpenWindows = predictionWindowService.findAllGlobal().stream()
        .filter(pw -> pw.getStatus() == PredictionWindowStatus.OPEN
                && pw.getOpenAt() != null
                && !now.isBefore(pw.getOpenAt())
                && now.isBefore(pw.getOpenAt().plusMinutes(10)))
        .toList();

for (PredictionWindow pw : newlyOpenWindows) {
    List<Match> matches = new ArrayList<>(pw.getMatches());
    if (matches.isEmpty()) continue;
    Match firstMatch = matches.stream()
            .min(Comparator.comparing(Match::getKickoffTime)).orElse(matches.get(0));
    for (Community community : communities) {
        boolean ok = notificationService.sendPredictionWindowOpen(activeUsers, firstMatch, community.getId());
        if (ok) sent++;
    }
}
```

Add required imports for `PredictionWindow`, `PredictionWindowService`, `TournamentSettingsService`, `PredictionWindowStatus`.

- [ ] **Step 4: Run all tests**

```bash
./mvnw test -q
```
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/web/CommunityWindowBannerAdvice.java \
        src/main/java/com/worldcup/prediction/web/AdminWindowBannerAdvice.java \
        src/main/java/com/worldcup/prediction/scheduler/NotificationScheduler.java
git commit -m "feat: DAILY mode support in banner advice and notification scheduler"
```

---

## Task 10: Admin controllers — tournament settings + prediction window CRUD

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/controller/admin/AdminSettingsController.java`
- Create: `src/main/java/com/worldcup/prediction/controller/admin/AdminPredictionWindowController.java`
- Modify: `src/main/resources/templates/admin/settings.html`
- Create: `src/main/resources/templates/admin/prediction-windows.html`
- Create: `src/main/resources/templates/admin/prediction-window-form.html`

- [ ] **Step 1: Add tournament mode endpoints to AdminSettingsController**

Add to `AdminSettingsController.java`:

```java
private final TournamentSettingsService tournamentSettingsService;

@GetMapping
public String settings(Model model) {
    model.addAttribute("tournamentSettings", tournamentSettingsService.getSettings());
    model.addAttribute("windowModes", WindowMode.values());
    return "admin/settings";
}

@PostMapping("/tournament-mode")
public String updateTournamentMode(@RequestParam WindowMode windowMode,
                                   @RequestParam int dailyWindowCloseOffsetMinutes,
                                   RedirectAttributes redirectAttributes) {
    tournamentSettingsService.updateMode(windowMode);
    tournamentSettingsService.updateCloseOffset(dailyWindowCloseOffsetMinutes);
    redirectAttributes.addFlashAttribute("successMessage", "Tournament window mode updated.");
    return "redirect:/admin/settings";
}
```

Add imports: `WindowMode`, `TournamentSettingsService`.

- [ ] **Step 2: Add tournament mode section to admin/settings.html**

After the existing password section in `settings.html`, add:

```html
<!-- Tournament Window Mode -->
<div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mb-6">
  <h2 class="text-lg font-semibold text-gray-900 mb-4">Prediction Window Mode</h2>
  <form th:action="@{/admin/settings/tournament-mode}" method="post">
    <div class="flex gap-6 mb-4">
      <label class="flex items-center gap-2 cursor-pointer">
        <input type="radio" name="windowMode" value="ROUND"
               th:checked="${tournamentSettings.windowMode.name() == 'ROUND'}"/>
        <span class="font-medium">Round Mode</span>
        <span class="text-sm text-gray-500">— one window per matchday</span>
      </label>
      <label class="flex items-center gap-2 cursor-pointer">
        <input type="radio" name="windowMode" value="DAILY"
               th:checked="${tournamentSettings.windowMode.name() == 'DAILY'}"/>
        <span class="font-medium">Daily Mode</span>
        <span class="text-sm text-gray-500">— custom windows per batch of matches</span>
      </label>
    </div>
    <div class="flex items-center gap-3 mb-4">
      <label class="text-sm text-gray-600">Auto-close offset (minutes before first match):</label>
      <input type="number" name="dailyWindowCloseOffsetMinutes" min="0" max="180"
             th:value="${tournamentSettings.dailyWindowCloseOffsetMinutes}"
             class="border border-gray-300 rounded px-2 py-1 w-20 text-sm"/>
    </div>
    <button type="submit"
            class="bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-indigo-700">
      Save Mode
    </button>
  </form>
</div>
```

- [ ] **Step 3: Create AdminPredictionWindowController**

```java
// src/main/java/com/worldcup/prediction/controller/admin/AdminPredictionWindowController.java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.PredictionWindowService;
import com.worldcup.prediction.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashSet;

@Controller
@RequestMapping("/admin/prediction-windows")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminPredictionWindowController {

    private final PredictionWindowService windowService;
    private final UserService userService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("windows", windowService.findAllGlobal());
        return "admin/prediction-windows";
    }

    @GetMapping("/new")
    public String newForm(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Model model) {
        model.addAttribute("window", PredictionWindow.builder().build());
        if (from != null && to != null) {
            model.addAttribute("preview", windowService.generatePreview(from, to));
            model.addAttribute("from", from);
            model.addAttribute("to", to);
        }
        return "admin/prediction-window-form";
    }

    @PostMapping("/new")
    public String create(
            @RequestParam String label,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime openAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime closeAt,
            @RequestParam(required = false) java.util.List<Long> matchIds,
            @AuthenticationPrincipal CustomOAuth2User principal,
            RedirectAttributes redirectAttributes) {

        User creator = userService.findById(principal.getUserId());
        var matches = matchIds != null
                ? new java.util.HashSet<>(com.worldcup.prediction.repository.MatchRepository.class.cast(null)
                        .findAllById(matchIds)) // resolved via matchRepository injection below
                : new HashSet<>();

        // Use injected matchRepository:
        var window = PredictionWindow.builder()
                .label(label).openAt(openAt).closeAt(closeAt).createdBy(creator).build();
        windowService.save(window);
        redirectAttributes.addFlashAttribute("successMessage", "Window created as DRAFT.");
        return "redirect:/admin/prediction-windows";
    }

    @PostMapping("/{id}/publish")
    public String publish(@PathVariable Long id, RedirectAttributes ra) {
        windowService.publish(id);
        ra.addFlashAttribute("successMessage", "Window published — will activate at open time.");
        return "redirect:/admin/prediction-windows";
    }

    @PostMapping("/{id}/force-open")
    public String forceOpen(@PathVariable Long id, RedirectAttributes ra) {
        windowService.forceOpen(id);
        ra.addFlashAttribute("successMessage", "Window forced OPEN.");
        return "redirect:/admin/prediction-windows";
    }

    @PostMapping("/{id}/force-close")
    public String forceClose(@PathVariable Long id, RedirectAttributes ra) {
        windowService.forceClose(id);
        ra.addFlashAttribute("successMessage", "Window forced CLOSED.");
        return "redirect:/admin/prediction-windows";
    }

    @PostMapping("/{id}/reset-override")
    public String resetOverride(@PathVariable Long id, RedirectAttributes ra) {
        windowService.resetOverride(id);
        ra.addFlashAttribute("successMessage", "Override cleared.");
        return "redirect:/admin/prediction-windows";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        windowService.delete(id);
        ra.addFlashAttribute("successMessage", "Window deleted.");
        return "redirect:/admin/prediction-windows";
    }
}
```

**Note:** The `create` method above has a placeholder for match resolution. Fix it by injecting `MatchRepository` directly:

```java
private final com.worldcup.prediction.repository.MatchRepository matchRepository;

// In create():
var matchSet = matchIds != null ? new HashSet<>(matchRepository.findAllById(matchIds)) : new HashSet<>();
var window = PredictionWindow.builder()
        .label(label).openAt(openAt).closeAt(closeAt).createdBy(creator)
        .matches(matchSet).build();
windowService.save(window);
```

- [ ] **Step 4: Create prediction-windows.html template**

```html
<!-- src/main/resources/templates/admin/prediction-windows.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" th:replace="~{admin/layout :: layout(~{::title}, ~{::main})}">
<title>Prediction Windows</title>
<main>
  <div class="flex items-center justify-between mb-6">
    <h1 class="text-2xl font-bold text-gray-900">Daily Prediction Windows</h1>
    <a href="/admin/prediction-windows/new"
       class="bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-indigo-700">
      + New Window
    </a>
  </div>

  <div th:if="${successMessage}" class="mb-4 p-3 bg-green-100 text-green-800 rounded-lg text-sm"
       th:text="${successMessage}"></div>

  <div class="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
    <table class="w-full text-sm">
      <thead class="bg-gray-50 text-gray-600 uppercase text-xs">
        <tr>
          <th class="px-4 py-3 text-left">Label</th>
          <th class="px-4 py-3 text-left">Status</th>
          <th class="px-4 py-3 text-left">Opens At</th>
          <th class="px-4 py-3 text-left">Closes At</th>
          <th class="px-4 py-3 text-left">Matches</th>
          <th class="px-4 py-3 text-left">Actions</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-gray-100">
        <tr th:each="pw : ${windows}" class="hover:bg-gray-50">
          <td class="px-4 py-3 font-medium" th:text="${pw.label}"></td>
          <td class="px-4 py-3">
            <span th:text="${pw.status}"
                  th:classappend="${pw.status.name() == 'OPEN'} ? 'bg-green-100 text-green-700' :
                                  (${pw.status.name() == 'SCHEDULED'} ? 'bg-blue-100 text-blue-700' :
                                  (${pw.status.name() == 'CLOSED'} ? 'bg-gray-100 text-gray-600' :
                                  'bg-yellow-100 text-yellow-700'))"
                  class="px-2 py-0.5 rounded text-xs font-medium"></span>
            <span th:if="${pw.overrideStatus != null}"
                  th:text="${pw.overrideStatus}"
                  class="ml-1 px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-700"></span>
          </td>
          <td class="px-4 py-3 text-gray-600" th:text="${#temporals.format(pw.openAt, 'd MMM HH:mm')}"></td>
          <td class="px-4 py-3 text-gray-600"
              th:text="${pw.effectiveCloseAt != null} ? ${#temporals.format(pw.effectiveCloseAt, 'd MMM HH:mm')} :
                       (${pw.closeAt != null} ? ${#temporals.format(pw.closeAt, 'd MMM HH:mm')} : 'Auto')"></td>
          <td class="px-4 py-3 text-gray-600" th:text="${pw.matches.size()}"></td>
          <td class="px-4 py-3">
            <div class="flex gap-2">
              <form th:if="${pw.status.name() == 'DRAFT'}"
                    th:action="@{/admin/prediction-windows/{id}/publish(id=${pw.id})}" method="post">
                <button class="text-xs bg-blue-600 text-white px-2 py-1 rounded hover:bg-blue-700">Publish</button>
              </form>
              <form th:if="${pw.overrideStatus == null}"
                    th:action="@{/admin/prediction-windows/{id}/force-open(id=${pw.id})}" method="post">
                <button class="text-xs bg-green-600 text-white px-2 py-1 rounded hover:bg-green-700">Force Open</button>
              </form>
              <form th:if="${pw.overrideStatus == null}"
                    th:action="@{/admin/prediction-windows/{id}/force-close(id=${pw.id})}" method="post">
                <button class="text-xs bg-orange-500 text-white px-2 py-1 rounded hover:bg-orange-600">Force Close</button>
              </form>
              <form th:if="${pw.overrideStatus != null}"
                    th:action="@{/admin/prediction-windows/{id}/reset-override(id=${pw.id})}" method="post">
                <button class="text-xs bg-gray-500 text-white px-2 py-1 rounded hover:bg-gray-600">Reset</button>
              </form>
              <form th:if="${pw.status.name() != 'OPEN'}"
                    th:action="@{/admin/prediction-windows/{id}/delete(id=${pw.id})}" method="post"
                    onsubmit="return confirm('Delete this window?')">
                <button class="text-xs bg-red-500 text-white px-2 py-1 rounded hover:bg-red-600">Delete</button>
              </form>
            </div>
          </td>
        </tr>
        <tr th:if="${windows.empty}">
          <td colspan="6" class="px-4 py-8 text-center text-gray-400">No prediction windows yet.</td>
        </tr>
      </tbody>
    </table>
  </div>
</main>
</html>
```

- [ ] **Step 5: Create prediction-window-form.html (generate form with timeline preview)**

```html
<!-- src/main/resources/templates/admin/prediction-window-form.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" th:replace="~{admin/layout :: layout(~{::title}, ~{::main})}">
<title>New Prediction Window</title>
<main>
  <h1 class="text-2xl font-bold text-gray-900 mb-6">New Prediction Window</h1>

  <!-- Step 1: date range picker to generate preview -->
  <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mb-6">
    <h2 class="text-base font-semibold text-gray-700 mb-3">1. Select match range</h2>
    <form method="get" action="/admin/prediction-windows/new" class="flex gap-3 items-end">
      <div>
        <label class="block text-xs text-gray-500 mb-1">From</label>
        <input type="datetime-local" name="from" th:value="${from}" class="border border-gray-300 rounded px-2 py-1 text-sm"/>
      </div>
      <div>
        <label class="block text-xs text-gray-500 mb-1">To</label>
        <input type="datetime-local" name="to" th:value="${to}" class="border border-gray-300 rounded px-2 py-1 text-sm"/>
      </div>
      <button type="submit" class="bg-gray-700 text-white px-4 py-2 rounded text-sm">Preview Matches</button>
    </form>
  </div>

  <!-- Step 2: preview + confirm form -->
  <div th:if="${preview != null}" class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
    <h2 class="text-base font-semibold text-gray-700 mb-3">2. Review &amp; confirm</h2>

    <!-- Adjacent match context -->
    <div th:if="${preview.prevMatch().isPresent()}" class="text-xs text-gray-400 mb-1">
      ← Previous match outside range:
      <span th:text="${preview.prevMatch().get().kickoffTime}"></span>
    </div>

    <!-- Included matches -->
    <div class="border border-green-200 bg-green-50 rounded-lg p-3 mb-2">
      <p class="text-xs font-medium text-green-700 mb-2">Included matches (<span th:text="${preview.includedMatches().size()}"></span>):</p>
      <div th:each="m : ${preview.includedMatches()}" class="text-sm text-gray-700 py-0.5">
        <span th:text="${m.kickoffTime}"></span> —
        <span th:text="${m.homeTeam != null ? m.homeTeam.name : m.homeTeamPlaceholder}"></span>
        vs
        <span th:text="${m.awayTeam != null ? m.awayTeam.name : m.awayTeamPlaceholder}"></span>
      </div>
      <p th:if="${preview.includedMatches().empty}" class="text-sm text-gray-400">No matches in this range.</p>
    </div>

    <div th:if="${preview.nextMatch().isPresent()}" class="text-xs text-gray-400 mb-4">
      → Next match outside range:
      <span th:text="${preview.nextMatch().get().kickoffTime}"></span>
    </div>

    <!-- Window config form -->
    <form th:action="@{/admin/prediction-windows/new}" method="post" class="space-y-4">
      <!-- Hidden match IDs -->
      <input th:each="m : ${preview.includedMatches()}" type="hidden" name="matchIds" th:value="${m.id}"/>

      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-xs text-gray-500 mb-1">Window Label</label>
          <input type="text" name="label" required
                 th:value="${'Matches ' + #temporals.format(from, 'd MMM')}"
                 class="w-full border border-gray-300 rounded px-3 py-2 text-sm"/>
        </div>
        <div></div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Opens At</label>
          <input type="datetime-local" name="openAt" required th:value="${from}"
                 class="w-full border border-gray-300 rounded px-3 py-2 text-sm"/>
        </div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Closes At (leave blank = auto from first match)</label>
          <input type="datetime-local" name="closeAt"
                 class="w-full border border-gray-300 rounded px-3 py-2 text-sm"/>
        </div>
      </div>

      <div class="flex gap-3">
        <button type="submit"
                class="bg-indigo-600 text-white px-5 py-2 rounded-lg text-sm font-medium hover:bg-indigo-700">
          Create Window (DRAFT)
        </button>
        <a href="/admin/prediction-windows" class="text-sm text-gray-500 self-center">Cancel</a>
      </div>
    </form>
  </div>
</main>
</html>
```

- [ ] **Step 6: Run all tests**

```bash
./mvnw test -q
```
Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/admin/AdminSettingsController.java \
        src/main/java/com/worldcup/prediction/controller/admin/AdminPredictionWindowController.java \
        src/main/resources/templates/admin/settings.html \
        src/main/resources/templates/admin/prediction-windows.html \
        src/main/resources/templates/admin/prediction-window-form.html
git commit -m "feat: admin UI for tournament mode settings and prediction window CRUD"
```

---

## Task 11: Community admin window settings controller + template

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/community/CommunityAdminWindowSettingsController.java`
- Create: `src/main/resources/templates/community/admin/window-settings.html`
- Modify: `src/main/resources/templates/layout/community-base.html`
- Modify: `src/main/java/com/worldcup/prediction/repository/CommunityRepository.java`

- [ ] **Step 1: Add save method to CommunityRepository (if not already present)**

Verify `CommunityRepository` has `save` (it inherits from `JpaRepository` — no action needed).

- [ ] **Step 2: Create CommunityAdminWindowSettingsController**

```java
// src/main/java/com/worldcup/prediction/controller/community/CommunityAdminWindowSettingsController.java
package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.WindowMode;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.PredictionWindowService;
import com.worldcup.prediction.service.TournamentSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/c/{slug}/admin/window-settings")
@RequiredArgsConstructor
public class CommunityAdminWindowSettingsController {

    private final CommunityRepository communityRepository;
    private final TournamentSettingsService tournamentSettingsService;
    private final PredictionWindowService predictionWindowService;

    @GetMapping
    public String settingsPage(@PathVariable String slug, HttpServletRequest request, Model model) {
        Community community = (Community) request.getAttribute("community");
        model.addAttribute("community", community);
        model.addAttribute("globalSettings", tournamentSettingsService.getSettings());
        model.addAttribute("effectiveMode", tournamentSettingsService.getEffectiveMode(community.getId()));
        model.addAttribute("globalWindows", predictionWindowService.findAllGlobal());
        model.addAttribute("communityWindows", predictionWindowService.findByCommunity(community.getId()));
        model.addAttribute("windowModes", WindowMode.values());
        return "community/admin/window-settings";
    }

    @PostMapping("/mode")
    public String updateMode(@PathVariable String slug,
                             HttpServletRequest request,
                             @RequestParam(required = false) WindowMode windowModeOverride,
                             RedirectAttributes ra) {
        Community community = (Community) request.getAttribute("community");
        community.setWindowModeOverride(windowModeOverride);
        communityRepository.save(community);
        ra.addFlashAttribute("successMessage", "Window mode updated.");
        return "redirect:/c/" + slug + "/admin/window-settings";
    }

    @PostMapping("/windows/{windowId}/force-open")
    public String forceOpen(@PathVariable String slug, @PathVariable Long windowId, RedirectAttributes ra) {
        predictionWindowService.forceOpen(windowId);
        ra.addFlashAttribute("successMessage", "Window forced OPEN.");
        return "redirect:/c/" + slug + "/admin/window-settings";
    }

    @PostMapping("/windows/{windowId}/force-close")
    public String forceClose(@PathVariable String slug, @PathVariable Long windowId, RedirectAttributes ra) {
        predictionWindowService.forceClose(windowId);
        ra.addFlashAttribute("successMessage", "Window forced CLOSED.");
        return "redirect:/c/" + slug + "/admin/window-settings";
    }

    @PostMapping("/windows/{windowId}/reset-override")
    public String resetOverride(@PathVariable String slug, @PathVariable Long windowId, RedirectAttributes ra) {
        predictionWindowService.resetOverride(windowId);
        ra.addFlashAttribute("successMessage", "Override cleared.");
        return "redirect:/c/" + slug + "/admin/window-settings";
    }
}
```

- [ ] **Step 3: Create window-settings.html template**

```html
<!-- src/main/resources/templates/community/admin/window-settings.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/community-base :: base(~{::title}, ~{::main})}">
<title>Window Settings</title>
<main>
  <h1 class="text-xl font-bold text-gray-900 mb-6">Window Settings</h1>

  <div th:if="${successMessage}" class="mb-4 p-3 bg-green-100 text-green-800 rounded-lg text-sm"
       th:text="${successMessage}"></div>

  <!-- Mode selector -->
  <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-5 mb-6">
    <h2 class="text-base font-semibold text-gray-700 mb-3">Window Mode</h2>
    <p class="text-sm text-gray-500 mb-3">
      Global default: <strong th:text="${globalSettings.windowMode}"></strong>.
      Effective for this community: <strong th:text="${effectiveMode}"></strong>.
    </p>
    <form th:action="@{/c/{slug}/admin/window-settings/mode(slug=${community.slug})}" method="post">
      <div class="flex gap-6 mb-3">
        <label class="flex items-center gap-2 cursor-pointer">
          <input type="radio" name="windowModeOverride" value=""
                 th:checked="${community.windowModeOverride == null}"/>
          Inherit global (<span th:text="${globalSettings.windowMode}"></span>)
        </label>
        <label class="flex items-center gap-2 cursor-pointer">
          <input type="radio" name="windowModeOverride" value="ROUND"
                 th:checked="${community.windowModeOverride != null and community.windowModeOverride.name() == 'ROUND'}"/>
          Round Mode
        </label>
        <label class="flex items-center gap-2 cursor-pointer">
          <input type="radio" name="windowModeOverride" value="DAILY"
                 th:checked="${community.windowModeOverride != null and community.windowModeOverride.name() == 'DAILY'}"/>
          Daily Mode
        </label>
      </div>
      <button type="submit" class="bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-indigo-700">
        Save
      </button>
    </form>
  </div>

  <!-- Global windows reference + override controls -->
  <div class="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
    <div class="px-5 py-4 border-b border-gray-100">
      <h2 class="text-base font-semibold text-gray-700">Global Prediction Windows</h2>
      <p class="text-xs text-gray-400 mt-1">Use override controls to temporarily adjust a window for your community.</p>
    </div>
    <table class="w-full text-sm">
      <thead class="bg-gray-50 text-gray-500 uppercase text-xs">
        <tr>
          <th class="px-4 py-2 text-left">Label</th>
          <th class="px-4 py-2 text-left">Status</th>
          <th class="px-4 py-2 text-left">Opens</th>
          <th class="px-4 py-2 text-left">Closes</th>
          <th class="px-4 py-2 text-left">Actions</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-gray-100">
        <tr th:each="pw : ${globalWindows}" class="hover:bg-gray-50">
          <td class="px-4 py-2 font-medium" th:text="${pw.label}"></td>
          <td class="px-4 py-2">
            <span th:text="${pw.overrideStatus != null ? pw.overrideStatus : pw.status}"
                  class="px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600"></span>
          </td>
          <td class="px-4 py-2 text-gray-500" th:text="${#temporals.format(pw.openAt, 'd MMM HH:mm')}"></td>
          <td class="px-4 py-2 text-gray-500"
              th:text="${pw.effectiveCloseAt != null ? #temporals.format(pw.effectiveCloseAt, 'd MMM HH:mm') : 'Auto'}"></td>
          <td class="px-4 py-2">
            <div class="flex gap-2" th:with="base=@{/c/{s}/admin/window-settings/windows/{id}(s=${community.slug},id=${pw.id})}">
              <form th:if="${pw.overrideStatus == null}" th:action="${base + '/force-open'}" method="post">
                <button class="text-xs bg-green-600 text-white px-2 py-1 rounded">Force Open</button>
              </form>
              <form th:if="${pw.overrideStatus == null}" th:action="${base + '/force-close'}" method="post">
                <button class="text-xs bg-orange-500 text-white px-2 py-1 rounded">Force Close</button>
              </form>
              <form th:if="${pw.overrideStatus != null}" th:action="${base + '/reset-override'}" method="post">
                <button class="text-xs bg-gray-500 text-white px-2 py-1 rounded">Reset</button>
              </form>
            </div>
          </td>
        </tr>
        <tr th:if="${globalWindows.empty}">
          <td colspan="5" class="px-4 py-6 text-center text-gray-400 text-sm">No windows configured yet.</td>
        </tr>
      </tbody>
    </table>
  </div>
</main>
</html>
```

- [ ] **Step 4: Add Window Settings nav link in community-base.html**

In `community-base.html`, find the existing admin nav links (near "Members" and "Submission Status") and add:

```html
<!-- Desktop nav -->
<a th:href="@{/c/{slug}/admin/window-settings(slug=${community.slug})}"
   th:classappend="${#httpServletRequest.requestURI.contains('window-settings')} ? 'text-indigo-600 font-medium' : 'text-gray-600 hover:text-gray-900'"
   class="text-sm">Window Settings</a>
```

Add the same link to the mobile menu and dropdown sections following the existing pattern in the template.

- [ ] **Step 5: Run all tests**

```bash
./mvnw test -q
```
Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/community/CommunityAdminWindowSettingsController.java \
        src/main/resources/templates/community/admin/window-settings.html \
        src/main/resources/templates/layout/community-base.html
git commit -m "feat: community admin window settings page with mode override and window controls"
```

---

## Task 12: Enable app.prediction-window.enabled flag + smoke test

**Files:**
- Modify: `src/main/resources/application.properties` (or `application-dev.properties`)

- [ ] **Step 1: Add feature flag to dev/prod config**

In `application-dev.properties` (or equivalent):
```properties
app.prediction-window.enabled=true
```

In `application.properties` (production default):
```properties
app.prediction-window.enabled=true
```

- [ ] **Step 2: Start the app and verify**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | grep -E "Flyway|prediction-window|ERROR" | head -30
```
Expected:
- `Successfully applied 3 migrations` (V11, V12, V13)
- `PredictionWindowScheduler` bean initializes (logged in context)
- No `ERROR` lines

- [ ] **Step 3: Manual smoke test**

1. Login as super admin → navigate to `/admin/settings`
2. Verify "Prediction Window Mode" section appears with ROUND selected
3. Navigate to `/admin/prediction-windows` — expect empty list with "+ New Window" button
4. Click "+ New Window", enter a date range → verify match preview appears with adjacent matches shown
5. Create a window → verify it appears as DRAFT in the list
6. Click "Publish" → status changes to SCHEDULED
7. Navigate to a community `/c/{slug}/admin/window-settings` → verify page loads with mode selector and global window list

- [ ] **Step 4: Run full test suite**

```bash
./mvnw test -q
```
Expected: all tests pass.

- [ ] **Step 5: Final commit**

```bash
git add src/main/resources/application.properties src/main/resources/application-dev.properties 2>/dev/null; true
git add -u
git commit -m "feat: enable prediction-window scheduler flag, complete daily prediction windows feature"
```

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Task covering it |
|---|---|
| ROUND / DAILY modes, switchable | Task 4 (TournamentSettingsService), Task 10 (UI) |
| Global mode + per-community override | Task 3 (Community entity), Task 11 (community settings UI) |
| Admin-initiated window generation with match preview | Task 5 (generatePreview), Task 10 (form template) |
| Match timeline: included + adjacent prev/next | Task 5 (WindowPreview record), Task 10 (form template) |
| Auto-close from first match kickoff - offset | Task 5 (computeEffectiveCloseAt) |
| Explicit close_at takes priority | Task 5 (computeEffectiveCloseAt) |
| Window lifecycle DRAFT→SCHEDULED→OPEN→CLOSED | Task 5 (service), Task 6 (scheduler) |
| FORCE_OPEN / FORCE_CLOSED / Reset overrides | Task 5 (service), Task 10 & 11 (UI) |
| isWindowOpen mode-aware | Task 5 (PredictionWindowService), Task 7 (PredictionService) |
| RoundSubmission predictionWindowId FK | Task 3 (migration + entity) |
| DAILY mode submission validation by window ID | Task 8 (PredictionViewService) |
| Countdown banner uses PredictionWindow in DAILY mode | Task 9 (CommunityWindowBannerAdvice) |
| NotificationScheduler detects newly-open PredictionWindows | Task 9 |
| V11, V12, V13 migrations | Tasks 1, 2, 3 |
| Community admin window settings page | Task 11 |
| Super admin prediction windows CRUD page | Task 10 |
| PredictionWindowScheduler auto-activate + auto-close | Task 6 |
