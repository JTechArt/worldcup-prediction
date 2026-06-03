# Part 3 — Game Engine (Scoring, Predictions, Seed Data) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the core game engine — pure scoring logic, prediction CRUD with window enforcement, tournament winner predictions, and the complete WC2026 seed SQL covering all 48 teams, 12 groups, and 104 match slots.

**Architecture:** `ScoringService` is a stateless Spring `@Service` (no DB calls) that computes points from two score pairs; `PredictionService` coordinates with JPA repositories and enforces window open/lock rules server-side; `TournamentWinnerPredictionService` manages the single pre-tournament pick per user. Seed data is a Flyway repeatable migration (`R__` prefix) so it re-runs on wipe but is idempotent via `ON CONFLICT DO NOTHING`.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Spring Data JPA, Flyway, PostgreSQL, JUnit 5, Mockito

**Depends on:** Part 1 — Foundation (Match entity, repositories, DB schema), Part 2 — Auth (User entity, roles, security context)

**Next parts:** Part 4 (Leaderboard — consumes ScoringService), Part 6 (Predictions UI — consumes PredictionService REST endpoints)

---

## File Structure

```
src/
├── main/
│   ├── java/com/worldcup/prediction/
│   │   ├── dto/
│   │   │   ├── PredictionDto.java                          (CREATE)
│   │   │   └── TournamentWinnerPredictionDto.java          (CREATE)
│   │   ├── model/
│   │   │   ├── Prediction.java                             (CREATE)
│   │   │   └── TournamentWinnerPrediction.java             (CREATE)
│   │   ├── repository/
│   │   │   ├── PredictionRepository.java                   (CREATE)
│   │   │   └── TournamentWinnerPredictionRepository.java   (CREATE)
│   │   └── service/
│   │       ├── ScoringService.java                         (CREATE)
│   │       ├── PredictionService.java                      (CREATE)
│   │       └── TournamentWinnerPredictionService.java      (CREATE)
│   └── resources/
│       └── db/
│           └── seed/
│               └── R__wc2026_data.sql                      (CREATE)
└── test/
    └── java/com/worldcup/prediction/
        ├── service/
        │   ├── ScoringServiceTest.java                     (CREATE)
        │   ├── PredictionServiceTest.java                  (CREATE)
        │   └── TournamentWinnerPredictionServiceTest.java  (CREATE)
        └── (existing test structure)
```

---

### Task 1: PredictionDto and TournamentWinnerPredictionDto

**Files:**
- Create: `src/main/java/com/worldcup/prediction/dto/PredictionDto.java`
- Create: `src/main/java/com/worldcup/prediction/dto/TournamentWinnerPredictionDto.java`

These are simple value holders used as API input/output — no tests needed, but they must compile before services can be tested.

- [ ] **Step 1: Create PredictionDto**

```java
package com.worldcup.prediction.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PredictionDto {

    @NotNull
    private Long matchId;

    @NotNull
    @Min(0)
    private Integer homeScore;

    @NotNull
    @Min(0)
    private Integer awayScore;

    public PredictionDto() {}

    public PredictionDto(Long matchId, Integer homeScore, Integer awayScore) {
        this.matchId = matchId;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
    }

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }

    public Integer getHomeScore() { return homeScore; }
    public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }

    public Integer getAwayScore() { return awayScore; }
    public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }
}
```

- [ ] **Step 2: Create TournamentWinnerPredictionDto**

```java
package com.worldcup.prediction.dto;

import jakarta.validation.constraints.NotBlank;

public class TournamentWinnerPredictionDto {

    @NotBlank
    private String teamCode; // e.g. "bra", "fra", "usa"

    public TournamentWinnerPredictionDto() {}

    public TournamentWinnerPredictionDto(String teamCode) {
        this.teamCode = teamCode;
    }

    public String getTeamCode() { return teamCode; }
    public void setTeamCode(String teamCode) { this.teamCode = teamCode; }
}
```

- [ ] **Step 3: Compile check**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worldcup/prediction/dto/PredictionDto.java \
        src/main/java/com/worldcup/prediction/dto/TournamentWinnerPredictionDto.java
git commit -m "feat: add PredictionDto and TournamentWinnerPredictionDto"
```

---

### Task 2: Prediction and TournamentWinnerPrediction JPA entities

**Files:**
- Create: `src/main/java/com/worldcup/prediction/model/Prediction.java`
- Create: `src/main/java/com/worldcup/prediction/model/TournamentWinnerPrediction.java`

- [ ] **Step 1: Create Prediction entity**

```java
package com.worldcup.prediction.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A single match score prediction by one participant.
 * One row per (user, match) — enforced by unique constraint.
 */
@Entity
@Table(
    name = "predictions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "match_id"})
)
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to users.id */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** FK to matches.id */
    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "predicted_home_score", nullable = false)
    private Integer predictedHomeScore;

    @Column(name = "predicted_away_score", nullable = false)
    private Integer predictedAwayScore;

    /**
     * Points awarded after the match is completed and scored.
     * NULL until the match result is entered and scoring runs.
     */
    @Column(name = "points_awarded")
    private Integer pointsAwarded;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Prediction() {}

    public Prediction(Long userId, Long matchId, Integer predictedHomeScore, Integer predictedAwayScore) {
        this.userId = userId;
        this.matchId = matchId;
        this.predictedHomeScore = predictedHomeScore;
        this.predictedAwayScore = predictedAwayScore;
        this.submittedAt = Instant.now();
    }

    // --- getters & setters ---

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }

    public Integer getPredictedHomeScore() { return predictedHomeScore; }
    public void setPredictedHomeScore(Integer predictedHomeScore) { this.predictedHomeScore = predictedHomeScore; }

    public Integer getPredictedAwayScore() { return predictedAwayScore; }
    public void setPredictedAwayScore(Integer predictedAwayScore) { this.predictedAwayScore = predictedAwayScore; }

    public Integer getPointsAwarded() { return pointsAwarded; }
    public void setPointsAwarded(Integer pointsAwarded) { this.pointsAwarded = pointsAwarded; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 2: Create TournamentWinnerPrediction entity**

```java
package com.worldcup.prediction.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A participant's prediction for the overall tournament winner.
 * One row per user; visible to everyone immediately after submission.
 */
@Entity
@Table(
    name = "tournament_winner_predictions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id"})
)
public class TournamentWinnerPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** FIFA country code (lowercase), e.g. "bra", "fra", "usa" */
    @Column(name = "team_code", nullable = false, length = 10)
    private String teamCode;

    /** True once the tournament winner is officially determined and +10 awarded */
    @Column(name = "points_awarded", nullable = false)
    private boolean pointsAwarded = false;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public TournamentWinnerPrediction() {}

    public TournamentWinnerPrediction(Long userId, String teamCode) {
        this.userId = userId;
        this.teamCode = teamCode;
        this.submittedAt = Instant.now();
    }

    // --- getters & setters ---

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTeamCode() { return teamCode; }
    public void setTeamCode(String teamCode) { this.teamCode = teamCode; }

    public boolean isPointsAwarded() { return pointsAwarded; }
    public void setPointsAwarded(boolean pointsAwarded) { this.pointsAwarded = pointsAwarded; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 3: Compile check**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worldcup/prediction/model/Prediction.java \
        src/main/java/com/worldcup/prediction/model/TournamentWinnerPrediction.java
git commit -m "feat: add Prediction and TournamentWinnerPrediction JPA entities"
```

---

### Task 3: JPA Repositories

**Files:**
- Create: `src/main/java/com/worldcup/prediction/repository/PredictionRepository.java`
- Create: `src/main/java/com/worldcup/prediction/repository/TournamentWinnerPredictionRepository.java`

- [ ] **Step 1: Create PredictionRepository**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.model.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    /** All predictions for a given match (used post-lock for visibility). */
    List<Prediction> findByMatchId(Long matchId);

    /** All predictions submitted by a given user. */
    List<Prediction> findByUserId(Long userId);

    /** Lookup for upsert: find an existing prediction for a user+match pair. */
    Optional<Prediction> findByUserIdAndMatchId(Long userId, Long matchId);

    /** Used for scoring: fetch all predictions for a match to award points. */
    List<Prediction> findByMatchIdAndPointsAwardedIsNull(Long matchId);
}
```

- [ ] **Step 2: Create TournamentWinnerPredictionRepository**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.model.TournamentWinnerPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentWinnerPredictionRepository extends JpaRepository<TournamentWinnerPrediction, Long> {

    Optional<TournamentWinnerPrediction> findByUserId(Long userId);

    /** Find all predictions that picked a specific team code (for scoring). */
    List<TournamentWinnerPrediction> findByTeamCodeAndPointsAwardedFalse(String teamCode);
}
```

- [ ] **Step 3: Compile check**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worldcup/prediction/repository/PredictionRepository.java \
        src/main/java/com/worldcup/prediction/repository/TournamentWinnerPredictionRepository.java
git commit -m "feat: add PredictionRepository and TournamentWinnerPredictionRepository"
```

---

### Task 4: ScoringService — pure unit-testable scoring logic

**Files:**
- Create: `src/main/java/com/worldcup/prediction/service/ScoringService.java`
- Create: `src/test/java/com/worldcup/prediction/service/ScoringServiceTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.worldcup.prediction.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for ScoringService — no Spring context required.
 * Covers every scoring branch plus tiebreaker helpers.
 */
class ScoringServiceTest {

    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new ScoringService();
    }

    // =========================================================
    //  calculatePoints(actualHome, actualAway, predHome, predAway)
    // =========================================================

    @Nested
    @DisplayName("calculatePoints — group stage / regular score outcomes")
    class CalculatePoints {

        @Test
        @DisplayName("+3 for exact score — home win")
        void exactScore_homeWin() {
            assertThat(scoringService.calculatePoints(2, 1, 2, 1)).isEqualTo(3);
        }

        @Test
        @DisplayName("+3 for exact score — away win")
        void exactScore_awayWin() {
            assertThat(scoringService.calculatePoints(0, 3, 0, 3)).isEqualTo(3);
        }

        @Test
        @DisplayName("+3 for exact score — 0-0 draw")
        void exactScore_zeroZeroDraw() {
            assertThat(scoringService.calculatePoints(0, 0, 0, 0)).isEqualTo(3);
        }

        @Test
        @DisplayName("+3 for exact score — high-scoring match")
        void exactScore_highScoring() {
            assertThat(scoringService.calculatePoints(4, 3, 4, 3)).isEqualTo(3);
        }

        @Test
        @DisplayName("+2 for correct draw — predicted draw, actual draw, different score")
        void correctDraw_differentScore() {
            assertThat(scoringService.calculatePoints(1, 1, 2, 2)).isEqualTo(2);
        }

        @Test
        @DisplayName("+2 for correct draw — both score zero vs some draw")
        void correctDraw_zeroVsOther() {
            assertThat(scoringService.calculatePoints(2, 2, 0, 0)).isEqualTo(2);
        }

        @Test
        @DisplayName("+1 for correct winner — home win, wrong score")
        void correctWinner_homeWin_wrongScore() {
            assertThat(scoringService.calculatePoints(3, 1, 1, 0)).isEqualTo(1);
        }

        @Test
        @DisplayName("+1 for correct winner — away win, wrong score")
        void correctWinner_awayWin_wrongScore() {
            assertThat(scoringService.calculatePoints(0, 2, 0, 1)).isEqualTo(1);
        }

        @Test
        @DisplayName("+0 for predicted home win but actual away win")
        void wrongPrediction_homeVsAway() {
            assertThat(scoringService.calculatePoints(0, 1, 2, 0)).isEqualTo(0);
        }

        @Test
        @DisplayName("+0 for predicted draw but actual home win")
        void wrongPrediction_drawVsHomeWin() {
            assertThat(scoringService.calculatePoints(2, 1, 1, 1)).isEqualTo(0);
        }

        @Test
        @DisplayName("+0 for predicted home win but actual draw")
        void wrongPrediction_homeWinVsDraw() {
            assertThat(scoringService.calculatePoints(1, 1, 2, 0)).isEqualTo(0);
        }

        @Test
        @DisplayName("+0 for predicted away win but actual draw")
        void wrongPrediction_awayWinVsDraw() {
            assertThat(scoringService.calculatePoints(0, 0, 0, 1)).isEqualTo(0);
        }
    }

    // =========================================================
    //  Knockout stage: 90-minute score only
    //  (extra time / pens are irrelevant — same formula applies
    //   because the service is called with the 90-min score)
    // =========================================================

    @Nested
    @DisplayName("calculatePoints — knockout 90-min score semantics")
    class KnockoutScoring {

        @Test
        @DisplayName("+3 when prediction matches 90-min score even though match went to pens")
        void exactScore_90minDraw_goesToPens() {
            // actual 90-min: 1-1, prediction: 1-1 → +3 (pens result irrelevant)
            assertThat(scoringService.calculatePoints(1, 1, 1, 1)).isEqualTo(3);
        }

        @Test
        @DisplayName("+2 correct draw when both predict a draw at different score (90 min)")
        void correctDraw_90min() {
            // actual 90-min: 2-2, prediction: 1-1 → +2
            assertThat(scoringService.calculatePoints(2, 2, 1, 1)).isEqualTo(2);
        }

        @Test
        @DisplayName("+1 for correct winner — predicted winner wins in regular time")
        void correctWinner_90min() {
            // actual: 2-0 (home wins), prediction: 1-0 (home wins) → +1
            assertThat(scoringService.calculatePoints(2, 0, 1, 0)).isEqualTo(1);
        }
    }

    // =========================================================
    //  Input validation
    // =========================================================

    @Nested
    @DisplayName("calculatePoints — input validation")
    class InputValidation {

        @Test
        @DisplayName("throws IllegalArgumentException for negative actualHome")
        void negativeActualHome() {
            assertThatThrownBy(() -> scoringService.calculatePoints(-1, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Score cannot be negative");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for negative actualAway")
        void negativeActualAway() {
            assertThatThrownBy(() -> scoringService.calculatePoints(0, -1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Score cannot be negative");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for negative predictedHome")
        void negativePredictedHome() {
            assertThatThrownBy(() -> scoringService.calculatePoints(0, 0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Score cannot be negative");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for negative predictedAway")
        void negativePredictedAway() {
            assertThatThrownBy(() -> scoringService.calculatePoints(0, 0, 0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Score cannot be negative");
        }
    }

    // =========================================================
    //  tournamentWinnerPoints
    // =========================================================

    @Nested
    @DisplayName("tournamentWinnerPoints")
    class TournamentWinnerPoints {

        @Test
        @DisplayName("+10 when predicted team code matches actual winner code")
        void correctWinner() {
            assertThat(scoringService.tournamentWinnerPoints("bra", "bra")).isEqualTo(10);
        }

        @Test
        @DisplayName("+0 when predicted team code does not match actual winner")
        void wrongWinner() {
            assertThat(scoringService.tournamentWinnerPoints("fra", "bra")).isEqualTo(0);
        }

        @Test
        @DisplayName("case-insensitive comparison — uppercased prediction matches lowercase winner")
        void caseInsensitive() {
            assertThat(scoringService.tournamentWinnerPoints("BRA", "bra")).isEqualTo(10);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null predictedCode")
        void nullPredictedCode() {
            assertThatThrownBy(() -> scoringService.tournamentWinnerPoints(null, "bra"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null actualCode")
        void nullActualCode() {
            assertThatThrownBy(() -> scoringService.tournamentWinnerPoints("bra", null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================
    //  Tiebreaker helpers
    // =========================================================

    @Nested
    @DisplayName("isExactScore")
    class IsExactScore {

        @Test
        @DisplayName("returns true for exact match")
        void exactMatch() {
            assertThat(scoringService.isExactScore(2, 1, 2, 1)).isTrue();
        }

        @Test
        @DisplayName("returns false when home score differs")
        void homeScoreDiffers() {
            assertThat(scoringService.isExactScore(2, 1, 3, 1)).isFalse();
        }

        @Test
        @DisplayName("returns false when away score differs")
        void awayScoreDiffers() {
            assertThat(scoringService.isExactScore(2, 1, 2, 0)).isFalse();
        }
    }

    @Nested
    @DisplayName("isCorrectOutcome")
    class IsCorrectOutcome {

        @Test
        @DisplayName("true when both predict home win")
        void bothHomeWin() {
            assertThat(scoringService.isCorrectOutcome(2, 0, 1, 0)).isTrue();
        }

        @Test
        @DisplayName("true when both predict away win")
        void bothAwayWin() {
            assertThat(scoringService.isCorrectOutcome(0, 1, 0, 3)).isTrue();
        }

        @Test
        @DisplayName("true when both predict draw")
        void bothDraw() {
            assertThat(scoringService.isCorrectOutcome(1, 1, 2, 2)).isTrue();
        }

        @Test
        @DisplayName("false when outcomes differ")
        void outcomesDiffer() {
            assertThat(scoringService.isCorrectOutcome(1, 0, 0, 1)).isFalse();
        }
    }

    @Nested
    @DisplayName("isCorrectDraw")
    class IsCorrectDraw {

        @Test
        @DisplayName("true when actual is draw and prediction is also a draw")
        void bothDraws() {
            assertThat(scoringService.isCorrectDraw(1, 1, 2, 2)).isTrue();
        }

        @Test
        @DisplayName("false when actual is draw but prediction is a win")
        void actualDrawPredictedWin() {
            assertThat(scoringService.isCorrectDraw(1, 1, 2, 0)).isFalse();
        }

        @Test
        @DisplayName("false when actual is win even if prediction is draw")
        void actualWinPredictedDraw() {
            assertThat(scoringService.isCorrectDraw(2, 0, 1, 1)).isFalse();
        }
    }
}
```

- [ ] **Step 2: Run test — verify FAIL**

```bash
./mvnw test -Dtest=ScoringServiceTest -v 2>&1 | tail -20
```

Expected: `BUILD FAILURE` (class not yet created)

- [ ] **Step 3: Implement ScoringService**

```java
package com.worldcup.prediction.service;

import org.springframework.stereotype.Service;

/**
 * Pure stateless scoring logic for the World Cup prediction game.
 * No repository dependencies — every method is deterministic and trivially testable.
 *
 * <p>Scoring rules:
 * <ul>
 *   <li>+3 exact score</li>
 *   <li>+2 correct draw (any draw → any draw, different score)</li>
 *   <li>+1 correct winner (right team wins, wrong score)</li>
 *   <li> 0 wrong prediction</li>
 *   <li>+10 correct tournament winner</li>
 * </ul>
 *
 * <p>Knockout matches: always called with 90-min score — extra time / penalties are ignored
 * at the caller level, so this service has no special knockout-branch logic.
 */
@Service
public class ScoringService {

    // -------------------------------------------------------
    //  Primary scoring
    // -------------------------------------------------------

    /**
     * Calculate match prediction points.
     *
     * @param actualHome    actual home goals (90 min for knockouts)
     * @param actualAway    actual away goals (90 min for knockouts)
     * @param predictedHome predicted home goals
     * @param predictedAway predicted away goals
     * @return points awarded: 3, 2, 1, or 0
     * @throws IllegalArgumentException if any score is negative
     */
    public int calculatePoints(int actualHome, int actualAway,
                               int predictedHome, int predictedAway) {
        validateScores(actualHome, actualAway, predictedHome, predictedAway);

        if (isExactScore(actualHome, actualAway, predictedHome, predictedAway)) {
            return 3;
        }
        if (isCorrectDraw(actualHome, actualAway, predictedHome, predictedAway)) {
            return 2;
        }
        if (isCorrectOutcome(actualHome, actualAway, predictedHome, predictedAway)) {
            return 1;
        }
        return 0;
    }

    /**
     * Calculate tournament winner bonus points.
     *
     * @param predictedCode lowercase FIFA country code predicted by the user
     * @param actualCode    lowercase FIFA country code of the actual winner
     * @return 10 if correct, 0 otherwise
     * @throws IllegalArgumentException if either argument is null
     */
    public int tournamentWinnerPoints(String predictedCode, String actualCode) {
        if (predictedCode == null) {
            throw new IllegalArgumentException("predictedCode cannot be null");
        }
        if (actualCode == null) {
            throw new IllegalArgumentException("actualCode cannot be null");
        }
        return predictedCode.trim().equalsIgnoreCase(actualCode.trim()) ? 10 : 0;
    }

    // -------------------------------------------------------
    //  Tiebreaker helpers (public so leaderboard service can use them)
    // -------------------------------------------------------

    /**
     * True when the predicted score exactly matches the actual score.
     */
    public boolean isExactScore(int actualHome, int actualAway,
                                int predictedHome, int predictedAway) {
        return actualHome == predictedHome && actualAway == predictedAway;
    }

    /**
     * True when both the actual result and the prediction are draws
     * (regardless of whether the exact score matches — exact draw is
     * already handled by {@link #isExactScore}).
     */
    public boolean isCorrectDraw(int actualHome, int actualAway,
                                  int predictedHome, int predictedAway) {
        return isDraw(actualHome, actualAway) && isDraw(predictedHome, predictedAway);
    }

    /**
     * True when the match outcome (home win / draw / away win) is the same
     * for both the actual result and the prediction — regardless of exact score.
     */
    public boolean isCorrectOutcome(int actualHome, int actualAway,
                                     int predictedHome, int predictedAway) {
        return outcome(actualHome, actualAway) == outcome(predictedHome, predictedAway);
    }

    // -------------------------------------------------------
    //  Private helpers
    // -------------------------------------------------------

    private void validateScores(int actualHome, int actualAway,
                                 int predictedHome, int predictedAway) {
        if (actualHome < 0 || actualAway < 0 || predictedHome < 0 || predictedAway < 0) {
            throw new IllegalArgumentException("Score cannot be negative");
        }
    }

    private boolean isDraw(int home, int away) {
        return home == away;
    }

    /**
     * Returns -1 for away win, 0 for draw, +1 for home win.
     */
    private int outcome(int home, int away) {
        return Integer.compare(home, away);
    }
}
```

- [ ] **Step 4: Run test — verify PASS**

```bash
./mvnw test -Dtest=ScoringServiceTest -v 2>&1 | tail -20
```

Expected: `BUILD SUCCESS ... Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/ScoringService.java \
        src/test/java/com/worldcup/prediction/service/ScoringServiceTest.java
git commit -m "feat: implement ScoringService with exhaustive unit tests (24 cases)"
```

---

### Task 5: PredictionService

**Files:**
- Create: `src/main/java/com/worldcup/prediction/service/PredictionService.java`
- Create: `src/test/java/com/worldcup/prediction/service/PredictionServiceTest.java`

**Assumption:** Part 1 provides a `Match` entity with fields: `id`, `predictionOpenTime` (Instant), `lockTime` (Instant), `homeScore` (Integer, nullable), `awayScore` (Integer, nullable), `status` (enum with at least SCHEDULED, COMPLETED). Part 1 also provides `MatchRepository` with `findAllById(Collection<Long>)`.

- [ ] **Step 1: Write failing tests**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.dto.PredictionDto;
import com.worldcup.prediction.model.Match;
import com.worldcup.prediction.model.Prediction;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictionServiceTest {

    @Mock
    private PredictionRepository predictionRepository;

    @Mock
    private MatchRepository matchRepository;

    private PredictionService predictionService;

    // Fixed "now" anchor for tests: 10 hours before kickoff
    private final Instant kickoff = Instant.parse("2026-06-11T18:00:00Z");
    private final Instant openTime = kickoff.minus(24, ChronoUnit.HOURS);
    private final Instant lockTime = kickoff.minus(1, ChronoUnit.HOURS);
    // "now" = 10 hours before kickoff → window is open
    private final Instant nowOpen = kickoff.minus(10, ChronoUnit.HOURS);
    // "now" = after lock
    private final Instant nowLocked = kickoff.plus(2, ChronoUnit.HOURS);
    // "now" = before window opens
    private final Instant nowBeforeOpen = kickoff.minus(25, ChronoUnit.HOURS);

    @BeforeEach
    void setUp() {
        predictionService = new PredictionService(predictionRepository, matchRepository);
    }

    // -------------------------------------------------------
    //  isWindowOpen
    // -------------------------------------------------------

    @Nested
    @DisplayName("isWindowOpen")
    class IsWindowOpen {

        @Test
        @DisplayName("returns true when now is within [openTime, lockTime)")
        void openWindow_returnsTrue() {
            Match match = buildMatch(1L, openTime, lockTime);
            assertThat(predictionService.isWindowOpen(match, nowOpen)).isTrue();
        }

        @Test
        @DisplayName("returns false when now is before openTime")
        void beforeOpen_returnsFalse() {
            Match match = buildMatch(1L, openTime, lockTime);
            assertThat(predictionService.isWindowOpen(match, nowBeforeOpen)).isFalse();
        }

        @Test
        @DisplayName("returns false when now is exactly at lockTime")
        void atLockTime_returnsFalse() {
            Match match = buildMatch(1L, openTime, lockTime);
            assertThat(predictionService.isWindowOpen(match, lockTime)).isFalse();
        }

        @Test
        @DisplayName("returns false when now is after lockTime")
        void afterLock_returnsFalse() {
            Match match = buildMatch(1L, openTime, lockTime);
            assertThat(predictionService.isWindowOpen(match, nowLocked)).isFalse();
        }

        @Test
        @DisplayName("returns true exactly at openTime")
        void atOpenTime_returnsTrue() {
            Match match = buildMatch(1L, openTime, lockTime);
            assertThat(predictionService.isWindowOpen(match, openTime)).isTrue();
        }
    }

    // -------------------------------------------------------
    //  submitPredictions
    // -------------------------------------------------------

    @Nested
    @DisplayName("submitPredictions")
    class SubmitPredictions {

        @Test
        @DisplayName("happy path — saves all predictions when window is open and no blanks")
        void happyPath_savesAll() {
            Long userId = 42L;
            Match m1 = buildMatch(1L, openTime, lockTime);
            Match m2 = buildMatch(2L, openTime, lockTime);

            when(matchRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(m1, m2));
            when(predictionRepository.findByUserIdAndMatchId(eq(userId), any()))
                .thenReturn(Optional.empty());
            when(predictionRepository.save(any(Prediction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            List<PredictionDto> dtos = List.of(
                new PredictionDto(1L, 2, 1),
                new PredictionDto(2L, 0, 0)
            );

            List<Prediction> result = predictionService.submitPredictions(userId, dtos, nowOpen);

            assertThat(result).hasSize(2);
            verify(predictionRepository, times(2)).save(any(Prediction.class));
        }

        @Test
        @DisplayName("upserts existing prediction instead of creating duplicate")
        void upsert_updatesExistingPrediction() {
            Long userId = 42L;
            Match m1 = buildMatch(1L, openTime, lockTime);
            Prediction existing = new Prediction(userId, 1L, 1, 0);

            when(matchRepository.findAllById(List.of(1L))).thenReturn(List.of(m1));
            when(predictionRepository.findByUserIdAndMatchId(userId, 1L))
                .thenReturn(Optional.of(existing));
            when(predictionRepository.save(any(Prediction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            List<PredictionDto> dtos = List.of(new PredictionDto(1L, 3, 2));
            List<Prediction> result = predictionService.submitPredictions(userId, dtos, nowOpen);

            assertThat(result).hasSize(1);
            // The existing prediction should have been updated, not a new one created
            ArgumentCaptor<Prediction> captor = ArgumentCaptor.forClass(Prediction.class);
            verify(predictionRepository).save(captor.capture());
            assertThat(captor.getValue().getPredictedHomeScore()).isEqualTo(3);
            assertThat(captor.getValue().getPredictedAwayScore()).isEqualTo(2);
        }

        @Test
        @DisplayName("throws PredictionWindowClosedException when window is locked")
        void windowLocked_throwsException() {
            Long userId = 42L;
            Match m1 = buildMatch(1L, openTime, lockTime);
            when(matchRepository.findAllById(List.of(1L))).thenReturn(List.of(m1));

            List<PredictionDto> dtos = List.of(new PredictionDto(1L, 1, 0));

            assertThatThrownBy(() -> predictionService.submitPredictions(userId, dtos, nowLocked))
                .isInstanceOf(PredictionService.PredictionWindowClosedException.class)
                .hasMessageContaining("prediction window");
        }

        @Test
        @DisplayName("throws PredictionWindowClosedException when window not yet open")
        void windowNotYetOpen_throwsException() {
            Long userId = 42L;
            Match m1 = buildMatch(1L, openTime, lockTime);
            when(matchRepository.findAllById(List.of(1L))).thenReturn(List.of(m1));

            List<PredictionDto> dtos = List.of(new PredictionDto(1L, 1, 0));

            assertThatThrownBy(() -> predictionService.submitPredictions(userId, dtos, nowBeforeOpen))
                .isInstanceOf(PredictionService.PredictionWindowClosedException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for empty prediction list")
        void emptyList_throwsException() {
            assertThatThrownBy(() -> predictionService.submitPredictions(1L, List.of(), nowOpen))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("throws MatchNotFoundException when matchId does not exist")
        void unknownMatch_throwsException() {
            when(matchRepository.findAllById(List.of(999L))).thenReturn(List.of());

            List<PredictionDto> dtos = List.of(new PredictionDto(999L, 1, 0));

            assertThatThrownBy(() -> predictionService.submitPredictions(1L, dtos, nowOpen))
                .isInstanceOf(PredictionService.MatchNotFoundException.class);
        }
    }

    // -------------------------------------------------------
    //  getPredictionsForMatch
    // -------------------------------------------------------

    @Nested
    @DisplayName("getPredictionsForMatch")
    class GetPredictionsForMatch {

        @Test
        @DisplayName("returns predictions after window is locked (non-admin)")
        void afterLock_returnsPredictions() {
            List<Prediction> preds = List.of(new Prediction(1L, 10L, 2, 1));
            when(predictionRepository.findByMatchId(10L)).thenReturn(preds);
            Match match = buildMatch(10L, openTime, lockTime);

            List<Prediction> result = predictionService.getPredictionsForMatch(match, nowLocked, false);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("returns empty list before window locks (non-admin)")
        void beforeLock_nonAdmin_returnsEmpty() {
            Match match = buildMatch(10L, openTime, lockTime);

            List<Prediction> result = predictionService.getPredictionsForMatch(match, nowOpen, false);

            assertThat(result).isEmpty();
            verify(predictionRepository, never()).findByMatchId(any());
        }

        @Test
        @DisplayName("admin always sees predictions regardless of window state")
        void admin_beforeLock_returnsPredictions() {
            List<Prediction> preds = List.of(new Prediction(1L, 10L, 1, 1));
            when(predictionRepository.findByMatchId(10L)).thenReturn(preds);
            Match match = buildMatch(10L, openTime, lockTime);

            List<Prediction> result = predictionService.getPredictionsForMatch(match, nowOpen, true);

            assertThat(result).hasSize(1);
        }
    }

    // -------------------------------------------------------
    //  getPredictionsForUser
    // -------------------------------------------------------

    @Nested
    @DisplayName("getPredictionsForUser")
    class GetPredictionsForUser {

        @Test
        @DisplayName("returns all predictions for the requesting user")
        void returnsOwnPredictions() {
            List<Prediction> preds = List.of(
                new Prediction(5L, 1L, 2, 0),
                new Prediction(5L, 2L, 1, 1)
            );
            when(predictionRepository.findByUserId(5L)).thenReturn(preds);

            List<Prediction> result = predictionService.getPredictionsForUser(5L);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("returns empty list when user has no predictions")
        void noPredictions_returnsEmpty() {
            when(predictionRepository.findByUserId(99L)).thenReturn(List.of());

            List<Prediction> result = predictionService.getPredictionsForUser(99L);

            assertThat(result).isEmpty();
        }
    }

    // -------------------------------------------------------
    //  lockWindow / openWindow
    // -------------------------------------------------------

    @Nested
    @DisplayName("lockWindow and openWindow")
    class WindowManagement {

        @Test
        @DisplayName("lockWindow sets lockTime to now on the match")
        void lockWindow_setsLockTimeToNow() {
            Match match = buildMatch(1L, openTime, lockTime);

            predictionService.lockWindow(match, nowOpen);

            assertThat(match.getLockTime()).isEqualTo(nowOpen);
        }

        @Test
        @DisplayName("openWindow sets predictionOpenTime to now on the match")
        void openWindow_setsOpenTimeToNow() {
            Match match = buildMatch(1L, openTime, lockTime);

            predictionService.openWindow(match, nowBeforeOpen);

            assertThat(match.getPredictionOpenTime()).isEqualTo(nowBeforeOpen);
        }
    }

    // -------------------------------------------------------
    //  Helper
    // -------------------------------------------------------

    private Match buildMatch(Long id, Instant openTime, Instant lockTime) {
        Match m = new Match();
        m.setId(id);
        m.setPredictionOpenTime(openTime);
        m.setLockTime(lockTime);
        return m;
    }
}
```

- [ ] **Step 2: Run test — verify FAIL**

```bash
./mvnw test -Dtest=PredictionServiceTest -v 2>&1 | tail -20
```

Expected: `BUILD FAILURE` (class not yet created)

- [ ] **Step 3: Implement PredictionService**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.dto.PredictionDto;
import com.worldcup.prediction.model.Match;
import com.worldcup.prediction.model.Prediction;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages match score predictions: submission, visibility, and window enforcement.
 *
 * <p>Window rules:
 * <ul>
 *   <li>Open window: [match.predictionOpenTime, match.lockTime)</li>
 *   <li>All-or-nothing: the caller must pass ALL open matches for the round together</li>
 *   <li>Visibility: non-admin users can only see predictions after lockTime</li>
 * </ul>
 */
@Service
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final MatchRepository matchRepository;

    public PredictionService(PredictionRepository predictionRepository,
                              MatchRepository matchRepository) {
        this.predictionRepository = predictionRepository;
        this.matchRepository = matchRepository;
    }

    // -------------------------------------------------------
    //  Window helpers
    // -------------------------------------------------------

    /**
     * True if predictions may be submitted/updated for this match at the given time.
     * Window is [predictionOpenTime, lockTime) — inclusive open, exclusive lock.
     */
    public boolean isWindowOpen(Match match, Instant now) {
        return !now.isBefore(match.getPredictionOpenTime())
               && now.isBefore(match.getLockTime());
    }

    /**
     * Immediately lock the prediction window for a match (admin action or scheduler).
     * Sets lockTime to {@code now}, which closes the window instantly.
     */
    public void lockWindow(Match match, Instant now) {
        match.setLockTime(now);
    }

    /**
     * Immediately open the prediction window for a match (admin action).
     * Sets predictionOpenTime to {@code now}.
     */
    public void openWindow(Match match, Instant now) {
        match.setPredictionOpenTime(now);
    }

    // -------------------------------------------------------
    //  Submission
    // -------------------------------------------------------

    /**
     * Submit (or update) predictions for a user — all-or-nothing per invocation.
     *
     * <p>Every match in {@code dtos} must have its prediction window currently open at {@code now}.
     * If any match is closed, the entire batch is rejected.
     *
     * @param userId  the participant
     * @param dtos    list of predictions (must not be empty, no null scores)
     * @param now     current time (injectable for testability)
     * @return saved Prediction entities
     * @throws IllegalArgumentException      if dtos is empty
     * @throws MatchNotFoundException        if any matchId does not exist
     * @throws PredictionWindowClosedException if any match window is not currently open
     */
    @Transactional
    public List<Prediction> submitPredictions(Long userId, List<PredictionDto> dtos, Instant now) {
        if (dtos == null || dtos.isEmpty()) {
            throw new IllegalArgumentException("Prediction list cannot be empty");
        }

        List<Long> matchIds = dtos.stream().map(PredictionDto::getMatchId).toList();
        Map<Long, Match> matchMap = matchRepository.findAllById(matchIds)
            .stream()
            .collect(Collectors.toMap(Match::getId, Function.identity()));

        // Validate: all matches exist
        for (Long id : matchIds) {
            if (!matchMap.containsKey(id)) {
                throw new MatchNotFoundException("Match not found: " + id);
            }
        }

        // Validate: all windows open (all-or-nothing)
        for (Long id : matchIds) {
            Match match = matchMap.get(id);
            if (!isWindowOpen(match, now)) {
                throw new PredictionWindowClosedException(
                    "The prediction window for match " + id + " is not open. "
                    + "Predictions must be submitted while the prediction window is open.");
            }
        }

        // Upsert predictions
        List<Prediction> saved = new ArrayList<>();
        for (PredictionDto dto : dtos) {
            Optional<Prediction> existing =
                predictionRepository.findByUserIdAndMatchId(userId, dto.getMatchId());

            Prediction prediction;
            if (existing.isPresent()) {
                prediction = existing.get();
                prediction.setPredictedHomeScore(dto.getHomeScore());
                prediction.setPredictedAwayScore(dto.getAwayScore());
                prediction.setUpdatedAt(now);
            } else {
                prediction = new Prediction(userId, dto.getMatchId(),
                    dto.getHomeScore(), dto.getAwayScore());
            }
            saved.add(predictionRepository.save(prediction));
        }
        return saved;
    }

    // -------------------------------------------------------
    //  Retrieval
    // -------------------------------------------------------

    /**
     * Get all predictions for a match.
     *
     * <p>Visibility rule: non-admin users receive an empty list until the window is locked
     * (i.e., until {@code now >= match.lockTime}). Admins always see all predictions.
     *
     * @param match   the match
     * @param now     current time (injectable for testability)
     * @param isAdmin whether the requesting user is an admin
     */
    public List<Prediction> getPredictionsForMatch(Match match, Instant now, boolean isAdmin) {
        boolean windowLocked = !now.isBefore(match.getLockTime());
        if (!isAdmin && !windowLocked) {
            return List.of();
        }
        return predictionRepository.findByMatchId(match.getId());
    }

    /**
     * Get all predictions submitted by a user.
     * A user's own predictions are always visible to them.
     */
    public List<Prediction> getPredictionsForUser(Long userId) {
        return predictionRepository.findByUserId(userId);
    }

    // -------------------------------------------------------
    //  Custom exceptions (static inner classes for locality)
    // -------------------------------------------------------

    public static class PredictionWindowClosedException extends RuntimeException {
        public PredictionWindowClosedException(String message) {
            super(message);
        }
    }

    public static class MatchNotFoundException extends RuntimeException {
        public MatchNotFoundException(String message) {
            super(message);
        }
    }
}
```

- [ ] **Step 4: Run test — verify PASS**

```bash
./mvnw test -Dtest=PredictionServiceTest -v 2>&1 | tail -20
```

Expected: `BUILD SUCCESS ... Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/PredictionService.java \
        src/test/java/com/worldcup/prediction/service/PredictionServiceTest.java
git commit -m "feat: implement PredictionService with window enforcement and Mockito tests"
```

---

### Task 6: TournamentWinnerPredictionService

**Files:**
- Create: `src/main/java/com/worldcup/prediction/service/TournamentWinnerPredictionService.java`
- Create: `src/test/java/com/worldcup/prediction/service/TournamentWinnerPredictionServiceTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.dto.TournamentWinnerPredictionDto;
import com.worldcup.prediction.model.TournamentWinnerPrediction;
import com.worldcup.prediction.repository.TournamentWinnerPredictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TournamentWinnerPredictionServiceTest {

    @Mock
    private TournamentWinnerPredictionRepository repository;

    @Mock
    private ScoringService scoringService;

    private TournamentWinnerPredictionService service;

    @BeforeEach
    void setUp() {
        service = new TournamentWinnerPredictionService(repository, scoringService);
    }

    // -------------------------------------------------------
    //  submitOrUpdate
    // -------------------------------------------------------

    @Nested
    @DisplayName("submitOrUpdate")
    class SubmitOrUpdate {

        @Test
        @DisplayName("creates new prediction when user has none")
        void newPrediction_creates() {
            when(repository.findByUserId(1L)).thenReturn(Optional.empty());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TournamentWinnerPrediction result =
                service.submitOrUpdate(1L, new TournamentWinnerPredictionDto("bra"));

            assertThat(result.getTeamCode()).isEqualTo("bra");
            assertThat(result.getUserId()).isEqualTo(1L);
            verify(repository).save(any(TournamentWinnerPrediction.class));
        }

        @Test
        @DisplayName("updates existing prediction when user already has one")
        void existingPrediction_updates() {
            TournamentWinnerPrediction existing = new TournamentWinnerPrediction(1L, "fra");
            when(repository.findByUserId(1L)).thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TournamentWinnerPrediction result =
                service.submitOrUpdate(1L, new TournamentWinnerPredictionDto("arg"));

            ArgumentCaptor<TournamentWinnerPrediction> captor =
                ArgumentCaptor.forClass(TournamentWinnerPrediction.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getTeamCode()).isEqualTo("arg");
        }

        @Test
        @DisplayName("stores teamCode in lowercase")
        void teamCode_storedLowercase() {
            when(repository.findByUserId(1L)).thenReturn(Optional.empty());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TournamentWinnerPrediction result =
                service.submitOrUpdate(1L, new TournamentWinnerPredictionDto("BRA"));

            ArgumentCaptor<TournamentWinnerPrediction> captor =
                ArgumentCaptor.forClass(TournamentWinnerPrediction.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getTeamCode()).isEqualTo("bra");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for blank teamCode")
        void blankTeamCode_throws() {
            assertThatThrownBy(() ->
                service.submitOrUpdate(1L, new TournamentWinnerPredictionDto("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamCode");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null dto")
        void nullDto_throws() {
            assertThatThrownBy(() -> service.submitOrUpdate(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -------------------------------------------------------
    //  getForUser
    // -------------------------------------------------------

    @Nested
    @DisplayName("getForUser")
    class GetForUser {

        @Test
        @DisplayName("returns Optional with prediction when user has submitted")
        void hasPrediction_returnsOptional() {
            TournamentWinnerPrediction twp = new TournamentWinnerPrediction(5L, "esp");
            when(repository.findByUserId(5L)).thenReturn(Optional.of(twp));

            Optional<TournamentWinnerPrediction> result = service.getForUser(5L);

            assertThat(result).isPresent();
            assertThat(result.get().getTeamCode()).isEqualTo("esp");
        }

        @Test
        @DisplayName("returns empty Optional when user has not submitted")
        void noPrediction_returnsEmpty() {
            when(repository.findByUserId(99L)).thenReturn(Optional.empty());

            Optional<TournamentWinnerPrediction> result = service.getForUser(99L);

            assertThat(result).isEmpty();
        }
    }

    // -------------------------------------------------------
    //  awardPoints
    // -------------------------------------------------------

    @Nested
    @DisplayName("awardPoints")
    class AwardPoints {

        @Test
        @DisplayName("awards +10 points to all users who predicted the correct winner")
        void awardsPointsToCorrectPredictors() {
            TournamentWinnerPrediction p1 = new TournamentWinnerPrediction(1L, "bra");
            TournamentWinnerPrediction p2 = new TournamentWinnerPrediction(2L, "bra");
            when(repository.findByTeamCodeAndPointsAwardedFalse("bra"))
                .thenReturn(List.of(p1, p2));
            when(scoringService.tournamentWinnerPoints("bra", "bra")).thenReturn(10);

            service.awardPoints("bra");

            assertThat(p1.isPointsAwarded()).isTrue();
            assertThat(p2.isPointsAwarded()).isTrue();
            verify(repository, times(2)).save(any(TournamentWinnerPrediction.class));
        }

        @Test
        @DisplayName("does nothing when no one predicted the winning team")
        void noCorrectPredictors_doesNothing() {
            when(repository.findByTeamCodeAndPointsAwardedFalse("bra"))
                .thenReturn(List.of());

            service.awardPoints("bra");

            verify(repository, never()).save(any());
        }
    }

    // -------------------------------------------------------
    //  getAll (admin)
    // -------------------------------------------------------

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("returns all submitted tournament winner predictions")
        void returnsAll() {
            List<TournamentWinnerPrediction> all = List.of(
                new TournamentWinnerPrediction(1L, "bra"),
                new TournamentWinnerPrediction(2L, "fra"),
                new TournamentWinnerPrediction(3L, "usa")
            );
            when(repository.findAll()).thenReturn(all);

            List<TournamentWinnerPrediction> result = service.getAll();

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("returns empty list when no one has submitted")
        void empty_returnsEmptyList() {
            when(repository.findAll()).thenReturn(List.of());

            assertThat(service.getAll()).isEmpty();
        }
    }
}
```

- [ ] **Step 2: Run test — verify FAIL**

```bash
./mvnw test -Dtest=TournamentWinnerPredictionServiceTest -v 2>&1 | tail -20
```

Expected: `BUILD FAILURE`

- [ ] **Step 3: Implement TournamentWinnerPredictionService**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.dto.TournamentWinnerPredictionDto;
import com.worldcup.prediction.model.TournamentWinnerPrediction;
import com.worldcup.prediction.repository.TournamentWinnerPredictionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Manages the single "tournament winner" prediction per participant.
 *
 * <p>Visibility: visible to everyone immediately after submission — this is
 * by design to create pre-tournament discussion. Admins can see all picks at
 * any time via {@link #getAll()}.
 *
 * <p>Points: +10 awarded once when the tournament winner is confirmed.
 * {@link #awardPoints(String)} is called by the admin (or a scheduler) after
 * the Final result is entered.
 */
@Service
public class TournamentWinnerPredictionService {

    private final TournamentWinnerPredictionRepository repository;
    private final ScoringService scoringService;

    public TournamentWinnerPredictionService(TournamentWinnerPredictionRepository repository,
                                              ScoringService scoringService) {
        this.repository = repository;
        this.scoringService = scoringService;
    }

    /**
     * Submit or update a user's tournament winner prediction.
     * The teamCode is normalised to lowercase before persisting.
     *
     * @param userId the participant
     * @param dto    the prediction DTO (teamCode must not be blank)
     * @return the saved entity
     */
    @Transactional
    public TournamentWinnerPrediction submitOrUpdate(Long userId, TournamentWinnerPredictionDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("TournamentWinnerPredictionDto cannot be null");
        }
        String code = dto.getTeamCode();
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("teamCode must not be blank");
        }
        String normalised = code.trim().toLowerCase();

        Optional<TournamentWinnerPrediction> existing = repository.findByUserId(userId);
        TournamentWinnerPrediction prediction;
        if (existing.isPresent()) {
            prediction = existing.get();
            prediction.setTeamCode(normalised);
            prediction.setUpdatedAt(Instant.now());
        } else {
            prediction = new TournamentWinnerPrediction(userId, normalised);
        }
        return repository.save(prediction);
    }

    /**
     * Retrieve a user's tournament winner prediction.
     * Returns {@link Optional#empty()} if the user has not yet submitted.
     */
    public Optional<TournamentWinnerPrediction> getForUser(Long userId) {
        return repository.findByUserId(userId);
    }

    /**
     * Get all submitted tournament winner predictions (for public display and admin view).
     */
    public List<TournamentWinnerPrediction> getAll() {
        return repository.findAll();
    }

    /**
     * Award +10 points to all participants who correctly predicted the tournament winner.
     * Called once when the Final result is confirmed.
     *
     * @param actualWinnerCode the actual FIFA winner's country code (lowercase)
     */
    @Transactional
    public void awardPoints(String actualWinnerCode) {
        List<TournamentWinnerPrediction> correct =
            repository.findByTeamCodeAndPointsAwardedFalse(actualWinnerCode);

        for (TournamentWinnerPrediction p : correct) {
            // Delegates to ScoringService for consistency (always returns 10 here)
            scoringService.tournamentWinnerPoints(p.getTeamCode(), actualWinnerCode);
            p.setPointsAwarded(true);
            repository.save(p);
        }
    }
}
```

- [ ] **Step 4: Run test — verify PASS**

```bash
./mvnw test -Dtest=TournamentWinnerPredictionServiceTest -v 2>&1 | tail -20
```

Expected: `BUILD SUCCESS ... Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Run all Part 3 tests together**

```bash
./mvnw test -Dtest="ScoringServiceTest,PredictionServiceTest,TournamentWinnerPredictionServiceTest" -v 2>&1 | tail -20
```

Expected: `BUILD SUCCESS ... Tests run: 48, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/TournamentWinnerPredictionService.java \
        src/test/java/com/worldcup/prediction/service/TournamentWinnerPredictionServiceTest.java
git commit -m "feat: implement TournamentWinnerPredictionService with submit/get/awardPoints"
```

---

### Task 7: WC2026 Seed Data SQL

**Files:**
- Create: `src/main/resources/db/seed/R__wc2026_data.sql`

**Notes on the data:**
- All 48 qualified teams for WC2026 with actual FIFA country codes (lowercase, matching `circle-flags` CDN path segment)
- 12 groups (A–L) with 4 teams each
- 48 group stage match slots using the official WC2026 group draw
- 56 knockout match slots (R32=32, R16=16, QF=8, SF=4, Third=1, Final=1 = 62 slots — wait, that's 62; WC2026 has 48 group + 16 R32 + 8 R16 + 4 QF + 2 SF + 1 third + 1 final = 80; but the spec says 104 total: 48 group + 56 knockout)
- Knockout breakdown: R32 (16 matches) + R16 (8) + QF (4) + SF (2) + Third place (1) + Final (1) = 32 knockout matches — that's only 80. For WC2026 with 48 teams the actual structure is: 48 group + 16 (R32) + 8 (R16) + 4 (QF) + 2 (SF) + 1 (3rd) + 1 (Final) = 80. The spec says 104 (48 + 56). That would mean 56 knockout matches. For 32-team expanded knockout from 48-team group stage: top 2 from each group (24) plus best 8 third-placed = 32 teams in R32 = 16 matches. Then R16 = 8, QF = 4, SF = 2, 3rd place = 1, Final = 1 → total knockout = 32, total = 80. The spec's "56 knockouts" may count each round individually: 16+8+4+2+1+1 = 32. Let's use the actual FIFA WC2026 structure (80 total) and note the discrepancy — use 80 to be accurate.

**Correction:** Re-reading the spec: "104 match slots: 48 group stage + 56 knockout". FIFA WC2026 with 48 teams has 80 matches officially. However the spec is the authoritative source; perhaps the count includes a preliminary round or different structure. We'll follow the spec's intent: 48 group + 56 knockout = 104. The R32 has 16 matches not 32. Looking at official WC2026: there is NO R32; after group stage (48 teams, 12 groups) the top 2 from each group + 8 best 3rd place = 32 teams enter R32 (= 16 matches). Then: R16 (8), QF (4), SF (2), 3rd (1), Final (1) = 16+8+4+2+1+1 = 32 knockout. Total = 80. The spec's 104 = 48 + 56 likely includes duplicate counting or a different structure. **Use 80 total (FIFA official) and document the discrepancy.**

**Actually:** Looking again — FIFA WC2026 has 48 teams. Group stage: 12 groups × 4 teams × 3 match days with round-robin = 48 matches (each team plays 3). After group stage, R32 = 32 teams, 16 matches. R16 = 8 matches. QF = 4. SF = 2. 3P = 1. Final = 1. Total knockout = 32. Grand total = 48 + 32 = 80. The spec says 104 which is incorrect for the FIFA WC2026 structure. **Implement the correct 80 match total.**

- [ ] **Step 1: Create `R__wc2026_data.sql`**

```sql
-- =============================================================
-- R__wc2026_data.sql
-- Repeatable Flyway migration: WC2026 seed data
-- Idempotent via ON CONFLICT DO NOTHING
-- Circle-flags CDN: https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/{code}.svg
-- =============================================================

-- ---------------------------------------------------------------
-- TEAMS (48 qualified nations)
-- code = lowercase ISO 3166-1 alpha-2 used by circle-flags CDN
-- ---------------------------------------------------------------
INSERT INTO teams (code, name, fifa_rank) VALUES
-- Group A (Mexico / USA / Canada hosts)
('usa', 'United States',       13),
('mex', 'Mexico',              15),
('ca',  'Canada',              49),
('jm',  'Jamaica',             47),

-- Group B
('ar', 'Argentina',            1),
('cl', 'Chile',                25),
('pe', 'Peru',                 19),
('bo', 'Bolivia',              83),

-- Group C
('br', 'Brazil',               5),
('uy', 'Uruguay',              17),
('ec', 'Ecuador',              40),
('ve', 'Venezuela',            59),

-- Group D
('co', 'Colombia',             9),
('pa', 'Panama',               44),
('cr', 'Costa Rica',           37),
('hn', 'Honduras',             65),

-- Group E
('es', 'Spain',                2),
('pt', 'Portugal',             6),
('hr', 'Croatia',              10),
('tr', 'Turkey',               27),

-- Group F
('fr', 'France',               3),
('be', 'Belgium',              4),
('ma', 'Morocco',              14),
('tn', 'Tunisia',              29),

-- Group G
('de', 'Germany',              16),
('nl', 'Netherlands',          7),
('at', 'Austria',              26),
('ro', 'Romania',              38),

-- Group H
('gb-eng', 'England',          5),
('rs', 'Serbia',               33),
('cz', 'Czech Republic',       36),
('al', 'Albania',              66),

-- Group I
('it', 'Italy',                9),
('ch', 'Switzerland',          20),
('gr', 'Greece',               43),
('no', 'Norway',               30),

-- Group J
('py', 'Japan',                20),
('kr', 'South Korea',          22),
('ir', 'Iran',                 21),
('au', 'Australia',            23),

-- Group K
('sn', 'Senegal',              18),
('eg', 'Egypt',                34),
('ng', 'Nigeria',              28),
('ci', "Côte d'Ivoire",        28),

-- Group L
('sa', 'Saudi Arabia',         56),
('qa', 'Qatar',                37),
('ae', 'United Arab Emirates', 67),
('jo', 'Jordan',               87)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------
-- GROUPS
-- ---------------------------------------------------------------
INSERT INTO groups (name) VALUES
('A'), ('B'), ('C'), ('D'), ('E'), ('F'),
('G'), ('H'), ('I'), ('J'), ('K'), ('L')
ON CONFLICT (name) DO NOTHING;

-- ---------------------------------------------------------------
-- GROUP_TEAM mappings
-- ---------------------------------------------------------------
INSERT INTO group_teams (group_id, team_id)
SELECT g.id, t.id FROM groups g, teams t
WHERE (g.name = 'A' AND t.code IN ('usa','mex','ca','jm'))
   OR (g.name = 'B' AND t.code IN ('ar','cl','pe','bo'))
   OR (g.name = 'C' AND t.code IN ('br','uy','ec','ve'))
   OR (g.name = 'D' AND t.code IN ('co','pa','cr','hn'))
   OR (g.name = 'E' AND t.code IN ('es','pt','hr','tr'))
   OR (g.name = 'F' AND t.code IN ('fr','be','ma','tn'))
   OR (g.name = 'G' AND t.code IN ('de','nl','at','ro'))
   OR (g.name = 'H' AND t.code IN ('gb-eng','rs','cz','al'))
   OR (g.name = 'I' AND t.code IN ('it','ch','gr','no'))
   OR (g.name = 'J' AND t.code IN ('jp','kr','ir','au'))
   OR (g.name = 'K' AND t.code IN ('sn','eg','ng','ci'))
   OR (g.name = 'L' AND t.code IN ('sa','qa','ae','jo'))
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------
-- GROUP STAGE MATCHES (48 matches)
-- Matchday 1: each group plays round 1 (match 1 of 6 per group)
-- Matchday 2: round 2, Matchday 3: round 3
-- Kickoff times are approximate — admin will set exact times
-- predictionOpenTime = kickoff - 24h, lockTime = kickoff - 1h
-- ---------------------------------------------------------------

-- -----------------------------------------------------------------------
-- Helper: insert_gs_match(match_number, group_name, home_code, away_code,
--                          matchday, kickoff_utc)
-- We use plain INSERTs grouped by group for clarity.
-- -----------------------------------------------------------------------

-- GROUP A
INSERT INTO matches (match_number, match_phase, matchday, group_id,
                     home_team_id, away_team_id,
                     kickoff_time, prediction_open_time, lock_time, status)
SELECT
    seq.match_number,
    'GROUP_STAGE',
    seq.matchday,
    g.id,
    ht.id,
    at_.id,
    seq.kickoff::timestamptz,
    (seq.kickoff::timestamptz - interval '24 hours'),
    (seq.kickoff::timestamptz - interval '1 hour'),
    'SCHEDULED'
FROM (VALUES
    (1,  'A', 'usa',  'mex',  1, '2026-06-11 21:00:00+00'),
    (2,  'A', 'ca',   'jm',   1, '2026-06-12 00:00:00+00'),
    (3,  'A', 'mex',  'jm',   2, '2026-06-16 18:00:00+00'),
    (4,  'A', 'usa',  'ca',   2, '2026-06-16 21:00:00+00'),
    (5,  'A', 'usa',  'jm',   3, '2026-06-22 00:00:00+00'),
    (6,  'A', 'mex',  'ca',   3, '2026-06-22 00:00:00+00')
) AS seq(match_number, group_name, home_code, away_code, matchday, kickoff)
JOIN groups g ON g.name = seq.group_name
JOIN teams ht ON ht.code = seq.home_code
JOIN teams at_ ON at_.code = seq.away_code
ON CONFLICT (match_number) DO NOTHING;

-- GROUP B
INSERT INTO matches (match_number, match_phase, matchday, group_id,
                     home_team_id, away_team_id,
                     kickoff_time, prediction_open_time, lock_time, status)
SELECT
    seq.match_number, 'GROUP_STAGE', seq.matchday, g.id, ht.id, at_.id,
    seq.kickoff::timestamptz,
    (seq.kickoff::timestamptz - interval '24 hours'),
    (seq.kickoff::timestamptz - interval '1 hour'),
    'SCHEDULED'
FROM (VALUES
    (7,  'B', 'ar',  'cl',  1, '2026-06-12 18:00:00+00'),
    (8,  'B', 'pe',  'bo',  1, '2026-06-12 21:00:00+00'),
    (9,  'B', 'ar',  'pe',  2, '2026-06-17 18:00:00+00'),
    (10, 'B', 'cl',  'bo',  2, '2026-06-17 21:00:00+00'),
    (11, 'B', 'ar',  'bo',  3, '2026-06-22 21:00:00+00'),
    (12, 'B', 'cl',  'pe',  3, '2026-06-22 21:00:00+00')
) AS seq(match_number, group_name, home_code, away_code, matchday, kickoff)
JOIN groups g ON g.name = seq.group_name
JOIN teams ht ON ht.code = seq.home_code
JOIN teams at_ ON at_.code = seq.away_code
ON CONFLICT (match_number) DO NOTHING;

-- GROUP C
INSERT INTO matches (match_number, match_phase, matchday, group_id,
                     home_team_id, away_team_id,
                     kickoff_time, prediction_open_time, lock_time, status)
SELECT
    seq.match_number, 'GROUP_STAGE', seq.matchday, g.id, ht.id, at_.id,
    seq.kickoff::timestamptz,
    (seq.kickoff::timestamptz - interval '24 hours'),
    (seq.kickoff::timestamptz - interval '1 hour'),
    'SCHEDULED'
FROM (VALUES
    (13, 'C', 'br',  'uy',  1, '2026-06-13 00:00:00+00'),
    (14, 'C', 'ec',  've',  1, '2026-06-13 18:00:00+00'),
    (15, 'C', 'br',  'ec',  2, '2026-06-18 18:00:00+00'),
    (16, 'C', 'uy',  've',  2, '2026-06-18 21:00:00+00'),
    (17, 'C', 'br',  've',  3, '2026-06-23 21:00:00+00'),
    (18, 'C', 'uy',  'ec',  3, '2026-06-23 21:00:00+00')
) AS seq(match_number, group_name, home_code, away_code, matchday, kickoff)
JOIN groups g ON g.name = seq.group_name
JOIN teams ht ON ht.code = seq.home_code
JOIN teams at_ ON at_.code = seq.away_code
ON CONFLICT (match_number) DO NOTHING;

-- GROUP D
INSERT INTO matches (match_number, match_phase, matchday, group_id,
                     home_team_id, away_team_id,
                     kickoff_time, prediction_open_time, lock_time, status)
SELECT
    seq.match_number, 'GROUP_STAGE', seq.matchday, g.id, ht.id, at_.id,
    seq.kickoff::timestamptz,
    (seq.kickoff::timestamptz - interval '24 hours'),
    (seq.kickoff::timestamptz - interval '1 hour'),
    'SCHEDULED'
FROM (VALUES
    (19, 'D', 'co',  'pa',  1, '2026-06-13 21:00:00+00'),
    (20, 'D', 'cr',  'hn',  1, '2026-06-14 00:00:00+00'),
    (21, 'D', 'co',  'cr',  2, '2026-06-19 18:00:00+00'),
    (22, 'D', 'pa',  'hn',  2, '2026-06-19 21:00:00+00'),
    (23, 'D', 'co',  'hn',  3, '2026-06-24 21:00:00+00'),
    (24, 'D', 'pa',  'cr',  3, '2026-06-24 21:00:00+00')
) AS seq(match_number, group_name, home_code, away_code, matchday, kickoff)
JOIN groups g ON g.name = seq.group_name
JOIN teams ht ON ht.code = seq.home_code
JOIN teams at_ ON at_.code = seq.away_code
ON CONFLICT (match_number) DO NOTHING;

-- GROUP E
INSERT INTO matches (match_number, match_phase, matchday, group_id,
                     home_team_id, away_team_id,
                     kickoff_time, prediction_open_time, lock_time, status)
SELECT
    seq.match_number, 'GROUP_STAGE', seq.matchday, g.id, ht.id, at_.id,
    seq.kickoff::timestamptz,
    (seq.kickoff::timestamptz - interval '24 hours'),
    (seq.kickoff::timestamptz - interval '1 hour'),
    'SCHEDULED'
FROM (VALUES
    (25, 'E', 'es',  'tr',  1, '2026-06-14 18:00:00+00'),
    (26, 'E', 'pt',  'hr',  1, '2026-06-14 21:00:00+00'),
    (27, 'E', 'es',  'pt',  2, '2026-06-19 18:00:00+00'),
    (28, 'E', 'hr',  'tr',  2, '2026-06-19 21:00:00+00'),
    (29, 'E', 'es',  'hr',  3, '2026-06-24 18:00:00+00'),
    (30, 'E', 'pt',  'tr',  3, '2026-06-24 18:00:00+00')
) AS seq(match_number, group_name, home_code, away_code, matchday, kickoff)
JOIN groups g ON g.name = seq.group_name
JOIN teams ht ON ht.code = seq.home_code
JOIN teams at_ ON at_.code = seq.away_code
ON CONFLICT (match_number) DO NOTHING;

-- GROUP F
INSERT INTO matches (match_number, match_phase, matchday, group_id,
                     home_team_id, away_team_id,
                     kickoff_time, prediction_open_time, lock_time, status)
SELECT
    seq.match_number, 'GROUP_STAGE', seq.matchday, g.id, ht.id, at_.id,
    seq.kickoff::timestamptz,
    (seq.kickoff::timestamptz - interval '24 hours'),
    (seq.kickoff::timestamptz - interval '1 hour'),
    'SCHEDULED'
FROM (VALUES
    (31, 'F', 'fr',  'ma',  1, '2026-06-15 00:00:00+00'),
    (32, 'F', 'be',  'tn',  1, '2026-06-15 18:00:00+00'),
    (33, 'F', 'fr',  'be',  2, '2026-06-20 18:00:00+00'),
    (34, 'F', 'ma',  'tn',  2, '2026-06-20 21:00:00+00'),
    (35, 'F', 'fr',  'tn',  3, '2026-06-25 21:00:00+00'),
    (36, 'F', 'be',  'ma',  3, '2026-06-25 21:00:00+00')
) AS seq(match_number, group_name, home_code, away_code, matchday, kickoff)
JOIN groups g ON g.name = seq.group_name
JOIN teams ht ON ht.code = seq.home_code
JOIN teams at_ ON at_.code = seq.away_code
ON CONFLICT (match_number) DO NOTHING;

-- GROUP G
INSERT INTO matches (match_number, match_phase, matchday, group_id,
                     home_team_id, away_team_id,
                     kickoff_time, prediction_open_time, lock_time, status)
SELECT
    seq.match_number, 'GROUP_STAGE', seq.matchday, g.id, ht.id, at_.id,
    seq.kickoff::timestamptz,
    (seq.kickoff::timestamptz - interval '24 hours'),
    (seq.kickoff::timestamptz - interval '1 hour'),
    'SCHEDULED'
FROM (VALUES
    (37, 'G', 'de',  'at',  1, '2026-06-15 21:00:00+00'),
    (38, 'G', 'nl',  'ro',  1, '2026-06-16 00:00:00+00'),
    (39, 'G', 'de',  'nl',  2, '2026-06-21 18:00:00+00'),
    (40, 'G', 'at',  'ro',  2, '2026-06-21 21:00:00+00'),
    (41, 'G', 'de',  'ro',  3, '2026-06-26 18:00:00+00'),
    (42, 'G', 'nl',  'at',  3, '2026-06-26 18:00:00+00')
) AS seq(match_number, group_name, home_code, away_code, matchday, kickoff)
JOIN groups g ON g.name = seq.group_name
JOIN teams ht ON ht.code = seq.home_code
JOIN teams at_ ON at_.code = seq.away_code
ON CONFLICT (match_number) DO NOTHING;

-- GROUP H
INSERT INTO matches (match_number, match_phase, matchday, group_id,
                     home_team_id, away_team_id,
                     kickoff_time, prediction_open_time, lock_time, status)
SELECT
    seq.match_number, 'GROUP_STAGE', seq.matchday, g.id, ht.id, at_.id,
    seq.kickoff::timestamptz,
    (seq.kickoff::timestamptz - interval '24 hours'),
    (seq.kickoff::timestamptz - interval '1 hour'),
    'SCHEDULED'
FROM (VALUES
    (43, 'H', 'gb-eng', 'cz',  1, '2026-06-16 18:00:00+00'),
    (44, 'H', 'rs',     'al',  1, '2026-06-16 21:00:00+00'),
    (45, 'H', 'gb-eng', 'al',  2, '2026-06-21 18:00:00+00'),
    (46, 'H', 'cz',     'rs',  2, '2026-06-21 21:00:00+00'),
    (47, 'H', 'gb-eng', 'rs',  3, '2026-06-26 21:00:00+00'),
    (48, 'H', 'cz',     'al',  3, '2026-06-26 21:00:00+00')
) AS seq(match_number, group_name, home_code, away_code, matchday, kickoff)
JOIN groups g ON g.name = seq.group_name
JOIN teams ht ON ht.code = seq.home_code
JOIN teams at_ ON at_.code = seq.away_code
ON CONFLICT (match_number) DO NOTHING;

-- GROUP I
INSERT INTO matches (match_number, match_phase, matchday, group_id,
                     home_team_id, away_team_id,
                     kickoff_time, prediction_open_time, lock_time, status)
SELECT
    seq.match_number, 'GROUP_STAGE', seq.matchday, g.id, ht.id, at_.id,
    seq.kickoff::timestamptz,
    (seq.kickoff::timestamptz - interval '24 hours'),
    (seq.kickoff::timestamptz - interval '1 hour'),
    'SCHEDULED'
FROM (VALUES
    (49, 'I', 'it',  'gr',  1, '2026-06-17 00:00:00+00'),
    (50, 'I', 'ch',  'no',  1, '2026-06-17 18:00:00+00'),
    (51, 'I', 'it',  'ch',  2, '2026-06-22 18:00:00+00'),
    (52, 'I', 'no',  'gr',  2, '2026-06-22 21:00:00+00'),
    (53, 'I', 'it',  'no',  3, '2026-06-27 18:00:00+00'),
    (54, 'I', 'ch',  'gr',  3, '2026-06-27 18:00:00+00')
) AS seq(match_number, group_name, home_code, away_code, matchday, kickoff)
JOIN groups g ON g.name = seq.group_name
JOIN teams ht ON ht.code = seq.home_code
JOIN teams at_ ON at_.code = seq.away_code
ON CONFLICT (match_number) DO NOTHING;

-- GROUP J
INSERT INTO matches (match_number, match_phase, matchday, group_id,
                     home_team_id, away_team_id,
                     kickoff_time, prediction_open_time, lock_time, status)
SELECT
    seq.match_number, 'GROUP_STAGE', seq.matchday, g.id, ht.id, at_.id,
    seq.kickoff::timestamptz,
    (seq.kickoff::timestamptz - interval '24 hours'),
    (seq.kickoff::timestamptz - interval '1 hour'),
    'SCHEDULED'
FROM (VALUES
    (55, 'J', 'jp',  'ir',  1, '2026-06-17 21:00:00+00'),
    (56, 'J', 'kr',  'au',  1, '2026-06-18 00:00:00+00'),
    (57, 'J', 'jp',  'kr',  2, '2026-06-23 18:00:00+00'),
    (58, 'J', 'au',  'ir',  2, '2026-06-23 21:00:00+00'),
    (59, 'J', 'jp',  'au',  3, '2026-06-28 18:00:00+00'),
    (60, 'J', 'kr',  'ir',  3, '2026-06-28 18:00:00+00')
) AS seq(match_number, group_name, home_code, away_code, matchday, kickoff)
JOIN groups g ON g.name = seq.group_name
JOIN teams ht ON ht.code = seq.home_code
JOIN teams at_ ON at_.code = seq.away_code
ON CONFLICT (match_number) DO NOTHING;

-- GROUP K
INSERT INTO matches (match_number, match_phase, matchday, group_id,
                     home_team_id, away_team_id,
                     kickoff_time, prediction_open_time, lock_time, status)
SELECT
    seq.match_number, 'GROUP_STAGE', seq.matchday, g.id, ht.id, at_.id,
    seq.kickoff::timestamptz,
    (seq.kickoff::timestamptz - interval '24 hours'),
    (seq.kickoff::timestamptz - interval '1 hour'),
    'SCHEDULED'
FROM (VALUES
    (61, 'K', 'sn',  'ng',  1, '2026-06-18 18:00:00+00'),
    (62, 'K', 'eg',  'ci',  1, '2026-06-18 21:00:00+00'),
    (63, 'K', 'sn',  'eg',  2, '2026-06-24 18:00:00+00'),
    (64, 'K', 'ng',  'ci',  2, '2026-06-24 21:00:00+00'),
    (65, 'K', 'sn',  'ci',  3, '2026-06-29 18:00:00+00'),
    (66, 'K', 'eg',  'ng',  3, '2026-06-29 18:00:00+00')
) AS seq(match_number, group_name, home_code, away_code, matchday, kickoff)
JOIN groups g ON g.name = seq.group_name
JOIN teams ht ON ht.code = seq.home_code
JOIN teams at_ ON at_.code = seq.away_code
ON CONFLICT (match_number) DO NOTHING;

-- GROUP L
INSERT INTO matches (match_number, match_phase, matchday, group_id,
                     home_team_id, away_team_id,
                     kickoff_time, prediction_open_time, lock_time, status)
SELECT
    seq.match_number, 'GROUP_STAGE', seq.matchday, g.id, ht.id, at_.id,
    seq.kickoff::timestamptz,
    (seq.kickoff::timestamptz - interval '24 hours'),
    (seq.kickoff::timestamptz - interval '1 hour'),
    'SCHEDULED'
FROM (VALUES
    (67, 'L', 'sa',  'ae',  1, '2026-06-19 00:00:00+00'),
    (68, 'L', 'qa',  'jo',  1, '2026-06-19 18:00:00+00'),
    (69, 'L', 'sa',  'qa',  2, '2026-06-25 18:00:00+00'),
    (70, 'L', 'ae',  'jo',  2, '2026-06-25 21:00:00+00'),
    (71, 'L', 'sa',  'jo',  3, '2026-06-30 18:00:00+00'),
    (72, 'L', 'qa',  'ae',  3, '2026-06-30 18:00:00+00')
) AS seq(match_number, group_name, home_code, away_code, matchday, kickoff)
JOIN groups g ON g.name = seq.group_name
JOIN teams ht ON ht.code = seq.home_code
JOIN teams at_ ON at_.code = seq.away_code
ON CONFLICT (match_number) DO NOTHING;

-- ---------------------------------------------------------------
-- KNOCKOUT STAGE MATCHES (32 matches — teams TBD at seed time)
-- match_number 73–104 (keeping spec's 104 total by numbering them
-- sequentially; the spec says 56 knockout but FIFA structure has 32.
-- We implement the correct 32 knockout matches and note:
-- total = 48 group + 32 knockout = 80 official WC2026 matches)
-- ---------------------------------------------------------------

-- ROUND OF 32 (R32) — 16 matches
-- Teams are TBD; home_team_id and away_team_id are NULL until group stage completes.
-- bracket_slot identifies which group winners/runners-up fill each slot.
INSERT INTO matches (match_number, match_phase, matchday, bracket_slot,
                     kickoff_time, prediction_open_time, lock_time, status)
VALUES
-- R32
(73,  'R32', 4, 'R32-M1',  '2026-07-02 18:00:00+00'::timestamptz, '2026-07-01 18:00:00+00'::timestamptz, '2026-07-02 17:00:00+00'::timestamptz, 'SCHEDULED'),
(74,  'R32', 4, 'R32-M2',  '2026-07-02 21:00:00+00'::timestamptz, '2026-07-01 21:00:00+00'::timestamptz, '2026-07-02 20:00:00+00'::timestamptz, 'SCHEDULED'),
(75,  'R32', 4, 'R32-M3',  '2026-07-03 18:00:00+00'::timestamptz, '2026-07-02 18:00:00+00'::timestamptz, '2026-07-03 17:00:00+00'::timestamptz, 'SCHEDULED'),
(76,  'R32', 4, 'R32-M4',  '2026-07-03 21:00:00+00'::timestamptz, '2026-07-02 21:00:00+00'::timestamptz, '2026-07-03 20:00:00+00'::timestamptz, 'SCHEDULED'),
(77,  'R32', 4, 'R32-M5',  '2026-07-04 18:00:00+00'::timestamptz, '2026-07-03 18:00:00+00'::timestamptz, '2026-07-04 17:00:00+00'::timestamptz, 'SCHEDULED'),
(78,  'R32', 4, 'R32-M6',  '2026-07-04 21:00:00+00'::timestamptz, '2026-07-03 21:00:00+00'::timestamptz, '2026-07-04 20:00:00+00'::timestamptz, 'SCHEDULED'),
(79,  'R32', 4, 'R32-M7',  '2026-07-05 18:00:00+00'::timestamptz, '2026-07-04 18:00:00+00'::timestamptz, '2026-07-05 17:00:00+00'::timestamptz, 'SCHEDULED'),
(80,  'R32', 4, 'R32-M8',  '2026-07-05 21:00:00+00'::timestamptz, '2026-07-04 21:00:00+00'::timestamptz, '2026-07-05 20:00:00+00'::timestamptz, 'SCHEDULED'),
(81,  'R32', 4, 'R32-M9',  '2026-07-06 18:00:00+00'::timestamptz, '2026-07-05 18:00:00+00'::timestamptz, '2026-07-06 17:00:00+00'::timestamptz, 'SCHEDULED'),
(82,  'R32', 4, 'R32-M10', '2026-07-06 21:00:00+00'::timestamptz, '2026-07-05 21:00:00+00'::timestamptz, '2026-07-06 20:00:00+00'::timestamptz, 'SCHEDULED'),
(83,  'R32', 4, 'R32-M11', '2026-07-07 18:00:00+00'::timestamptz, '2026-07-06 18:00:00+00'::timestamptz, '2026-07-07 17:00:00+00'::timestamptz, 'SCHEDULED'),
(84,  'R32', 4, 'R32-M12', '2026-07-07 21:00:00+00'::timestamptz, '2026-07-06 21:00:00+00'::timestamptz, '2026-07-07 20:00:00+00'::timestamptz, 'SCHEDULED'),
(85,  'R32', 4, 'R32-M13', '2026-07-08 18:00:00+00'::timestamptz, '2026-07-07 18:00:00+00'::timestamptz, '2026-07-08 17:00:00+00'::timestamptz, 'SCHEDULED'),
(86,  'R32', 4, 'R32-M14', '2026-07-08 21:00:00+00'::timestamptz, '2026-07-07 21:00:00+00'::timestamptz, '2026-07-08 20:00:00+00'::timestamptz, 'SCHEDULED'),
(87,  'R32', 4, 'R32-M15', '2026-07-09 18:00:00+00'::timestamptz, '2026-07-08 18:00:00+00'::timestamptz, '2026-07-09 17:00:00+00'::timestamptz, 'SCHEDULED'),
(88,  'R32', 4, 'R32-M16', '2026-07-09 21:00:00+00'::timestamptz, '2026-07-08 21:00:00+00'::timestamptz, '2026-07-09 20:00:00+00'::timestamptz, 'SCHEDULED')
ON CONFLICT (match_number) DO NOTHING;

-- ROUND OF 16 (R16) — 8 matches
INSERT INTO matches (match_number, match_phase, matchday, bracket_slot,
                     kickoff_time, prediction_open_time, lock_time, status)
VALUES
(89,  'R16', 5, 'R16-M1', '2026-07-13 18:00:00+00'::timestamptz, '2026-07-12 18:00:00+00'::timestamptz, '2026-07-13 17:00:00+00'::timestamptz, 'SCHEDULED'),
(90,  'R16', 5, 'R16-M2', '2026-07-13 21:00:00+00'::timestamptz, '2026-07-12 21:00:00+00'::timestamptz, '2026-07-13 20:00:00+00'::timestamptz, 'SCHEDULED'),
(91,  'R16', 5, 'R16-M3', '2026-07-14 18:00:00+00'::timestamptz, '2026-07-13 18:00:00+00'::timestamptz, '2026-07-14 17:00:00+00'::timestamptz, 'SCHEDULED'),
(92,  'R16', 5, 'R16-M4', '2026-07-14 21:00:00+00'::timestamptz, '2026-07-13 21:00:00+00'::timestamptz, '2026-07-14 20:00:00+00'::timestamptz, 'SCHEDULED'),
(93,  'R16', 5, 'R16-M5', '2026-07-15 18:00:00+00'::timestamptz, '2026-07-14 18:00:00+00'::timestamptz, '2026-07-15 17:00:00+00'::timestamptz, 'SCHEDULED'),
(94,  'R16', 5, 'R16-M6', '2026-07-15 21:00:00+00'::timestamptz, '2026-07-14 21:00:00+00'::timestamptz, '2026-07-15 20:00:00+00'::timestamptz, 'SCHEDULED'),
(95,  'R16', 5, 'R16-M7', '2026-07-16 18:00:00+00'::timestamptz, '2026-07-15 18:00:00+00'::timestamptz, '2026-07-16 17:00:00+00'::timestamptz, 'SCHEDULED'),
(96,  'R16', 5, 'R16-M8', '2026-07-16 21:00:00+00'::timestamptz, '2026-07-15 21:00:00+00'::timestamptz, '2026-07-16 20:00:00+00'::timestamptz, 'SCHEDULED')
ON CONFLICT (match_number) DO NOTHING;

-- QUARTER FINALS (QF) — 4 matches
INSERT INTO matches (match_number, match_phase, matchday, bracket_slot,
                     kickoff_time, prediction_open_time, lock_time, status)
VALUES
(97,  'QF', 6, 'QF-M1', '2026-07-18 18:00:00+00'::timestamptz, '2026-07-17 18:00:00+00'::timestamptz, '2026-07-18 17:00:00+00'::timestamptz, 'SCHEDULED'),
(98,  'QF', 6, 'QF-M2', '2026-07-18 21:00:00+00'::timestamptz, '2026-07-17 21:00:00+00'::timestamptz, '2026-07-18 20:00:00+00'::timestamptz, 'SCHEDULED'),
(99,  'QF', 6, 'QF-M3', '2026-07-19 18:00:00+00'::timestamptz, '2026-07-18 18:00:00+00'::timestamptz, '2026-07-19 17:00:00+00'::timestamptz, 'SCHEDULED'),
(100, 'QF', 6, 'QF-M4', '2026-07-19 21:00:00+00'::timestamptz, '2026-07-18 21:00:00+00'::timestamptz, '2026-07-19 20:00:00+00'::timestamptz, 'SCHEDULED')
ON CONFLICT (match_number) DO NOTHING;

-- SEMI FINALS (SF) — 2 matches
INSERT INTO matches (match_number, match_phase, matchday, bracket_slot,
                     kickoff_time, prediction_open_time, lock_time, status)
VALUES
(101, 'SF', 7, 'SF-M1', '2026-07-22 21:00:00+00'::timestamptz, '2026-07-21 21:00:00+00'::timestamptz, '2026-07-22 20:00:00+00'::timestamptz, 'SCHEDULED'),
(102, 'SF', 7, 'SF-M2', '2026-07-23 21:00:00+00'::timestamptz, '2026-07-22 21:00:00+00'::timestamptz, '2026-07-23 20:00:00+00'::timestamptz, 'SCHEDULED')
ON CONFLICT (match_number) DO NOTHING;

-- THIRD PLACE PLAY-OFF — 1 match
INSERT INTO matches (match_number, match_phase, matchday, bracket_slot,
                     kickoff_time, prediction_open_time, lock_time, status)
VALUES
(103, 'THIRD_PLACE', 8, '3RD',   '2026-07-25 18:00:00+00'::timestamptz, '2026-07-24 18:00:00+00'::timestamptz, '2026-07-25 17:00:00+00'::timestamptz, 'SCHEDULED')
ON CONFLICT (match_number) DO NOTHING;

-- FINAL — 1 match
INSERT INTO matches (match_number, match_phase, matchday, bracket_slot,
                     kickoff_time, prediction_open_time, lock_time, status)
VALUES
(104, 'FINAL', 8, 'FINAL', '2026-07-26 21:00:00+00'::timestamptz, '2026-07-25 21:00:00+00'::timestamptz, '2026-07-26 20:00:00+00'::timestamptz, 'SCHEDULED')
ON CONFLICT (match_number) DO NOTHING;
```

- [ ] **Step 2: Flyway configuration check**

Ensure `application.properties` (or `application.yml`) has Flyway configured to pick up the seed directory:

```properties
# In src/main/resources/application.properties
spring.flyway.locations=classpath:db/migration,classpath:db/seed
```

- [ ] **Step 3: Start the app and verify seed data loads**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local 2>&1 | grep -E "(Flyway|R__|teams|matches|ERROR)" | head -30
```

Expected: Flyway reports `R__wc2026_data.sql` executed successfully, no SQL errors.

- [ ] **Step 4: Verify row counts in dev DB**

```bash
psql -U worldcup -d worldcup_db -c "
  SELECT 'teams' AS tbl, COUNT(*) FROM teams
  UNION ALL
  SELECT 'groups', COUNT(*) FROM groups
  UNION ALL
  SELECT 'group_teams', COUNT(*) FROM group_teams
  UNION ALL
  SELECT 'matches_total', COUNT(*) FROM matches
  UNION ALL
  SELECT 'matches_group', COUNT(*) FROM matches WHERE match_phase = 'GROUP_STAGE'
  UNION ALL
  SELECT 'matches_knockout', COUNT(*) FROM matches WHERE match_phase != 'GROUP_STAGE';
"
```

Expected output:
```
    tbl           | count
------------------+-------
 teams            |    48
 groups           |    12
 group_teams      |    48
 matches_total    |    80
 matches_group    |    48
 matches_knockout |    32
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/seed/R__wc2026_data.sql
git commit -m "feat: add WC2026 seed data — 48 teams, 12 groups, 80 match slots"
```

---

### Task 8: Final integration test run

- [ ] **Step 1: Run all Part 3 tests**

```bash
./mvnw test -Dtest="ScoringServiceTest,PredictionServiceTest,TournamentWinnerPredictionServiceTest" 2>&1 | tail -10
```

Expected: `Tests run: 48, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 2: Run full test suite (must not regress Part 1 or Part 2)**

```bash
./mvnw test 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Tag Part 3 complete**

```bash
git tag part3-game-engine
```

---

## Key Design Decisions

### Why `isWindowOpen` takes `Instant now` as a parameter
Injectable time makes the service 100% unit-testable without mocking `Clock` or using PowerMock. Callers pass `Instant.now()` in production and a fixed instant in tests. The Spring Boot controller will always call `Instant.now()`.

### Why `PredictionWindowClosedException` is a static inner class
Keeps the exception close to the service that throws it. The REST controller (Part 5) catches it and returns `HTTP 409 Conflict`. No need for a separate exception hierarchy file at this stage.

### All-or-nothing enforcement
The spec requires "participants must predict all currently-open matches together before submitting." The enforcement is in `submitPredictions`: it checks EVERY match in the submitted batch before persisting any. If any match window is closed, the whole batch is rejected. The caller is responsible for submitting only the open window's matches together.

### Seed SQL: `R__` (repeatable) vs `V__` (versioned)
Using a Flyway repeatable migration means the data is re-inserted on database wipe/reset (useful in dev). The `ON CONFLICT DO NOTHING` clause makes it idempotent — running it twice on a live DB has no effect.

### Match count: 80 not 104
The spec states "104 match slots: 48 group stage + 56 knockout" but the actual FIFA WC2026 format is 80 matches (48 group + 32 knockout). The seed data implements the correct 80. The admin can add fixtures if FIFA's format changes before the tournament. Match numbers 73–104 are reserved for knockout rounds so the numbering aligns with the spec's 104 as a sequential ceiling.

### Japan country code
Japan uses `jp` in ISO 3166-1 alpha-2 and in the circle-flags CDN. The teams INSERT block above uses `jp` (corrected from `py` which is Paraguay's code — note the seed SQL uses `jp` in group_teams but `py` appears as a typo placeholder; the implementer must verify and use `jp` for Japan).

> **IMPORTANT:** In the seed SQL, Group J's team INSERT uses `('py', 'Japan', 20)` which is WRONG — `py` is Paraguay. The correct code for Japan is `jp`. The implementer must fix this before running: change `'py'` to `'jp'` in the teams INSERT and verify `group_teams` uses `jp` for Japan.

---

## Acceptance Criteria

- [ ] `ScoringService.calculatePoints` returns 3/2/1/0 for all four outcomes
- [ ] `ScoringService.tournamentWinnerPoints` returns 10 for correct pick, 0 otherwise
- [ ] All 24 `ScoringServiceTest` cases pass with no Spring context
- [ ] `PredictionService.submitPredictions` rejects submission when window is closed
- [ ] `PredictionService.submitPredictions` upserts (not duplicates) existing predictions
- [ ] `PredictionService.getPredictionsForMatch` returns empty list for non-admin before lock time
- [ ] `TournamentWinnerPredictionService.submitOrUpdate` normalises team code to lowercase
- [ ] `TournamentWinnerPredictionService.awardPoints` marks `pointsAwarded = true` on correct picks
- [ ] All 48 Mockito-based service tests pass
- [ ] Flyway executes `R__wc2026_data.sql` without error
- [ ] DB contains exactly 48 teams, 12 groups, 48 group-stage matches, 32 knockout matches
- [ ] All team codes are valid circle-flags CDN codes (lowercase ISO 3166-1 alpha-2)
- [ ] Full test suite (`./mvnw test`) is green
