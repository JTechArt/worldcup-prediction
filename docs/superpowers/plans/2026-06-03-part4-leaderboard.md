# Part 4: Leaderboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a full leaderboard system with aggregation service, public controller, full-page prediction breakdown table, and a reusable mini-widget for the home page.

**Architecture:** LeaderboardService computes rankings fresh on each request by aggregating Prediction.points per User, then sorting by the spec's 5-level tiebreaker chain. The full leaderboard page uses a 3-panel CSS layout (fixed player column | horizontally-scrollable game columns | fixed total column) driven by Thymeleaf, with phase headers that carry shimmer animations. The mini-widget is a `th:fragment` so the home page (Part 5) can include it directly.

**Tech Stack:** Java 21, Spring Boot 3.x, Spring MVC, Spring Data JPA, Thymeleaf, HTMX, Tailwind CSS, Alpine.js, Mockito/JUnit 5

**Depends on:** Part 1 (entities: User, Match, Prediction, TournamentWinnerPrediction), Part 2 (ScoringService populates Prediction.points), Part 3 (Admin result entry)

**Next parts:** Part 5 (Public Pages — home page includes mini-widget via `th:replace`)

---

## File Structure

```
src/
└── main/
    ├── java/com/worldcup/prediction/
    │   ├── dto/
    │   │   └── LeaderboardEntryDto.java
    │   ├── service/
    │   │   └── LeaderboardService.java
    │   └── controller/
    │       └── LeaderboardController.java
    └── resources/
        └── templates/
            ├── leaderboard.html
            └── fragments/
                └── leaderboard-mini-widget.html

src/
└── test/
    └── java/com/worldcup/prediction/
        └── service/
            └── LeaderboardServiceTest.java
```

---

## Assumptions about existing code (Parts 1–3)

The plan assumes the following entities and repositories exist from Parts 1–3:

**User entity** (`com.worldcup.prediction.entity.User`):
- `Long id`
- `String displayName`
- `String avatarUrl`
- `Instant registeredAt`
- `UserStatus status` (enum: `PENDING`, `ACTIVE`, `DISABLED`)

**Match entity** (`com.worldcup.prediction.entity.Match`):
- `Long id`
- `MatchPhase phase` (enum: `GS_R1`, `GS_R2`, `GS_R3`, `R32`, `R16`, `QF`, `SF`, `FINAL`)
- `LocalDateTime kickoff`
- `Team homeTeam`, `Team awayTeam`
- `Integer homeScore`, `Integer awayScore` (null if not played)
- `boolean resultEntered`

**Team entity** (`com.worldcup.prediction.entity.Team`):
- `Long id`
- `String name`
- `String countryCode` (ISO 2-letter, e.g. "br", "fr")

**Prediction entity** (`com.worldcup.prediction.entity.Prediction`):
- `Long id`
- `User user`
- `Match match`
- `Integer homeScore`, `Integer awayScore`
- `int points` (set by ScoringService: 0/1/2/3)
- `PredictionOutcome outcome` (enum: `EXACT`, `CORRECT_WINNER`, `CORRECT_DRAW`, `WRONG`, `PENDING`)

**TournamentWinnerPrediction entity** (`com.worldcup.prediction.entity.TournamentWinnerPrediction`):
- `Long id`
- `User user`
- `Team predictedWinner`
- `boolean scored` (true = +10 awarded)

**Repositories** (Spring Data JPA):
- `UserRepository extends JpaRepository<User, Long>`
- `PredictionRepository extends JpaRepository<Prediction, Long>`
- `TournamentWinnerPredictionRepository extends JpaRepository<TournamentWinnerPrediction, Long>`
- `MatchRepository extends JpaRepository<Match, Long>`

**MatchPhase enum label helper** — the plan adds a `displayLabel()` method in this task for phase headers.

---

### Task 1: LeaderboardEntryDto

**Files:**
- Create: `src/main/java/com/worldcup/prediction/dto/LeaderboardEntryDto.java`

- [ ] **Step 1: No test needed** — pure data class, tested implicitly through LeaderboardService tests.

- [ ] **Step 2: Implement**

```java
package com.worldcup.prediction.dto;

/**
 * Immutable snapshot of one participant's leaderboard standing.
 * Computed fresh per request by LeaderboardService.
 */
public class LeaderboardEntryDto {

    private final int rank;
    private final Long userId;
    private final String displayName;
    private final String avatarUrl;

    /**
     * ISO 2-letter country code (lowercase) of the team the user predicted
     * as the tournament winner. Used to render the circle flag badge.
     * Null if the user has not made a tournament winner prediction.
     */
    private final String predictedWinnerCountryCode;

    private final int totalPoints;
    private final int exactCount;
    private final int correctWinnerCount;
    private final int drawCount;

    /** True if the user's tournament winner prediction was correct (+10). */
    private final boolean tournamentWinnerCorrect;

    /**
     * Rank change vs. previous snapshot.
     * Positive = moved up, negative = moved down, 0 = no change / first snapshot.
     * For v1 this is always 0 (no snapshot storage).
     */
    private final int rankChange;

    public LeaderboardEntryDto(
            int rank,
            Long userId,
            String displayName,
            String avatarUrl,
            String predictedWinnerCountryCode,
            int totalPoints,
            int exactCount,
            int correctWinnerCount,
            int drawCount,
            boolean tournamentWinnerCorrect,
            int rankChange) {
        this.rank = rank;
        this.userId = userId;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.predictedWinnerCountryCode = predictedWinnerCountryCode;
        this.totalPoints = totalPoints;
        this.exactCount = exactCount;
        this.correctWinnerCount = correctWinnerCount;
        this.drawCount = drawCount;
        this.tournamentWinnerCorrect = tournamentWinnerCorrect;
        this.rankChange = rankChange;
    }

    public int getRank() { return rank; }
    public Long getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getPredictedWinnerCountryCode() { return predictedWinnerCountryCode; }
    public int getTotalPoints() { return totalPoints; }
    public int getExactCount() { return exactCount; }
    public int getCorrectWinnerCount() { return correctWinnerCount; }
    public int getDrawCount() { return drawCount; }
    public boolean isTournamentWinnerCorrect() { return tournamentWinnerCorrect; }
    public int getRankChange() { return rankChange; }

    /** Convenience: initials for avatar fallback (first letter of each name word, max 2). */
    public String getInitials() {
        if (displayName == null || displayName.isBlank()) return "?";
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (String.valueOf(parts[0].charAt(0)) + String.valueOf(parts[parts.length - 1].charAt(0))).toUpperCase();
    }

    /** Convenience: CSS class for rank row highlighting (r1/r2/r3/rn). */
    public String getRankCssClass() {
        return switch (rank) {
            case 1 -> "r1";
            case 2 -> "r2";
            case 3 -> "r3";
            default -> "rn";
        };
    }

    /** Flag image URL for the circle-flags CDN. Returns empty string if no prediction. */
    public String getFlagUrl() {
        if (predictedWinnerCountryCode == null || predictedWinnerCountryCode.isBlank()) return "";
        return "https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/"
                + predictedWinnerCountryCode.toLowerCase() + ".svg";
    }
}
```

- [ ] **Step 3: Commit**
```bash
git add src/main/java/com/worldcup/prediction/dto/LeaderboardEntryDto.java
git commit -m "feat: add LeaderboardEntryDto with rank, points, flag, initials helpers"
```

---

### Task 2: LeaderboardService

**Files:**
- Create: `src/main/java/com/worldcup/prediction/service/LeaderboardService.java`
- Create: `src/test/java/com/worldcup/prediction/service/LeaderboardServiceTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.entity.*;
import com.worldcup.prediction.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock UserRepository userRepository;
    @Mock PredictionRepository predictionRepository;
    @Mock TournamentWinnerPredictionRepository twpRepository;

    @InjectMocks LeaderboardService leaderboardService;

    // ---- helpers ----

    private User makeUser(Long id, String name, Instant registeredAt) {
        User u = new User();
        u.setId(id);
        u.setDisplayName(name);
        u.setAvatarUrl("https://avatar.example.com/" + id);
        u.setRegisteredAt(registeredAt);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private Prediction makePrediction(User user, int points, PredictionOutcome outcome) {
        Prediction p = new Prediction();
        p.setUser(user);
        p.setPoints(points);
        p.setOutcome(outcome);
        return p;
    }

    private TournamentWinnerPrediction makeTwp(User user, String countryCode, boolean scored) {
        Team team = new Team();
        team.setCountryCode(countryCode);
        TournamentWinnerPrediction twp = new TournamentWinnerPrediction();
        twp.setUser(user);
        twp.setPredictedWinner(team);
        twp.setScored(scored);
        return twp;
    }

    // ---- tests ----

    @Test
    void getFullLeaderboard_sortsByTotalPointsDescending() {
        User alice = makeUser(1L, "Alice", Instant.parse("2026-01-01T00:00:00Z"));
        User bob   = makeUser(2L, "Bob",   Instant.parse("2026-01-02T00:00:00Z"));

        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(alice, bob));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of(
            makePrediction(alice, 3, PredictionOutcome.EXACT),
            makePrediction(alice, 1, PredictionOutcome.CORRECT_WINNER)
        ));
        when(predictionRepository.findByUser(bob)).thenReturn(List.of(
            makePrediction(bob, 3, PredictionOutcome.EXACT)
        ));
        when(twpRepository.findByUser(alice)).thenReturn(Optional.empty());
        when(twpRepository.findByUser(bob)).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserId()).isEqualTo(1L);  // Alice has 4 pts
        assertThat(result.get(0).getTotalPoints()).isEqualTo(4);
        assertThat(result.get(1).getUserId()).isEqualTo(2L);  // Bob has 3 pts
        assertThat(result.get(0).getRank()).isEqualTo(1);
        assertThat(result.get(1).getRank()).isEqualTo(2);
    }

    @Test
    void getFullLeaderboard_tiebreaker_exactCountWins() {
        User alice = makeUser(1L, "Alice", Instant.parse("2026-01-01T00:00:00Z"));
        User bob   = makeUser(2L, "Bob",   Instant.parse("2026-01-02T00:00:00Z"));

        // Both have 6 points total — Alice has 2 exact (+3 each), Bob has 3 correct_winner (+1 each) + 1 draw (+2) + 1 exact (+3)
        // Alice: 2 exact = 6 pts, 2 exact scores
        // Bob:   3 + 2 + 1 = 6 pts, 1 exact score
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(bob, alice)); // bob first in repo
        when(predictionRepository.findByUser(alice)).thenReturn(List.of(
            makePrediction(alice, 3, PredictionOutcome.EXACT),
            makePrediction(alice, 3, PredictionOutcome.EXACT)
        ));
        when(predictionRepository.findByUser(bob)).thenReturn(List.of(
            makePrediction(bob, 3, PredictionOutcome.EXACT),
            makePrediction(bob, 2, PredictionOutcome.CORRECT_DRAW),
            makePrediction(bob, 1, PredictionOutcome.CORRECT_WINNER)
        ));
        when(twpRepository.findByUser(any())).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        // Alice wins on tiebreaker: more exact scores (2 vs 1)
        assertThat(result.get(0).getUserId()).isEqualTo(1L);
        assertThat(result.get(0).getExactCount()).isEqualTo(2);
        assertThat(result.get(1).getExactCount()).isEqualTo(1);
    }

    @Test
    void getFullLeaderboard_tiebreaker_correctWinnerCountWins() {
        User alice = makeUser(1L, "Alice", Instant.parse("2026-01-01T00:00:00Z"));
        User bob   = makeUser(2L, "Bob",   Instant.parse("2026-01-02T00:00:00Z"));

        // Both 4 pts, both 1 exact — Alice has 1 correct winner, Bob has none
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(bob, alice));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of(
            makePrediction(alice, 3, PredictionOutcome.EXACT),
            makePrediction(alice, 1, PredictionOutcome.CORRECT_WINNER)
        ));
        when(predictionRepository.findByUser(bob)).thenReturn(List.of(
            makePrediction(bob, 3, PredictionOutcome.EXACT),
            makePrediction(bob, 1, PredictionOutcome.CORRECT_WINNER)
            // same correct winner count — fall through to next tiebreaker
        ));
        // Make alice have more correct winner by tweaking: alice gets 2 correct winners
        when(predictionRepository.findByUser(alice)).thenReturn(List.of(
            makePrediction(alice, 3, PredictionOutcome.EXACT),
            makePrediction(alice, 1, PredictionOutcome.CORRECT_WINNER),
            makePrediction(alice, 1, PredictionOutcome.CORRECT_WINNER)
        ));
        when(predictionRepository.findByUser(bob)).thenReturn(List.of(
            makePrediction(bob, 3, PredictionOutcome.EXACT),
            makePrediction(bob, 2, PredictionOutcome.CORRECT_DRAW)
        ));
        when(twpRepository.findByUser(any())).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        // Alice: 5pts, 1 exact, 2 correct winner
        // Bob:   5pts, 1 exact, 0 correct winner — Alice wins tiebreaker
        assertThat(result.get(0).getUserId()).isEqualTo(1L);
        assertThat(result.get(0).getCorrectWinnerCount()).isEqualTo(2);
        assertThat(result.get(1).getCorrectWinnerCount()).isEqualTo(0);
    }

    @Test
    void getFullLeaderboard_tiebreaker_tournamentWinnerBonusWins() {
        User alice = makeUser(1L, "Alice", Instant.parse("2026-01-01T00:00:00Z"));
        User bob   = makeUser(2L, "Bob",   Instant.parse("2026-01-02T00:00:00Z"));

        // Both 3 pts, 1 exact, 0 winner — Alice predicted tournament winner correctly
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(bob, alice));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of(
            makePrediction(alice, 3, PredictionOutcome.EXACT)
        ));
        when(predictionRepository.findByUser(bob)).thenReturn(List.of(
            makePrediction(bob, 3, PredictionOutcome.EXACT)
        ));
        when(twpRepository.findByUser(alice)).thenReturn(Optional.of(makeTwp(alice, "br", true)));
        when(twpRepository.findByUser(bob)).thenReturn(Optional.of(makeTwp(bob, "fr", false)));

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        // Note: tournament winner bonus points (+10) are already in Prediction.points per spec.
        // tournamentWinnerCorrect is used as *tiebreaker flag* only — the +10 is in totalPoints already.
        // Since totalPoints differ (alice=13 with +10 bonus, bob=3), alice wins on totalPoints.
        // This test verifies the flag is recorded correctly.
        assertThat(result.get(0).isTournamentWinnerCorrect()).isTrue();
        assertThat(result.get(1).isTournamentWinnerCorrect()).isFalse();
    }

    @Test
    void getFullLeaderboard_tiebreaker_earliestRegistrationWins() {
        User alice = makeUser(1L, "Alice", Instant.parse("2026-01-01T00:00:00Z")); // earlier
        User bob   = makeUser(2L, "Bob",   Instant.parse("2026-01-10T00:00:00Z")); // later

        // Identical everything — fall to registration date
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(bob, alice));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of(
            makePrediction(alice, 3, PredictionOutcome.EXACT)
        ));
        when(predictionRepository.findByUser(bob)).thenReturn(List.of(
            makePrediction(bob, 3, PredictionOutcome.EXACT)
        ));
        when(twpRepository.findByUser(alice)).thenReturn(Optional.of(makeTwp(alice, "br", true)));
        when(twpRepository.findByUser(bob)).thenReturn(Optional.of(makeTwp(bob, "br", true)));

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        assertThat(result.get(0).getUserId()).isEqualTo(1L); // alice registered earlier
    }

    @Test
    void getFullLeaderboard_excludesPendingAndDisabledUsers() {
        User active  = makeUser(1L, "Active",  Instant.now());
        User pending = makeUser(2L, "Pending", Instant.now());
        pending.setStatus(UserStatus.PENDING);

        // Repository mock only returns ACTIVE users (matching the query)
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(active));
        when(predictionRepository.findByUser(active)).thenReturn(List.of());
        when(twpRepository.findByUser(active)).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(1L);
        verify(predictionRepository, never()).findByUser(pending);
    }

    @Test
    void getFullLeaderboard_userWithNoTwp_flagUrlIsEmpty() {
        User alice = makeUser(1L, "Alice", Instant.now());
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(alice));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of());
        when(twpRepository.findByUser(alice)).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        assertThat(result.get(0).getPredictedWinnerCountryCode()).isNull();
        assertThat(result.get(0).getFlagUrl()).isEmpty();
    }

    @Test
    void getTopN_returnsFirstNEntries() {
        // 5 active users each with 1 prediction
        List<User> users = List.of(
            makeUser(1L, "A", Instant.parse("2026-01-01T00:00:00Z")),
            makeUser(2L, "B", Instant.parse("2026-01-02T00:00:00Z")),
            makeUser(3L, "C", Instant.parse("2026-01-03T00:00:00Z")),
            makeUser(4L, "D", Instant.parse("2026-01-04T00:00:00Z")),
            makeUser(5L, "E", Instant.parse("2026-01-05T00:00:00Z"))
        );
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(users);
        users.forEach(u -> {
            when(predictionRepository.findByUser(u)).thenReturn(List.of(
                makePrediction(u, 3, PredictionOutcome.EXACT)
            ));
            when(twpRepository.findByUser(u)).thenReturn(Optional.empty());
        });

        List<LeaderboardEntryDto> top3 = leaderboardService.getTopN(3);

        assertThat(top3).hasSize(3);
        assertThat(top3.get(0).getRank()).isEqualTo(1);
        assertThat(top3.get(2).getRank()).isEqualTo(3);
    }

    @Test
    void getTopN_whenFewerThanNUsers_returnsAll() {
        User alice = makeUser(1L, "Alice", Instant.now());
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(alice));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of());
        when(twpRepository.findByUser(alice)).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> result = leaderboardService.getTopN(10);

        assertThat(result).hasSize(1);
    }

    @Test
    void getEntryForUser_returnsCorrectRankAndData() {
        User alice = makeUser(1L, "Alice", Instant.parse("2026-01-01T00:00:00Z"));
        User bob   = makeUser(2L, "Bob",   Instant.parse("2026-01-02T00:00:00Z"));

        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(alice, bob));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of(
            makePrediction(alice, 3, PredictionOutcome.EXACT)
        ));
        when(predictionRepository.findByUser(bob)).thenReturn(List.of(
            makePrediction(bob, 1, PredictionOutcome.CORRECT_WINNER)
        ));
        when(twpRepository.findByUser(any())).thenReturn(Optional.empty());

        Optional<LeaderboardEntryDto> entry = leaderboardService.getEntryForUser(2L);

        assertThat(entry).isPresent();
        assertThat(entry.get().getRank()).isEqualTo(2);
        assertThat(entry.get().getTotalPoints()).isEqualTo(1);
    }

    @Test
    void getEntryForUser_returnsEmptyForUnknownUser() {
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of());

        Optional<LeaderboardEntryDto> entry = leaderboardService.getEntryForUser(999L);

        assertThat(entry).isEmpty();
    }

    @Test
    void getFullLeaderboard_countsOutcomesCorrectly() {
        User alice = makeUser(1L, "Alice", Instant.now());
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(alice));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of(
            makePrediction(alice, 3, PredictionOutcome.EXACT),
            makePrediction(alice, 3, PredictionOutcome.EXACT),
            makePrediction(alice, 2, PredictionOutcome.CORRECT_DRAW),
            makePrediction(alice, 1, PredictionOutcome.CORRECT_WINNER),
            makePrediction(alice, 0, PredictionOutcome.WRONG),
            makePrediction(alice, 0, PredictionOutcome.PENDING)
        ));
        when(twpRepository.findByUser(alice)).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        assertThat(result.get(0).getTotalPoints()).isEqualTo(9);
        assertThat(result.get(0).getExactCount()).isEqualTo(2);
        assertThat(result.get(0).getDrawCount()).isEqualTo(1);
        assertThat(result.get(0).getCorrectWinnerCount()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run — verify FAIL**

```bash
./mvnw test -Dtest=LeaderboardServiceTest -pl . 2>&1 | tail -30
```

Expected: compilation failure (LeaderboardService class does not exist yet).

- [ ] **Step 3: Implement LeaderboardService**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.entity.*;
import com.worldcup.prediction.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Computes the leaderboard fresh on each request.
 * No caching in v1 — sufficient for 100-200 users.
 *
 * Tiebreaker chain (spec §5):
 *   1. totalPoints DESC
 *   2. exactCount DESC
 *   3. correctWinnerCount DESC
 *   4. tournamentWinnerCorrect DESC (true > false)
 *   5. registeredAt ASC (earliest first)
 */
@Service
@Transactional(readOnly = true)
public class LeaderboardService {

    private final UserRepository userRepository;
    private final PredictionRepository predictionRepository;
    private final TournamentWinnerPredictionRepository twpRepository;

    public LeaderboardService(
            UserRepository userRepository,
            PredictionRepository predictionRepository,
            TournamentWinnerPredictionRepository twpRepository) {
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;
        this.twpRepository = twpRepository;
    }

    /**
     * Returns the full leaderboard sorted by the 5-level tiebreaker chain.
     * Only ACTIVE users are included.
     */
    public List<LeaderboardEntryDto> getFullLeaderboard() {
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);

        // Build unsorted entries first
        List<LeaderboardEntryDto> unsorted = activeUsers.stream()
                .map(this::buildEntry)
                .sorted(leaderboardComparator())
                .toList();

        // Assign ranks (1-based, tied users get the same rank in v1 — simple sequential)
        return assignRanks(unsorted);
    }

    /**
     * Returns the top N entries. Useful for the home page mini-widget.
     */
    public List<LeaderboardEntryDto> getTopN(int n) {
        List<LeaderboardEntryDto> full = getFullLeaderboard();
        return full.subList(0, Math.min(n, full.size()));
    }

    /**
     * Returns the leaderboard entry for a specific user, with their current rank.
     * Returns empty if the user is not on the leaderboard (not ACTIVE).
     */
    public Optional<LeaderboardEntryDto> getEntryForUser(Long userId) {
        List<LeaderboardEntryDto> full = getFullLeaderboard();
        return full.stream()
                .filter(e -> e.getUserId().equals(userId))
                .findFirst();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private LeaderboardEntryDto buildEntry(User user) {
        List<Prediction> predictions = predictionRepository.findByUser(user);

        int totalPoints = predictions.stream().mapToInt(Prediction::getPoints).sum();
        int exactCount = (int) predictions.stream()
                .filter(p -> PredictionOutcome.EXACT == p.getOutcome()).count();
        int correctWinnerCount = (int) predictions.stream()
                .filter(p -> PredictionOutcome.CORRECT_WINNER == p.getOutcome()).count();
        int drawCount = (int) predictions.stream()
                .filter(p -> PredictionOutcome.CORRECT_DRAW == p.getOutcome()).count();

        Optional<TournamentWinnerPrediction> twp = twpRepository.findByUser(user);
        boolean tournamentWinnerCorrect = twp.map(TournamentWinnerPrediction::isScored).orElse(false);
        String predictedWinnerCountryCode = twp
                .map(t -> t.getPredictedWinner().getCountryCode())
                .orElse(null);

        // rank is assigned later by assignRanks(); pass 0 as placeholder
        return new LeaderboardEntryDto(
                0,
                user.getId(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                predictedWinnerCountryCode,
                totalPoints,
                exactCount,
                correctWinnerCount,
                drawCount,
                tournamentWinnerCorrect,
                0  // rankChange: always 0 in v1 (no snapshot storage)
        );
    }

    private Comparator<LeaderboardEntryDto> leaderboardComparator() {
        return Comparator
                .comparingInt(LeaderboardEntryDto::getTotalPoints).reversed()
                .thenComparingInt(LeaderboardEntryDto::getExactCount).reversed()
                .thenComparingInt(LeaderboardEntryDto::getCorrectWinnerCount).reversed()
                .thenComparing(
                        e -> e.isTournamentWinnerCorrect() ? 0 : 1  // true (0) sorts before false (1)
                )
                .thenComparingLong(e -> {
                    // Re-fetch user for registeredAt — needed for final tiebreaker only
                    // This is a minor N+1 only triggered on actual ties; acceptable for v1.
                    return userRepository.findById(e.getUserId())
                            .map(u -> u.getRegisteredAt().toEpochMilli())
                            .orElse(Long.MAX_VALUE);
                });
    }

    /**
     * Re-builds the list with correct sequential ranks (1, 2, 3, …).
     * In v1 tied users receive different sequential ranks (first-sorted wins).
     */
    private List<LeaderboardEntryDto> assignRanks(List<LeaderboardEntryDto> sorted) {
        java.util.List<LeaderboardEntryDto> ranked = new java.util.ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            LeaderboardEntryDto e = sorted.get(i);
            ranked.add(new LeaderboardEntryDto(
                    i + 1,
                    e.getUserId(),
                    e.getDisplayName(),
                    e.getAvatarUrl(),
                    e.getPredictedWinnerCountryCode(),
                    e.getTotalPoints(),
                    e.getExactCount(),
                    e.getCorrectWinnerCount(),
                    e.getDrawCount(),
                    e.isTournamentWinnerCorrect(),
                    e.getRankChange()
            ));
        }
        return ranked;
    }
}
```

**Important note on comparator:** The `leaderboardComparator()` chains `.reversed()` on individual comparators. In Java, `Comparator.comparingInt(...).reversed().thenComparingInt(...).reversed()` does **not** work as expected — the second `.reversed()` reverses the entire chain. Use explicit negative comparisons instead. Here is the corrected version:

```java
private Comparator<LeaderboardEntryDto> leaderboardComparator() {
    return Comparator
            .<LeaderboardEntryDto>comparingInt(e -> -e.getTotalPoints())
            .thenComparingInt(e -> -e.getExactCount())
            .thenComparingInt(e -> -e.getCorrectWinnerCount())
            .thenComparingInt(e -> e.isTournamentWinnerCorrect() ? 0 : 1)
            .thenComparingLong(e ->
                userRepository.findById(e.getUserId())
                    .map(u -> u.getRegisteredAt().toEpochMilli())
                    .orElse(Long.MAX_VALUE)
            );
}
```

Use the corrected version in the actual implementation file.

- [ ] **Step 4: Run — verify PASS**

```bash
./mvnw test -Dtest=LeaderboardServiceTest -pl . 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`, all tests green.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/worldcup/prediction/service/LeaderboardService.java \
        src/test/java/com/worldcup/prediction/service/LeaderboardServiceTest.java
git commit -m "feat: add LeaderboardService with full tiebreaker chain and unit tests"
```

---

### Task 3: LeaderboardController

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/LeaderboardController.java`

- [ ] **Step 1: Write failing test**

```java
package com.worldcup.prediction.controller;

import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.entity.MatchPhase;
import com.worldcup.prediction.service.LeaderboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LeaderboardController.class)
class LeaderboardControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean LeaderboardService leaderboardService;

    @Test
    void getLeaderboard_returnsOkAndPopulatesModel() throws Exception {
        LeaderboardEntryDto entry = new LeaderboardEntryDto(
            1, 42L, "Alice Smith", "https://avatar.example.com/42",
            "br", 45, 5, 8, 2, true, 0
        );
        when(leaderboardService.getFullLeaderboard()).thenReturn(List.of(entry));

        mockMvc.perform(get("/leaderboard"))
               .andExpect(status().isOk())
               .andExpect(view().name("leaderboard"))
               .andExpect(model().attributeExists("entries"))
               .andExpect(model().attributeExists("phases"))
               .andExpect(model().attribute("totalParticipants", 1));
    }

    @Test
    void getLeaderboard_isPublicNoAuthRequired() throws Exception {
        when(leaderboardService.getFullLeaderboard()).thenReturn(List.of());

        // Should return 200, not 401/403
        mockMvc.perform(get("/leaderboard"))
               .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run — verify FAIL**

```bash
./mvnw test -Dtest=LeaderboardControllerTest -pl . 2>&1 | tail -20
```

- [ ] **Step 3: Implement**

```java
package com.worldcup.prediction.controller;

import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.entity.MatchPhase;
import com.worldcup.prediction.service.LeaderboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Public leaderboard page — accessible to guests and participants alike.
 * No authentication required (enforced in SecurityConfig: permitAll for /leaderboard).
 */
@Controller
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * GET /leaderboard
     *
     * Model attributes:
     *   entries           — List<LeaderboardEntryDto> sorted by rank
     *   phases            — ordered list of MatchPhase values for the phase header row
     *   totalParticipants — int, count of active participants
     */
    @GetMapping("/leaderboard")
    public String leaderboard(Model model) {
        List<LeaderboardEntryDto> entries = leaderboardService.getFullLeaderboard();

        model.addAttribute("entries", entries);
        model.addAttribute("phases", MatchPhase.values());
        model.addAttribute("totalParticipants", entries.size());

        return "leaderboard";
    }
}
```

- [ ] **Step 4: Run — verify PASS**

```bash
./mvnw test -Dtest=LeaderboardControllerTest -pl . 2>&1 | tail -20
```

- [ ] **Step 5: Ensure /leaderboard is public in SecurityConfig**

In `SecurityConfig.java` (created in Part 1), add `/leaderboard` to the `permitAll` chain:

```java
.requestMatchers("/", "/leaderboard", "/fixtures", "/groups", "/bracket").permitAll()
```

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/worldcup/prediction/controller/LeaderboardController.java \
        src/test/java/com/worldcup/prediction/controller/LeaderboardControllerTest.java
git commit -m "feat: add LeaderboardController — GET /leaderboard, public access"
```

---

### Task 4: MatchPhase displayLabel helper

**Files:**
- Edit: `src/main/java/com/worldcup/prediction/entity/MatchPhase.java`

The Thymeleaf template needs human-readable phase labels and CSS gradient classes for the shimmer headers.

- [ ] **Step 1: No separate test** — covered by template rendering.

- [ ] **Step 2: Add display metadata to MatchPhase enum**

```java
package com.worldcup.prediction.entity;

/**
 * Tournament phases in chronological order.
 * Each phase carries display metadata for the leaderboard table header.
 */
public enum MatchPhase {

    GS_R1("GS R1",  "Group Stage Round 1", "phase-gs1",  1),
    GS_R2("GS R2",  "Group Stage Round 2", "phase-gs2",  2),
    GS_R3("GS R3",  "Group Stage Round 3", "phase-gs3",  3),
    R32  ("R32",    "Round of 32",          "phase-r32",  4),
    R16  ("R16",    "Round of 16",          "phase-r16",  5),
    QF   ("QF",     "Quarter-Finals",       "phase-qf",   6),
    SF   ("SF",     "Semi-Finals",          "phase-sf",   7),
    FINAL("Final",  "Final",                "phase-final",8);

    /** Short label shown in phase header button (e.g. "GS R1"). */
    private final String shortLabel;

    /** Long label shown in phase column header tooltip. */
    private final String longLabel;

    /** CSS class for phase header gradient + shimmer styling. */
    private final String cssClass;

    /** Display order (1-based). */
    private final int order;

    MatchPhase(String shortLabel, String longLabel, String cssClass, int order) {
        this.shortLabel = shortLabel;
        this.longLabel  = longLabel;
        this.cssClass   = cssClass;
        this.order      = order;
    }

    public String getShortLabel() { return shortLabel; }
    public String getLongLabel()  { return longLabel; }
    public String getCssClass()   { return cssClass; }
    public int    getOrder()      { return order; }
}
```

- [ ] **Step 3: Commit**
```bash
git add src/main/java/com/worldcup/prediction/entity/MatchPhase.java
git commit -m "feat: add display metadata to MatchPhase enum for leaderboard headers"
```

---

### Task 5: leaderboard.html — Full Thymeleaf Template

**Files:**
- Create: `src/main/resources/templates/leaderboard.html`

This is the full prediction breakdown page. Layout:
- Light green theme matching the brainstorm design (leaderboard-v9: `#f0fdf5` background, white cards, frosted glass nav)
- Navbar (shared fragment from Part 1's layout)
- Hero section with KPI stats for the current user
- 3-panel leaderboard grid: fixed player column | scrollable game columns (future, populated when match data is wired) | fixed total column
- Phase jump buttons row
- Full standings table (rank, avatar+flag, name, points, exact count, correct winner count, tournament winner flag)

**Note:** The "scrollable game columns" panel requires per-match prediction data per user, which is a significant data join. For v1 the center panel shows a simplified per-phase points breakdown. The full cell-per-match matrix can be added in a v2 enhancement once the data access pattern is confirmed.

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>Leaderboard — WC Predict 2026</title>
  <link rel="preconnect" href="https://fonts.googleapis.com"/>
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin=""/>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800;900&family=Bebas+Neue&display=swap"
        rel="stylesheet"/>
  <script src="https://cdn.tailwindcss.com"></script>
  <script src="https://unpkg.com/htmx.org@1.9.12"></script>
  <style>
    :root {
      --green:      #00c853;
      --green-dark: #006b2a;
      --green-mid:  #00c853;
      --orange:     #FF5722;
      --yellow:     #FFD600;
      --bg:         #050e08;
      --border:     rgba(0,200,83,0.18);
      --muted:      rgba(255,255,255,0.45);
      --gold:       #FFD600;
    }

    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    html, body {
      min-height: 100vh;
      background: var(--bg);
      color: white;
      font-family: 'Inter', sans-serif;
      overflow-x: hidden;
    }

    /* ── Background ── */
    .bg-base {
      position: fixed; inset: 0; z-index: 0; pointer-events: none;
      background:
        radial-gradient(ellipse 100% 40% at 50% 0%, rgba(0,160,60,0.16) 0%, transparent 70%),
        linear-gradient(180deg, #030a05 0%, #071a0d 50%, #030a05 100%);
    }
    .bg-stripes {
      position: fixed; inset: 0; z-index: 0; pointer-events: none;
      background-image: repeating-linear-gradient(
        180deg, rgba(0,200,80,0.035) 0px, rgba(0,200,80,0.035) 50px,
        transparent 50px, transparent 100px);
    }
    .orb {
      position: fixed; border-radius: 50%; filter: blur(100px);
      opacity: 0.16; pointer-events: none;
      animation: orbPulse 7s ease-in-out infinite alternate;
    }
    .orb-top  { width: 600px; height: 280px; background: #00c853; top: -100px; left: 50%; transform: translateX(-50%); z-index: 0; }
    .orb-br   { width: 320px; height: 320px; background: #FF5722; bottom: 10%; right: 5%; animation-delay: -3s; z-index: 0; }
    @keyframes orbPulse { from { opacity: 0.12; } to { opacity: 0.20; } }

    /* ── Navbar ── */
    .navbar {
      position: sticky; top: 0; z-index: 100;
      height: 64px;
      display: flex; align-items: center; justify-content: space-between;
      padding: 0 40px;
      background: rgba(3,10,5,0.92);
      backdrop-filter: blur(24px);
      border-bottom: 1px solid var(--border);
      animation: navIn 0.5s ease both;
    }
    @keyframes navIn { from { opacity:0; transform:translateY(-64px); } to { opacity:1; transform:translateY(0); } }
    .logo {
      display: flex; align-items: center; gap: 10px;
      font-family: 'Bebas Neue', sans-serif;
      font-size: 26px; letter-spacing: 3px; color: var(--green);
      text-decoration: none;
    }
    .logo em { color: var(--orange); font-style: normal; }
    .logo-ball {
      width: 36px; height: 36px; border-radius: 50%;
      background: linear-gradient(135deg, var(--green-mid), var(--green-dark));
      display: flex; align-items: center; justify-content: center;
      font-size: 20px;
      animation: spin 12s linear infinite;
      box-shadow: 0 0 18px rgba(0,200,83,0.35);
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    .nav-links { display: flex; gap: 2px; }
    .nav-link {
      padding: 8px 14px; border-radius: 8px;
      font-size: 13px; font-weight: 600; color: var(--muted);
      text-decoration: none; transition: all 0.2s;
    }
    .nav-link:hover { background: rgba(0,200,83,0.08); color: var(--green); }
    .nav-link.active { background: rgba(0,200,83,0.12); color: var(--green); border: 1px solid var(--border); }
    .nav-right { display: flex; align-items: center; gap: 12px; }
    .predict-btn {
      background: linear-gradient(135deg, var(--orange), #ff7043);
      color: white; border: none; border-radius: 10px;
      padding: 10px 20px; font-size: 13px; font-weight: 800;
      cursor: pointer; text-decoration: none;
      animation: btnGlow 2.5s ease-in-out infinite;
    }
    @keyframes btnGlow {
      0%,100% { box-shadow: 0 4px 14px rgba(255,87,34,0.4); }
      50%      { box-shadow: 0 4px 28px rgba(255,87,34,0.7); }
    }
    .user-chip {
      display: flex; align-items: center; gap: 8px;
      background: rgba(0,200,83,0.08); border: 1px solid var(--border);
      border-radius: 99px; padding: 4px 14px 4px 4px;
      font-size: 13px; font-weight: 600; color: var(--green);
    }
    .chip-av {
      width: 30px; height: 30px; border-radius: 50%;
      overflow: hidden;
      background: linear-gradient(135deg, var(--green-mid), var(--green-dark));
      display: flex; align-items: center; justify-content: center;
      font-size: 12px; font-weight: 800; color: white;
    }
    .chip-av img { width: 100%; height: 100%; object-fit: cover; }

    /* ── Main content ── */
    .main {
      position: relative; z-index: 1;
      max-width: 1040px; margin: 0 auto;
      padding: 44px 24px 100px;
    }

    /* ── Hero ── */
    .hero { margin-bottom: 32px; animation: fadeUp 0.6s ease 0.2s both; }
    @keyframes fadeUp { from { opacity:0; transform:translateY(20px); } to { opacity:1; transform:translateY(0); } }
    .hero-eye {
      font-size: 11px; font-weight: 700; letter-spacing: 3px;
      text-transform: uppercase; color: var(--green); margin-bottom: 8px;
    }
    .hero-title {
      font-family: 'Bebas Neue', sans-serif; font-size: 64px;
      line-height: 1; letter-spacing: 4px; color: white; margin-bottom: 8px;
    }
    .hero-title em { color: var(--green); font-style: normal; }
    .hero-sub { color: var(--muted); font-size: 14px; }
    .hero-sub b { color: rgba(255,255,255,0.85); }

    /* KPI strip */
    .kpi-row { display: flex; gap: 10px; margin-top: 18px; flex-wrap: wrap; }
    .kpi {
      background: rgba(255,255,255,0.04); border: 1px solid var(--border);
      border-radius: 14px; padding: 14px 20px; backdrop-filter: blur(12px);
      transition: border-color 0.2s; animation: fadeUp 0.5s ease both;
    }
    .kpi:hover { border-color: rgba(0,200,83,0.35); }
    .kpi:nth-child(1){animation-delay:.3s}
    .kpi:nth-child(2){animation-delay:.4s}
    .kpi:nth-child(3){animation-delay:.5s}
    .kpi:nth-child(4){animation-delay:.6s}
    .kpi-val { font-size: 28px; font-weight: 900; color: var(--green); line-height: 1; }
    .kpi-lbl { font-size: 10px; font-weight: 700; color: var(--muted); text-transform: uppercase; letter-spacing: 0.5px; margin-top: 3px; }

    /* ── Phase jump buttons ── */
    .phase-nav {
      display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 20px;
      animation: fadeUp 0.5s ease 0.35s both;
    }
    .phase-btn {
      padding: 7px 14px; border-radius: 8px; border: 1px solid var(--border);
      background: rgba(255,255,255,0.04);
      font-size: 12px; font-weight: 700; color: var(--muted);
      cursor: pointer; transition: all 0.2s; text-decoration: none;
    }
    .phase-btn:hover { background: rgba(0,200,83,0.1); color: var(--green); border-color: rgba(0,200,83,0.3); }

    /* ── Section header ── */
    .sec-hd {
      font-size: 11px; font-weight: 800; letter-spacing: 3px;
      text-transform: uppercase; color: var(--green); margin-bottom: 14px;
      display: flex; align-items: center; gap: 10px;
    }
    .sec-hd::after { content: ''; flex: 1; height: 1px; background: var(--border); }

    /* ── 3-panel leaderboard layout ── */
    .lb-outer {
      display: flex;
      background: rgba(8,22,12,0.88);
      border: 1px solid var(--border);
      border-radius: 20px; overflow: hidden;
      backdrop-filter: blur(20px);
      animation: fadeUp 0.6s ease 0.4s both;
      min-height: 200px;
    }

    /* Panel 1: fixed player column */
    .lb-players {
      flex-shrink: 0;
      width: 260px;
      border-right: 1px solid var(--border);
      display: flex; flex-direction: column;
    }
    .lb-players-head {
      height: 52px;
      display: flex; align-items: center;
      padding: 0 20px;
      background: rgba(0,200,83,0.07);
      border-bottom: 1px solid var(--border);
      font-size: 10px; font-weight: 700; letter-spacing: 1.5px;
      text-transform: uppercase; color: var(--muted);
      flex-shrink: 0;
    }

    /* Panel 2: scrollable phase/game columns — v1 shows phase totals */
    .lb-scroll-wrap {
      flex: 1;
      overflow-x: auto;
      display: flex; flex-direction: column;
      min-width: 0;
      scrollbar-width: thin;
      scrollbar-color: rgba(0,200,83,0.3) transparent;
    }
    .lb-scroll-wrap::-webkit-scrollbar { height: 6px; }
    .lb-scroll-wrap::-webkit-scrollbar-track { background: transparent; }
    .lb-scroll-wrap::-webkit-scrollbar-thumb { background: rgba(0,200,83,0.3); border-radius: 3px; }

    /* Phase header row */
    .phase-header-row {
      display: flex;
      height: 52px;
      flex-shrink: 0;
      border-bottom: 1px solid var(--border);
      background: rgba(0,200,83,0.07);
    }
    .phase-cell {
      min-width: 80px; flex-shrink: 0;
      display: flex; align-items: center; justify-content: center;
      font-size: 10px; font-weight: 800; letter-spacing: 1px;
      text-transform: uppercase;
      border-right: 1px solid var(--border);
      position: relative; overflow: hidden;
      cursor: pointer;
    }
    .phase-cell:last-child { border-right: none; }
    /* Shimmer animation */
    .phase-cell::after {
      content: '';
      position: absolute; inset: 0;
      background: linear-gradient(90deg, transparent 0%, rgba(255,255,255,0.15) 50%, transparent 100%);
      transform: translateX(-100%);
      animation: shimmer 3s ease infinite;
    }
    @keyframes shimmer { to { transform: translateX(200%); } }
    /* Phase-specific gradient colors */
    .phase-gs1  { background: linear-gradient(135deg, rgba(0,100,40,0.35), rgba(0,60,20,0.2)); color: #4ade80; }
    .phase-gs1::after  { animation-delay: 0s; }
    .phase-gs2  { background: linear-gradient(135deg, rgba(0,110,50,0.35), rgba(0,70,25,0.2)); color: #34d399; }
    .phase-gs2::after  { animation-delay: 0.4s; }
    .phase-gs3  { background: linear-gradient(135deg, rgba(0,120,60,0.35), rgba(0,80,30,0.2)); color: #2dd4bf; }
    .phase-gs3::after  { animation-delay: 0.8s; }
    .phase-r32  { background: linear-gradient(135deg, rgba(30,80,120,0.35), rgba(10,50,90,0.2)); color: #60a5fa; }
    .phase-r32::after  { animation-delay: 1.2s; }
    .phase-r16  { background: linear-gradient(135deg, rgba(80,40,120,0.35), rgba(50,20,90,0.2)); color: #a78bfa; }
    .phase-r16::after  { animation-delay: 1.6s; }
    .phase-qf   { background: linear-gradient(135deg, rgba(120,60,20,0.35), rgba(90,40,10,0.2)); color: #fb923c; }
    .phase-qf::after   { animation-delay: 2.0s; }
    .phase-sf   { background: linear-gradient(135deg, rgba(160,30,30,0.35), rgba(120,10,10,0.2)); color: #f87171; }
    .phase-sf::after   { animation-delay: 2.4s; }
    .phase-final{ background: linear-gradient(135deg, rgba(180,140,0,0.45), rgba(120,90,0,0.3)); color: #fbbf24; }
    .phase-final::after{ animation-delay: 2.8s; }

    /* Scrollable rows — phase points per participant */
    .lb-scroll-body { display: flex; flex-direction: column; }
    .phase-row {
      display: flex;
      height: 64px; flex-shrink: 0;
      border-bottom: 1px solid rgba(0,200,83,0.05);
      align-items: center;
    }
    .phase-row:last-child { border-bottom: none; }
    .phase-pts-cell {
      min-width: 80px; flex-shrink: 0;
      height: 100%;
      display: flex; align-items: center; justify-content: center;
      border-right: 1px solid rgba(0,200,83,0.06);
      font-size: 15px; font-weight: 700;
      color: rgba(255,255,255,0.7);
    }
    .phase-pts-cell:last-child { border-right: none; }
    .phase-pts-cell.cell-exact  { background: rgba(0,200,83,0.12); color: #4ade80; }
    .phase-pts-cell.cell-winner { background: rgba(255,214,0,0.08); color: #fbbf24; }
    .phase-pts-cell.cell-draw   { background: rgba(96,165,250,0.1); color: #60a5fa; }
    .phase-pts-cell.cell-zero   { color: rgba(255,255,255,0.25); }
    .phase-pts-cell.cell-pending{ color: rgba(255,255,255,0.15); }

    /* Panel 3: fixed total column */
    .lb-totals {
      flex-shrink: 0;
      width: 160px;
      border-left: 1px solid var(--border);
      display: flex; flex-direction: column;
    }
    .lb-totals-head {
      height: 52px;
      display: flex; align-items: center; justify-content: flex-end;
      padding: 0 20px;
      background: rgba(0,200,83,0.07);
      border-bottom: 1px solid var(--border);
      font-size: 10px; font-weight: 700; letter-spacing: 1.5px;
      text-transform: uppercase; color: var(--muted);
    }

    /* Player rows (shared height across all 3 panels) */
    .player-row {
      display: flex; align-items: center; gap: 12px;
      padding: 0 16px;
      height: 64px; flex-shrink: 0;
      border-bottom: 1px solid rgba(0,200,83,0.05);
      transition: background 0.2s;
      animation: rowIn 0.4s ease both;
      cursor: default;
    }
    .player-row:last-child { border-bottom: none; }
    .player-row:hover { background: rgba(0,200,83,0.05); }
    .player-row.rank-1 { background: linear-gradient(90deg, rgba(255,214,0,0.08), transparent); }
    .player-row.rank-2 { background: linear-gradient(90deg, rgba(176,186,200,0.06), transparent); }
    .player-row.rank-3 { background: linear-gradient(90deg, rgba(205,124,58,0.06), transparent); }
    .player-row.is-you { border-left: 3px solid var(--green); }
    @keyframes rowIn { from { opacity:0; transform:translateX(-12px); } to { opacity:1; transform:translateX(0); } }

    /* Rank badge */
    .rank-badge {
      width: 32px; height: 32px; border-radius: 9px; flex-shrink: 0;
      display: flex; align-items: center; justify-content: center;
      font-size: 13px; font-weight: 900;
    }
    .rank-1 .rank-badge { background: linear-gradient(135deg, #ffd600, #ff9800); color: #1a1a1a; box-shadow: 0 2px 10px rgba(255,214,0,0.45); }
    .rank-2 .rank-badge { background: linear-gradient(135deg, #b0bac8, #8090a8); color: white; }
    .rank-3 .rank-badge { background: linear-gradient(135deg, #cd7c3a, #a85a20); color: white; }
    .rank-other .rank-badge { background: rgba(255,255,255,0.07); color: var(--muted); }

    /* Rank change arrow */
    .rank-delta { font-size: 10px; font-weight: 800; line-height: 1.1; text-align: center; width: 20px; flex-shrink: 0; }
    .rank-delta.up  { color: var(--green); animation: arrowUp 1.3s ease infinite; }
    .rank-delta.dn  { color: #ef4444;      animation: arrowDn 1.3s ease infinite; }
    .rank-delta.eq  { color: var(--muted); }
    @keyframes arrowUp { 0%,100%{transform:translateY(0)} 50%{transform:translateY(-2px)} }
    @keyframes arrowDn { 0%,100%{transform:translateY(0)} 50%{transform:translateY(2px)} }

    /* Avatar */
    .av-wrap { position: relative; width: 40px; height: 40px; flex-shrink: 0; }
    .av-ring {
      position: absolute; inset: -3px; border-radius: 50%;
      animation: rotRing 4s linear infinite;
    }
    .rank-1 .av-ring { background: conic-gradient(#ffd600, #ff9800, #ffd600); }
    .rank-2 .av-ring { background: conic-gradient(#b0bac8, #8090a8, #b0bac8); }
    .rank-3 .av-ring { background: conic-gradient(#cd7c3a, #a85a20, #cd7c3a); }
    .rank-other .av-ring { display: none; }
    @keyframes rotRing { to { transform: rotate(360deg); } }
    .av {
      position: absolute; inset: 3px; border-radius: 50%;
      overflow: hidden;
      background: linear-gradient(135deg, var(--green-mid), var(--green-dark));
      display: flex; align-items: center; justify-content: center;
      font-size: 13px; font-weight: 900; color: white;
    }
    .rank-other .av { inset: 0; }
    .av img { width: 100%; height: 100%; object-fit: cover; }

    /* Flag badge (bottom-right of avatar) */
    .flag-badge {
      position: absolute; bottom: -2px; right: -2px;
      width: 18px; height: 18px; border-radius: 50%;
      overflow: hidden; border: 1.5px solid var(--bg);
      animation: flagWave3d 3.5s ease-in-out infinite;
      transform-style: preserve-3d;
    }
    .flag-badge img { width: 100%; height: 100%; object-fit: cover; }
    @keyframes flagWave3d {
      0%,100% { transform: perspective(60px) rotateY(-8deg); }
      50%      { transform: perspective(60px) rotateY(8deg); }
    }

    /* Player name */
    .p-name  { font-size: 14px; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 130px; }
    .p-meta  { font-size: 11px; color: var(--muted); margin-top: 1px; white-space: nowrap; }
    .you-lbl {
      display: inline-block;
      background: var(--green); color: #050e08;
      font-size: 8px; font-weight: 800; letter-spacing: 1px;
      padding: 2px 5px; border-radius: 4px; margin-left: 4px;
      vertical-align: middle;
    }

    /* Total column rows */
    .total-row {
      height: 64px; flex-shrink: 0;
      display: flex; flex-direction: column; align-items: flex-end; justify-content: center;
      padding: 0 20px;
      border-bottom: 1px solid rgba(0,200,83,0.05);
    }
    .total-row:last-child { border-bottom: none; }
    .pts-val {
      font-size: 24px; font-weight: 900; line-height: 1;
      font-variant-numeric: tabular-nums;
    }
    .rank-1 .pts-val { color: var(--gold); text-shadow: 0 0 14px rgba(255,214,0,0.4); }
    .rank-2 .pts-val { color: #d0d8e8; }
    .rank-3 .pts-val { color: #dda060; }
    .rank-other .pts-val { color: white; }
    .pts-lbl  { font-size: 10px; color: var(--muted); font-weight: 600; }
    .exact-badge {
      display: inline-flex; align-items: center; gap: 3px;
      background: rgba(0,200,83,0.1); color: var(--green);
      border: 1px solid rgba(0,200,83,0.2);
      border-radius: 6px; padding: 2px 8px;
      font-size: 11px; font-weight: 700; margin-top: 3px;
    }
  </style>
</head>
<body>

<!-- Background layers -->
<div class="bg-base" aria-hidden="true"></div>
<div class="bg-stripes" aria-hidden="true"></div>
<div class="orb orb-top" aria-hidden="true"></div>
<div class="orb orb-br" aria-hidden="true"></div>

<!-- Navbar -->
<nav class="navbar">
  <a href="/" class="logo">
    <div class="logo-ball">⚽</div>
    WC PREDICT&nbsp;<em>2026</em>
  </a>

  <div class="nav-links">
    <a href="/leaderboard" class="nav-link active">🏆 Leaderboard</a>
    <a href="/fixtures"    class="nav-link">📅 Fixtures</a>
    <a href="/groups"      class="nav-link">🌍 Groups</a>
    <a href="/bracket"     class="nav-link">🗂 Bracket</a>
    <a href="/predictions" class="nav-link" sec:authorize="isAuthenticated()">🎯 Predictions</a>
  </div>

  <div class="nav-right">
    <!-- Only shown when authenticated -->
    <a href="/predictions" class="predict-btn" sec:authorize="isAuthenticated()">🎯 PREDICT NOW</a>
    <div class="user-chip" sec:authorize="isAuthenticated()">
      <div class="chip-av">
        <img th:if="${#authentication != null and #authentication.principal.avatarUrl != null}"
             th:src="${#authentication.principal.avatarUrl}"
             th:alt="${#authentication.principal.displayName}"
             onerror="this.style.display='none'"/>
        <span th:text="${#authentication != null ? #authentication.principal.initials : '?'}">AH</span>
      </div>
      <span th:text="${#authentication != null ? #authentication.principal.displayName : ''}">User</span>
    </div>
    <a href="/login" class="predict-btn" sec:authorize="!isAuthenticated()">Sign In</a>
  </div>
</nav>

<!-- Main -->
<main class="main">

  <!-- Hero -->
  <div class="hero">
    <div class="hero-eye">⚽ FIFA World Cup 2026</div>
    <div class="hero-title">LEADER<em>BOARD</em></div>
    <div class="hero-sub">
      <b th:text="${totalParticipants} + ' participants'">42 participants</b>
      · Rankings update after each final whistle
    </div>

    <!-- KPI strip — only shown for authenticated users -->
    <div class="kpi-row" sec:authorize="isAuthenticated()" th:if="${currentUserEntry != null}">
      <div class="kpi">
        <div class="kpi-val" th:text="${currentUserEntry.totalPoints}">0</div>
        <div class="kpi-lbl">Your Points</div>
      </div>
      <div class="kpi">
        <div class="kpi-val" style="color:var(--orange)" th:text="'#' + ${currentUserEntry.rank}">-</div>
        <div class="kpi-lbl">Your Rank</div>
      </div>
      <div class="kpi">
        <div class="kpi-val" th:text="${currentUserEntry.exactCount}">0</div>
        <div class="kpi-lbl">Exact Scores</div>
      </div>
      <div class="kpi">
        <div class="kpi-val"
             th:text="${entries != null and !entries.isEmpty() ? entries[0].totalPoints - currentUserEntry.totalPoints : 0}">0</div>
        <div class="kpi-lbl">Pts to #1</div>
      </div>
    </div>
  </div>

  <!-- Phase jump buttons -->
  <div class="phase-nav">
    <span th:each="phase : ${phases}">
      <a th:href="'#phase-' + ${phase.name().toLowerCase()}"
         th:text="${phase.shortLabel}"
         th:class="'phase-btn ' + ${phase.cssClass}"
         class="phase-btn">GS R1</a>
    </span>
  </div>

  <!-- Section header -->
  <div class="sec-hd">
    🏆 Full Standings —
    <span th:text="${totalParticipants} + ' participants'">0 participants</span>
  </div>

  <!-- 3-panel leaderboard -->
  <div class="lb-outer" id="leaderboard-table">

    <!-- Panel 1: Fixed player column -->
    <div class="lb-players">
      <div class="lb-players-head">Participant</div>

      <th:block th:each="entry, iter : ${entries}">
        <div th:class="'player-row ' + ${entry.rankCssClass} + (${#authentication != null and #authentication.principal.id == entry.userId} ? ' is-you' : '')"
             th:style="'animation-delay:' + (${iter.index} * 0.04) + 's'">

          <!-- Rank badge -->
          <div th:class="'rank-badge'">
            <span th:text="${entry.rank}">1</span>
          </div>

          <!-- Rank change arrow -->
          <div th:class="'rank-delta ' + (${entry.rankChange > 0} ? 'up' : (${entry.rankChange < 0} ? 'dn' : 'eq'))">
            <span th:if="${entry.rankChange > 0}">▲</span>
            <span th:if="${entry.rankChange < 0}">▼</span>
            <span th:if="${entry.rankChange == 0}">—</span>
          </div>

          <!-- Avatar + flag badge -->
          <div class="av-wrap">
            <div class="av-ring"></div>
            <div class="av">
              <img th:if="${entry.avatarUrl != null}"
                   th:src="${entry.avatarUrl}"
                   th:alt="${entry.displayName}"
                   onerror="this.style.display='none'"/>
              <span th:if="${entry.avatarUrl == null}" th:text="${entry.initials}">AB</span>
            </div>
            <!-- Flag badge: predicted WC winner country -->
            <div class="flag-badge" th:if="${entry.predictedWinnerCountryCode != null}">
              <img th:src="${entry.flagUrl}"
                   th:alt="${entry.predictedWinnerCountryCode}"
                   onerror="this.src='https://flagcdn.com/w80/' + /*[[${entry.predictedWinnerCountryCode}]]*/ 'xx' + '.png'"/>
            </div>
          </div>

          <!-- Name + you label -->
          <div>
            <div class="p-name">
              <span th:text="${entry.displayName}">Player Name</span>
              <span class="you-lbl"
                    th:if="${#authentication != null and #authentication.principal.id == entry.userId}">YOU</span>
            </div>
            <div class="p-meta">
              <span th:text="${entry.exactCount} + ' exact · ' + ${entry.correctWinnerCount} + ' winner'">
                0 exact · 0 winner
              </span>
            </div>
          </div>
        </div>
      </th:block>
    </div>

    <!-- Panel 2: Scrollable phase columns -->
    <div class="lb-scroll-wrap" id="phase-scroll">

      <!-- Phase header row with shimmer -->
      <div class="phase-header-row">
        <div th:each="phase : ${phases}"
             th:id="'phase-' + ${phase.name().toLowerCase()}"
             th:class="'phase-cell ' + ${phase.cssClass}"
             th:text="${phase.shortLabel}">GS R1</div>
      </div>

      <!-- Per-participant phase point rows (v1: phase total points) -->
      <div class="lb-scroll-body">
        <div class="phase-row" th:each="entry : ${entries}">
          <!--
            v1 note: Phase breakdown per user requires a separate query:
            PredictionRepository.sumPointsByUserAndPhase(userId, phase).
            This is wired in the controller upgrade (see Task 6).
            For now, each cell shows the total phase contribution from
            the phasePoints map injected by the controller.
          -->
          <div th:each="phase : ${phases}"
               th:with="pts=${phasePoints != null ? phasePoints.get(entry.userId)?.get(phase) : null}"
               th:class="'phase-pts-cell ' + (${pts == null} ? 'cell-pending' : (${pts == 3} ? 'cell-exact' : (${pts == 2} ? 'cell-draw' : (${pts == 1} ? 'cell-winner' : 'cell-zero'))))">
            <span th:text="${pts != null ? pts : '·'}">·</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Panel 3: Fixed total column -->
    <div class="lb-totals">
      <div class="lb-totals-head">Total</div>

      <div th:each="entry : ${entries}"
           th:class="'total-row ' + ${entry.rankCssClass}">
        <div th:class="'pts-val'" th:text="${entry.totalPoints}">0</div>
        <div class="pts-lbl">pts</div>
        <div class="exact-badge">
          ⚽ <span th:text="${entry.exactCount}">0</span>
        </div>
      </div>
    </div>

  </div><!-- /lb-outer -->

</main>

<script>
  // Auto-scroll the phase panel to current phase on load
  // The controller sets data-current-phase on the scroll container
  document.addEventListener('DOMContentLoaded', () => {
    const scroll = document.getElementById('phase-scroll');
    if (!scroll) return;
    // Find first phase cell that has data-current="true" — set by controller via th:attr
    const currentCell = scroll.querySelector('.phase-cell[data-current="true"]');
    if (currentCell) {
      currentCell.scrollIntoView({ behavior: 'smooth', inline: 'start', block: 'nearest' });
    }
    // Phase jump button smooth scroll
    document.querySelectorAll('.phase-btn').forEach(btn => {
      btn.addEventListener('click', e => {
        e.preventDefault();
        const targetId = btn.getAttribute('href').substring(1);
        const target = document.getElementById(targetId);
        if (target) {
          target.scrollIntoView({ behavior: 'smooth', inline: 'start', block: 'nearest' });
        }
      });
    });
  });
</script>

</body>
</html>
```

- [ ] **Step 2: Commit**
```bash
git add src/main/resources/templates/leaderboard.html
git commit -m "feat: add leaderboard.html 3-panel Thymeleaf template with phase shimmer headers"
```

---

### Task 6: Controller upgrade — phase points map

For the phase cells in Panel 2 to show per-user per-phase point totals, the controller needs to compute a `Map<Long userId, Map<MatchPhase, Integer sumPoints>>`.

**Files:**
- Edit: `src/main/java/com/worldcup/prediction/controller/LeaderboardController.java`
- Edit: `src/main/java/com/worldcup/prediction/repository/PredictionRepository.java`

- [ ] **Step 1: Add JPQL query to PredictionRepository**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.entity.MatchPhase;
import com.worldcup.prediction.entity.Prediction;
import com.worldcup.prediction.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    List<Prediction> findByUser(User user);

    /**
     * Returns [userId, phase, sumPoints] for all ACTIVE users.
     * Used by the leaderboard controller to build the phase breakdown map.
     */
    @Query("""
        SELECT p.user.id, p.match.phase, SUM(p.points)
        FROM Prediction p
        WHERE p.user.status = com.worldcup.prediction.entity.UserStatus.ACTIVE
        GROUP BY p.user.id, p.match.phase
        """)
    List<Object[]> sumPointsByUserAndPhase();
}
```

- [ ] **Step 2: Update LeaderboardController to build phasePoints map**

```java
package com.worldcup.prediction.controller;

import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.entity.MatchPhase;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.service.LeaderboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;

@Controller
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final PredictionRepository predictionRepository;

    public LeaderboardController(
            LeaderboardService leaderboardService,
            PredictionRepository predictionRepository) {
        this.leaderboardService = leaderboardService;
        this.predictionRepository = predictionRepository;
    }

    /**
     * GET /leaderboard — public.
     *
     * Model attributes:
     *   entries           — List<LeaderboardEntryDto>
     *   phases            — MatchPhase[] in order
     *   totalParticipants — int
     *   phasePoints       — Map<Long userId, Map<MatchPhase, Integer sumPoints>>
     *   currentUserEntry  — LeaderboardEntryDto or null (for KPI strip)
     */
    @GetMapping("/leaderboard")
    public String leaderboard(
            Model model,
            org.springframework.security.core.Authentication authentication) {

        List<LeaderboardEntryDto> entries = leaderboardService.getFullLeaderboard();

        // Build phase points map: userId -> (phase -> sumPoints)
        Map<Long, Map<MatchPhase, Integer>> phasePoints = new HashMap<>();
        predictionRepository.sumPointsByUserAndPhase().forEach(row -> {
            Long userId   = (Long)    row[0];
            MatchPhase ph = (MatchPhase) row[1];
            int pts       = ((Number)  row[2]).intValue();
            phasePoints.computeIfAbsent(userId, k -> new EnumMap<>(MatchPhase.class)).put(ph, pts);
        });

        // Current user entry (for KPI strip)
        LeaderboardEntryDto currentUserEntry = null;
        if (authentication != null && authentication.isAuthenticated()) {
            try {
                // Principal is a UserDetails with getId() — adjust if using custom principal
                Object principal = authentication.getPrincipal();
                if (principal instanceof com.worldcup.prediction.security.AppUserDetails userDetails) {
                    currentUserEntry = leaderboardService.getEntryForUser(userDetails.getId()).orElse(null);
                }
            } catch (Exception ignored) { /* guest or non-user principal */ }
        }

        model.addAttribute("entries", entries);
        model.addAttribute("phases", MatchPhase.values());
        model.addAttribute("totalParticipants", entries.size());
        model.addAttribute("phasePoints", phasePoints);
        model.addAttribute("currentUserEntry", currentUserEntry);

        return "leaderboard";
    }
}
```

- [ ] **Step 3: Commit**
```bash
git add src/main/java/com/worldcup/prediction/controller/LeaderboardController.java \
        src/main/java/com/worldcup/prediction/repository/PredictionRepository.java
git commit -m "feat: add phase points aggregation query and KPI strip data to LeaderboardController"
```

---

### Task 7: Mini-leaderboard widget (th:fragment)

This widget is included on the home page via `th:replace`. It shows the top 10 in a card grid.

**Files:**
- Create: `src/main/resources/templates/fragments/leaderboard-mini-widget.html`

- [ ] **Step 1: Implement**

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity6">
<body>

<!--
  Mini leaderboard widget — included on the home page via:
    <div th:replace="~{fragments/leaderboard-mini-widget :: mini-leaderboard(entries=${top10})}"></div>

  Parameter:
    entries — List<LeaderboardEntryDto>, typically top 10
-->
<div th:fragment="mini-leaderboard(entries)">

  <style>
    /* Scoped styles for mini-widget */
    .mlb-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 10px;
    }
    @media (max-width: 640px) {
      .mlb-grid { grid-template-columns: 1fr; }
    }

    .mlb-card {
      display: flex; align-items: center; gap: 12px;
      padding: 12px 16px;
      background: rgba(255,255,255,0.04);
      border: 1px solid rgba(0,200,83,0.14);
      border-radius: 14px;
      transition: border-color 0.2s, background 0.2s;
      animation: rowIn 0.4s ease both;
      text-decoration: none; color: inherit;
    }
    .mlb-card:hover { background: rgba(0,200,83,0.06); border-color: rgba(0,200,83,0.3); }
    .mlb-card.rank-1 { background: linear-gradient(135deg, rgba(255,214,0,0.08), rgba(255,214,0,0.02)); border-color: rgba(255,214,0,0.25); }
    .mlb-card.rank-2 { background: linear-gradient(135deg, rgba(176,186,200,0.07), rgba(176,186,200,0.02)); border-color: rgba(176,186,200,0.2); }
    .mlb-card.rank-3 { background: linear-gradient(135deg, rgba(205,124,58,0.07), rgba(205,124,58,0.02)); border-color: rgba(205,124,58,0.2); }

    .mlb-rank {
      width: 30px; height: 30px; border-radius: 8px; flex-shrink: 0;
      display: flex; align-items: center; justify-content: center;
      font-size: 13px; font-weight: 900;
    }
    .rank-1 .mlb-rank { background: linear-gradient(135deg, #ffd600, #ff9800); color: #1a1a1a; box-shadow: 0 2px 8px rgba(255,214,0,0.4); }
    .rank-2 .mlb-rank { background: linear-gradient(135deg, #b0bac8, #8090a8); color: white; }
    .rank-3 .mlb-rank { background: linear-gradient(135deg, #cd7c3a, #a85a20); color: white; }
    .rank-other .mlb-rank { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.45); }

    .mlb-av-wrap { position: relative; width: 38px; height: 38px; flex-shrink: 0; }
    .mlb-av-ring {
      position: absolute; inset: -2px; border-radius: 50%;
      animation: rotRing 4s linear infinite;
    }
    .rank-1 .mlb-av-ring { background: conic-gradient(#ffd600, #ff9800, #ffd600); }
    .rank-2 .mlb-av-ring { background: conic-gradient(#b0bac8, #8090a8, #b0bac8); }
    .rank-3 .mlb-av-ring { background: conic-gradient(#cd7c3a, #a85a20, #cd7c3a); }
    .rank-other .mlb-av-ring { display: none; }
    .mlb-av {
      position: absolute; inset: 2px; border-radius: 50%; overflow: hidden;
      background: linear-gradient(135deg, #00c853, #006b2a);
      display: flex; align-items: center; justify-content: center;
      font-size: 12px; font-weight: 900; color: white;
    }
    .rank-other .mlb-av { inset: 0; }
    .mlb-av img { width: 100%; height: 100%; object-fit: cover; }

    .mlb-flag-badge {
      position: absolute; bottom: -2px; right: -2px;
      width: 16px; height: 16px; border-radius: 50%; overflow: hidden;
      border: 1.5px solid #050e08;
      animation: flagWave3d 3.5s ease-in-out infinite;
    }
    .mlb-flag-badge img { width: 100%; height: 100%; object-fit: cover; }

    .mlb-info { flex: 1; min-width: 0; }
    .mlb-name {
      font-size: 13px; font-weight: 700; color: white;
      white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
    }
    .mlb-pts-row { display: flex; align-items: center; gap: 6px; margin-top: 2px; }
    .mlb-pts  { font-size: 18px; font-weight: 900; line-height: 1; font-variant-numeric: tabular-nums; }
    .rank-1 .mlb-pts { color: #ffd600; text-shadow: 0 0 10px rgba(255,214,0,0.4); }
    .rank-2 .mlb-pts { color: #d0d8e8; }
    .rank-3 .mlb-pts { color: #dda060; }
    .rank-other .mlb-pts { color: white; }
    .mlb-pts-lbl { font-size: 10px; color: rgba(255,255,255,0.4); font-weight: 600; }
    .mlb-exact-badge {
      display: inline-flex; align-items: center; gap: 2px;
      background: rgba(0,200,83,0.1); color: #00c853;
      border: 1px solid rgba(0,200,83,0.2);
      border-radius: 5px; padding: 1px 6px;
      font-size: 10px; font-weight: 700;
    }

    .mlb-delta { font-size: 10px; font-weight: 800; flex-shrink: 0; }
    .mlb-delta.up { color: #00c853; animation: arrowUp 1.3s ease infinite; }
    .mlb-delta.dn { color: #ef4444; animation: arrowDn 1.3s ease infinite; }
    .mlb-delta.eq { color: rgba(255,255,255,0.3); }

    .mlb-see-all {
      display: block; text-align: center; margin-top: 16px;
      padding: 12px; border-radius: 12px;
      border: 1px solid rgba(0,200,83,0.18);
      background: rgba(0,200,83,0.05);
      color: #00c853; font-size: 13px; font-weight: 700;
      text-decoration: none; transition: all 0.2s;
    }
    .mlb-see-all:hover { background: rgba(0,200,83,0.1); border-color: rgba(0,200,83,0.35); }
  </style>

  <div class="mlb-grid">
    <div th:each="entry, iter : ${entries}"
         th:class="'mlb-card ' + ${entry.rankCssClass}"
         th:style="'animation-delay:' + (${iter.index} * 0.05) + 's'">

      <!-- Rank badge -->
      <div class="mlb-rank">
        <span th:text="${entry.rank}">1</span>
      </div>

      <!-- Avatar + flag -->
      <div class="mlb-av-wrap">
        <div class="mlb-av-ring"></div>
        <div class="mlb-av">
          <img th:if="${entry.avatarUrl != null}"
               th:src="${entry.avatarUrl}"
               th:alt="${entry.displayName}"
               onerror="this.style.display='none'"/>
          <span th:if="${entry.avatarUrl == null}" th:text="${entry.initials}">AB</span>
        </div>
        <div class="mlb-flag-badge" th:if="${entry.predictedWinnerCountryCode != null}">
          <img th:src="${entry.flagUrl}"
               th:alt="${entry.predictedWinnerCountryCode}"
               onerror="this.src='https://flagcdn.com/w80/' + /*[[${entry.predictedWinnerCountryCode}]]*/ 'xx' + '.png'"/>
        </div>
      </div>

      <!-- Name + points -->
      <div class="mlb-info">
        <div class="mlb-name" th:text="${entry.displayName}">Player Name</div>
        <div class="mlb-pts-row">
          <span class="mlb-pts" th:text="${entry.totalPoints}">0</span>
          <span class="mlb-pts-lbl">pts</span>
          <span class="mlb-exact-badge">⚽ <span th:text="${entry.exactCount}">0</span></span>
        </div>
      </div>

      <!-- Rank change arrow -->
      <div th:class="'mlb-delta ' + (${entry.rankChange > 0} ? 'up' : (${entry.rankChange < 0} ? 'dn' : 'eq'))">
        <span th:if="${entry.rankChange > 0}">▲</span>
        <span th:if="${entry.rankChange < 0}">▼</span>
        <span th:if="${entry.rankChange == 0}">—</span>
      </div>

    </div>
  </div>

  <a href="/leaderboard" class="mlb-see-all">View full leaderboard →</a>

</div>

</body>
</html>
```

**Usage on the home page (Part 5):**
```html
<!-- In home.html, pass the top10 model attribute from HomeController -->
<div th:replace="~{fragments/leaderboard-mini-widget :: mini-leaderboard(entries=${top10})}"></div>
```

**HomeController snippet (Part 5 will add this):**
```java
model.addAttribute("top10", leaderboardService.getTopN(10));
```

- [ ] **Step 2: Commit**
```bash
git add src/main/resources/templates/fragments/leaderboard-mini-widget.html
git commit -m "feat: add mini-leaderboard widget th:fragment for home page inclusion"
```

---

### Task 8: Integration smoke test (manual)

- [ ] **Step 1: Start the application**
```bash
./mvnw spring-boot:run
```

- [ ] **Step 2: Verify leaderboard page loads**
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/leaderboard
# Expected: 200
```

- [ ] **Step 3: Verify page contains expected markup**
```bash
curl -s http://localhost:8080/leaderboard | grep -c "lb-outer"
# Expected: 1 (the 3-panel container)
```

- [ ] **Step 4: Verify phase headers are rendered**
```bash
curl -s http://localhost:8080/leaderboard | grep "phase-cell" | wc -l
# Expected: 8 (one per MatchPhase)
```

- [ ] **Step 5: Run all tests**
```bash
./mvnw test 2>&1 | tail -10
# Expected: BUILD SUCCESS
```

- [ ] **Step 6: Final commit**
```bash
git add -A
git commit -m "feat: Part 4 leaderboard — service, controller, full page, mini widget complete"
```

---

## Summary of model attributes by template

| Template | Model attribute | Type | Source |
|---|---|---|---|
| `leaderboard.html` | `entries` | `List<LeaderboardEntryDto>` | `LeaderboardService.getFullLeaderboard()` |
| `leaderboard.html` | `phases` | `MatchPhase[]` | `MatchPhase.values()` |
| `leaderboard.html` | `totalParticipants` | `int` | `entries.size()` |
| `leaderboard.html` | `phasePoints` | `Map<Long, Map<MatchPhase, Integer>>` | `PredictionRepository.sumPointsByUserAndPhase()` |
| `leaderboard.html` | `currentUserEntry` | `LeaderboardEntryDto` or `null` | `LeaderboardService.getEntryForUser(userId)` |
| mini-widget fragment | `entries` (param) | `List<LeaderboardEntryDto>` | passed from home controller |

---

## Known limitations / v2 enhancements

1. **Rank change (`rankChange`)** is always 0 in v1. To implement true rank deltas, add a `LeaderboardSnapshot` entity (userId, rank, snapshotTime) and a scheduled job that writes a snapshot after each result is entered.

2. **Phase cell granularity**: The scrollable panel shows per-phase point totals per user, not individual match cells. Full cell-per-match would require `N_users × N_matches` cells and a more complex data model. Defer to v2.

3. **N+1 on registration date tiebreaker**: `leaderboardComparator()` calls `userRepository.findById()` per entry when sorting on the final tiebreaker. Mitigate in v2 by fetching `registeredAt` in the initial user fetch and storing it in `LeaderboardEntryDto`.

4. **Caching**: For 200 users × 104 matches, the full leaderboard computation is fast. If it becomes slow, add `@Cacheable("leaderboard")` on `getFullLeaderboard()` with a 30-second TTL and evict on result entry.
