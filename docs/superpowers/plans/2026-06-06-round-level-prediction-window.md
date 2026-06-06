# Round-Level Prediction Window Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-match prediction window control with round-level `RoundWindow` entity, automatic open/close based on kickoff times, and admin override capability.

**Architecture:** New `RoundWindow` JPA entity keyed by `roundLabel`. `RoundWindowService` centralizes open/closed logic (auto from kickoff times + admin override). All consumers (PredictionService, PredictionViewService, MatchServiceImpl, NotificationScheduler, AdminMatchController) delegate to `RoundWindowService` instead of reading per-match flags. DB migration rebuilds the `matches` table without the 3 prediction window columns.

**Tech Stack:** Spring Boot, JPA/Hibernate, Flyway (SQLite + PostgreSQL), Thymeleaf, Alpine.js, Lombok

**Spec:** `docs/superpowers/specs/2026-06-06-round-level-prediction-window-design.md`

---

### Task 1: Create `RoundOverrideStatus` Enum and `RoundWindow` Entity

**Files:**
- Create: `src/main/java/com/worldcup/prediction/domain/enums/RoundOverrideStatus.java`
- Create: `src/main/java/com/worldcup/prediction/domain/RoundWindow.java`
- Create: `src/main/java/com/worldcup/prediction/repository/RoundWindowRepository.java`

- [ ] **Step 1: Create the enum**

```java
package com.worldcup.prediction.domain.enums;

public enum RoundOverrideStatus {
    FORCE_OPEN,
    FORCE_CLOSED
}
```

- [ ] **Step 2: Create the entity**

```java
package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "round_windows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RoundWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "round_label", nullable = false, unique = true, length = 50)
    private String roundLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_status", length = 20)
    private RoundOverrideStatus overrideStatus;

    @Column(name = "auto_opens_at")
    private LocalDateTime autoOpensAt;

    @Column(name = "auto_closes_at")
    private LocalDateTime autoClosesAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: Create the repository**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.RoundWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoundWindowRepository extends JpaRepository<RoundWindow, Long> {

    Optional<RoundWindow> findByRoundLabel(String roundLabel);

    @Query("SELECT rw FROM RoundWindow rw ORDER BY rw.autoOpensAt ASC")
    List<RoundWindow> findAllOrderByAutoOpensAtAsc();
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worldcup/prediction/domain/enums/RoundOverrideStatus.java \
        src/main/java/com/worldcup/prediction/domain/RoundWindow.java \
        src/main/java/com/worldcup/prediction/repository/RoundWindowRepository.java
git commit -m "feat: add RoundWindow entity, enum, and repository"
```

---

### Task 2: Create `RoundWindowService` with Tests

**Files:**
- Create: `src/main/java/com/worldcup/prediction/service/RoundWindowService.java`
- Create: `src/test/java/com/worldcup/prediction/service/RoundWindowServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.RoundWindowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoundWindowServiceTest {

    @Mock private RoundWindowRepository roundWindowRepository;
    @Mock private MatchRepository matchRepository;

    private RoundWindowService service;

    private final LocalDateTime autoOpens = LocalDateTime.of(2026, 6, 10, 18, 0);
    private final LocalDateTime autoCloses = LocalDateTime.of(2026, 6, 12, 17, 0);

    @BeforeEach
    void setUp() {
        service = new RoundWindowService(roundWindowRepository, matchRepository);
    }

    @Nested
    @DisplayName("isRoundOpen")
    class IsRoundOpen {

        @Test @DisplayName("returns false when round window not found")
        void notFound_returnsFalse() {
            when(roundWindowRepository.findByRoundLabel("Unknown")).thenReturn(Optional.empty());
            assertThat(service.isRoundOpen("Unknown", autoOpens)).isFalse();
        }

        @Test @DisplayName("returns true when within auto window")
        void withinAutoWindow_returnsTrue() {
            RoundWindow rw = buildWindow(null);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(service.isRoundOpen("Matchday 1", autoOpens.plusHours(1))).isTrue();
        }

        @Test @DisplayName("returns false before auto window opens")
        void beforeAutoWindow_returnsFalse() {
            RoundWindow rw = buildWindow(null);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(service.isRoundOpen("Matchday 1", autoOpens.minusHours(1))).isFalse();
        }

        @Test @DisplayName("returns false after auto window closes")
        void afterAutoWindow_returnsFalse() {
            RoundWindow rw = buildWindow(null);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(service.isRoundOpen("Matchday 1", autoCloses.plusHours(1))).isFalse();
        }

        @Test @DisplayName("returns true at exactly autoOpensAt")
        void atAutoOpensAt_returnsTrue() {
            RoundWindow rw = buildWindow(null);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(service.isRoundOpen("Matchday 1", autoOpens)).isTrue();
        }

        @Test @DisplayName("returns false at exactly autoClosesAt (exclusive)")
        void atAutoClosesAt_returnsFalse() {
            RoundWindow rw = buildWindow(null);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(service.isRoundOpen("Matchday 1", autoCloses)).isFalse();
        }

        @Test @DisplayName("FORCE_OPEN overrides auto closed")
        void forceOpen_overridesAutoClosed() {
            RoundWindow rw = buildWindow(RoundOverrideStatus.FORCE_OPEN);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(service.isRoundOpen("Matchday 1", autoCloses.plusHours(5))).isTrue();
        }

        @Test @DisplayName("FORCE_CLOSED overrides auto open")
        void forceClosed_overridesAutoOpen() {
            RoundWindow rw = buildWindow(RoundOverrideStatus.FORCE_CLOSED);
            when(roundWindowRepository.findByRoundLabel("Matchday 1")).thenReturn(Optional.of(rw));
            assertThat(service.isRoundOpen("Matchday 1", autoOpens.plusHours(1))).isFalse();
        }
    }

    private RoundWindow buildWindow(RoundOverrideStatus override) {
        return RoundWindow.builder()
                .id(1L)
                .roundLabel("Matchday 1")
                .overrideStatus(override)
                .autoOpensAt(autoOpens)
                .autoClosesAt(autoCloses)
                .build();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw test -pl . -Dtest=RoundWindowServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation error — `RoundWindowService` does not exist yet.

- [ ] **Step 3: Implement `RoundWindowService`**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.RoundWindowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RoundWindowService {

    private final RoundWindowRepository roundWindowRepository;
    private final MatchRepository matchRepository;

    public boolean isRoundOpen(String roundLabel, LocalDateTime now) {
        Optional<RoundWindow> opt = roundWindowRepository.findByRoundLabel(roundLabel);
        if (opt.isEmpty()) return false;
        RoundWindow rw = opt.get();
        if (rw.getOverrideStatus() == RoundOverrideStatus.FORCE_OPEN) return true;
        if (rw.getOverrideStatus() == RoundOverrideStatus.FORCE_CLOSED) return false;
        if (rw.getAutoOpensAt() == null || rw.getAutoClosesAt() == null) return false;
        return !now.isBefore(rw.getAutoOpensAt()) && now.isBefore(rw.getAutoClosesAt());
    }

    @Transactional
    public RoundWindow openRound(String roundLabel) {
        RoundWindow rw = findOrThrow(roundLabel);
        rw.setOverrideStatus(RoundOverrideStatus.FORCE_OPEN);
        return roundWindowRepository.save(rw);
    }

    @Transactional
    public RoundWindow closeRound(String roundLabel) {
        RoundWindow rw = findOrThrow(roundLabel);
        rw.setOverrideStatus(RoundOverrideStatus.FORCE_CLOSED);
        return roundWindowRepository.save(rw);
    }

    @Transactional
    public RoundWindow resetOverride(String roundLabel) {
        RoundWindow rw = findOrThrow(roundLabel);
        rw.setOverrideStatus(null);
        return roundWindowRepository.save(rw);
    }

    public List<RoundWindow> findAll() {
        return roundWindowRepository.findAllOrderByAutoOpensAtAsc();
    }

    public Optional<RoundWindow> findByRoundLabel(String roundLabel) {
        return roundWindowRepository.findByRoundLabel(roundLabel);
    }

    @Transactional
    public void recalculateAutoTimes(String roundLabel) {
        RoundWindow rw = findOrThrow(roundLabel);
        var matches = matchRepository.findByRoundLabelWithTeams(roundLabel);
        if (matches.isEmpty()) return;
        LocalDateTime firstKickoff = matches.stream()
                .map(m -> m.getKickoffTime())
                .min(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime lastKickoff = matches.stream()
                .map(m -> m.getKickoffTime())
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (firstKickoff != null) rw.setAutoOpensAt(firstKickoff.minusHours(24));
        if (lastKickoff != null) rw.setAutoClosesAt(lastKickoff.minusHours(1));
        roundWindowRepository.save(rw);
    }

    private RoundWindow findOrThrow(String roundLabel) {
        return roundWindowRepository.findByRoundLabel(roundLabel)
                .orElseThrow(() -> new IllegalArgumentException("Round window not found: " + roundLabel));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw test -pl . -Dtest=RoundWindowServiceTest`
Expected: All 8 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/RoundWindowService.java \
        src/test/java/com/worldcup/prediction/service/RoundWindowServiceTest.java
git commit -m "feat: add RoundWindowService with auto/override logic and tests"
```

---

### Task 3: Flyway Migration — Create `round_windows` Table and Rebuild `matches`

**Files:**
- Create: `src/main/resources/db/migration/V7__round_level_prediction_window.sql`

- [ ] **Step 1: Write the migration**

The migration must: (1) create `round_windows`, (2) populate from existing match data, (3) rebuild `matches` without the 3 prediction window columns, (4) recreate all indexes. Uses the SQLite table-rebuild pattern consistent with V6.

```sql
-- =============================================================================
-- V7__round_level_prediction_window.sql
-- Move prediction window from per-match to per-round (RoundWindow entity).
-- =============================================================================

-- 1. Create round_windows table
CREATE TABLE round_windows (
    id                 INTEGER PRIMARY KEY,
    round_label        VARCHAR(50)  NOT NULL UNIQUE,
    override_status    VARCHAR(20),
    auto_opens_at      TIMESTAMP,
    auto_closes_at     TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Populate from existing match data
INSERT INTO round_windows (round_label, auto_opens_at, auto_closes_at)
SELECT m.round_label,
       DATETIME(MIN(m.kickoff_time), '-24 hours'),
       DATETIME(MAX(m.kickoff_time), '-1 hours')
FROM matches m
WHERE m.round_label IS NOT NULL
GROUP BY m.round_label;

-- 3. Rebuild matches table without prediction window columns
CREATE TABLE matches_new (
    id                           INTEGER PRIMARY KEY,
    external_id                  VARCHAR(50),
    stage                        VARCHAR(50)  NOT NULL,
    group_id                     INTEGER REFERENCES groups(id),
    match_number                 INTEGER      NOT NULL,
    round_label                  VARCHAR(50),
    home_team_id                 INTEGER REFERENCES teams(id),
    away_team_id                 INTEGER REFERENCES teams(id),
    home_team_placeholder        VARCHAR(100),
    away_team_placeholder        VARCHAR(100),
    kickoff_time                 TIMESTAMP    NOT NULL,
    venue                        VARCHAR(200),
    city                         VARCHAR(100),
    status                       VARCHAR(50)  NOT NULL DEFAULT 'SCHEDULED',
    home_score                   INTEGER,
    away_score                   INTEGER,
    home_score_90                INTEGER,
    away_score_90                INTEGER,
    lineup_fetched               INTEGER      NOT NULL DEFAULT 0,
    result_entered_at            TIMESTAMP,
    result_entered_by_id         INTEGER REFERENCES users(id),
    created_at                   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(match_number)
);

INSERT INTO matches_new (
    id, external_id, stage, group_id, match_number, round_label,
    home_team_id, away_team_id, home_team_placeholder, away_team_placeholder,
    kickoff_time, venue, city, status, home_score, away_score,
    home_score_90, away_score_90, lineup_fetched,
    result_entered_at, result_entered_by_id, created_at, updated_at
)
SELECT
    id, external_id, stage, group_id, match_number, round_label,
    home_team_id, away_team_id, home_team_placeholder, away_team_placeholder,
    kickoff_time, venue, city, status, home_score, away_score,
    home_score_90, away_score_90, lineup_fetched,
    result_entered_at, result_entered_by_id, created_at, updated_at
FROM matches;

DROP TABLE matches;
ALTER TABLE matches_new RENAME TO matches;

-- 4. Recreate all matches indexes (no prediction_window_idx)
CREATE INDEX matches_stage_idx ON matches(stage);
CREATE INDEX matches_kickoff_time_idx ON matches(kickoff_time);
CREATE INDEX matches_status_idx ON matches(status);
CREATE INDEX matches_group_id_idx ON matches(group_id);
CREATE INDEX matches_home_team_id_idx ON matches(home_team_id);
CREATE INDEX matches_away_team_id_idx ON matches(away_team_id);
CREATE INDEX matches_lineup_fetched_idx ON matches(lineup_fetched);
CREATE INDEX matches_round_label_idx ON matches(round_label);
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/db/migration/V7__round_level_prediction_window.sql
git commit -m "feat: add V7 migration — round_windows table, drop per-match window columns"
```

---

### Task 4: Update `Match` Entity — Remove Prediction Window Fields

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/domain/Match.java`

- [ ] **Step 1: Remove the 3 prediction window fields from `Match.java`**

Remove these lines from `Match.java`:

```java
    @Column(name = "prediction_window_open", nullable = false)
    @Builder.Default
    private boolean predictionWindowOpen = false;

    @Column(name = "prediction_window_opens_at")
    private LocalDateTime predictionWindowOpensAt;

    @Column(name = "prediction_window_closes_at")
    private LocalDateTime predictionWindowClosesAt;
```

The resulting `Match.java` should go directly from the `lineupFetched` field to the `resultEnteredAt` field.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/worldcup/prediction/domain/Match.java
git commit -m "refactor: remove per-match prediction window fields from Match entity"
```

---

### Task 5: Update `AuditAction` Enum

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/domain/enums/AuditAction.java`

- [ ] **Step 1: Replace per-match audit actions with round-level ones**

Replace the full enum with:

```java
package com.worldcup.prediction.domain.enums;

public enum AuditAction {
    USER_APPROVED,
    USER_REJECTED,
    USER_DISABLED,
    USER_ENABLED,
    MATCH_RESULT_ENTERED,
    MATCH_RESULT_OVERRIDDEN,
    ROUND_WINDOW_OPENED,
    ROUND_WINDOW_CLOSED,
    ROUND_WINDOW_RESET,
    PREDICTION_EDITED_BY_ADMIN,
    POINTS_OVERRIDDEN,
    KNOCKOUT_PROGRESSION_UPDATED,
    REMINDER_SENT
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/worldcup/prediction/domain/enums/AuditAction.java
git commit -m "refactor: replace per-match window audit actions with round-level ones"
```

---

### Task 6: Update `MatchRepository` — Remove Prediction Window Queries

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/repository/MatchRepository.java`

- [ ] **Step 1: Remove these methods from `MatchRepository`**

Remove these 4 items:
1. The `findOpenPredictionWindows()` method and its `@Query`
2. The `findMatchesWhereWindowShouldOpen(LocalDateTime now)` method and its `@Query`
3. The `findMatchesWhereWindowShouldClose(LocalDateTime now)` method and its `@Query`
4. The `findByPredictionWindowOpen(boolean open)` method

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/worldcup/prediction/repository/MatchRepository.java
git commit -m "refactor: remove per-match prediction window queries from MatchRepository"
```

---

### Task 7: Update `PredictionRepository` — Rewrite `countPendingForOpenWindows`

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/repository/PredictionRepository.java`

- [ ] **Step 1: Rewrite the query to join `RoundWindow`**

Replace the `countPendingForOpenWindows` method with:

```java
    @Query("""
            SELECT COUNT(p) FROM Prediction p
            JOIN p.match m
            JOIN RoundWindow rw ON rw.roundLabel = m.roundLabel
            WHERE p.user.id = :userId
              AND (rw.overrideStatus = 'FORCE_OPEN'
                   OR (rw.overrideStatus IS NULL
                       AND rw.autoOpensAt <= CURRENT_TIMESTAMP
                       AND rw.autoClosesAt > CURRENT_TIMESTAMP))
              AND p.scoreResult = 'PENDING'
            """)
    long countPendingForOpenWindows(@Param("userId") Long userId);
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/worldcup/prediction/repository/PredictionRepository.java
git commit -m "refactor: rewrite countPendingForOpenWindows to use RoundWindow"
```

---

### Task 8: Update `PredictionService` — Delegate to `RoundWindowService`

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/service/PredictionService.java`

- [ ] **Step 1: Add `RoundWindowService` dependency and rewrite `isWindowOpen`**

Add `RoundWindowService` to the constructor-injected fields:

```java
    private final RoundWindowService roundWindowService;
```

Replace the `isWindowOpen` method:

```java
    public boolean isWindowOpen(Match match, LocalDateTime now) {
        return roundWindowService.isRoundOpen(match.getRoundLabel(), now);
    }
```

Update the class-level Javadoc to reflect the new window rules:

```java
/**
 * Manages match score predictions: submission, visibility, and window enforcement.
 *
 * Window rules:
 *   Round-level: window is determined by RoundWindowService (auto from kickoff times + admin override).
 *   All-or-nothing: all matches in the submitted batch must be in an open round.
 *   Visibility: non-admin users see predictions only after the round window closes.
 */
```

Update `getPredictionsForMatch` to use round-level window:

```java
    public List<Prediction> getPredictionsForMatch(Match match, LocalDateTime now, boolean isAdmin, Long communityId) {
        boolean roundClosed = !roundWindowService.isRoundOpen(match.getRoundLabel(), now);
        if (!isAdmin && !roundClosed) {
            return List.of();
        }
        return predictionRepository.findByMatchIdAndCommunityId(match.getId(), communityId);
    }
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/PredictionService.java
git commit -m "refactor: PredictionService delegates window checks to RoundWindowService"
```

---

### Task 9: Update `MatchAdminService` — Remove Per-Match Window Methods

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/service/MatchAdminService.java`

- [ ] **Step 1: Remove per-match window methods**

Remove these two methods:
1. `setPredictionWindowOpen(Long matchId, boolean open)` — the entire method
2. `findByPredictionWindowOpen(boolean open)` — the entire method

Also remove the `match.setPredictionWindowOpen(false);` line from `setResult()` method — prediction windows are now managed at round level. The updated `setResult` should be:

```java
    @Transactional
    public Match setResult(Long matchId, int homeScore, int awayScore) {
        Match match = findById(matchId);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setStatus(MatchStatus.COMPLETED);
        return matchRepository.save(match);
    }
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/MatchAdminService.java
git commit -m "refactor: remove per-match window methods from MatchAdminService"
```

---

### Task 10: Update `MatchServiceImpl` — Use `RoundWindowService`

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/service/MatchServiceImpl.java`

- [ ] **Step 1: Add `RoundWindowService` dependency and rewrite match queries**

Add the dependency:

```java
    private final RoundWindowService roundWindowService;
```

Replace `getNextPredictableMatch`:

```java
    @Override
    public FixtureViewDto getNextPredictableMatch(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return matchRepository.findAllWithTeams().stream()
                .filter(m -> roundWindowService.isRoundOpen(m.getRoundLabel(), now))
                .findFirst()
                .map(this::toDto)
                .orElse(null);
    }
```

Replace `getOpenMatchCount`:

```java
    @Override
    public int getOpenMatchCount() {
        LocalDateTime now = LocalDateTime.now();
        return (int) matchRepository.findAllWithTeams().stream()
                .filter(m -> roundWindowService.isRoundOpen(m.getRoundLabel(), now))
                .count();
    }
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/MatchServiceImpl.java
git commit -m "refactor: MatchServiceImpl uses RoundWindowService for open match queries"
```

---

### Task 11: Update `PredictionViewService` — Use `RoundWindowService`

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/service/PredictionViewService.java`

- [ ] **Step 1: Add `RoundWindowService` dependency**

Add to the constructor-injected fields:

```java
    private final RoundWindowService roundWindowService;
```

- [ ] **Step 2: Update `getRoundSummaries` to use `RoundWindowService`**

Replace the status determination block inside the `for` loop. Currently the code checks `allComplete`, then `now.isAfter(firstKickoff.minusHours(24))`. Replace with:

```java
            if (allComplete) {
                dto.setStatus("PAST");
                int pts = (int) predictionRepository.findByUserIdAndMatchIdInAndCommunityId(userId, matchIds, communityId).stream()
                        .mapToInt(Prediction::getPointsAwarded).sum();
                dto.setPointsEarned(pts);
            } else if (roundWindowService.isRoundOpen(label, now)) {
                dto.setStatus("OPEN");
                long predicted = predictionRepository.countByUserIdAndMatchIdInAndCommunityId(userId, matchIds, communityId);
                dto.setPredictedCount((int) predicted);
            } else {
                dto.setStatus("FUTURE");
            }
```

- [ ] **Step 3: Update `submitPredictionsForRound` to check round window**

Add a round-level check at the beginning of the method, before the existing per-match kickoff check:

```java
        if (!roundWindowService.isRoundOpen(dto.getRoundLabel(), now)) {
            throw new IllegalStateException("The prediction window for " + dto.getRoundLabel() + " is not open.");
        }
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/PredictionViewService.java
git commit -m "refactor: PredictionViewService uses RoundWindowService for round status"
```

---

### Task 12: Update `AdminMatchController` — Round-Level Window Actions

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/controller/admin/AdminMatchController.java`

- [ ] **Step 1: Add `RoundWindowService` dependency and restructure**

Add to the constructor-injected fields:

```java
    private final RoundWindowService roundWindowService;
```

- [ ] **Step 2: Update `listMatches` to group by round**

Replace the `listMatches` method:

```java
    @GetMapping
    public String listMatches(Model model) {
        List<Match> allMatches = matchAdminService.findAllOrderByKickoffAsc();
        List<RoundWindow> roundWindows = roundWindowService.findAll();
        LocalDateTime now = LocalDateTime.now();

        Map<String, RoundWindow> windowMap = roundWindows.stream()
                .collect(Collectors.toMap(RoundWindow::getRoundLabel, rw -> rw));

        Map<String, List<Match>> matchesByRound = new LinkedHashMap<>();
        for (Match m : allMatches) {
            matchesByRound.computeIfAbsent(m.getRoundLabel(), k -> new ArrayList<>()).add(m);
        }

        Map<String, String> roundStatuses = new LinkedHashMap<>();
        Map<String, Boolean> roundOverridden = new LinkedHashMap<>();
        for (String roundLabel : matchesByRound.keySet()) {
            List<Match> matches = matchesByRound.get(roundLabel);
            boolean allCompleted = matches.stream().allMatch(Match::isCompleted);
            if (allCompleted) {
                roundStatuses.put(roundLabel, "PAST");
            } else if (roundWindowService.isRoundOpen(roundLabel, now)) {
                roundStatuses.put(roundLabel, "OPEN");
            } else {
                roundStatuses.put(roundLabel, "FUTURE");
            }
            RoundWindow rw = windowMap.get(roundLabel);
            roundOverridden.put(roundLabel, rw != null && rw.getOverrideStatus() != null);
        }

        model.addAttribute("matchesByRound", matchesByRound);
        model.addAttribute("roundStatuses", roundStatuses);
        model.addAttribute("roundOverridden", roundOverridden);
        return "admin/matches";
    }
```

Add these imports at the top:

```java
import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.service.RoundWindowService;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
```

- [ ] **Step 3: Replace per-match open/close endpoints with round-level ones**

Remove the `openWindow` and `closeWindow` methods. Add:

```java
    @PostMapping("/rounds/{roundLabel}/open")
    public String openRound(@PathVariable String roundLabel,
                            @AuthenticationPrincipal CustomOAuth2User admin,
                            RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        roundWindowService.openRound(roundLabel);
        auditLogService.log(adminId, AuditAction.ROUND_WINDOW_OPENED, "ROUND", null,
                "Round opened: " + roundLabel);
        redirectAttributes.addFlashAttribute("successMessage",
                "Prediction window opened for " + roundLabel);
        return "redirect:/admin/matches";
    }

    @PostMapping("/rounds/{roundLabel}/close")
    public String closeRound(@PathVariable String roundLabel,
                             @AuthenticationPrincipal CustomOAuth2User admin,
                             RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        roundWindowService.closeRound(roundLabel);
        auditLogService.log(adminId, AuditAction.ROUND_WINDOW_CLOSED, "ROUND", null,
                "Round closed: " + roundLabel);
        redirectAttributes.addFlashAttribute("successMessage",
                "Prediction window closed for " + roundLabel);
        return "redirect:/admin/matches";
    }

    @PostMapping("/rounds/{roundLabel}/reset")
    public String resetRound(@PathVariable String roundLabel,
                             @AuthenticationPrincipal CustomOAuth2User admin,
                             RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        roundWindowService.resetOverride(roundLabel);
        auditLogService.log(adminId, AuditAction.ROUND_WINDOW_RESET, "ROUND", null,
                "Round reset to auto: " + roundLabel);
        redirectAttributes.addFlashAttribute("successMessage",
                "Prediction window reset to automatic for " + roundLabel);
        return "redirect:/admin/matches";
    }
```

- [ ] **Step 4: Update `sendReminder` to be round-level**

Replace the `sendReminder` method:

```java
    @PostMapping("/rounds/{roundLabel}/send-reminder")
    public String sendRoundReminder(@PathVariable String roundLabel,
                                    @AuthenticationPrincipal CustomOAuth2User admin,
                                    RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        List<User> activeUsers = userService.findByStatus(UserStatus.ACTIVE);
        activeUsers.forEach(u -> emailService.sendPredictionReminder(u, roundLabel));
        auditLogService.log(adminId, AuditAction.REMINDER_SENT, "ROUND", null,
                "Reminder for: " + roundLabel + " (" + activeUsers.size() + " recipients)");
        redirectAttributes.addFlashAttribute("successMessage",
                "Reminder sent for " + roundLabel + " (" + activeUsers.size() + " participants).");
        return "redirect:/admin/matches";
    }
```

Also remove the old `sendReminder` method that takes `@PathVariable Long id`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/admin/AdminMatchController.java
git commit -m "refactor: AdminMatchController uses round-level window actions"
```

---

### Task 13: Update `AdminDashboardController` — Round-Level Open Windows

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/controller/admin/AdminDashboardController.java`

- [ ] **Step 1: Replace per-match open windows with round-level count**

Add `RoundWindowService` dependency:

```java
    private final RoundWindowService roundWindowService;
```

Replace the `openWindows` model attribute in the `dashboard` method:

```java
        long openRoundCount = roundWindowService.findAll().stream()
                .filter(rw -> roundWindowService.isRoundOpen(rw.getRoundLabel(), LocalDateTime.now()))
                .count();
        model.addAttribute("openRoundCount", openRoundCount);
```

Remove the old `openWindows` line:
```java
        List<Match> openWindows = matchAdminService.findByPredictionWindowOpen(true);
        model.addAttribute("openWindows", openWindows);
```

Add import:
```java
import com.worldcup.prediction.service.RoundWindowService;
import java.time.LocalDateTime;
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/admin/AdminDashboardController.java
git commit -m "refactor: AdminDashboardController uses round-level open window count"
```

---

### Task 14: Update `NotificationScheduler` — Round-Level Window Checks

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/scheduler/NotificationScheduler.java`

- [ ] **Step 1: Add `RoundWindowService` dependency**

Add to the constructor-injected fields:

```java
    private final RoundWindowService roundWindowService;
```

- [ ] **Step 2: Rewrite `checkPredictionWindowOpen`**

Replace the method. Instead of checking per-match `findMatchesWhereWindowShouldOpen`, check rounds that have just become open. Since the scheduler runs every 5 minutes, we check all rounds and only notify for ones that recently transitioned to open:

```java
    @Scheduled(fixedDelay = 300_000)
    public void checkPredictionWindowOpen() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<RoundWindow> allRounds = roundWindowService.findAll();
            List<RoundWindow> openRounds = allRounds.stream()
                    .filter(rw -> roundWindowService.isRoundOpen(rw.getRoundLabel(), now))
                    .filter(rw -> rw.getAutoOpensAt() != null
                            && !now.isBefore(rw.getAutoOpensAt())
                            && now.isBefore(rw.getAutoOpensAt().plusMinutes(10)))
                    .toList();

            if (openRounds.isEmpty()) {
                log.debug("NotificationScheduler: no newly-open rounds — skipping");
                return;
            }

            List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
            List<Community> communities = communityRepository.findAll();
            for (RoundWindow rw : openRounds) {
                List<Match> matches = matchRepository.findByRoundLabelWithTeams(rw.getRoundLabel());
                if (matches.isEmpty()) continue;
                Match firstMatch = matches.get(0);
                for (Community community : communities) {
                    boolean sent = notificationService.sendPredictionWindowOpen(activeUsers, firstMatch, community.getId());
                    if (sent) {
                        log.info("Sent prediction-window-open notification for round {} in community {}", rw.getRoundLabel(), community.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.error("NotificationScheduler.checkPredictionWindowOpen error", e);
        }
    }
```

- [ ] **Step 3: Update `checkPredictionDeadline`**

Replace the filter on `isPredictionWindowOpen()` with a round-level check:

Change:
```java
            approachingMatches = approachingMatches.stream()
                    .filter(m -> m.getStatus() == MatchStatus.SCHEDULED && m.isPredictionWindowOpen())
                    .collect(Collectors.toList());
```

To:
```java
            LocalDateTime deadlineNow = LocalDateTime.now();
            approachingMatches = approachingMatches.stream()
                    .filter(m -> m.getStatus() == MatchStatus.SCHEDULED
                            && roundWindowService.isRoundOpen(m.getRoundLabel(), deadlineNow))
                    .collect(Collectors.toList());
```

Add these imports:
```java
import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.service.RoundWindowService;
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worldcup/prediction/scheduler/NotificationScheduler.java
git commit -m "refactor: NotificationScheduler uses round-level window checks"
```

---

### Task 15: Update `admin/matches.html` — Group by Round with Collapsible Sections

**Files:**
- Modify: `src/main/resources/templates/admin/matches.html`

- [ ] **Step 1: Replace the flat table with grouped-by-round layout**

Replace the entire `layout:fragment="content"` block:

```html
<th:block layout:fragment="content">

  <div th:if="${successMessage}" class="mb-4 px-4 py-3 rounded-lg bg-green-50 border border-green-200 text-green-800 text-sm">
    <span th:text="${successMessage}"></span>
  </div>

  <div th:if="${#maps.isEmpty(matchesByRound)}" class="bg-white rounded-xl shadow-sm border border-gray-100 p-12 text-center text-sm text-gray-400">
    No matches found.
  </div>

  <div th:each="entry : ${matchesByRound}" class="mb-4" x-data="{ open: ${roundStatuses[entry.key]} == 'OPEN' }">

    <!-- Round header -->
    <div class="bg-white rounded-t-xl shadow-sm border border-gray-100 px-6 py-4 flex items-center justify-between cursor-pointer"
         @click="open = !open"
         :class="{ 'rounded-b-xl': !open }">
      <div class="flex items-center gap-3">
        <svg :class="{ 'rotate-90': open }" class="w-4 h-4 text-gray-400 transition-transform duration-200" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7"/>
        </svg>
        <h2 class="font-semibold text-gray-800" th:text="${entry.key}">Round Label</h2>
        <span class="text-xs text-gray-400" th:text="${#lists.size(entry.value)} + ' matches'"></span>

        <!-- Status badge -->
        <span th:if="${roundStatuses[entry.key] == 'OPEN'}"
              class="px-2 py-0.5 rounded-full bg-green-100 text-green-700 text-xs font-medium">Open</span>
        <span th:if="${roundStatuses[entry.key] == 'FUTURE'}"
              class="px-2 py-0.5 rounded-full bg-blue-100 text-blue-700 text-xs font-medium">Future</span>
        <span th:if="${roundStatuses[entry.key] == 'PAST'}"
              class="px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 text-xs font-medium">Past</span>

        <span th:if="${roundOverridden[entry.key]}"
              class="px-2 py-0.5 rounded-full bg-yellow-100 text-yellow-700 text-xs font-medium">Override</span>
      </div>

      <div class="flex items-center gap-2" @click.stop>
        <form th:if="${roundStatuses[entry.key] != 'OPEN'}"
              th:action="@{'/admin/matches/rounds/' + ${entry.key} + '/open'}" method="post" class="inline">
          <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
          <button type="submit"
                  class="px-3 py-1 text-xs font-medium rounded-md bg-green-100 hover:bg-green-200 text-green-800 transition-colors duration-150">
            Open Round
          </button>
        </form>

        <form th:if="${roundStatuses[entry.key] == 'OPEN'}"
              th:action="@{'/admin/matches/rounds/' + ${entry.key} + '/close'}" method="post" class="inline">
          <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
          <button type="submit"
                  class="px-3 py-1 text-xs font-medium rounded-md bg-red-100 hover:bg-red-200 text-red-800 transition-colors duration-150">
            Close Round
          </button>
        </form>

        <form th:if="${roundOverridden[entry.key]}"
              th:action="@{'/admin/matches/rounds/' + ${entry.key} + '/reset'}" method="post" class="inline">
          <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
          <button type="submit"
                  class="px-3 py-1 text-xs font-medium rounded-md bg-yellow-100 hover:bg-yellow-200 text-yellow-800 transition-colors duration-150">
            Reset
          </button>
        </form>

        <form th:action="@{'/admin/matches/rounds/' + ${entry.key} + '/send-reminder'}" method="post" class="inline">
          <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
          <button type="submit"
                  class="px-3 py-1 text-xs font-medium rounded-md bg-blue-100 hover:bg-blue-200 text-blue-800 transition-colors duration-150">
            Reminder
          </button>
        </form>
      </div>
    </div>

    <!-- Matches table (collapsible) -->
    <div x-show="open" x-transition class="bg-white rounded-b-xl shadow-sm border border-t-0 border-gray-100 overflow-hidden">
      <table class="w-full text-sm">
        <thead>
          <tr class="bg-gray-50 border-b border-gray-100 text-left">
            <th class="px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Match</th>
            <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Kickoff</th>
            <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Result</th>
            <th class="px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Actions</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr th:each="match : ${entry.value}" class="hover:bg-gray-50 transition-colors duration-100">
            <td class="px-6 py-3">
              <div class="flex items-center gap-2">
                <img th:if="${match.homeTeam != null}"
                     th:src="@{'/images/flags/' + ${match.homeTeam.flagCode} + '.svg'}"
                     class="w-5 h-5 rounded-full object-cover" alt=""/>
                <span class="font-medium text-gray-900" th:text="${match.homeTeam?.name ?: 'TBD'}">Home</span>
                <span class="text-gray-400 text-xs">vs</span>
                <span class="font-medium text-gray-900" th:text="${match.awayTeam?.name ?: 'TBD'}">Away</span>
                <img th:if="${match.awayTeam != null}"
                     th:src="@{'/images/flags/' + ${match.awayTeam.flagCode} + '.svg'}"
                     class="w-5 h-5 rounded-full object-cover" alt=""/>
              </div>
            </td>
            <td class="px-4 py-3 text-xs text-gray-600"
                th:text="${#temporals.format(match.kickoffTime, 'dd MMM HH:mm')}">01 Jun 18:00</td>
            <td class="px-4 py-3">
              <span th:if="${match.completed}"
                    class="font-mono font-semibold text-gray-900"
                    th:text="${match.homeScore} + '–' + ${match.awayScore}">0–0</span>
              <span th:unless="${match.completed}" class="text-gray-400 text-xs">Pending</span>
            </td>
            <td class="px-6 py-3">
              <div class="flex items-center gap-2 flex-wrap" x-data="{ showResultForm: false }">
                <button @click="showResultForm = !showResultForm"
                        class="px-3 py-1 text-xs font-medium rounded-md bg-admin-dark hover:bg-admin-mid text-white transition-colors duration-150"
                        th:text="${match.completed} ? 'Update Result' : 'Enter Result'">Enter Result</button>

                <div x-show="showResultForm" x-transition class="w-full mt-2">
                  <form th:action="@{'/admin/matches/' + ${match.id} + '/result'}" method="post"
                        class="flex items-center gap-2 bg-gray-50 rounded-lg px-3 py-2 border border-gray-200">
                    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
                    <label class="text-xs text-gray-500">Home</label>
                    <input type="number" name="homeScore" min="0" max="99"
                           th:value="${match.homeScore ?: 0}"
                           class="w-14 text-center border border-gray-300 rounded px-2 py-1 text-sm focus:ring-1 focus:ring-admin-light focus:outline-none"/>
                    <span class="text-gray-400 font-bold">–</span>
                    <input type="number" name="awayScore" min="0" max="99"
                           th:value="${match.awayScore ?: 0}"
                           class="w-14 text-center border border-gray-300 rounded px-2 py-1 text-sm focus:ring-1 focus:ring-admin-light focus:outline-none"/>
                    <label class="text-xs text-gray-500">Away</label>
                    <button type="submit"
                            class="px-3 py-1 text-xs font-medium rounded-md bg-green-600 hover:bg-green-700 text-white transition-colors duration-150">Save</button>
                    <button type="button" @click="showResultForm = false"
                            class="px-3 py-1 text-xs font-medium rounded-md bg-gray-200 hover:bg-gray-300 text-gray-700 transition-colors duration-150">Cancel</button>
                  </form>
                </div>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>

</th:block>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/admin/matches.html
git commit -m "feat: admin matches page grouped by round with collapsible sections"
```

---

### Task 16: Update `admin/dashboard.html` — Round-Level Window Count

**Files:**
- Modify: `src/main/resources/templates/admin/dashboard.html`

- [ ] **Step 1: Update the "Open Prediction Windows" stat card**

Replace the stat card that references `openWindows` with `openRoundCount`:

Find the third stat card div. Change:
```html
        <p class="text-2xl font-bold text-gray-900" th:text="${#lists.size(openWindows)}">0</p>
        <p class="text-sm text-gray-500">Open Prediction Windows</p>
```

To:
```html
        <p class="text-2xl font-bold text-gray-900" th:text="${openRoundCount}">0</p>
        <p class="text-sm text-gray-500">Open Rounds</p>
```

- [ ] **Step 2: Remove per-match window badge from Today's Schedule**

In the Today's Schedule section, remove the two `span` elements that show "Open"/"Closed" based on `match.predictionWindowOpen`:

```html
            <span th:if="${match.predictionWindowOpen}"
                  class="px-2 py-0.5 rounded-full bg-green-100 text-green-700 text-xs font-medium">Open</span>
            <span th:unless="${match.predictionWindowOpen}"
                  class="px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 text-xs font-medium">Closed</span>
```

Remove both of these spans entirely. The kickoff time display is sufficient for the today schedule.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/admin/dashboard.html
git commit -m "refactor: dashboard uses round-level open count instead of per-match windows"
```

---

### Task 17: Update `MatchSyncService` — Create/Update `RoundWindow` on Sync

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/integration/football/MatchSyncService.java`

- [ ] **Step 1: Add `RoundWindowRepository` dependency**

Add to the constructor-injected fields:

```java
    private final RoundWindowRepository roundWindowRepository;
```

Add import:
```java
import com.worldcup.prediction.repository.RoundWindowRepository;
import com.worldcup.prediction.domain.RoundWindow;
```

- [ ] **Step 2: Create/update `RoundWindow` entries after match sync**

At the end of `syncGroupStageMatches()`, before the return statement, add:

```java
        // Create or update RoundWindow entries for synced rounds
        List<String> roundLabels = matchRepository.findDistinctRoundLabels();
        for (String label : roundLabels) {
            var matches = matchRepository.findByRoundLabelWithTeams(label);
            if (matches.isEmpty()) continue;
            LocalDateTime firstKickoff = matches.stream()
                    .map(Match::getKickoffTime).min(LocalDateTime::compareTo).orElse(null);
            LocalDateTime lastKickoff = matches.stream()
                    .map(Match::getKickoffTime).max(LocalDateTime::compareTo).orElse(null);
            RoundWindow rw = roundWindowRepository.findByRoundLabel(label)
                    .orElse(RoundWindow.builder().roundLabel(label).build());
            if (firstKickoff != null) rw.setAutoOpensAt(firstKickoff.minusHours(24));
            if (lastKickoff != null) rw.setAutoClosesAt(lastKickoff.minusHours(1));
            roundWindowRepository.save(rw);
        }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worldcup/prediction/integration/football/MatchSyncService.java
git commit -m "feat: MatchSyncService creates/updates RoundWindow entries on sync"
```

---

### Task 18: Update Tests

**Files:**
- Modify: `src/test/java/com/worldcup/prediction/service/PredictionServiceTest.java`
- Modify: `src/test/java/com/worldcup/prediction/repository/MatchRepositoryTest.java`
- Modify: `src/test/java/com/worldcup/prediction/repository/PredictionRepositoryTest.java`
- Modify: `src/test/java/com/worldcup/prediction/controller/admin/AdminMatchControllerTest.java`
- Modify: `src/test/java/com/worldcup/prediction/scheduler/NotificationSchedulerTest.java`

- [ ] **Step 1: Update `PredictionServiceTest`**

Add a `@Mock RoundWindowService roundWindowService;` field.

Update `setUp()` to pass the new dependency:
```java
        predictionService = new PredictionService(predictionRepository, matchRepository, userRepository, communityRepository, roundWindowService);
```

Update `buildMatch` — remove `predictionWindowOpensAt` and `predictionWindowClosesAt`:
```java
    private Match buildMatch(Long id, LocalDateTime openTime, LocalDateTime closeTime) {
        Match m = Match.builder()
                .matchNumber(id.intValue())
                .stage(MatchStage.GROUP)
                .roundLabel("Group Stage Round 1")
                .kickoffTime(openTime.plusHours(24))
                .status(MatchStatus.SCHEDULED)
                .build();
        m.setId(id);
        return m;
    }
```

Update `IsWindowOpen` tests to mock `roundWindowService`:
```java
    @Nested
    @DisplayName("isWindowOpen")
    class IsWindowOpen {

        @Test @DisplayName("returns true when round is open")
        void roundOpen_returnsTrue() {
            Match m = buildMatch(1L, openTime, lockTime);
            when(roundWindowService.isRoundOpen("Group Stage Round 1", nowOpen)).thenReturn(true);
            assertThat(predictionService.isWindowOpen(m, nowOpen)).isTrue();
        }

        @Test @DisplayName("returns false when round is closed")
        void roundClosed_returnsFalse() {
            Match m = buildMatch(1L, openTime, lockTime);
            when(roundWindowService.isRoundOpen("Group Stage Round 1", nowLocked)).thenReturn(false);
            assertThat(predictionService.isWindowOpen(m, nowLocked)).isFalse();
        }
    }
```

Update `SubmitPredictions` tests — add `roundWindowService` mock setup. In `happyPath_savesAll` and `upsert_updatesExisting`, add:
```java
            when(roundWindowService.isRoundOpen("Group Stage Round 1", nowOpen)).thenReturn(true);
```

In `windowLocked_throws` and `windowNotYetOpen_throws`, add:
```java
            when(roundWindowService.isRoundOpen("Group Stage Round 1", nowLocked)).thenReturn(false);
```
and
```java
            when(roundWindowService.isRoundOpen("Group Stage Round 1", nowBeforeOpen)).thenReturn(false);
```

Update `GetPredictionsForMatch` tests — mock `roundWindowService`:
- `afterLock_returns`: add `when(roundWindowService.isRoundOpen("Group Stage Round 1", nowLocked)).thenReturn(false);`
- `beforeLock_nonAdmin_empty`: add `when(roundWindowService.isRoundOpen("Group Stage Round 1", nowOpen)).thenReturn(true);`
- `admin_beforeLock_returns`: add `when(roundWindowService.isRoundOpen("Group Stage Round 1", nowOpen)).thenReturn(true);`

- [ ] **Step 2: Update `MatchRepositoryTest`**

Remove the `findOpenPredictionWindows_returnsOnlyOpenWindows` test entirely (the query no longer exists).

Remove `.predictionWindowOpen(false)` and `.predictionWindowOpen(true).predictionWindowClosesAt(...)` from any remaining test builders.

- [ ] **Step 3: Update `PredictionRepositoryTest`**

In `setUp`, remove `.predictionWindowOpen(true).predictionWindowClosesAt(LocalDateTime.now().plusHours(23))` from `match1` builder and `.predictionWindowOpen(false)` from `match2` builder:

```java
        match1 = matchRepository.save(Match.builder()
                .matchNumber(1).stage(MatchStage.GROUP).roundLabel("Group Stage Round 1")
                .kickoffTime(LocalDateTime.now().plusDays(1)).status(MatchStatus.SCHEDULED)
                .build());

        match2 = matchRepository.save(Match.builder()
                .matchNumber(2).stage(MatchStage.GROUP).roundLabel("Group Stage Round 1")
                .kickoffTime(LocalDateTime.now().plusDays(2)).status(MatchStatus.SCHEDULED)
                .build());
```

- [ ] **Step 4: Update `AdminMatchControllerTest`**

Add `@MockBean RoundWindowService roundWindowService;` field.

Remove `openWindow_opensAndRedirects` and `closeWindow_closesAndRedirects` tests.

Add round-level tests:
```java
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void openRound_opensAndRedirects() throws Exception {
        when(roundWindowService.openRound("Group Stage MD1"))
                .thenReturn(RoundWindow.builder().roundLabel("Group Stage MD1").build());

        mockMvc.perform(post("/admin/matches/rounds/Group Stage MD1/open").with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/matches"));

        verify(auditLogService).log(any(), eq(AuditAction.ROUND_WINDOW_OPENED), eq("ROUND"), isNull(), anyString());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void closeRound_closesAndRedirects() throws Exception {
        when(roundWindowService.closeRound("Group Stage MD1"))
                .thenReturn(RoundWindow.builder().roundLabel("Group Stage MD1").build());

        mockMvc.perform(post("/admin/matches/rounds/Group Stage MD1/close").with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/matches"));

        verify(auditLogService).log(any(), eq(AuditAction.ROUND_WINDOW_CLOSED), eq("ROUND"), isNull(), anyString());
    }
```

Add import:
```java
import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.enums.AuditAction;
import com.worldcup.prediction.service.RoundWindowService;
import static org.mockito.ArgumentMatchers.isNull;
```

Update `listMatches_returnsMatchesPage` to also mock `roundWindowService.findAll()`:
```java
        when(roundWindowService.findAll()).thenReturn(List.of());
```

- [ ] **Step 5: Update `NotificationSchedulerTest`**

Add `@Mock RoundWindowService roundWindowService;` field.

Update `checkPredictionWindowOpen_noMatches_skips`:
```java
    @Test
    void checkPredictionWindowOpen_noMatches_skips() {
        when(roundWindowService.findAll()).thenReturn(List.of());
        scheduler.checkPredictionWindowOpen();
        verify(notificationService, never()).sendPredictionWindowOpen(anyList(), any(), anyLong());
    }
```

Add import:
```java
import com.worldcup.prediction.service.RoundWindowService;
```

- [ ] **Step 6: Run all tests**

Run: `cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw test`
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add src/test/
git commit -m "test: update all tests for round-level prediction window"
```

---

### Task 19: Delete SQLite DB and Verify Full Application Startup

- [ ] **Step 1: Delete the existing SQLite database so Flyway re-runs from scratch**

```bash
rm -f /Users/arthurho/dev/tools/world-cup-prediction/worldcup.db
```

- [ ] **Step 2: Run all tests again to verify clean migration**

Run: `cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw test`
Expected: All tests PASS with clean schema.

- [ ] **Step 3: Run a final build**

Run: `cd /Users/arthurho/dev/tools/world-cup-prediction && ./mvnw clean package -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit any remaining changes**

```bash
git status
# If worldcup.db was tracked, add .gitignore entry or just confirm clean state
```
