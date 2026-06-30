# Playoff Scoring & Manual Result Protection — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 90-min result tracking for knockout matches, protect manually-entered results from API overwrite, and let users predict the playoff winner when they predict a draw.

**Architecture:** New enums and entity fields wire through migrations → domain → service → API sync → controllers → templates. ScoringService stays stateless and gains a `calculatePlayoffWinnerBonus` method. MatchAdminService grows `set90MinResult` for knockout matches alongside the existing `setResult` for group stage. FootballApiSyncService is updated to write `homeScore90`/`awayScore90` from `score.regularTime` and respect the `MANUAL` lock.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Data JPA, Flyway, Thymeleaf, Alpine.js, SQLite/PostgreSQL

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `src/main/resources/db/migration/V18__match_result_tracking.sql` | Create | Add `result_source`, `playoff_winner` to matches |
| `src/main/resources/db/migration/V19__prediction_playoff_winner.sql` | Create | Add `predicted_playoff_winner` to predictions |
| `src/main/resources/db/migration/V20__tournament_settings_knockout_mode.sql` | Create | Add `knockout_scoring_mode` to tournament_settings |
| `src/main/java/com/worldcup/prediction/domain/enums/ResultSource.java` | Create | MANUAL / API enum |
| `src/main/java/com/worldcup/prediction/domain/enums/PlayoffWinner.java` | Create | HOME_WIN / AWAY_WIN enum |
| `src/main/java/com/worldcup/prediction/domain/enums/PlayoffWinnerPick.java` | Create | HOME / AWAY enum (user's pick) |
| `src/main/java/com/worldcup/prediction/domain/enums/KnockoutScoringMode.java` | Create | NINETY_MINUTES / FULL_TIME enum |
| `src/main/java/com/worldcup/prediction/domain/enums/PredictionScore.java` | Modify | Add EXACT_DRAW_WINNER(3) |
| `src/main/java/com/worldcup/prediction/domain/enums/MatchStage.java` | Modify | Add isKnockout() helper |
| `src/main/java/com/worldcup/prediction/domain/Match.java` | Modify | Add resultSource, playoffWinner fields; add isKnockout() |
| `src/main/java/com/worldcup/prediction/domain/Prediction.java` | Modify | Add predictedPlayoffWinner field |
| `src/main/java/com/worldcup/prediction/domain/TournamentSettings.java` | Modify | Add knockoutScoringMode field |
| `src/main/java/com/worldcup/prediction/integration/football/FootballApiClient.java` | Modify | Add winner, regularTime to FootballApiScoreDto |
| `src/main/java/com/worldcup/prediction/service/TournamentSettingsService.java` | Modify | Add getKnockoutScoringMode(), updateKnockoutScoringMode() |
| `src/main/java/com/worldcup/prediction/service/ScoringService.java` | Modify | Add calculatePlayoffWinnerBonus() |
| `src/main/java/com/worldcup/prediction/service/MatchAdminService.java` | Modify | Add set90MinResult(); update scoreAllPredictions(), resetResult() |
| `src/main/java/com/worldcup/prediction/integration/football/FootballApiSyncService.java` | Modify | Sync regularTime, winner; respect MANUAL lock |
| `src/main/java/com/worldcup/prediction/dto/PredictionDto.java` | Modify | Add playoffWinner (String, nullable) |
| `src/main/java/com/worldcup/prediction/service/PredictionService.java` | Modify | Save predictedPlayoffWinner in submitPredictions() |
| `src/main/java/com/worldcup/prediction/controller/admin/AdminMatchController.java` | Modify | Update enterResult(); add unlockResult() endpoint |
| `src/main/java/com/worldcup/prediction/controller/admin/AdminSettingsController.java` | Modify | Add updateKnockoutScoringMode() endpoint |
| `src/main/resources/templates/admin/matches.html` | Modify | Split form for knockout: 90-min + playoff winner picker |
| `src/main/resources/templates/admin/settings.html` | Modify | Add knockout scoring mode radio |
| `src/main/resources/templates/fragments/predictions-round-content.html` | Modify | Alpine.js playoff winner picker; submit winner with score |
| `src/main/resources/templates/fragments/heroes-content.html` | Modify | Include EXACT_DRAW_WINNER in exact count display |
| `src/main/resources/templates/rules.html` | Modify | Add knockout scoring section |
| `src/test/java/com/worldcup/prediction/service/ScoringServiceTest.java` | Modify | Tests for calculatePlayoffWinnerBonus |
| `src/test/java/com/worldcup/prediction/service/MatchAdminServiceTest.java` | Create | Tests for set90MinResult, updated scoreAllPredictions |

---

## Task 1: DB Migrations

**Files:**
- Create: `src/main/resources/db/migration/V18__match_result_tracking.sql`
- Create: `src/main/resources/db/migration/V19__prediction_playoff_winner.sql`
- Create: `src/main/resources/db/migration/V20__tournament_settings_knockout_mode.sql`

- [ ] **Step 1: Create V18**

```sql
-- V18__match_result_tracking.sql
ALTER TABLE matches ADD COLUMN result_source VARCHAR(10);
ALTER TABLE matches ADD COLUMN playoff_winner VARCHAR(10);
```

- [ ] **Step 2: Create V19**

```sql
-- V19__prediction_playoff_winner.sql
ALTER TABLE predictions ADD COLUMN predicted_playoff_winner VARCHAR(5);
```

- [ ] **Step 3: Create V20**

```sql
-- V20__tournament_settings_knockout_mode.sql
ALTER TABLE tournament_settings ADD COLUMN knockout_scoring_mode VARCHAR(20) NOT NULL DEFAULT 'NINETY_MINUTES';
```

- [ ] **Step 4: Run migrations to verify they apply cleanly**

```bash
./mvnw flyway:migrate -Dflyway.url=jdbc:sqlite:./data/prediction.db
```

Expected: `Successfully applied 3 migrations`

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V18__match_result_tracking.sql \
        src/main/resources/db/migration/V19__prediction_playoff_winner.sql \
        src/main/resources/db/migration/V20__tournament_settings_knockout_mode.sql
git commit -m "feat: add db migrations for playoff result tracking and knockout scoring mode"
```

---

## Task 2: New Enums + Update PredictionScore + MatchStage

**Files:**
- Create: `src/main/java/com/worldcup/prediction/domain/enums/ResultSource.java`
- Create: `src/main/java/com/worldcup/prediction/domain/enums/PlayoffWinner.java`
- Create: `src/main/java/com/worldcup/prediction/domain/enums/PlayoffWinnerPick.java`
- Create: `src/main/java/com/worldcup/prediction/domain/enums/KnockoutScoringMode.java`
- Modify: `src/main/java/com/worldcup/prediction/domain/enums/PredictionScore.java`
- Modify: `src/main/java/com/worldcup/prediction/domain/enums/MatchStage.java`

- [ ] **Step 1: Create ResultSource.java**

```java
package com.worldcup.prediction.domain.enums;

public enum ResultSource {
    MANUAL,
    API
}
```

- [ ] **Step 2: Create PlayoffWinner.java**

```java
package com.worldcup.prediction.domain.enums;

public enum PlayoffWinner {
    HOME_WIN,
    AWAY_WIN
}
```

- [ ] **Step 3: Create PlayoffWinnerPick.java**

```java
package com.worldcup.prediction.domain.enums;

public enum PlayoffWinnerPick {
    HOME,
    AWAY
}
```

- [ ] **Step 4: Create KnockoutScoringMode.java**

```java
package com.worldcup.prediction.domain.enums;

public enum KnockoutScoringMode {
    NINETY_MINUTES,
    FULL_TIME
}
```

- [ ] **Step 5: Add EXACT_DRAW_WINNER to PredictionScore.java**

Find the file and add the new value. Current file has `EXACT(3), CORRECT_DRAW(2), CORRECT_WINNER(1), WRONG(0), PENDING(0)`. Add after EXACT:

```java
EXACT(3),
EXACT_DRAW_WINNER(3),
CORRECT_DRAW(2),
CORRECT_WINNER(1),
WRONG(0),
PENDING(0);
```

Keep all existing constructors and methods unchanged.

- [ ] **Step 6: Add isKnockout() to MatchStage.java**

Add this method to the enum body (after the existing `getOrder()` or similar last method):

```java
public boolean isKnockout() {
    return this != GROUP;
}
```

- [ ] **Step 7: Compile to verify**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/worldcup/prediction/domain/enums/
git commit -m "feat: add ResultSource, PlayoffWinner, PlayoffWinnerPick, KnockoutScoringMode enums; add EXACT_DRAW_WINNER to PredictionScore"
```

---

## Task 3: Update Match Entity

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/domain/Match.java`

- [ ] **Step 1: Add new fields to Match.java**

Add these two fields after the `resultEnteredBy` field:

```java
@Enumerated(EnumType.STRING)
@Column(name = "result_source", length = 10)
private ResultSource resultSource;

@Enumerated(EnumType.STRING)
@Column(name = "playoff_winner", length = 10)
private PlayoffWinner playoffWinner;
```

Add the required imports:
```java
import com.worldcup.prediction.domain.enums.PlayoffWinner;
import com.worldcup.prediction.domain.enums.ResultSource;
```

- [ ] **Step 2: Add isKnockout() to Match.java**

Add this method alongside the existing `isGroupStage()` and `isCompleted()` methods:

```java
public boolean isKnockout() {
    return stage != null && stage.isKnockout();
}
```

- [ ] **Step 3: Compile to verify**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worldcup/prediction/domain/Match.java
git commit -m "feat: add resultSource, playoffWinner fields to Match; add isKnockout() helper"
```

---

## Task 4: Update Prediction Entity

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/domain/Prediction.java`

- [ ] **Step 1: Add predictedPlayoffWinner field to Prediction.java**

Add after the `adminEditNote` field:

```java
@Enumerated(EnumType.STRING)
@Column(name = "predicted_playoff_winner", length = 5)
private PlayoffWinnerPick predictedPlayoffWinner;
```

Add the import:
```java
import com.worldcup.prediction.domain.enums.PlayoffWinnerPick;
```

The Lombok `@Getter @Setter` annotations already on the class will generate the accessor methods automatically.

- [ ] **Step 2: Compile to verify**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worldcup/prediction/domain/Prediction.java
git commit -m "feat: add predictedPlayoffWinner field to Prediction entity"
```

---

## Task 5: Update TournamentSettings Entity + Service

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/domain/TournamentSettings.java`
- Modify: `src/main/java/com/worldcup/prediction/service/TournamentSettingsService.java`

- [ ] **Step 1: Add knockoutScoringMode field to TournamentSettings.java**

Add after the `roundLockOffsetMinutes` field:

```java
@Enumerated(EnumType.STRING)
@Column(name = "knockout_scoring_mode", nullable = false, length = 20)
@Builder.Default
private KnockoutScoringMode knockoutScoringMode = KnockoutScoringMode.NINETY_MINUTES;
```

Add import:
```java
import com.worldcup.prediction.domain.enums.KnockoutScoringMode;
```

- [ ] **Step 2: Update getSettings() default in TournamentSettingsService.java**

Find the `getSettings()` method where defaults are applied (when creating initial settings). Add `knockoutScoringMode = KnockoutScoringMode.NINETY_MINUTES` to the defaults builder or constructor call.

- [ ] **Step 3: Add getKnockoutScoringMode() to TournamentSettingsService.java**

```java
public KnockoutScoringMode getKnockoutScoringMode() {
    return getSettings().getKnockoutScoringMode();
}
```

Add import:
```java
import com.worldcup.prediction.domain.enums.KnockoutScoringMode;
```

- [ ] **Step 4: Add updateKnockoutScoringMode() to TournamentSettingsService.java**

```java
@Transactional
public TournamentSettings updateKnockoutScoringMode(KnockoutScoringMode mode) {
    TournamentSettings settings = getSettings();
    settings.setKnockoutScoringMode(mode);
    return settingsRepository.save(settings);
}
```

(Use the same repository field name already in the service, e.g., `settingsRepository` or `tournamentSettingsRepository`.)

- [ ] **Step 5: Compile to verify**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/worldcup/prediction/domain/TournamentSettings.java \
        src/main/java/com/worldcup/prediction/service/TournamentSettingsService.java
git commit -m "feat: add knockoutScoringMode to TournamentSettings with runtime getter/updater"
```

---

## Task 6: Update FootballApiClient DTO

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/integration/football/FootballApiClient.java`

- [ ] **Step 1: Update FootballApiScoreDto record**

Find the existing record:
```java
public record FootballApiScoreDto(
    FootballApiScoreSideDto fullTime,
    FootballApiScoreSideDto halfTime
)
```

Replace with:
```java
public record FootballApiScoreDto(
    String winner,
    FootballApiScoreSideDto fullTime,
    FootballApiScoreSideDto halfTime,
    FootballApiScoreSideDto regularTime
)
```

`winner` is `"HOME_TEAM"`, `"AWAY_TEAM"`, `"DRAW"`, or `null`. `regularTime` is `null` for matches decided in 90 minutes; non-null for extra-time / penalty matches. Jackson deserializes missing fields as `null` for records.

- [ ] **Step 2: Compile to verify**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS (if any callers of `FootballApiScoreDto` constructor break, fix them now — there should be none outside the client itself since records are deserialized by Jackson, not constructed manually in production code).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worldcup/prediction/integration/football/FootballApiClient.java
git commit -m "feat: add winner and regularTime fields to FootballApiScoreDto"
```

---

## Task 7: Tests + ScoringService — calculatePlayoffWinnerBonus

**Files:**
- Modify: `src/test/java/com/worldcup/prediction/service/ScoringServiceTest.java`
- Modify: `src/main/java/com/worldcup/prediction/service/ScoringService.java`

- [ ] **Step 1: Write failing tests for calculatePlayoffWinnerBonus**

Add a new `@Nested` class `CalculatePlayoffWinnerBonus` inside `ScoringServiceTest`:

```java
@Nested
@DisplayName("calculatePlayoffWinnerBonus")
class CalculatePlayoffWinnerBonus {

    @Test
    @DisplayName("+1 when exact draw and winner correct (HOME)")
    void bonus_exactDrawCorrectWinnerHome() {
        assertThat(scoringService.calculatePlayoffWinnerBonus(
                1, 1, PlayoffWinner.HOME_WIN,
                1, 1, PlayoffWinnerPick.HOME)).isEqualTo(1);
    }

    @Test
    @DisplayName("+1 when exact draw and winner correct (AWAY)")
    void bonus_exactDrawCorrectWinnerAway() {
        assertThat(scoringService.calculatePlayoffWinnerBonus(
                0, 0, PlayoffWinner.AWAY_WIN,
                0, 0, PlayoffWinnerPick.AWAY)).isEqualTo(1);
    }

    @Test
    @DisplayName("0 when exact draw but wrong winner pick")
    void bonus_exactDrawWrongWinner() {
        assertThat(scoringService.calculatePlayoffWinnerBonus(
                1, 1, PlayoffWinner.HOME_WIN,
                1, 1, PlayoffWinnerPick.AWAY)).isEqualTo(0);
    }

    @Test
    @DisplayName("0 when exact draw but no winner pick")
    void bonus_exactDrawNoWinnerPick() {
        assertThat(scoringService.calculatePlayoffWinnerBonus(
                1, 1, PlayoffWinner.HOME_WIN,
                1, 1, null)).isEqualTo(0);
    }

    @Test
    @DisplayName("0 when predicted draw but wrong score (not exact)")
    void bonus_wrongDrawScore() {
        assertThat(scoringService.calculatePlayoffWinnerBonus(
                1, 1, PlayoffWinner.HOME_WIN,
                0, 0, PlayoffWinnerPick.HOME)).isEqualTo(0);
    }

    @Test
    @DisplayName("0 when match is not a draw at 90 min")
    void bonus_notADraw() {
        assertThat(scoringService.calculatePlayoffWinnerBonus(
                2, 1, PlayoffWinner.HOME_WIN,
                2, 1, PlayoffWinnerPick.HOME)).isEqualTo(0);
    }

    @Test
    @DisplayName("0 when match playoff winner not set")
    void bonus_noActualWinner() {
        assertThat(scoringService.calculatePlayoffWinnerBonus(
                1, 1, null,
                1, 1, PlayoffWinnerPick.HOME)).isEqualTo(0);
    }

    @Test
    @DisplayName("0 when prediction is not a draw")
    void bonus_predictedNotDraw() {
        assertThat(scoringService.calculatePlayoffWinnerBonus(
                1, 1, PlayoffWinner.HOME_WIN,
                2, 1, PlayoffWinnerPick.HOME)).isEqualTo(0);
    }
}
```

Add imports at the top of the test file:
```java
import com.worldcup.prediction.domain.enums.PlayoffWinner;
import com.worldcup.prediction.domain.enums.PlayoffWinnerPick;
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./mvnw test -pl . -Dtest=ScoringServiceTest#CalculatePlayoffWinnerBonus -q
```

Expected: FAIL — `calculatePlayoffWinnerBonus` does not exist yet.

- [ ] **Step 3: Implement calculatePlayoffWinnerBonus in ScoringService.java**

Add the method to `ScoringService`:

```java
public int calculatePlayoffWinnerBonus(int homeScore90, int awayScore90, PlayoffWinner actualWinner,
                                        int predictedHome, int predictedAway, PlayoffWinnerPick predictedWinner) {
    if (actualWinner == null || predictedWinner == null) return 0;
    if (homeScore90 != awayScore90) return 0;           // not a 90-min draw
    if (predictedHome != predictedAway) return 0;       // user didn't predict draw
    if (predictedHome != homeScore90) return 0;         // not exact draw score
    boolean homeWon = actualWinner == PlayoffWinner.HOME_WIN;
    boolean pickedHome = predictedWinner == PlayoffWinnerPick.HOME;
    return homeWon == pickedHome ? 1 : 0;
}
```

Add imports to ScoringService:
```java
import com.worldcup.prediction.domain.enums.PlayoffWinner;
import com.worldcup.prediction.domain.enums.PlayoffWinnerPick;
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=ScoringServiceTest -q
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/ScoringService.java \
        src/test/java/com/worldcup/prediction/service/ScoringServiceTest.java
git commit -m "feat: add calculatePlayoffWinnerBonus to ScoringService with tests"
```

---

## Task 8: Tests + MatchAdminService — set90MinResult + updated scoreAllPredictions + resetResult

**Files:**
- Create: `src/test/java/com/worldcup/prediction/service/MatchAdminServiceTest.java`
- Modify: `src/main/java/com/worldcup/prediction/service/MatchAdminService.java`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/com/worldcup/prediction/service/MatchAdminServiceTest.java`:

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.*;
import com.worldcup.prediction.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchAdminServiceTest {

    @Mock MatchRepository matchRepository;
    @Mock PredictionRepository predictionRepository;
    @Mock CommunityMembershipRepository membershipRepository;
    @Mock TournamentSettingsService tournamentSettingsService;
    @InjectMocks MatchAdminService matchAdminService;

    // ScoringService is NOT mocked — use real instance for scoring logic
    @Spy ScoringService scoringService = new ScoringService();

    private Match knockoutMatch(int home90, int away90, PlayoffWinner winner) {
        Match m = new Match();
        m.setId(1L);
        m.setStage(MatchStage.QUARTER_FINAL);
        m.setHomeScore90(home90);
        m.setAwayScore90(away90);
        m.setHomeScore(home90);
        m.setAwayScore(away90);
        m.setPlayoffWinner(winner);
        m.setStatus(MatchStatus.COMPLETED);
        return m;
    }

    private Prediction pred(Match m, Community c, User u, int home, int away, PlayoffWinnerPick pick) {
        Prediction p = new Prediction();
        p.setMatch(m);
        p.setCommunity(c);
        p.setUser(u);
        p.setPredictedHome(home);
        p.setPredictedAway(away);
        p.setPredictedPlayoffWinner(pick);
        return p;
    }

    @Nested
    @DisplayName("set90MinResult")
    class Set90MinResult {

        @Test
        @DisplayName("sets homeScore90, awayScore90, resultSource MANUAL, status COMPLETED")
        void setsFieldsCorrectly() {
            Match m = new Match();
            m.setId(1L);
            m.setStage(MatchStage.QUARTER_FINAL);
            m.setStatus(MatchStatus.SCHEDULED);
            User admin = new User();
            when(matchRepository.findById(1L)).thenReturn(Optional.of(m));
            when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            matchAdminService.set90MinResult(1L, 1, 1, PlayoffWinner.HOME_WIN, admin);

            assertThat(m.getHomeScore90()).isEqualTo(1);
            assertThat(m.getAwayScore90()).isEqualTo(1);
            assertThat(m.getPlayoffWinner()).isEqualTo(PlayoffWinner.HOME_WIN);
            assertThat(m.getResultSource()).isEqualTo(ResultSource.MANUAL);
            assertThat(m.getResultEnteredBy()).isEqualTo(admin);
            assertThat(m.getResultEnteredAt()).isNotNull();
            assertThat(m.getStatus()).isEqualTo(MatchStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("scoreAllPredictions — knockout NINETY_MINUTES mode")
    class ScoreAllPredictionsKnockout {

        @Test
        @DisplayName("awards EXACT_DRAW_WINNER (3pts) for exact draw + correct winner")
        void exactDrawWithWinner() {
            Match m = knockoutMatch(1, 1, PlayoffWinner.HOME_WIN);
            Community c = new Community(); c.setId(10L);
            User u = new User(); u.setId(5L);
            Prediction p = pred(m, c, u, 1, 1, PlayoffWinnerPick.HOME);

            when(matchRepository.findById(1L)).thenReturn(Optional.of(m));
            when(predictionRepository.findByMatchIdWithDetails(1L)).thenReturn(List.of(p));
            when(predictionRepository.findByUserIdAndCommunityId(5L, 10L)).thenReturn(List.of(p));
            when(membershipRepository.findByUserIdAndCommunityId(5L, 10L)).thenReturn(Optional.empty());
            when(tournamentSettingsService.getKnockoutScoringMode())
                .thenReturn(KnockoutScoringMode.NINETY_MINUTES);

            matchAdminService.scoreAllPredictions(1L);

            assertThat(p.getPointsAwarded()).isEqualTo(3);
            assertThat(p.getScoreResult()).isEqualTo(PredictionScore.EXACT_DRAW_WINNER);
        }

        @Test
        @DisplayName("awards CORRECT_DRAW (2pts) for exact draw + wrong winner")
        void exactDrawWrongWinner() {
            Match m = knockoutMatch(1, 1, PlayoffWinner.HOME_WIN);
            Community c = new Community(); c.setId(10L);
            User u = new User(); u.setId(5L);
            Prediction p = pred(m, c, u, 1, 1, PlayoffWinnerPick.AWAY);

            when(matchRepository.findById(1L)).thenReturn(Optional.of(m));
            when(predictionRepository.findByMatchIdWithDetails(1L)).thenReturn(List.of(p));
            when(predictionRepository.findByUserIdAndCommunityId(5L, 10L)).thenReturn(List.of(p));
            when(membershipRepository.findByUserIdAndCommunityId(5L, 10L)).thenReturn(Optional.empty());
            when(tournamentSettingsService.getKnockoutScoringMode())
                .thenReturn(KnockoutScoringMode.NINETY_MINUTES);

            matchAdminService.scoreAllPredictions(1L);

            assertThat(p.getPointsAwarded()).isEqualTo(2);
            assertThat(p.getScoreResult()).isEqualTo(PredictionScore.CORRECT_DRAW);
        }
    }

    @Nested
    @DisplayName("resetResult")
    class ResetResult {

        @Test
        @DisplayName("clears resultSource, resultEnteredAt, resultEnteredBy, playoffWinner")
        void clearsNewFields() {
            Match m = new Match();
            m.setId(1L);
            m.setStage(MatchStage.QUARTER_FINAL);
            m.setStatus(MatchStatus.COMPLETED);
            m.setResultSource(ResultSource.MANUAL);
            m.setPlayoffWinner(PlayoffWinner.HOME_WIN);
            m.setResultEnteredAt(java.time.LocalDateTime.now());
            m.setResultEnteredBy(new User());

            when(matchRepository.findById(1L)).thenReturn(Optional.of(m));
            when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(predictionRepository.findByMatchIdWithDetails(1L)).thenReturn(List.of());

            matchAdminService.resetResult(1L);

            assertThat(m.getResultSource()).isNull();
            assertThat(m.getPlayoffWinner()).isNull();
            assertThat(m.getResultEnteredAt()).isNull();
            assertThat(m.getResultEnteredBy()).isNull();
        }
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./mvnw test -pl . -Dtest=MatchAdminServiceTest -q 2>&1 | tail -20
```

Expected: Compilation errors — `set90MinResult` not found, `findByMatchIdWithDetails` may not exist. This is expected.

- [ ] **Step 3: Add set90MinResult to MatchAdminService.java**

Add the method (keep existing `setResult` for group-stage use):

```java
@Transactional
public Match set90MinResult(Long matchId, int home90, int away90,
                             PlayoffWinner playoffWinner, User adminUser) {
    Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
    match.setHomeScore90(home90);
    match.setAwayScore90(away90);
    match.setPlayoffWinner(playoffWinner);
    match.setResultSource(ResultSource.MANUAL);
    match.setResultEnteredAt(LocalDateTime.now());
    match.setResultEnteredBy(adminUser);
    match.setStatus(MatchStatus.COMPLETED);
    if (match.getHomeScore() == null) {
        match.setHomeScore(home90);
        match.setAwayScore(away90);
    }
    return matchRepository.save(match);
}
```

Add imports:
```java
import com.worldcup.prediction.domain.enums.PlayoffWinner;
import com.worldcup.prediction.domain.enums.ResultSource;
import java.time.LocalDateTime;
```

- [ ] **Step 4: Update scoreAllPredictions in MatchAdminService.java**

Replace the existing scoring loop. The key changes: check `knockoutScoringMode`, apply bonus, detect `EXACT_DRAW_WINNER`, update `recalculateMembershipStats` to count `EXACT_DRAW_WINNER`:

```java
@Transactional
public void scoreAllPredictions(Long matchId) {
    Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

    KnockoutScoringMode mode = tournamentSettingsService.getKnockoutScoringMode();
    int effectiveHome, effectiveAway;
    if (match.isKnockout() && mode == KnockoutScoringMode.NINETY_MINUTES) {
        effectiveHome = match.getEffectiveHomeScore();
        effectiveAway = match.getEffectiveAwayScore();
    } else {
        effectiveHome = match.getHomeScore();
        effectiveAway = match.getAwayScore();
    }

    List<Prediction> predictions = predictionRepository.findByMatchIdWithDetails(matchId);
    for (Prediction prediction : predictions) {
        int basePoints = scoringService.calculatePoints(
                effectiveHome, effectiveAway,
                prediction.getPredictedHome(), prediction.getPredictedAway());

        int bonusPoints = 0;
        if (match.isKnockout() && mode == KnockoutScoringMode.NINETY_MINUTES) {
            bonusPoints = scoringService.calculatePlayoffWinnerBonus(
                    effectiveHome, effectiveAway, match.getPlayoffWinner(),
                    prediction.getPredictedHome(), prediction.getPredictedAway(),
                    prediction.getPredictedPlayoffWinner());
        }

        prediction.setPointsAwarded(basePoints + bonusPoints);

        PredictionScore scoreResult;
        if (bonusPoints == 1 && basePoints == 2) {
            scoreResult = PredictionScore.EXACT_DRAW_WINNER;
        } else {
            scoreResult = scoringService.determineScoreResult(
                    effectiveHome, effectiveAway,
                    prediction.getPredictedHome(), prediction.getPredictedAway());
        }
        prediction.setScoreResult(scoreResult);
        predictionRepository.save(prediction);
        recalculateMembershipStats(prediction.getUser().getId(), prediction.getCommunity().getId());
    }
}
```

Add imports:
```java
import com.worldcup.prediction.domain.enums.KnockoutScoringMode;
import com.worldcup.prediction.domain.enums.PredictionScore;
```

Note: `predictionRepository.findByMatchIdWithDetails` may already exist as `findByMatchId` — check the existing method name in `PredictionRepository` and use it. If it's named differently, update the test to match.

- [ ] **Step 5: Update recalculateMembershipStats to count EXACT_DRAW_WINNER as exact**

Find `recalculateMembershipStats` in `MatchAdminService`. The line counting exact scores currently looks like:

```java
long exactCount = preds.stream().filter(p -> p.getScoreResult() == PredictionScore.EXACT).count();
```

Change to:

```java
long exactCount = preds.stream()
        .filter(p -> p.getScoreResult() == PredictionScore.EXACT
                  || p.getScoreResult() == PredictionScore.EXACT_DRAW_WINNER)
        .count();
```

- [ ] **Step 6: Update resetResult to clear new fields**

Find the existing `resetResult` method and add these clears after the existing score/status resets:

```java
match.setResultSource(null);
match.setResultEnteredAt(null);
match.setResultEnteredBy(null);
match.setPlayoffWinner(null);
```

Also reset `predictedPlayoffWinner` on each prediction when resetting:
```java
for (Prediction prediction : predictions) {
    prediction.setPointsAwarded(0);
    prediction.setScoreResult(PredictionScore.PENDING);
    // predictedPlayoffWinner is intentionally NOT cleared — user's pick stays
    predictionRepository.save(prediction);
}
```

- [ ] **Step 7: Inject TournamentSettingsService into MatchAdminService**

Add the field (Lombok `@RequiredArgsConstructor` will handle injection):

```java
private final TournamentSettingsService tournamentSettingsService;
```

- [ ] **Step 8: Run tests**

```bash
./mvnw test -pl . -Dtest=MatchAdminServiceTest,ScoringServiceTest -q
```

Expected: BUILD SUCCESS, all tests pass. If `findByMatchIdWithDetails` doesn't exist, check `PredictionRepository` for the correct method name and update both the test and the service.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/MatchAdminService.java \
        src/test/java/com/worldcup/prediction/service/MatchAdminServiceTest.java
git commit -m "feat: add set90MinResult, update scoreAllPredictions with playoff bonus and EXACT_DRAW_WINNER"
```

---

## Task 9: Update FootballApiSyncService

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/integration/football/FootballApiSyncService.java`

- [ ] **Step 1: Update syncResults() in FootballApiSyncService.java**

Find the block inside `syncResults()` that sets match scores after resolving a finished API match. Replace:

```java
match.setHomeScore(apiMatch.score().fullTime().home());
match.setAwayScore(apiMatch.score().fullTime().away());
match.setStatus(MatchStatus.COMPLETED);
```

With:

```java
// Always update final result (includes ET goals)
match.setHomeScore(apiMatch.score().fullTime().home());
match.setAwayScore(apiMatch.score().fullTime().away());
match.setStatus(MatchStatus.COMPLETED);

// Update playoff winner from API
if (apiMatch.score().winner() != null) {
    PlayoffWinner winner = switch (apiMatch.score().winner()) {
        case "HOME_TEAM" -> PlayoffWinner.HOME_WIN;
        case "AWAY_TEAM" -> PlayoffWinner.AWAY_WIN;
        default -> null;
    };
    match.setPlayoffWinner(winner);
}

// Update 90-min scores only if not manually locked
if (match.getResultSource() != ResultSource.MANUAL) {
    var regularTime = apiMatch.score().regularTime();
    if (regularTime != null && regularTime.home() != null) {
        match.setHomeScore90(regularTime.home());
        match.setAwayScore90(regularTime.away());
    } else {
        // Match decided in 90 min — fullTime IS the 90-min score
        match.setHomeScore90(apiMatch.score().fullTime().home());
        match.setAwayScore90(apiMatch.score().fullTime().away());
    }
    match.setResultSource(ResultSource.API);
}
```

Add imports:
```java
import com.worldcup.prediction.domain.enums.PlayoffWinner;
import com.worldcup.prediction.domain.enums.ResultSource;
```

- [ ] **Step 2: Compile to verify**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worldcup/prediction/integration/football/FootballApiSyncService.java
git commit -m "feat: sync 90-min result and playoff winner from API; respect MANUAL result lock"
```

---

## Task 10: Update PredictionDto + PredictionService

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/dto/PredictionDto.java`
- Modify: `src/main/java/com/worldcup/prediction/service/PredictionService.java`

- [ ] **Step 1: Add playoffWinner to PredictionDto.java**

Add the field and accessor alongside the existing `homeScore`/`awayScore`:

```java
private String playoffWinner; // "HOME", "AWAY", or null

public String getPlayoffWinner() { return playoffWinner; }
public void setPlayoffWinner(String playoffWinner) { this.playoffWinner = playoffWinner; }
```

Also add an update to the 3-arg constructor to keep it compiling (leave `playoffWinner` as null there):
```java
public PredictionDto(Long matchId, Integer homeScore, Integer awayScore) {
    this.matchId = matchId;
    this.homeScore = homeScore;
    this.awayScore = awayScore;
    this.playoffWinner = null;
}
```

- [ ] **Step 2: Update submitPredictions in PredictionService.java to save predictedPlayoffWinner**

Find the section where a new `Prediction` is built and where an existing prediction is updated. In both places, set `predictedPlayoffWinner` from the DTO:

```java
PlayoffWinnerPick winnerPick = null;
if ("HOME".equals(dto.getPlayoffWinner())) winnerPick = PlayoffWinnerPick.HOME;
else if ("AWAY".equals(dto.getPlayoffWinner())) winnerPick = PlayoffWinnerPick.AWAY;
```

For a new prediction (`.builder()` block), add:
```java
.predictedPlayoffWinner(winnerPick)
```

For updating an existing prediction, add:
```java
prediction.setPredictedPlayoffWinner(winnerPick);
```

Add import:
```java
import com.worldcup.prediction.domain.enums.PlayoffWinnerPick;
```

- [ ] **Step 3: Compile to verify**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worldcup/prediction/dto/PredictionDto.java \
        src/main/java/com/worldcup/prediction/service/PredictionService.java
git commit -m "feat: add predictedPlayoffWinner to PredictionDto and persist in submitPredictions"
```

---

## Task 11: Update AdminMatchController

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/controller/admin/AdminMatchController.java`

- [ ] **Step 1: Update enterResult() to call set90MinResult for knockout matches**

Find the existing `enterResult` method. It currently calls `matchAdminService.setResult(id, homeScore, awayScore)`. Replace the body with:

```java
@PostMapping("/{id}/result")
public String enterResult(@PathVariable Long id,
                          @RequestParam(required = false) @Min(0) Integer homeScore,
                          @RequestParam(required = false) @Min(0) Integer awayScore,
                          @RequestParam(required = false) @Min(0) Integer home90,
                          @RequestParam(required = false) @Min(0) Integer away90,
                          @RequestParam(required = false) String playoffWinner,
                          @AuthenticationPrincipal CustomOAuth2User admin,
                          RedirectAttributes redirectAttributes) {

    Match match = matchAdminService.findById(id);

    if (match.isKnockout()) {
        PlayoffWinner winner = "HOME".equals(playoffWinner) ? PlayoffWinner.HOME_WIN
                : "AWAY".equals(playoffWinner) ? PlayoffWinner.AWAY_WIN : null;
        matchAdminService.set90MinResult(id, home90, away90, winner, admin.getUser());
    } else {
        matchAdminService.setResult(id, homeScore, awayScore);
    }

    matchAdminService.scoreAllPredictions(id);
    auditLogService.log(admin.getUser(), AuditAction.MATCH_RESULT_ENTERED,
            "Match " + id + " result entered");
    redirectAttributes.addFlashAttribute("successMessage", "Result saved successfully.");
    return "redirect:/admin/matches";
}
```

Add imports:
```java
import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.enums.PlayoffWinner;
```

- [ ] **Step 2: Add unlockResult() endpoint**

```java
@PostMapping("/{id}/unlock-result")
public String unlockResult(@PathVariable Long id,
                           @AuthenticationPrincipal CustomOAuth2User admin,
                           RedirectAttributes redirectAttributes) {
    matchAdminService.unlockResult(id);
    auditLogService.log(admin.getUser(), AuditAction.MATCH_RESULT_RESET,
            "Match " + id + " result source unlocked for API re-sync");
    redirectAttributes.addFlashAttribute("successMessage", "Result unlocked. API will re-sync 90-min scores.");
    return "redirect:/admin/matches";
}
```

- [ ] **Step 3: Add unlockResult() to MatchAdminService.java**

```java
@Transactional
public void unlockResult(Long matchId) {
    Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
    match.setResultSource(null);
    match.setResultEnteredAt(null);
    match.setResultEnteredBy(null);
    matchRepository.save(match);
}
```

- [ ] **Step 4: Compile to verify**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/admin/AdminMatchController.java \
        src/main/java/com/worldcup/prediction/service/MatchAdminService.java
git commit -m "feat: update AdminMatchController to handle knockout 90-min result entry and unlock"
```

---

## Task 12: Update AdminSettingsController + admin/settings.html

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/controller/admin/AdminSettingsController.java`
- Modify: `src/main/resources/templates/admin/settings.html`

- [ ] **Step 1: Add knockout scoring mode to settings() model in AdminSettingsController**

In the `@GetMapping settings()` method, add to model:

```java
model.addAttribute("knockoutScoringModes", KnockoutScoringMode.values());
model.addAttribute("currentKnockoutScoringMode", tournamentSettingsService.getKnockoutScoringMode());
```

Add import:
```java
import com.worldcup.prediction.domain.enums.KnockoutScoringMode;
```

- [ ] **Step 2: Add updateKnockoutScoringMode() endpoint to AdminSettingsController**

```java
@PostMapping("/knockout-scoring-mode")
public String updateKnockoutScoringMode(
        @RequestParam KnockoutScoringMode knockoutScoringMode,
        RedirectAttributes redirectAttributes) {
    tournamentSettingsService.updateKnockoutScoringMode(knockoutScoringMode);
    redirectAttributes.addFlashAttribute("successMessage", "Knockout scoring mode updated.");
    return "redirect:/admin/settings";
}
```

- [ ] **Step 3: Add knockout scoring mode section to admin/settings.html**

Add a new card after the existing "Prediction Window Mode" card (before the closing `</div>` of `max-w-lg`):

```html
<!-- Knockout Scoring Mode -->
<div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
  <h2 class="text-lg font-semibold text-gray-800 mb-1">Knockout Scoring Mode</h2>
  <p class="text-sm text-gray-500 mb-4">Controls which result is used to score knockout-stage predictions.</p>
  <form th:action="@{/admin/settings/knockout-scoring-mode}" method="post" class="space-y-4">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <div class="flex gap-6 flex-wrap">
      <label class="flex items-center gap-2 cursor-pointer">
        <input type="radio" name="knockoutScoringMode" value="NINETY_MINUTES"
               th:checked="${currentKnockoutScoringMode != null and currentKnockoutScoringMode.name() == 'NINETY_MINUTES'}"/>
        <span class="font-medium text-sm">90-min Result</span>
        <span class="text-sm text-gray-500">— ignore extra time &amp; penalties (default)</span>
      </label>
      <label class="flex items-center gap-2 cursor-pointer">
        <input type="radio" name="knockoutScoringMode" value="FULL_TIME"
               th:checked="${currentKnockoutScoringMode != null and currentKnockoutScoringMode.name() == 'FULL_TIME'}"/>
        <span class="font-medium text-sm">Full-time Result</span>
        <span class="text-sm text-gray-500">— include extra time; playoff winner bonus disabled</span>
      </label>
    </div>
    <button type="submit"
            class="bg-admin-dark hover:bg-admin-mid text-white rounded-lg px-4 py-2 text-sm font-semibold">
      Save
    </button>
  </form>
</div>
```

- [ ] **Step 4: Compile to verify**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/admin/AdminSettingsController.java \
        src/main/resources/templates/admin/settings.html
git commit -m "feat: add knockout scoring mode toggle to admin settings page"
```

---

## Task 13: Update admin/matches.html — knockout result form

**Files:**
- Modify: `src/main/resources/templates/admin/matches.html`

- [ ] **Step 1: Update the result entry form in admin/matches.html**

Find the result entry form block (currently around line 134–152). Replace it with a conditional that shows different forms for group vs knockout matches:

```html
<div x-show="showResultForm" x-transition class="w-full mt-2">

  <!-- GROUP STAGE: simple home/away score -->
  <form th:if="${match.groupStage}"
        th:action="@{'/admin/matches/' + ${match.id} + '/result'}" method="post"
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

  <!-- KNOCKOUT: 90-min result + optional playoff winner -->
  <div th:unless="${match.groupStage}"
       x-data="{
         home90: /*[[${match.homeScore90 != null ? match.homeScore90 : 0}]]*/ 0,
         away90: /*[[${match.awayScore90 != null ? match.awayScore90 : 0}]]*/ 0,
         get isDraw() { return this.home90 === this.away90; }
       }">

    <!-- Final result (API-managed, read-only) -->
    <div th:if="${match.homeScore != null}"
         class="text-xs text-gray-500 mb-2">
      Final result (API):
      <span class="font-mono font-semibold text-gray-700"
            th:text="${match.homeScore} + '–' + ${match.awayScore}"></span>
      <span th:if="${match.playoffWinner != null}"
            class="ml-1 text-gray-500"
            th:text="'(' + ${match.playoffWinner.name()} + ')'"></span>
    </div>

    <form th:action="@{'/admin/matches/' + ${match.id} + '/result'}" method="post"
          class="flex flex-wrap items-center gap-2 bg-gray-50 rounded-lg px-3 py-2 border border-gray-200">
      <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
      <label class="text-xs text-gray-500">90-min Home</label>
      <input type="number" name="home90" min="0" max="30" x-model="home90"
             class="w-14 text-center border border-gray-300 rounded px-2 py-1 text-sm focus:ring-1 focus:ring-admin-light focus:outline-none"/>
      <span class="text-gray-400 font-bold">–</span>
      <input type="number" name="away90" min="0" max="30" x-model="away90"
             class="w-14 text-center border border-gray-300 rounded px-2 py-1 text-sm focus:ring-1 focus:ring-admin-light focus:outline-none"/>
      <label class="text-xs text-gray-500">Away</label>

      <!-- Playoff winner (shown when draw) -->
      <div x-show="isDraw" x-transition class="flex items-center gap-2 ml-2">
        <label class="text-xs text-gray-500">Winner:</label>
        <label class="flex items-center gap-1 text-xs cursor-pointer">
          <input type="radio" name="playoffWinner" value="HOME"
                 th:checked="${match.playoffWinner?.name() == 'HOME_WIN'}"/>
          <span th:text="${match.homeTeam?.name ?: 'Home'}">Home</span>
        </label>
        <label class="flex items-center gap-1 text-xs cursor-pointer">
          <input type="radio" name="playoffWinner" value="AWAY"
                 th:checked="${match.playoffWinner?.name() == 'AWAY_WIN'}"/>
          <span th:text="${match.awayTeam?.name ?: 'Away'}">Away</span>
        </label>
      </div>

      <button type="submit"
              class="px-3 py-1 text-xs font-medium rounded-md bg-green-600 hover:bg-green-700 text-white transition-colors duration-150">Save 90-min</button>
      <button type="button" @click="showResultForm = false"
              class="px-3 py-1 text-xs font-medium rounded-md bg-gray-200 hover:bg-gray-300 text-gray-700 transition-colors duration-150">Cancel</button>
    </form>

    <!-- Manual lock indicator + unlock -->
    <div th:if="${match.resultSource?.name() == 'MANUAL'}"
         class="mt-2 flex items-center gap-2 text-xs text-amber-700">
      <span>🔒 Manual — API sync locked</span>
      <form th:action="@{'/admin/matches/' + ${match.id} + '/unlock-result'}" method="post" class="inline">
        <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
        <button type="submit"
                class="px-2 py-0.5 rounded bg-amber-100 hover:bg-amber-200 text-amber-800">Unlock</button>
      </form>
    </div>
  </div>

</div>
```

Also update the result display cell (currently shows `match.homeScore`/`match.awayScore`) to show 90-min score for knockout matches:

Find line:
```html
<span th:if="${match.completed}"
      class="font-mono font-semibold text-gray-900"
      th:text="${match.homeScore} + '–' + ${match.awayScore}">0–0</span>
```

Replace with:
```html
<span th:if="${match.completed}" class="font-mono font-semibold text-gray-900">
  <span th:if="${match.knockout and match.homeScore90 != null}"
        th:text="${match.homeScore90} + '–' + ${match.awayScore90} + ' (90'')'">(90min)</span>
  <span th:if="${not match.knockout or match.homeScore90 == null}"
        th:text="${match.homeScore} + '–' + ${match.awayScore}">0–0</span>
  <span th:if="${match.resultSource?.name() == 'MANUAL'}"
        class="ml-1 text-amber-500 text-xs">🔒</span>
</span>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/admin/matches.html
git commit -m "feat: update admin match form with knockout 90-min result entry and playoff winner picker"
```

---

## Task 14: Update User Prediction Form — Dynamic Playoff Winner Picker

**Files:**
- Modify: `src/main/resources/templates/fragments/predictions-round-content.html`

- [ ] **Step 1: Add knockout + predictedPlayoffWinner to match data from backend**

Find the controller that puts `roundMatches` into the model (search for `addAttribute("roundMatches"` or similar). In the DTO/projection used for each match item, add two fields:
- `knockout` (boolean) — `match.isKnockout()`
- `predictedPlayoffWinner` (String: "HOME", "AWAY", or null)

Serialize these in whatever DTO/record/map is used.

- [ ] **Step 2: Update Alpine.js matches initialization in predictions-round-content.html**

In the `matches: rawData.map(m => { ... })` block, add `predPlayoffWinner`:

```javascript
matches: rawData.map(m => {
  var serverHome = (m.predictedHome != null && m.predictedHome >= 0) ? m.predictedHome : -1;
  var serverAway = (m.predictedAway != null && m.predictedAway >= 0) ? m.predictedAway : -1;
  var draft = drafts[m.matchId];
  return {
    ...m,
    predHome: serverHome >= 0 ? serverHome : (draft && draft.home >= 0 ? draft.home : -1),
    predAway: serverAway >= 0 ? serverAway : (draft && draft.away >= 0 ? draft.away : -1),
    predPlayoffWinner: m.predictedPlayoffWinner || null,
  };
}),
```

- [ ] **Step 3: Add isDraw computed check and playoff winner setter**

In the Alpine.js `return { ... }` object, add:

```javascript
isDraw(m) { return m.predHome >= 0 && m.predAway >= 0 && m.predHome === m.predAway; },
setPlayoffWinner(m, val) { m.predPlayoffWinner = val; this._saveDrafts(); },
```

Update `saveDrafts` to also persist `predPlayoffWinner`:
```javascript
if (!m.locked && (m.predHome >= 0 || m.predAway >= 0)) {
  drafts[m.matchId] = { home: m.predHome, away: m.predAway, pw: m.predPlayoffWinner || null };
}
```

And in `loadDrafts` application:
```javascript
predHome: serverHome >= 0 ? serverHome : (draft && draft.home >= 0 ? draft.home : -1),
predAway: serverAway >= 0 ? serverAway : (draft && draft.away >= 0 ? draft.away : -1),
predPlayoffWinner: m.predictedPlayoffWinner || (draft && draft.pw) || null,
```

- [ ] **Step 4: Add playoff winner UI to the LIST VIEW match row**

In the list view's score stepper section (inside `x-show="!match.locked"`), after the score widgets, add:

```html
<!-- Playoff winner (knockout matches, when draw predicted) -->
<div x-show="match.knockout && isDraw(match)" x-transition
     class="w-full flex items-center justify-center gap-3 mt-2 text-xs">
  <span style="color:var(--muted)">Who wins?</span>
  <label class="flex items-center gap-1 cursor-pointer">
    <input type="radio" :name="'pw_' + match.matchId" value="HOME"
           :checked="match.predPlayoffWinner === 'HOME'"
           @change="setPlayoffWinner(match, 'HOME')"/>
    <span x-text="match.homeTeamName"></span>
  </label>
  <label class="flex items-center gap-1 cursor-pointer">
    <input type="radio" :name="'pw_' + match.matchId" value="AWAY"
           :checked="match.predPlayoffWinner === 'AWAY'"
           @change="setPlayoffWinner(match, 'AWAY')"/>
    <span x-text="match.awayTeamName"></span>
  </label>
</div>
```

- [ ] **Step 5: Add playoff winner UI to the CARD VIEW score section**

After the card's score row (inside `x-show="!curMatch.locked"`), add:

```html
<!-- Playoff winner picker (card) -->
<div x-show="curMatch.knockout && isDraw(curMatch)" x-transition
     class="flex items-center justify-center gap-4 mt-3 text-sm">
  <span style="color:var(--muted);font-size:12px;">Who wins?</span>
  <label class="flex items-center gap-1 cursor-pointer">
    <input type="radio" :name="'pw_card_' + curMatch.matchId" value="HOME"
           :checked="curMatch.predPlayoffWinner === 'HOME'"
           @change="setPlayoffWinner(curMatch, 'HOME')"/>
    <span x-text="curMatch.homeTeamName"></span>
  </label>
  <label class="flex items-center gap-1 cursor-pointer">
    <input type="radio" :name="'pw_card_' + curMatch.matchId" value="AWAY"
           :checked="curMatch.predPlayoffWinner === 'AWAY'"
           @change="setPlayoffWinner(curMatch, 'AWAY')"/>
    <span x-text="curMatch.awayTeamName"></span>
  </label>
</div>
```

- [ ] **Step 6: Add playoffWinner to form submission in Alpine submit()**

In the `submit()` function, after the existing `add(...)` calls, add:

```javascript
if (m.knockout && m.predPlayoffWinner) {
  add('predictions[' + i + '].playoffWinner', m.predPlayoffWinner);
}
```

- [ ] **Step 7: Clear predPlayoffWinner when scores change to non-draw**

Update `setScore` and `adj` to clear playoff winner when scores diverge:

```javascript
setScore(m, side, val) {
  const n = parseInt(val);
  const v = isNaN(n) ? -1 : Math.max(0, Math.min(20, n));
  if (side === 'home') m.predHome = v; else m.predAway = v;
  if (!this.isDraw(m)) m.predPlayoffWinner = null;
  this._saveDrafts();
},
adj(m, side, d) {
  if (m.locked) return;
  const cur = side === 'home' ? m.predHome : m.predAway;
  const next = Math.max(0, Math.min(20, (cur >= 0 ? cur : 0) + d));
  if (side === 'home') m.predHome = next; else m.predAway = next;
  if (!this.isDraw(m)) m.predPlayoffWinner = null;
  this._saveDrafts();
},
```

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates/fragments/predictions-round-content.html
git commit -m "feat: add dynamic playoff winner picker to user prediction form for knockout draw predictions"
```

---

## Task 15: Update Display Pages

**Files:**
- Modify: `src/main/resources/templates/fragments/heroes-content.html`
- Modify: `src/main/resources/templates/rules.html`
- Modify: `src/main/resources/templates/community/leaderboard.html` (check if scoreResult badge is displayed)
- Modify: `src/main/resources/templates/fragments/community-predictions-round-content.html` (if predictions are shown with score badges)

- [ ] **Step 1: heroes-content.html — include EXACT_DRAW_WINNER in hero display**

The heroes page queries by `exactPredictors` which comes from a service. Find the service/repository query that fetches exact predictors. It likely filters on `scoreResult = 'EXACT'`. Update the query or the where-clause to include `scoreResult = 'EXACT_DRAW_WINNER'` as well.

Search for the heroes service method:
```bash
grep -r "EXACT" src/main/java --include="*.java" -l
```

In whatever repository query selects `scoreResult = EXACT`, add `OR scoreResult = EXACT_DRAW_WINNER`.

In `heroes-content.html`, the template already uses `predictor.exactCount` — no template change needed if the data query is fixed. Verify by checking if the count query uses `PredictionScore.EXACT` directly and add `PredictionScore.EXACT_DRAW_WINNER`.

- [ ] **Step 2: rules.html — add knockout scoring section**

Find the "Scoring System" section in `rules.html`. After the existing `+2 Correct Draw` row and before the `+0 Wrong` row (or at end of scoring rows), add a new knockout section:

```html
<!-- Knockout Playoff Winner Bonus -->
<div class="border-t border-gray-200 mt-4 pt-4">
  <div class="font-bold text-gray-800 mb-2 text-sm uppercase tracking-wide">Knockout Stage Bonus</div>
  <p class="text-xs text-gray-500 mb-3">Predictions for knockout-stage matches are scored on the 90-minute result only (extra time and penalties do not count).</p>
  <div class="flex items-center gap-4 py-3 border-b border-gray-50 hover:bg-gray-50 transition-colors rounded-lg px-2">
    <div class="w-14 h-14 rounded-2xl bg-purple-50 flex items-center justify-center flex-shrink-0">
      <span class="font-display text-2xl text-purple-600">+1</span>
    </div>
    <div class="flex-1">
      <div class="font-bold text-gray-800">Playoff Winner Bonus</div>
      <div class="text-xs text-gray-400 mt-0.5">When you predict a draw in a knockout match, you also pick who wins — correct pick earns +1 bonus point (max 3 total)</div>
    </div>
    <div class="hidden sm:block text-xs text-gray-400 bg-purple-50 px-3 py-1.5 rounded-lg font-medium">
      Predict 1-1 + pick winner &rarr; 3 pts
    </div>
  </div>
</div>
```

- [ ] **Step 3: Search for scoreResult badge rendering in leaderboard/prediction views**

```bash
grep -r "scoreResult\|EXACT\|CORRECT_DRAW\|CORRECT_WINNER" src/main/resources/templates --include="*.html" -l
```

For each template found: check if it maps `scoreResult` to a badge or label. Add handling for `EXACT_DRAW_WINNER` wherever `EXACT` is displayed, using the same styling but with label like "Draw + Winner" or "+3".

A typical pattern:
```html
<span th:if="${pred.scoreResult?.name() == 'EXACT'}" class="badge-exact">Exact +3</span>
```

Add alongside it:
```html
<span th:if="${pred.scoreResult?.name() == 'EXACT_DRAW_WINNER'}" class="badge-exact">Draw + Winner +3</span>
```

- [ ] **Step 4: Compile to verify**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Run all tests**

```bash
./mvnw test -q
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/
git commit -m "feat: update heroes, rules, and prediction displays to handle EXACT_DRAW_WINNER"
```

---

## Task 16: Full Smoke Test

- [ ] **Step 1: Start the app**

```bash
./mvnw spring-boot:run
```

Expected: Application starts on port 8080 with no errors. Check logs for Flyway migration success.

- [ ] **Step 2: Verify group stage result entry unchanged**

Navigate to Admin → Matches → any group stage match → Enter Result. Confirm single score pair form appears, Save works.

- [ ] **Step 3: Verify knockout result entry**

Navigate to Admin → Matches → any knockout match → Enter Result. Confirm:
- 90-min score inputs appear
- Entering equal scores shows "Who wins?" radio
- Entering unequal scores hides the radio
- Save records correct values in DB (`homeScore90`, `awayScore90`, `resultSource=MANUAL`)
- Locked indicator (🔒) appears after save

- [ ] **Step 4: Verify API does not overwrite manual result**

Wait for or manually trigger `FootballApiSyncService.syncResults()`. Confirm the locked match's `homeScore90`/`awayScore90` are unchanged.

- [ ] **Step 5: Verify user playoff winner picker**

Navigate to a knockout round predictions page. Enter equal scores for a match. Confirm winner picker appears. Change to unequal score — picker disappears. Change back to equal — picker re-appears with prior selection cleared.

- [ ] **Step 6: Verify scoring produces EXACT_DRAW_WINNER**

Enter a knockout match result (e.g., 1-1, home wins). Verify a prediction of 1-1 + home team scores 3 pts and shows `EXACT_DRAW_WINNER`. Verify 1-1 + away team shows 2 pts and `CORRECT_DRAW`.

- [ ] **Step 7: Verify admin settings toggle**

Navigate to Admin → Settings. Confirm "Knockout Scoring Mode" radio is present. Switch to Full-time, save. Verify it persists. Switch back to 90-min.

- [ ] **Step 8: Verify rules page**

Navigate to /rules. Confirm new "Knockout Stage Bonus" section appears with +1 playoff winner explanation.

- [ ] **Step 9: Final commit**

```bash
git add -A
git status  # check nothing unexpected is staged
git commit -m "feat: playoff scoring complete — 90-min tracking, API lock, playoff winner prediction"
```
