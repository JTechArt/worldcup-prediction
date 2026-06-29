# Admin Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three admin improvements: fix the DB restore form submission bug, make the round prediction lock offset configurable, and show/create missing community predictions on the admin predictions page.

**Architecture:** All server-side Spring Boot changes. Round lock offset adds one DB column via Flyway V10 migration and threads the value from `TournamentSettings` through `RoundWindowService`. Missing predictions adds one repository query, two service methods, expands the admin prediction controller, and updates the template. Restore fix removes a broken Alpine.js submit pattern and raises multipart size limits.

**Tech Stack:** Spring Boot 3.3.5, Spring Data JPA, Thymeleaf, Alpine.js, Flyway, SQLite, JUnit 5 + Mockito + AssertJ (`@DataJpaTest` for repo tests, `@ExtendWith(MockitoExtension.class)` for service tests)

---

## File Map

| Action | File |
|--------|------|
| Modify | `src/main/resources/templates/admin/backup.html` |
| Modify | `src/main/resources/application.properties` |
| Create | `src/main/resources/db/migration/V10__round_lock_offset.sql` |
| Modify | `src/main/java/com/worldcup/prediction/domain/TournamentSettings.java` |
| Modify | `src/main/java/com/worldcup/prediction/service/TournamentSettingsService.java` |
| Modify | `src/main/java/com/worldcup/prediction/service/RoundWindowService.java` |
| Modify | `src/main/java/com/worldcup/prediction/controller/admin/AdminSettingsController.java` |
| Modify | `src/main/resources/templates/admin/settings.html` |
| Modify | `src/main/java/com/worldcup/prediction/repository/CommunityMembershipRepository.java` |
| Modify | `src/main/java/com/worldcup/prediction/repository/PredictionRepository.java` |
| Modify | `src/main/java/com/worldcup/prediction/service/PredictionService.java` |
| Modify | `src/main/java/com/worldcup/prediction/controller/admin/AdminPredictionController.java` |
| Modify | `src/main/resources/templates/admin/predictions.html` |
| Create | `src/test/java/com/worldcup/prediction/service/RoundWindowServiceTest.java` |
| Create | `src/test/java/com/worldcup/prediction/service/PredictionServiceCreateOnBehalfTest.java` |
| Create | `src/test/java/com/worldcup/prediction/repository/CommunityMembershipMissingPredictionTest.java` |

---

## Task 1: Fix DB Restore Form

**Files:**
- Modify: `src/main/resources/templates/admin/backup.html:75-77`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Remove broken Alpine submit interceptor from restore form**

In `backup.html`, locate the restore `<form>` tag (line 75). Change from:
```html
<form th:action="@{/admin/backup/restore}" method="post" enctype="multipart/form-data"
      x-data="{ filename: '', confirmed: false }"
      @submit.prevent="if(confirmed) $el.submit()">
```
To:
```html
<form th:action="@{/admin/backup/restore}" method="post" enctype="multipart/form-data"
      x-data="{ filename: '', confirmed: false }">
```
The button's `:disabled="!confirmed || !filename"` already gates submission. The `@submit.prevent` was calling `$el.submit()` programmatically, which drops the file input in some browsers — causing the upload to arrive empty and the server to redirect back with no change.

- [ ] **Step 2: Add multipart size limits to application.properties**

Append to `src/main/resources/application.properties`:
```properties
# Restore upload — SQLite databases can be tens of MB
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB
```

- [ ] **Step 3: Commit**
```bash
git add src/main/resources/templates/admin/backup.html \
        src/main/resources/application.properties
git commit -m "fix: restore DB form submits file correctly by removing @submit.prevent hack

Alpine's programmatic $el.submit() drops file inputs in some browsers.
The :disabled button gate is sufficient. Also raise multipart limit to 100MB."
```

---

## Task 2: Round Lock Offset — Migration, Entity, Settings Service

**Files:**
- Create: `src/main/resources/db/migration/V10__round_lock_offset.sql`
- Modify: `src/main/java/com/worldcup/prediction/domain/TournamentSettings.java`
- Modify: `src/main/java/com/worldcup/prediction/service/TournamentSettingsService.java`

- [ ] **Step 1: Write a failing service test**

Create `src/test/java/com/worldcup/prediction/service/RoundWindowServiceTest.java`:

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.TournamentSettings;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.domain.enums.WindowMode;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.RoundWindowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoundWindowServiceTest {

    @Mock RoundWindowRepository roundWindowRepository;
    @Mock MatchRepository matchRepository;
    @Mock TournamentSettingsService tournamentSettingsService;
    @InjectMocks RoundWindowService roundWindowService;

    private RoundWindow roundWindow;
    private LocalDateTime kickoffEarly;
    private LocalDateTime kickoffLate;

    @BeforeEach
    void setUp() {
        roundWindow = RoundWindow.builder().roundLabel("Matchday 1").build();
        kickoffEarly = LocalDateTime.of(2026, 6, 11, 16, 0);
        kickoffLate  = LocalDateTime.of(2026, 6, 11, 22, 0);

        when(roundWindowRepository.findByRoundLabel("Matchday 1"))
                .thenReturn(Optional.of(roundWindow));
        when(roundWindowRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void recalculateAutoTimes_usesFirstKickoffForCloseTime() {
        Match early = buildMatch(kickoffEarly);
        Match late  = buildMatch(kickoffLate);
        when(matchRepository.findByRoundLabelWithTeams("Matchday 1"))
                .thenReturn(List.of(early, late));

        TournamentSettings settings = TournamentSettings.builder()
                .id(1L).windowMode(WindowMode.ROUND)
                .dailyWindowCloseOffsetMinutes(30)
                .roundLockOffsetMinutes(60)
                .build();
        when(tournamentSettingsService.getSettings()).thenReturn(settings);

        roundWindowService.recalculateAutoTimes("Matchday 1");

        // Opens 24h before earliest kickoff
        assertThat(roundWindow.getAutoOpensAt())
                .isEqualTo(kickoffEarly.minusHours(24));
        // Closes based on FIRST kickoff minus configured offset (60 min)
        assertThat(roundWindow.getAutoClosesAt())
                .isEqualTo(kickoffEarly.minusMinutes(60));
    }

    @Test
    void recalculateAutoTimes_respectsCustomOffset() {
        Match only = buildMatch(kickoffEarly);
        when(matchRepository.findByRoundLabelWithTeams("Matchday 1"))
                .thenReturn(List.of(only));

        TournamentSettings settings = TournamentSettings.builder()
                .id(1L).windowMode(WindowMode.ROUND)
                .dailyWindowCloseOffsetMinutes(30)
                .roundLockOffsetMinutes(10)
                .build();
        when(tournamentSettingsService.getSettings()).thenReturn(settings);

        roundWindowService.recalculateAutoTimes("Matchday 1");

        assertThat(roundWindow.getAutoClosesAt())
                .isEqualTo(kickoffEarly.minusMinutes(10));
    }

    private Match buildMatch(LocalDateTime kickoff) {
        Match m = new Match();
        m.setKickoffTime(kickoff);
        m.setStage(MatchStage.GROUP);
        m.setStatus(MatchStatus.SCHEDULED);
        m.setMatchNumber(kickoff.getHour());
        return m;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
```bash
./mvnw test -pl . -Dtest=RoundWindowServiceTest -q 2>&1 | tail -20
```
Expected: FAIL — `RoundWindowService` has no `TournamentSettingsService` field and still uses `lastKickoff`.

- [ ] **Step 3: Create the Flyway migration**

Create `src/main/resources/db/migration/V10__round_lock_offset.sql`:
```sql
ALTER TABLE tournament_settings ADD COLUMN round_lock_offset_minutes INTEGER NOT NULL DEFAULT 60;
```

- [ ] **Step 4: Add field to TournamentSettings entity**

In `TournamentSettings.java`, add after `dailyWindowCloseOffsetMinutes`:
```java
@Column(name = "round_lock_offset_minutes", nullable = false)
private int roundLockOffsetMinutes;
```

- [ ] **Step 5: Update TournamentSettingsService — default and new method**

In `TournamentSettingsService.java`:

Update `getSettings()` default builder to include the new field:
```java
return settingsRepository.save(
        TournamentSettings.builder()
                .id(1L)
                .windowMode(WindowMode.ROUND)
                .dailyWindowCloseOffsetMinutes(30)
                .roundLockOffsetMinutes(60)
                .build());
```

Add new method after `updateCloseOffset`:
```java
@Transactional
public TournamentSettings updateRoundLockOffset(int minutes) {
    TournamentSettings s = getSettings();
    s.setRoundLockOffsetMinutes(minutes);
    return settingsRepository.save(s);
}
```

- [ ] **Step 6: Update RoundWindowService to inject settings and use firstKickoff**

In `RoundWindowService.java`:

Add field (Lombok `@RequiredArgsConstructor` will inject it):
```java
private final TournamentSettingsService tournamentSettingsService;
```

Replace `recalculateAutoTimes` body (remove `lastKickoff` variable, anchor close to firstKickoff):
```java
@Transactional
public void recalculateAutoTimes(String roundLabel) {
    RoundWindow rw = findOrThrow(roundLabel);
    var matches = matchRepository.findByRoundLabelWithTeams(roundLabel);
    if (matches.isEmpty()) return;
    LocalDateTime firstKickoff = matches.stream()
            .map(m -> m.getKickoffTime())
            .min(LocalDateTime::compareTo)
            .orElse(null);
    int lockOffsetMinutes = tournamentSettingsService.getSettings().getRoundLockOffsetMinutes();
    if (firstKickoff != null) rw.setAutoOpensAt(firstKickoff.minusHours(24));
    if (firstKickoff != null) rw.setAutoClosesAt(firstKickoff.minusMinutes(lockOffsetMinutes));
    roundWindowRepository.save(rw);
}
```

Add `recalculateAllRoundWindows` method after `recalculateAutoTimes`:
```java
@Transactional
public void recalculateAllRoundWindows() {
    roundWindowRepository.findAllOrderByAutoOpensAtAsc()
            .stream()
            .map(RoundWindow::getRoundLabel)
            .forEach(this::recalculateAutoTimes);
}
```

- [ ] **Step 7: Run test to verify it passes**
```bash
./mvnw test -pl . -Dtest=RoundWindowServiceTest -q 2>&1 | tail -10
```
Expected: BUILD SUCCESS, 2 tests passed.

- [ ] **Step 8: Commit**
```bash
git add src/main/resources/db/migration/V10__round_lock_offset.sql \
        src/main/java/com/worldcup/prediction/domain/TournamentSettings.java \
        src/main/java/com/worldcup/prediction/service/TournamentSettingsService.java \
        src/main/java/com/worldcup/prediction/service/RoundWindowService.java \
        src/test/java/com/worldcup/prediction/service/RoundWindowServiceTest.java
git commit -m "feat: make round lock offset configurable

Add round_lock_offset_minutes to TournamentSettings (default 60).
RoundWindowService now closes windows relative to first kickoff minus
the configured offset instead of last kickoff minus 1 hour."
```

---

## Task 3: Round Lock Offset — Admin Settings Controller & Template

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/controller/admin/AdminSettingsController.java`
- Modify: `src/main/resources/templates/admin/settings.html`

- [ ] **Step 1: Update AdminSettingsController**

Add `RoundWindowService` field (via `@RequiredArgsConstructor`):
```java
private final RoundWindowService roundWindowService;
```

Update `updateTournamentMode` to accept and persist the new param, then recalculate all windows:
```java
@PostMapping("/tournament-mode")
public String updateTournamentMode(@RequestParam WindowMode windowMode,
                                   @RequestParam int dailyWindowCloseOffsetMinutes,
                                   @RequestParam int roundLockOffsetMinutes,
                                   RedirectAttributes redirectAttributes) {
    tournamentSettingsService.updateMode(windowMode);
    tournamentSettingsService.updateCloseOffset(dailyWindowCloseOffsetMinutes);
    tournamentSettingsService.updateRoundLockOffset(roundLockOffsetMinutes);
    roundWindowService.recalculateAllRoundWindows();
    redirectAttributes.addFlashAttribute("successMessage", "Tournament window mode updated.");
    return "redirect:/admin/settings";
}
```

Also add the import for `RoundWindowService` at the top of the file:
```java
import com.worldcup.prediction.service.RoundWindowService;
```

- [ ] **Step 2: Add round lock offset field to settings template**

In `settings.html`, add after the `dailyWindowCloseOffsetMinutes` div (inside the tournament-mode form):
```html
<div class="flex items-center gap-3">
  <label class="text-sm text-gray-600">Round lock offset (minutes before first match kickoff):</label>
  <input type="number" name="roundLockOffsetMinutes" min="1" max="360"
         th:value="${tournamentSettings != null ? tournamentSettings.roundLockOffsetMinutes : 60}"
         class="border border-gray-300 rounded px-2 py-1 w-20 text-sm"/>
</div>
```

The full updated form block after both offset rows should look like:
```html
<div class="flex items-center gap-3">
  <label class="text-sm text-gray-600">Auto-close offset (minutes before first match):</label>
  <input type="number" name="dailyWindowCloseOffsetMinutes" min="0" max="180"
         th:value="${tournamentSettings != null ? tournamentSettings.dailyWindowCloseOffsetMinutes : 30}"
         class="border border-gray-300 rounded px-2 py-1 w-20 text-sm"/>
</div>
<div class="flex items-center gap-3">
  <label class="text-sm text-gray-600">Round lock offset (minutes before first match kickoff):</label>
  <input type="number" name="roundLockOffsetMinutes" min="1" max="360"
         th:value="${tournamentSettings != null ? tournamentSettings.roundLockOffsetMinutes : 60}"
         class="border border-gray-300 rounded px-2 py-1 w-20 text-sm"/>
</div>
```

- [ ] **Step 3: Run full test suite to confirm nothing broken**
```bash
./mvnw test -q 2>&1 | tail -20
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**
```bash
git add src/main/java/com/worldcup/prediction/controller/admin/AdminSettingsController.java \
        src/main/resources/templates/admin/settings.html
git commit -m "feat: expose round lock offset in admin settings UI

Settings page now shows the round lock offset field. Saving triggers
recalculation of all existing round windows immediately."
```

---

## Task 4: Missing Predictions — Repository Queries

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/repository/CommunityMembershipRepository.java`
- Modify: `src/main/java/com/worldcup/prediction/repository/PredictionRepository.java`
- Create: `src/test/java/com/worldcup/prediction/repository/CommunityMembershipMissingPredictionTest.java`

- [ ] **Step 1: Write the failing repository test**

Create `src/test/java/com/worldcup/prediction/repository/CommunityMembershipMissingPredictionTest.java`:

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommunityMembershipMissingPredictionTest {

    @Autowired CommunityMembershipRepository communityMembershipRepository;
    @Autowired PredictionRepository predictionRepository;
    @Autowired UserRepository userRepository;
    @Autowired MatchRepository matchRepository;
    @Autowired CommunityRepository communityRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private User alice, bob, charlie;
    private Match match;
    private Community community;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM predictions");
        jdbcTemplate.execute("DELETE FROM tournament_winner_predictions");
        jdbcTemplate.execute("DELETE FROM community_memberships");
        jdbcTemplate.execute("DELETE FROM communities");
        jdbcTemplate.execute("DELETE FROM matches");
        jdbcTemplate.execute("DELETE FROM group_standings");
        jdbcTemplate.execute("DELETE FROM group_teams");
        jdbcTemplate.execute("DELETE FROM teams");
        jdbcTemplate.execute("DELETE FROM users");

        alice   = userRepository.save(buildUser("alice@test.com", "Alice"));
        bob     = userRepository.save(buildUser("bob@test.com", "Bob"));
        charlie = userRepository.save(buildUser("charlie@test.com", "Charlie"));

        community = communityRepository.save(
                Community.builder().name("Test Community").slug("test-community").build());

        match = matchRepository.save(Match.builder()
                .matchNumber(1)
                .roundLabel("Matchday 1")
                .stage(MatchStage.GROUP)
                .status(MatchStatus.SCHEDULED)
                .kickoffTime(LocalDateTime.now().plusDays(1))
                .build());

        // All three are active members
        for (User u : List.of(alice, bob, charlie)) {
            communityMembershipRepository.save(CommunityMembership.builder()
                    .community(community).user(u).role(CommunityRole.MEMBER)
                    .status(MembershipStatus.ACTIVE).build());
        }

        // Only alice submitted a prediction
        predictionRepository.save(Prediction.builder()
                .user(alice).match(match).community(community)
                .predictedHome(2).predictedAway(1).build());
    }

    @Test
    void findActiveMembersWithoutPrediction_returnsOnlyMissingUsers() {
        List<CommunityMembership> missing = communityMembershipRepository
                .findActiveMembersWithoutPrediction(community.getId(), match.getId());

        assertThat(missing).hasSize(2);
        assertThat(missing.stream().map(cm -> cm.getUser().getEmail()))
                .containsExactlyInAnyOrder("bob@test.com", "charlie@test.com");
    }

    @Test
    void findActiveMembersWithoutPrediction_excludesPendingMembers() {
        // Make bob's membership pending (not active)
        CommunityMembership bobMembership = communityMembershipRepository
                .findByCommunityIdAndUserId(community.getId(), bob.getId()).orElseThrow();
        bobMembership.setStatus(MembershipStatus.PENDING);
        communityMembershipRepository.save(bobMembership);

        List<CommunityMembership> missing = communityMembershipRepository
                .findActiveMembersWithoutPrediction(community.getId(), match.getId());

        assertThat(missing).hasSize(1);
        assertThat(missing.get(0).getUser().getEmail()).isEqualTo("charlie@test.com");
    }

    private User buildUser(String email, String firstName) {
        return User.builder()
                .email(email).firstName(firstName).lastName("Test")
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
```bash
./mvnw test -pl . -Dtest=CommunityMembershipMissingPredictionTest -q 2>&1 | tail -20
```
Expected: FAIL — method `findActiveMembersWithoutPrediction` does not exist.

- [ ] **Step 3: Add query to CommunityMembershipRepository**

Add this method to `CommunityMembershipRepository`:
```java
@Query("""
        SELECT cm FROM CommunityMembership cm
        JOIN FETCH cm.user u
        WHERE cm.community.id = :communityId
          AND cm.status = 'ACTIVE'
          AND cm.user.id NOT IN (
              SELECT p.user.id FROM Prediction p
              WHERE p.match.id = :matchId AND p.community.id = :communityId
          )
        ORDER BY u.firstName ASC
        """)
List<CommunityMembership> findActiveMembersWithoutPrediction(
        @Param("communityId") Long communityId,
        @Param("matchId") Long matchId);
```

- [ ] **Step 4: Add community-filtered predictions query to PredictionRepository**

Add this method to `PredictionRepository` (needed by controller/service for community-scoped admin view):
```java
@Query("""
        SELECT p FROM Prediction p
        JOIN FETCH p.user u
        WHERE p.match.id = :matchId AND p.community.id = :communityId
        ORDER BY u.totalPoints DESC
        """)
List<Prediction> findByMatchIdAndCommunityIdWithUsers(
        @Param("matchId") Long matchId,
        @Param("communityId") Long communityId);
```

- [ ] **Step 5: Run test to verify it passes**
```bash
./mvnw test -pl . -Dtest=CommunityMembershipMissingPredictionTest -q 2>&1 | tail -10
```
Expected: BUILD SUCCESS, 2 tests passed.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/worldcup/prediction/repository/CommunityMembershipRepository.java \
        src/main/java/com/worldcup/prediction/repository/PredictionRepository.java \
        src/test/java/com/worldcup/prediction/repository/CommunityMembershipMissingPredictionTest.java
git commit -m "feat: add repo queries for missing predictions by community

CommunityMembershipRepository.findActiveMembersWithoutPrediction returns
active members who have not submitted for a given match+community."
```

---

## Task 5: Missing Predictions — PredictionService.createOnBehalfOf

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/service/PredictionService.java`
- Create: `src/test/java/com/worldcup/prediction/service/PredictionServiceCreateOnBehalfTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/worldcup/prediction/service/PredictionServiceCreateOnBehalfTest.java`:

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.*;
import com.worldcup.prediction.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictionServiceCreateOnBehalfTest {

    @Mock PredictionRepository predictionRepository;
    @Mock UserRepository userRepository;
    @Mock MatchRepository matchRepository;
    @Mock CommunityRepository communityRepository;
    @Mock RoundWindowService roundWindowService;
    @Mock TournamentSettingsService tournamentSettingsService;
    @Mock PredictionWindowService predictionWindowService;
    @Mock ScoringService scoringService;
    @InjectMocks PredictionService predictionService;

    private User user;
    private Match scheduledMatch;
    private Match completedMatch;
    private Community community;

    @BeforeEach
    void setUp() {
        user = User.builder().id(10L).email("user@test.com")
                .firstName("Joe").lastName("Doe")
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();

        scheduledMatch = new Match();
        scheduledMatch.setId(1L);
        scheduledMatch.setMatchNumber(1);
        scheduledMatch.setStatus(MatchStatus.SCHEDULED);
        scheduledMatch.setKickoffTime(LocalDateTime.now().plusDays(1));

        completedMatch = new Match();
        completedMatch.setId(2L);
        completedMatch.setMatchNumber(2);
        completedMatch.setStage(MatchStage.GROUP);
        completedMatch.setStatus(MatchStatus.COMPLETED);
        completedMatch.setHomeScore(3);
        completedMatch.setAwayScore(1);
        completedMatch.setKickoffTime(LocalDateTime.now().minusDays(1));

        community = Community.builder().id(5L).name("Test").slug("test").build();
    }

    @Test
    void createOnBehalfOf_savesWithAdminFlag() {
        when(predictionRepository.existsByUserIdAndMatchIdAndCommunityId(10L, 1L, 5L))
                .thenReturn(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(matchRepository.findById(1L)).thenReturn(Optional.of(scheduledMatch));
        when(communityRepository.findById(5L)).thenReturn(Optional.of(community));
        when(predictionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Prediction result = predictionService.createOnBehalfOf(10L, 1L, 5L, 2, 0, scoringService);

        assertThat(result.isEditedByAdmin()).isTrue();
        assertThat(result.getAdminEditNote()).isNotBlank();
        assertThat(result.getPredictedHome()).isEqualTo(2);
        assertThat(result.getPredictedAway()).isEqualTo(0);
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getCommunity()).isEqualTo(community);
    }

    @Test
    void createOnBehalfOf_throwsIfAlreadyExists() {
        when(predictionRepository.existsByUserIdAndMatchIdAndCommunityId(10L, 1L, 5L))
                .thenReturn(true);

        assertThatThrownBy(() ->
                predictionService.createOnBehalfOf(10L, 1L, 5L, 2, 0, scoringService))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createOnBehalfOf_scoresImmediatelyIfMatchCompleted() {
        when(predictionRepository.existsByUserIdAndMatchIdAndCommunityId(10L, 2L, 5L))
                .thenReturn(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(matchRepository.findById(2L)).thenReturn(Optional.of(completedMatch));
        when(communityRepository.findById(5L)).thenReturn(Optional.of(community));
        when(predictionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(scoringService.calculatePoints(3, 1, 3, 1)).thenReturn(3);
        when(scoringService.determineScoreResult(3, 1, 3, 1)).thenReturn(PredictionScore.EXACT);

        Prediction result = predictionService.createOnBehalfOf(10L, 2L, 5L, 3, 1, scoringService);

        assertThat(result.getPointsAwarded()).isEqualTo(3);
        assertThat(result.getScoreResult()).isEqualTo(PredictionScore.EXACT);
        verify(scoringService).calculatePoints(3, 1, 3, 1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
```bash
./mvnw test -pl . -Dtest=PredictionServiceCreateOnBehalfTest -q 2>&1 | tail -20
```
Expected: FAIL — `createOnBehalfOf` method does not exist.

- [ ] **Step 3: Add createOnBehalfOf and findAllByMatchIdAndCommunityId to PredictionService**

In `PredictionService.java`, add two methods in the Admin operations section:

```java
/** Returns predictions for a match within a specific community (admin use — bypasses window checks). */
public List<Prediction> findAllByMatchIdAndCommunityId(Long matchId, Long communityId) {
    return predictionRepository.findByMatchIdAndCommunityIdWithUsers(matchId, communityId);
}

/**
 * Creates a prediction on behalf of a user — bypasses window lock checks.
 * Admin backdoor for members who submitted predictions via email.
 * Re-scores immediately if the match result is already recorded.
 */
@Transactional
public Prediction createOnBehalfOf(Long userId, Long matchId, Long communityId,
                                    int homeScore, int awayScore,
                                    ScoringService scoringService) {
    if (predictionRepository.existsByUserIdAndMatchIdAndCommunityId(userId, matchId, communityId)) {
        throw new IllegalStateException(
                "Prediction already exists for user " + userId + " / match " + matchId +
                " / community " + communityId + "; use override instead.");
    }
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
    Community community = communityRepository.findById(communityId)
            .orElseThrow(() -> new IllegalArgumentException("Community not found: " + communityId));

    Prediction prediction = Prediction.builder()
            .user(user)
            .match(match)
            .community(community)
            .predictedHome(homeScore)
            .predictedAway(awayScore)
            .editedByAdmin(true)
            .adminEditNote("Created on behalf of user by admin")
            .build();

    if (match.isCompleted() && match.getHomeScore() != null && match.getAwayScore() != null) {
        int pts = scoringService.calculatePoints(
                match.getEffectiveHomeScore(), match.getEffectiveAwayScore(),
                homeScore, awayScore);
        prediction.setPointsAwarded(pts);
        prediction.setScoreResult(scoringService.determineScoreResult(
                match.getEffectiveHomeScore(), match.getEffectiveAwayScore(),
                homeScore, awayScore));
    }

    return predictionRepository.save(prediction);
}
```

- [ ] **Step 4: Run test to verify it passes**
```bash
./mvnw test -pl . -Dtest=PredictionServiceCreateOnBehalfTest -q 2>&1 | tail -10
```
Expected: BUILD SUCCESS, 3 tests passed.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/worldcup/prediction/service/PredictionService.java \
        src/test/java/com/worldcup/prediction/service/PredictionServiceCreateOnBehalfTest.java
git commit -m "feat: add PredictionService.createOnBehalfOf for admin backdoor

Allows super admin to submit a prediction for any user+match+community
pair bypassing window lock checks. Scores immediately if match is done."
```

---

## Task 6: Missing Predictions — Admin Controller & Template

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/controller/admin/AdminPredictionController.java`
- Modify: `src/main/resources/templates/admin/predictions.html`

- [ ] **Step 1: Update AdminPredictionController**

Replace the entire file content with:

```java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.enums.AuditAction;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.AuditLogService;
import com.worldcup.prediction.service.MatchAdminService;
import com.worldcup.prediction.service.PredictionService;
import com.worldcup.prediction.service.ScoringService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/predictions")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminPredictionController {

    private final PredictionService predictionService;
    private final MatchAdminService matchAdminService;
    private final AuditLogService auditLogService;
    private final ScoringService scoringService;
    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository communityMembershipRepository;

    @GetMapping
    public String listPredictions(@RequestParam(required = false) Long matchId,
                                   @RequestParam(required = false) Long communityId,
                                   Model model) {
        model.addAttribute("allMatches", matchAdminService.findAllOrderByKickoffAsc());
        model.addAttribute("allCommunities", communityRepository.findAll(Sort.by("name")));
        model.addAttribute("matchId", matchId);
        model.addAttribute("communityId", communityId);

        if (matchId != null) {
            Match selectedMatch = matchAdminService.findById(matchId);
            model.addAttribute("selectedMatch", selectedMatch);

            if (communityId != null) {
                Community selectedCommunity = communityRepository.findById(communityId).orElse(null);
                List<Prediction> predictions = predictionService.findAllByMatchIdAndCommunityId(matchId, communityId);
                var missingMembers = communityMembershipRepository
                        .findActiveMembersWithoutPrediction(communityId, matchId);
                model.addAttribute("selectedCommunity", selectedCommunity);
                model.addAttribute("predictions", predictions);
                model.addAttribute("missingMembers", missingMembers);
            } else {
                List<Prediction> predictions = predictionService.findAllByMatchId(matchId);
                model.addAttribute("predictions", predictions);
            }
        }
        return "admin/predictions";
    }

    @PostMapping("/{id}/override")
    public String overridePrediction(@PathVariable Long id,
                                     @RequestParam @Min(0) int homeScore,
                                     @RequestParam @Min(0) int awayScore,
                                     @RequestParam(required = false) Long communityId,
                                     @AuthenticationPrincipal CustomOAuth2User admin,
                                     RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        Prediction prediction = predictionService.overridePrediction(id, homeScore, awayScore, scoringService);
        auditLogService.log(adminId, AuditAction.PREDICTION_EDITED_BY_ADMIN, "PREDICTION", id,
                "Override: " + homeScore + "–" + awayScore
                        + " userId=" + prediction.getUser().getId()
                        + " matchId=" + prediction.getMatch().getId());
        redirectAttributes.addFlashAttribute("successMessage",
                "Prediction #" + id + " overridden to " + homeScore + "–" + awayScore);
        String redirect = "redirect:/admin/predictions?matchId=" + prediction.getMatch().getId();
        if (communityId != null) redirect += "&communityId=" + communityId;
        return redirect;
    }

    @PostMapping("/create")
    public String createPrediction(@RequestParam Long matchId,
                                    @RequestParam Long communityId,
                                    @RequestParam Long userId,
                                    @RequestParam @Min(0) int homeScore,
                                    @RequestParam @Min(0) int awayScore,
                                    @AuthenticationPrincipal CustomOAuth2User admin,
                                    RedirectAttributes redirectAttributes) {
        Long adminId = admin != null ? admin.getUserId() : 0L;
        try {
            Prediction prediction = predictionService.createOnBehalfOf(
                    userId, matchId, communityId, homeScore, awayScore, scoringService);
            auditLogService.log(adminId, AuditAction.PREDICTION_EDITED_BY_ADMIN, "PREDICTION",
                    prediction.getId(),
                    "Created on behalf: " + homeScore + "–" + awayScore
                            + " userId=" + userId
                            + " matchId=" + matchId
                            + " communityId=" + communityId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Prediction created for " + prediction.getUser().getFullName()
                            + ": " + homeScore + "–" + awayScore);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/predictions?matchId=" + matchId + "&communityId=" + communityId;
    }
}
```

- [ ] **Step 2: Update predictions.html template**

Replace the full file content with:

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{admin/layout}">
<head><title>Predictions</title></head>
<body>

<th:block layout:fragment="page-title">Predictions — Admin View</th:block>

<th:block layout:fragment="content">

  <!-- Match + Community selector -->
  <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-6">
    <h2 class="text-sm font-semibold text-gray-700 mb-3">Select a Match</h2>
    <form th:action="@{/admin/predictions}" method="get" class="flex items-center gap-3 flex-wrap">
      <select name="matchId"
              class="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-1 focus:ring-admin-light focus:outline-none min-w-64">
        <option value="">— Choose match —</option>
        <option th:each="m : ${allMatches}"
                th:value="${m.id}"
                th:selected="${m.id == matchId}"
                th:text="${(m.homeTeam?.name ?: 'TBD') + ' vs ' + (m.awayTeam?.name ?: 'TBD') + ' (' + #temporals.format(m.kickoffTime, 'dd MMM') + ')'}">
          Match
        </option>
      </select>
      <select name="communityId"
              class="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-1 focus:ring-admin-light focus:outline-none min-w-48">
        <option value="">— All communities —</option>
        <option th:each="c : ${allCommunities}"
                th:value="${c.id}"
                th:selected="${c.id == communityId}"
                th:text="${c.name}">
          Community
        </option>
      </select>
      <button type="submit"
              class="px-4 py-2 text-sm font-medium bg-admin-dark hover:bg-admin-mid text-white rounded-lg transition-colors duration-150">
        Load Predictions
      </button>
    </form>
  </div>

  <!-- Predictions table -->
  <div th:if="${selectedMatch != null}" class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-x-auto mb-6">

    <div class="px-6 py-4 border-b border-gray-100 bg-gray-50 flex items-center justify-between">
      <div class="flex items-center gap-3">
        <img th:if="${selectedMatch.homeTeam != null}"
             th:src="@{'/images/flags/' + ${selectedMatch.homeTeam.flagCode} + '.svg'}"
             class="w-6 h-6 rounded-full object-cover" alt=""/>
        <span class="font-semibold text-gray-900" th:text="${selectedMatch.homeTeam?.name ?: 'TBD'}">Home</span>
        <span class="text-gray-400">vs</span>
        <span class="font-semibold text-gray-900" th:text="${selectedMatch.awayTeam?.name ?: 'TBD'}">Away</span>
        <img th:if="${selectedMatch.awayTeam != null}"
             th:src="@{'/images/flags/' + ${selectedMatch.awayTeam.flagCode} + '.svg'}"
             class="w-6 h-6 rounded-full object-cover" alt=""/>
        <span th:if="${selectedCommunity != null}"
              class="ml-3 text-xs font-medium bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full"
              th:text="${selectedCommunity.name}"></span>
      </div>
      <div class="flex items-center gap-3 text-sm">
        <span class="text-gray-500" th:text="${#temporals.format(selectedMatch.kickoffTime, 'dd MMM yyyy HH:mm')}"></span>
        <span th:if="${selectedMatch.completed}" class="font-mono font-bold text-gray-900"
              th:text="'Result: ' + ${selectedMatch.homeScore} + '–' + ${selectedMatch.awayScore}"></span>
        <span th:unless="${selectedMatch.completed}" class="text-amber-600 text-xs font-medium">No result yet</span>
        <span class="text-gray-400 text-xs"
              th:text="${#lists.size(predictions)} + ' predictions'"></span>
      </div>
    </div>

    <table class="w-full text-sm">
      <thead>
        <tr class="bg-gray-50 border-b border-gray-100 text-left">
          <th class="px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Participant</th>
          <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Prediction</th>
          <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Points</th>
          <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Submitted</th>
          <th class="px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Override</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-gray-50">
        <tr th:if="${#lists.isEmpty(predictions)}" class="text-center">
          <td colspan="5" class="py-12 text-sm text-gray-400">No predictions submitted yet.</td>
        </tr>

        <tr th:each="pred : ${predictions}" class="hover:bg-gray-50 transition-colors duration-100"
            x-data="{ editing: false }">

          <td class="px-6 py-3">
            <div class="flex items-center gap-2">
              <img th:src="${pred.user.avatarUrl ?: 'https://ui-avatars.com/api/?background=006b2a&color=fff&name=U'}"
                   class="w-7 h-7 rounded-full object-cover" alt=""/>
              <span class="font-medium text-gray-800" th:text="${pred.user.fullName}">User</span>
            </div>
          </td>

          <td class="px-4 py-3">
            <span class="font-mono font-semibold text-gray-900"
                  th:text="${pred.predictedHome} + '–' + ${pred.predictedAway}">0–0</span>
          </td>

          <td class="px-4 py-3">
            <span th:classappend="${pred.pointsAwarded == 3} ? ' font-bold text-green-600' :
                                  (${pred.pointsAwarded == 2} ? ' font-bold text-blue-600' :
                                  (${pred.pointsAwarded == 1} ? ' font-bold text-yellow-600' : ' font-bold text-red-500'))"
                  th:text="${pred.pointsAwarded}">0</span>
          </td>

          <td class="px-4 py-3 text-xs text-gray-500"
              th:text="${pred.submittedAt != null ? #temporals.format(pred.submittedAt, 'dd MMM HH:mm') : '–'}"></td>

          <td class="px-6 py-3">
            <button @click="editing = !editing"
                    class="px-3 py-1 text-xs font-medium rounded-md bg-amber-100 hover:bg-amber-200 text-amber-800 transition-colors duration-150">
              Override
            </button>
            <div x-show="editing" x-transition class="mt-2">
              <form th:action="@{'/admin/predictions/' + ${pred.id} + '/override'}" method="post"
                    class="flex items-center gap-2 bg-amber-50 rounded-lg px-3 py-2 border border-amber-200">
                <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
                <input type="hidden" name="communityId" th:value="${communityId}"/>
                <label class="text-xs text-gray-500">H</label>
                <input type="number" name="homeScore" min="0" max="99" th:value="${pred.predictedHome}"
                       class="w-12 text-center border border-gray-300 rounded px-1 py-1 text-sm focus:ring-1 focus:ring-amber-400 focus:outline-none"/>
                <span class="text-gray-400">–</span>
                <input type="number" name="awayScore" min="0" max="99" th:value="${pred.predictedAway}"
                       class="w-12 text-center border border-gray-300 rounded px-1 py-1 text-sm focus:ring-1 focus:ring-amber-400 focus:outline-none"/>
                <label class="text-xs text-gray-500">A</label>
                <button type="submit"
                        class="px-3 py-1 text-xs font-medium rounded-md bg-amber-500 hover:bg-amber-600 text-white transition-colors duration-150">
                  Save
                </button>
                <button type="button" @click="editing = false"
                        class="px-2 py-1 text-xs text-gray-500 hover:text-gray-700">Cancel</button>
              </form>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <!-- Missing predictions table (only shown when a community is selected) -->
  <div th:if="${selectedMatch != null and communityId != null and missingMembers != null and !#lists.isEmpty(missingMembers)}"
       class="bg-white rounded-xl shadow-sm border border-orange-100 overflow-x-auto">

    <div class="px-6 py-4 border-b border-orange-100 bg-orange-50 flex items-center justify-between">
      <div>
        <h3 class="text-sm font-semibold text-orange-800">Missing Predictions</h3>
        <p class="text-xs text-orange-600 mt-0.5">Active community members who have not submitted for this match</p>
      </div>
      <span class="text-xs font-medium bg-orange-200 text-orange-800 px-2 py-0.5 rounded-full"
            th:text="${#lists.size(missingMembers)} + ' missing'"></span>
    </div>

    <table class="w-full text-sm">
      <thead>
        <tr class="bg-orange-50 border-b border-orange-100 text-left">
          <th class="px-6 py-3 text-xs font-semibold text-orange-500 uppercase tracking-wider">Member</th>
          <th class="px-6 py-3 text-xs font-semibold text-orange-500 uppercase tracking-wider">Create Prediction on Behalf</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-orange-50">
        <tr th:each="cm : ${missingMembers}" class="hover:bg-orange-50 transition-colors duration-100"
            x-data="{ creating: false }">
          <td class="px-6 py-3">
            <div class="flex items-center gap-2">
              <img th:src="${cm.user.avatarUrl ?: 'https://ui-avatars.com/api/?background=ea580c&color=fff&name=U'}"
                   class="w-7 h-7 rounded-full object-cover" alt=""/>
              <span class="font-medium text-gray-800" th:text="${cm.user.fullName}">User</span>
            </div>
          </td>
          <td class="px-6 py-3">
            <button @click="creating = !creating"
                    class="px-3 py-1 text-xs font-medium rounded-md bg-orange-100 hover:bg-orange-200 text-orange-800 transition-colors duration-150">
              Create
            </button>
            <div x-show="creating" x-transition class="mt-2">
              <form th:action="@{/admin/predictions/create}" method="post"
                    class="flex items-center gap-2 bg-orange-50 rounded-lg px-3 py-2 border border-orange-200">
                <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
                <input type="hidden" name="matchId" th:value="${matchId}"/>
                <input type="hidden" name="communityId" th:value="${communityId}"/>
                <input type="hidden" name="userId" th:value="${cm.user.id}"/>
                <label class="text-xs text-gray-500">H</label>
                <input type="number" name="homeScore" min="0" max="99" value="0"
                       class="w-12 text-center border border-gray-300 rounded px-1 py-1 text-sm focus:ring-1 focus:ring-orange-400 focus:outline-none"/>
                <span class="text-gray-400">–</span>
                <input type="number" name="awayScore" min="0" max="99" value="0"
                       class="w-12 text-center border border-gray-300 rounded px-1 py-1 text-sm focus:ring-1 focus:ring-orange-400 focus:outline-none"/>
                <label class="text-xs text-gray-500">A</label>
                <button type="submit"
                        class="px-3 py-1 text-xs font-medium rounded-md bg-orange-500 hover:bg-orange-600 text-white transition-colors duration-150">
                  Save
                </button>
                <button type="button" @click="creating = false"
                        class="px-2 py-1 text-xs text-gray-500 hover:text-gray-700">Cancel</button>
              </form>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <!-- No missing members notice -->
  <div th:if="${selectedMatch != null and communityId != null and (missingMembers == null or #lists.isEmpty(missingMembers))}"
       class="bg-white rounded-xl shadow-sm border border-green-100 p-4 text-center text-sm text-green-700">
    All active community members have submitted predictions for this match.
  </div>

  <div th:unless="${selectedMatch != null}"
       class="bg-white rounded-xl shadow-sm border border-gray-100 p-12 text-center text-gray-400 text-sm">
    Select a match above to view and manage predictions.
  </div>

</th:block>
</body>
</html>
```

- [ ] **Step 3: Run full test suite**
```bash
./mvnw test -q 2>&1 | tail -20
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**
```bash
git add src/main/java/com/worldcup/prediction/controller/admin/AdminPredictionController.java \
        src/main/resources/templates/admin/predictions.html
git commit -m "feat: show missing predictions per community and allow admin creation

Admin predictions page now has a community selector. When a community is
chosen, a second table lists members with no submission and provides
an inline Create form to fill predictions on their behalf."
```

---

## Verification Checklist

After all tasks complete, verify:

- [ ] Run full test suite: `./mvnw test -q` → BUILD SUCCESS
- [ ] Start the app: `./mvnw spring-boot:run -q` and navigate to `http://localhost:8888/admin`
- [ ] Settings page: confirm Round lock offset field appears, save 10, reload → shows 10
- [ ] Predictions page: select match + community → see existing predictions + missing members table
- [ ] Backup page: restore form uploads without downloading HTML
- [ ] DB migration ran cleanly (check logs for `Flyway: Successfully applied 1 migration to schema...`)
