# Features v4 — Play-Off Stage Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare the app for the knockout stage (starts 2026-06-28) by syncing knockout matches from the API and updating Standing, Leaderboard, and Exact Score Heroes pages.

**Architecture:** Seven tasks in dependency order. Task 1 (knockout match sync) is a prerequisite for Tasks 3 and 4 to show live data; Tasks 2–7 can otherwise be built and tested independently. Each task is a self-contained backend+frontend change with its own commit.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA / JPQL, Thymeleaf, HTMX, Alpine.js, Mockito/JUnit 5.

---

## File Map

| File | Task | Action |
|---|---|---|
| `src/main/java/.../integration/football/MatchSyncService.java` | 1 | Add `syncKnockoutMatches()`, `mapKnockoutStage()` |
| `src/test/java/.../integration/football/MatchSyncServiceKnockoutTest.java` | 1 | New test class |
| `src/main/java/.../controller/admin/AdminSyncController.java` | 2 | Add `POST /admin/sync/knockout-matches` |
| `src/main/resources/templates/admin/sync.html` | 2 | Add "Knockout Matches" sync card |
| `src/main/resources/templates/fragments/bracket-view.html` | 3 | New — extracted bracket HTML |
| `src/main/resources/templates/bracket.html` | 3 | Replace body with `th:replace` |
| `src/main/java/.../controller/GroupController.java` | 4 | Load bracket data + `defaultTab` |
| `src/main/resources/templates/groups.html` | 4 | Add Groups/Play Off tab bar |
| `src/main/java/.../controller/community/CommunityLeaderboardController.java` | 5 | Add `gsOpenDefault` + `gsStage` to model |
| `src/main/resources/templates/community/leaderboard.html` | 5 | Collapsible GS section |
| `src/main/java/.../repository/PredictionRepository.java` | 6 | Add `findCumulativeExactPredictions()` |
| `src/main/java/.../service/DailyExactPredictorService.java` | 6 | Add `getCumulativeHeroes()` |
| `src/test/java/.../service/DailyExactPredictorServiceCumulativeTest.java` | 6 | New test class |
| `src/main/java/.../controller/community/CommunityHomeController.java` | 7 | Switch to cumulative heroes + add HTMX endpoint |
| `src/main/resources/templates/fragments/heroes-content.html` | 7 | New — HTMX swap fragment |
| `src/main/resources/templates/community/home.html` | 7 | Add stage tabs + HTMX wiring |

---

## Task 1: Knockout Match Sync Service Method

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/integration/football/MatchSyncService.java`
- Create: `src/test/java/com/worldcup/prediction/integration/football/MatchSyncServiceKnockoutTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/worldcup/prediction/integration/football/MatchSyncServiceKnockoutTest.java`:

```java
package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.integration.football.dto.*;
import com.worldcup.prediction.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchSyncServiceKnockoutTest {

    @Mock FootballApiClient client;
    @Mock FootballApiRateLimiter rateLimiter;
    @Mock MatchRepository matchRepository;
    @Mock GroupRepository groupRepository;
    @Mock TeamRepository teamRepository;
    @Mock MatchLineupRepository lineupRepository;
    @Mock MatchGoalRepository goalRepository;
    @Mock PredictionRepository predictionRepository;
    @Mock RoundWindowRepository roundWindowRepository;
    @InjectMocks MatchSyncService service;

    @Test
    void syncKnockoutMatches_createsRoundOf32MatchWithPlaceholders() {
        FootballApiTeamDto homeTbd = new FootballApiTeamDto(null, "Winner Group A", "WGA");
        FootballApiTeamDto awayTbd = new FootballApiTeamDto(null, "Runner-up Group B", "RGB");
        FootballApiMatchDto knockoutMatch = new FootballApiMatchDto(
                5001L, "2026-07-01T18:00:00Z", "SCHEDULED",
                null, "LAST_32", null, homeTbd, awayTbd, null);
        FootballApiMatchDto groupMatch = new FootballApiMatchDto(
                1L, "2026-06-12T18:00:00Z", "FINISHED",
                1, "GROUP_STAGE", "GROUP_A", homeTbd, awayTbd, null);
        FootballApiResponseDto response = new FootballApiResponseDto(2, List.of(groupMatch, knockoutMatch));

        when(rateLimiter.call(any())).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(client.fetchAllMatches()).thenReturn(response);
        when(matchRepository.findByExternalId("5001")).thenReturn(Optional.empty());
        when(teamRepository.findByExternalId(any())).thenReturn(Optional.empty());
        when(teamRepository.findByFifaCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(teamRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());

        SyncResult result = service.syncKnockoutMatches();

        assertThat(result.skipped()).isFalse();
        verify(matchRepository).save(argThat(m ->
                m.getStage() == MatchStage.ROUND_OF_32
                && "5001".equals(m.getExternalId())
                && "Winner Group A".equals(m.getHomeTeamPlaceholder())
                && "Runner-up Group B".equals(m.getAwayTeamPlaceholder())));
    }

    @Test
    void syncKnockoutMatches_skipsNullApiResponse() {
        when(rateLimiter.call(any())).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(client.fetchAllMatches()).thenReturn(null);

        SyncResult result = service.syncKnockoutMatches();

        assertThat(result.skipped()).isTrue();
        verify(matchRepository, never()).save(any());
    }

    @Test
    void syncKnockoutMatches_updatesExistingMatchKickoff() {
        FootballApiTeamDto home = new FootballApiTeamDto(null, "TBD", "TBD");
        FootballApiMatchDto knockoutMatch = new FootballApiMatchDto(
                5001L, "2026-07-01T18:00:00Z", "SCHEDULED",
                null, "LAST_32", null, home, home, null);
        FootballApiResponseDto response = new FootballApiResponseDto(1, List.of(knockoutMatch));

        Match existing = new Match();
        existing.setExternalId("5001");
        existing.setStage(MatchStage.ROUND_OF_32);

        when(rateLimiter.call(any())).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(client.fetchAllMatches()).thenReturn(response);
        when(matchRepository.findByExternalId("5001")).thenReturn(Optional.of(existing));

        service.syncKnockoutMatches();

        verify(matchRepository).save(argThat(m -> "5001".equals(m.getExternalId())));
        verify(matchRepository, times(1)).save(any());
    }

    @Test
    void syncKnockoutMatches_skipsGroupStageMatches() {
        FootballApiMatchDto groupMatch = new FootballApiMatchDto(
                1L, "2026-06-12T18:00:00Z", "FINISHED",
                1, "GROUP_STAGE", "GROUP_A", null, null, null);
        FootballApiResponseDto response = new FootballApiResponseDto(List.of(groupMatch));

        when(rateLimiter.call(any())).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(client.fetchAllMatches()).thenReturn(response);

        SyncResult result = service.syncKnockoutMatches();

        assertThat(result.skipped()).isTrue();
        verify(matchRepository, never()).save(any());
    }

    @Test
    void syncKnockoutMatches_mapsAllKnownStages() {
        List<String> apiStages = List.of(
                "LAST_32", "ROUND_OF_32", "LAST_16", "ROUND_OF_16",
                "QUARTER_FINALS", "QUARTER_FINAL",
                "SEMI_FINALS", "SEMI_FINAL",
                "FINAL", "THIRD_PLACE", "PLAY_OFF_FOR_THIRD_PLACE");

        for (String stage : apiStages) {
            FootballApiTeamDto tbd = new FootballApiTeamDto(null, "TBD", "TBD");
            FootballApiMatchDto m = new FootballApiMatchDto(
                    (long) stage.hashCode(), "2026-07-01T18:00:00Z", "SCHEDULED",
                    null, stage, null, tbd, tbd, null);
            FootballApiResponseDto response = new FootballApiResponseDto(List.of(m));

            when(rateLimiter.call(any())).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
            when(client.fetchAllMatches()).thenReturn(response);
            when(matchRepository.findByExternalId(any())).thenReturn(Optional.empty());
            when(teamRepository.findByExternalId(any())).thenReturn(Optional.empty());
            when(teamRepository.findByFifaCodeIgnoreCase(any())).thenReturn(Optional.empty());
            when(teamRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());

            SyncResult result = service.syncKnockoutMatches();
            assertThat(result.skipped()).as("Stage '%s' should not be skipped", stage).isFalse();
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/arthurho/dev/tools/world-cup-prediction
./mvnw test -pl . -Dtest=MatchSyncServiceKnockoutTest -q 2>&1 | tail -20
```

Expected: compilation error — `syncKnockoutMatches()` does not exist yet.

- [ ] **Step 3: Add `syncKnockoutMatches()` to `MatchSyncService`**

Open `src/main/java/com/worldcup/prediction/integration/football/MatchSyncService.java`.

Add after the `syncMatchDatesOnly()` method:

```java
/**
 * Non-destructive upsert of knockout stage matches from the API.
 * Creates new matches for unseen externalIds; updates kickoff/status for existing ones.
 * Never touches predictions or group stage matches.
 */
public SyncResult syncKnockoutMatches() {
    FootballApiResponseDto response = rateLimiter.call(client::fetchAllMatches);
    if (response == null || response.matches() == null) {
        return SyncResult.skipped("No API response");
    }

    response.matches().stream()
            .map(FootballApiMatchDto::stage)
            .filter(s -> s != null && !"GROUP_STAGE".equals(s))
            .distinct()
            .forEach(s -> log.info("Knockout API stage string observed: '{}'", s));

    List<FootballApiMatchDto> knockoutMatches = response.matches().stream()
            .filter(m -> m.stage() != null && !"GROUP_STAGE".equals(m.stage()))
            .filter(m -> m.id() != null && m.utcDate() != null)
            .sorted(Comparator.comparing(FootballApiMatchDto::utcDate))
            .toList();

    if (knockoutMatches.isEmpty()) {
        return SyncResult.skipped("No knockout matches in API response");
    }

    int created = 0;
    int updated = 0;
    int skipped = 0;

    for (FootballApiMatchDto apiMatch : knockoutMatches) {
        MatchStage stage = mapKnockoutStage(apiMatch.stage());
        if (stage == null) {
            log.warn("Unknown knockout stage '{}' for match id={} — skipping", apiMatch.stage(), apiMatch.id());
            skipped++;
            continue;
        }

        String extId = String.valueOf(apiMatch.id());
        Optional<Match> existing = matchRepository.findByExternalId(extId);

        if (existing.isPresent()) {
            Match m = existing.get();
            m.setKickoffTime(parseUtc(apiMatch.utcDate()));
            m.setStatus(mapStatus(apiMatch.status()));
            matchRepository.save(m);
            updated++;
            continue;
        }

        Team home = null;
        Team away = null;
        String homePlaceholder = null;
        String awayPlaceholder = null;

        if (apiMatch.homeTeam() != null) {
            Optional<Team> opt = resolveTeam(apiMatch.homeTeam());
            if (opt.isPresent()) {
                home = opt.get();
            } else {
                homePlaceholder = apiMatch.homeTeam().name() != null ? apiMatch.homeTeam().name() : "TBD";
            }
        } else {
            homePlaceholder = "TBD";
        }

        if (apiMatch.awayTeam() != null) {
            Optional<Team> opt = resolveTeam(apiMatch.awayTeam());
            if (opt.isPresent()) {
                away = opt.get();
            } else {
                awayPlaceholder = apiMatch.awayTeam().name() != null ? apiMatch.awayTeam().name() : "TBD";
            }
        } else {
            awayPlaceholder = "TBD";
        }

        Match match = Match.builder()
                .externalId(extId)
                .stage(stage)
                .matchNumber(0)
                .roundLabel(stage.getDisplayName())
                .homeTeam(home)
                .awayTeam(away)
                .homeTeamPlaceholder(homePlaceholder)
                .awayTeamPlaceholder(awayPlaceholder)
                .kickoffTime(parseUtc(apiMatch.utcDate()))
                .status(mapStatus(apiMatch.status()))
                .build();

        matchRepository.save(match);
        created++;
    }

    return SyncResult.success(created + " created, " + updated + " updated" +
            (skipped > 0 ? ", " + skipped + " skipped" : ""));
}

private MatchStage mapKnockoutStage(String apiStage) {
    if (apiStage == null) return null;
    return switch (apiStage) {
        case "LAST_32",   "ROUND_OF_32"                -> MatchStage.ROUND_OF_32;
        case "LAST_16",   "ROUND_OF_16"                -> MatchStage.ROUND_OF_16;
        case "QUARTER_FINALS", "QUARTER_FINAL"         -> MatchStage.QUARTER_FINAL;
        case "SEMI_FINALS",    "SEMI_FINAL"            -> MatchStage.SEMI_FINAL;
        case "FINAL"                                   -> MatchStage.FINAL;
        case "THIRD_PLACE", "PLAY_OFF_FOR_THIRD_PLACE" -> MatchStage.THIRD_PLACE;
        default -> null;
    };
}
```

Also add the `FootballApiResponseDto` import if not already there — check the existing imports at the top of the file; `FootballApiResponseDto` is already imported.

- [ ] **Step 4: Run tests**

```bash
./mvnw test -pl . -Dtest=MatchSyncServiceKnockoutTest -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`, all 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/integration/football/MatchSyncService.java \
        src/test/java/com/worldcup/prediction/integration/football/MatchSyncServiceKnockoutTest.java
git commit -m "feat: add syncKnockoutMatches() to MatchSyncService"
```

---

## Task 2: Admin Sync Button for Knockout Matches

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/controller/admin/AdminSyncController.java`
- Modify: `src/main/resources/templates/admin/sync.html`

- [ ] **Step 1: Add endpoint to `AdminSyncController`**

Open `AdminSyncController.java`. Add after the `syncMatches()` method:

```java
@PostMapping("/knockout-matches")
public String syncKnockoutMatches(RedirectAttributes ra) {
    SyncResult r = matchSyncService.syncKnockoutMatches();
    ra.addFlashAttribute("successMessage", "Knockout Matches: " + r.message());
    return "redirect:/admin/sync";
}
```

- [ ] **Step 2: Add sync card to `admin/sync.html`**

Open `src/main/resources/templates/admin/sync.html`. Find the card for "Group Stage Matches" (the one with `th:action="@{/admin/sync/matches}"`). Add the following card **directly after** that card:

```html
<div class="bg-white rounded-xl border border-gray-100 shadow-sm p-4 flex items-center justify-between">
  <div>
    <p class="font-semibold text-gray-800">Knockout Matches</p>
    <p class="text-xs text-gray-400 mt-0.5">Upsert Round of 32 through Final from API. Predictions are <span class="text-green-600 font-medium">preserved</span>. 1 API call.</p>
  </div>
  <form th:action="@{/admin/sync/knockout-matches}" method="post">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <button type="submit" class="px-4 py-2 text-sm font-semibold bg-admin-dark text-white rounded-lg hover:bg-admin-mid transition-colors">Run</button>
  </form>
</div>
```

- [ ] **Step 3: Build and smoke-test**

```bash
./mvnw compile -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/admin/AdminSyncController.java \
        src/main/resources/templates/admin/sync.html
git commit -m "feat: add Knockout Matches sync button to admin panel"
```

---

## Task 3: Extract Bracket Fragment

**Files:**
- Create: `src/main/resources/templates/fragments/bracket-view.html`
- Modify: `src/main/resources/templates/bracket.html`

This extracts the bracket HTML into a reusable fragment so both `bracket.html` and the groups Play Off tab can use the same markup without duplication.

- [ ] **Step 1: Create `fragments/bracket-view.html`**

Create `src/main/resources/templates/fragments/bracket-view.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
<div th:fragment="bracket-view">

  <!-- Empty state -->
  <div th:if="${bracketByStage == null or bracketByStage.isEmpty()}"
       class="text-center py-20 text-white/50 animate-fade-up stagger-1">
    <div class="text-6xl mb-4">🏆</div>
    <p class="text-xl font-semibold">Knockout rounds not started yet</p>
    <p class="text-sm mt-2">Check back after the Group Stage!</p>
  </div>

  <!-- Bracket -->
  <div class="bracket-scroll animate-fade-up stagger-1" th:unless="${bracketByStage == null or bracketByStage.isEmpty()}">
    <div class="bracket-grid">

      <div th:each="entry, iterStat : ${bracketByStage}"
           th:with="roundName=${entry.key}"
           th:classappend="${roundName == 'Final'} ? ' round-col final' : (${roundName == '3rd Place'} ? ' round-col third' : ' round-col')"
           class="round-col">

        <div class="round-header" th:text="${roundName}">Round</div>

        <div class="matches-col">
          <div th:each="match : ${entry.value}"
               th:classappend="${match.status?.name() == 'COMPLETED'} ? ' finished' : ''"
               class="match-card">

            <!-- Home team -->
            <div class="team-row">
              <img th:if="${match.homeTeam != null}"
                   th:src="@{'/images/flags/' + ${match.homeTeam.flagCode} + '.svg'}"
                   th:alt="${match.homeTeam.name}"
                   class="flag-sm" />
              <div th:unless="${match.homeTeam != null}" class="flag-sm bg-gray-100"></div>
              <span th:if="${match.homeTeam != null}" th:text="${match.homeTeam.name}" class="team-name"></span>
              <span th:unless="${match.homeTeam != null}"
                    th:text="${match.homeTeamPlaceholder != null ? match.homeTeamPlaceholder : 'TBD'}"
                    class="team-name tbd"></span>
              <span th:if="${match.status?.name() == 'COMPLETED' and match.homeScore != null}"
                    th:text="${match.homeScore}"
                    th:classappend="${match.homeScore > match.awayScore} ? ' winner' : ''"
                    class="score"></span>
              <div th:unless="${match.status?.name() == 'COMPLETED'}" class="tbd-dots">
                <div class="tbd-dot"></div><div class="tbd-dot"></div><div class="tbd-dot"></div>
              </div>
            </div>

            <!-- Away team -->
            <div class="team-row">
              <img th:if="${match.awayTeam != null}"
                   th:src="@{'/images/flags/' + ${match.awayTeam.flagCode} + '.svg'}"
                   th:alt="${match.awayTeam.name}"
                   class="flag-sm" />
              <div th:unless="${match.awayTeam != null}" class="flag-sm bg-gray-100"></div>
              <span th:if="${match.awayTeam != null}" th:text="${match.awayTeam.name}" class="team-name"></span>
              <span th:unless="${match.awayTeam != null}"
                    th:text="${match.awayTeamPlaceholder != null ? match.awayTeamPlaceholder : 'TBD'}"
                    class="team-name tbd"></span>
              <span th:if="${match.status?.name() == 'COMPLETED' and match.awayScore != null}"
                    th:text="${match.awayScore}"
                    th:classappend="${match.awayScore > match.homeScore} ? ' winner' : ''"
                    class="score"></span>
              <div th:unless="${match.status?.name() == 'COMPLETED'}" class="tbd-dots">
                <div class="tbd-dot"></div><div class="tbd-dot"></div><div class="tbd-dot"></div>
              </div>
            </div>

            <!-- Kickoff date chip -->
            <div class="mt-1.5 text-center">
              <span th:if="${match.kickoffTime != null}"
                    th:text="${#temporals.format(match.kickoffTime, 'MMM d')}"
                    class="text-xs text-white/50 font-medium"></span>
            </div>

          </div>
        </div>
      </div>

    </div>
  </div>

  <div class="md:hidden text-center py-2 text-xs text-white/50">← Scroll horizontally to see all rounds →</div>

</div>
</body>
</html>
```

- [ ] **Step 2: Update `bracket.html` to use the fragment**

Open `src/main/resources/templates/bracket.html`. Replace the entire content between the `<style>...</style>` block and the closing `</th:block>` with:

```html
  <div th:replace="~{fragments/bracket-view :: bracket-view}"></div>
```

The full updated `bracket.html` inner content (inside `<th:block th:fragment="main-content">`) becomes:

```html
<th:block th:fragment="main-content">

  <style>
    /* ... keep all existing styles unchanged ... */
  </style>

  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">

    <!-- Page header -->
    <div class="mb-6 animate-fade-up">
      <h1 class="font-display text-5xl text-white tracking-wider mb-1">KNOCKOUT BRACKET</h1>
      <p class="text-white/60 text-sm font-medium">Round of 32 through Final</p>
    </div>

    <div th:replace="~{fragments/bracket-view :: bracket-view}"></div>

  </div>

</th:block>
```

Note: keep all existing CSS `<style>` rules in `bracket.html` — they are needed for the bracket cards. The fragment relies on these CSS classes being present on the page that includes it. Copy those same styles to `groups.html` in Task 4.

- [ ] **Step 3: Build**

```bash
./mvnw compile -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/fragments/bracket-view.html \
        src/main/resources/templates/bracket.html
git commit -m "refactor: extract bracket view into shared fragment"
```

---

## Task 4: Standing Page — Groups + Play Off Tabs

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/controller/GroupController.java`
- Modify: `src/main/resources/templates/groups.html`

- [ ] **Step 1: Update `GroupController`**

Replace the entire `GroupController.java` with:

```java
package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.dto.GroupStandingDto;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class GroupController {

    private static final LocalDate PLAYOFF_START = LocalDate.of(2026, 6, 28);

    private static final List<MatchStage> BRACKET_STAGES = List.of(
            MatchStage.ROUND_OF_32,
            MatchStage.ROUND_OF_16,
            MatchStage.QUARTER_FINAL,
            MatchStage.SEMI_FINAL,
            MatchStage.FINAL,
            MatchStage.THIRD_PLACE
    );

    private final GroupService groupService;
    private final MatchRepository matchRepository;

    @GetMapping("/groups")
    public String groups(Model model) {
        Map<String, List<GroupStandingDto>> groups = groupService.getAllGroupStandings();
        List<String> qualifiedThirdGroups = groupService.getQualifiedThirdPlaceGroups();
        Map<String, List<Match>> matchesByGroup = groupService.getMatchesByGroup();

        Map<String, List<Match>> bracketByStage = new LinkedHashMap<>();
        for (MatchStage stage : BRACKET_STAGES) {
            List<Match> stageMatches = matchRepository.findByStageOrderByKickoffTimeAsc(stage);
            if (!stageMatches.isEmpty()) {
                bracketByStage.put(stageName(stage), stageMatches);
            }
        }

        String defaultTab = LocalDate.now().isBefore(PLAYOFF_START) ? "groups" : "playoff";

        model.addAttribute("groups", groups);
        model.addAttribute("qualifiedThirdGroups", qualifiedThirdGroups);
        model.addAttribute("matchesByGroup", matchesByGroup);
        model.addAttribute("bracketByStage", bracketByStage);
        model.addAttribute("defaultTab", defaultTab);
        model.addAttribute("pageTitle", "Standings");
        return "groups";
    }

    private String stageName(MatchStage stage) {
        return switch (stage) {
            case ROUND_OF_32   -> "Round of 32";
            case ROUND_OF_16   -> "Round of 16";
            case QUARTER_FINAL -> "Quarter-Finals";
            case SEMI_FINAL    -> "Semi-Finals";
            case FINAL         -> "Final";
            case THIRD_PLACE   -> "3rd Place";
            default            -> stage.name();
        };
    }
}
```

- [ ] **Step 2: Add tab bar and Play Off section to `groups.html`**

Open `src/main/resources/templates/groups.html`.

**Change 1:** Replace the page header `h1` text:
```html
<!-- Before -->
<h1 class="font-display text-5xl text-white tracking-wider mb-1">GROUP STANDINGS</h1>
<p class="text-white/60 text-sm font-medium">12 groups · 48 teams · Top 2 advance automatically</p>

<!-- After -->
<h1 class="font-display text-5xl text-white tracking-wider mb-1">STANDINGS</h1>
<p class="text-white/60 text-sm font-medium">Group Stage · Play Off</p>
```

**Change 2:** Wrap the entire content area in an Alpine component and add tabs. Find the outer `<div class="max-w-7xl mx-auto ...">` div. Change it to:

```html
<div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10"
     x-data="{ tab: '[[${defaultTab}]]' }">

  <!-- Page header -->
  <div class="mb-6 animate-fade-up">
    <h1 class="font-display text-5xl text-white tracking-wider mb-1">STANDINGS</h1>
    <p class="text-white/60 text-sm font-medium">Group Stage · Play Off</p>
  </div>

  <!-- Tab bar -->
  <div class="flex gap-2 mb-8 animate-fade-up stagger-1">
    <button @click="tab = 'groups'"
            :class="tab === 'groups' ? 'bg-green-dark text-white shadow-md' : 'bg-white/10 text-white/70 hover:bg-white/20'"
            class="px-6 py-2 rounded-xl text-sm font-semibold transition-all duration-200">
      Groups
    </button>
    <button @click="tab = 'playoff'"
            :class="tab === 'playoff' ? 'bg-green-dark text-white shadow-md' : 'bg-white/10 text-white/70 hover:bg-white/20'"
            class="px-6 py-2 rounded-xl text-sm font-semibold transition-all duration-200">
      Play Off
    </button>
  </div>

  <!-- Groups tab content -->
  <div x-show="tab === 'groups'" x-transition>

    <!-- Legend (move inside here) -->
    <!-- ... keep existing legend HTML ... -->

    <!-- Group cards grid (keep existing) -->
    <!-- ... keep existing group cards HTML ... -->

  </div>

  <!-- Play Off tab content -->
  <div x-show="tab === 'playoff'" x-transition>

    <!-- Bracket CSS (copied from bracket.html styles) -->
    <style>
      .bracket-scroll { overflow-x: auto; padding-bottom: 16px; }
      .bracket-grid   { display:flex; gap:32px; align-items:flex-start; min-width:max-content; padding:24px 16px; }
      .round-col      { display:flex; flex-direction:column; gap:0; }
      .round-header   {
        font-family:'Bebas Neue',sans-serif; font-size:1.05rem; letter-spacing:0.08em;
        text-align:center; padding:6px 16px;
        background:linear-gradient(135deg,#006b2a,#00c853); color:white;
        border-radius:8px 8px 0 0; position:sticky; top:0; z-index:1;
        overflow:hidden;
      }
      .round-header::after {
        content:''; position:absolute; top:0; left:-100%; width:60%; height:100%;
        background:linear-gradient(90deg,transparent,rgba(255,255,255,0.3),transparent);
        animation:shimmer 2.5s infinite;
      }
      @keyframes shimmer { to { left:200%; } }
      .matches-col { display:flex; flex-direction:column; justify-content:space-around; flex:1; gap:10px; padding:10px 0; }
      .match-card {
        background:white; border-radius:12px; border:2px solid #e2f5ea;
        padding:10px 14px; min-width:180px;
        box-shadow:0 2px 8px rgba(0,107,42,0.08); transition:box-shadow .2s,transform .2s;
      }
      .match-card:hover { transform:translateY(-2px); box-shadow:0 6px 20px rgba(0,107,42,0.15); }
      .match-card.finished { border-color:#00c853; }
      .team-row { display:flex; align-items:center; gap:8px; padding:4px 0; }
      .team-row + .team-row { border-top:1px solid #f0fdf5; }
      .flag-sm { width:26px; height:26px; border-radius:50%; object-fit:cover; border:1.5px solid #e2f5ea; flex-shrink:0; }
      .team-name { flex:1; font-size:0.83rem; font-weight:600; color:#1a3a2a; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
      @media (max-width:640px) {
        .team-name { font-size:0.65rem; }
        .match-card { min-width:140px; padding:8px 10px; }
        .flag-sm { width:20px; height:20px; }
      }
      .team-name.tbd { color:#94a3b8; font-style:italic; font-weight:400; }
      .score { font-family:'Bebas Neue',sans-serif; font-size:1.15rem; color:#006b2a; min-width:18px; text-align:right; }
      .score.winner { color:#00c853; }
      .tbd-dots { display:flex; gap:3px; align-items:center; }
      .tbd-dot  { width:4px; height:4px; border-radius:50%; background:#cbd5e1; animation:pulse 1.5s ease-in-out infinite; }
      .tbd-dot:nth-child(2) { animation-delay:.3s; }
      .tbd-dot:nth-child(3) { animation-delay:.6s; }
      @keyframes pulse { 0%,100%{opacity:.4} 50%{opacity:1} }
      .round-col.final .match-card { border-color:#FFD600; box-shadow:0 4px 20px rgba(255,214,0,0.25); }
      .round-col.third .match-card { border-color:#94a3b8; }
    </style>

    <div th:replace="~{fragments/bracket-view :: bracket-view}"></div>

  </div>

</div>
```

> **Note:** When editing `groups.html`, move the existing legend and group cards grid divs inside the `x-show="tab === 'groups'"` div. Keep all their HTML unchanged — just wrap them.

- [ ] **Step 3: Build**

```bash
./mvnw compile -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/GroupController.java \
        src/main/resources/templates/groups.html
git commit -m "feat: add Groups/Play Off tabs to Standing page"
```

---

## Task 5: Leaderboard — Collapsible Group Stage

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/controller/community/CommunityLeaderboardController.java`
- Modify: `src/main/resources/templates/community/leaderboard.html`

- [ ] **Step 1: Add `gsOpenDefault` and `gsStage` to the leaderboard controller**

Open `CommunityLeaderboardController.java`. In the `leaderboard()` method, before the `return "community/leaderboard"` statement, add:

```java
boolean gsOpenDefault = LocalDate.now().isBefore(LocalDate.of(2026, 6, 28));
model.addAttribute("gsOpenDefault", gsOpenDefault);
model.addAttribute("gsStage", MatchStage.GROUP);
```

`MatchStage` is already imported in the file.

- [ ] **Step 2: Add Alpine state to the leaderboard outer wrapper**

Open `src/main/resources/templates/community/leaderboard.html`.

Find the `<div class="lb-outer"` line (around line 414). Add `x-data` to it:

```html
<!-- Before -->
<div class="lb-outer" th:unless="${#lists.isEmpty(entries)}">

<!-- After -->
<div class="lb-outer" th:unless="${#lists.isEmpty(entries)}"
     x-data="{ gsOpen: [[${gsOpenDefault}]] }">
```

- [ ] **Step 3: Add collapse toggle button to the first GS phase header cell**

Find the GS phase header block (around line 436–443):

```html
<div th:each="entry, iter : ${groupRounds}"
     th:id="'ph-gs-r' + ${iter.count}"
     class="lb-phase-hd-cell phase-gs"
     th:classappend="${entry.key == currentRoundLabel} ? ' lb-phase-current' : ''"
     th:style="'width:' + (${entry.value.size()} * 76) + 'px'">
  <span th:text="'⚽ GS · Round ' + ${iter.count}">⚽ GS · Round 1</span>
</div>
```

Replace with:

```html
<div th:each="entry, iter : ${groupRounds}"
     th:id="'ph-gs-r' + ${iter.count}"
     class="lb-phase-hd-cell phase-gs"
     th:classappend="${entry.key == currentRoundLabel} ? ' lb-phase-current' : ''"
     th:style="'width:' + (${entry.value.size()} * 76) + 'px'"
     x-show="gsOpen || [[${iter.first}]]">
  <span th:text="'⚽ GS · Round ' + ${iter.count}" x-show="gsOpen">⚽ GS · Round 1</span>
  <th:block th:if="${iter.first}">
    <span x-show="!gsOpen" class="text-white/60 text-xs">⚽ GS (collapsed)</span>
    <button @click="gsOpen = !gsOpen"
            class="ml-auto flex-shrink-0 w-5 h-5 flex items-center justify-center rounded hover:bg-white/20 transition-colors"
            :title="gsOpen ? 'Collapse Group Stage' : 'Expand Group Stage'">
      <svg class="w-3 h-3 text-white transition-transform" :class="gsOpen ? '' : 'rotate-180'"
           viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
        <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7"/>
      </svg>
    </button>
  </th:block>
</div>
```

- [ ] **Step 4: Hide GS game-header columns when collapsed**

Find the game header `th:block th:each="entry : ${groupRounds}"` (around line 454):

```html
<th:block th:each="entry : ${groupRounds}">
  <div th:each="m : ${entry.value}"
       class="lb-game-col-hd"
       ...>
```

Wrap the entire `th:block` with `x-show`:

```html
<th:block th:each="entry : ${groupRounds}">
  <div th:each="m : ${entry.value}"
       class="lb-game-col-hd"
       x-show="gsOpen"
       th:data-date="${#temporals.format(m.kickoffTime, 'yyyy-MM-dd')}">
```

(Add `x-show="gsOpen"` to each `lb-game-col-hd` div.)

Also add a collapsed GS summary column that shows only when closed — add this immediately after the last `th:block th:each="entry : ${groupRounds}"` close in the game header row:

```html
<!-- GS total column — shown only when GS is collapsed -->
<div class="lb-psc-hd" x-show="!gsOpen" x-cloak>
  <div class="lb-psc-hd-label" style="background:linear-gradient(90deg,#004d1e,#00a845)">GS</div>
</div>
```

- [ ] **Step 5: Hide GS match cells in data rows when collapsed**

Find the body `th:block th:each="roundEntry : ${groupRounds}"` (around line 562):

```html
<th:block th:each="roundEntry : ${groupRounds}">
  <div th:each="m : ${roundEntry.value}"
       ...
       th:class="'lb-game-cell ' + ...">
```

Add `x-show="gsOpen"` to each `lb-game-cell` div:

```html
<th:block th:each="roundEntry : ${groupRounds}">
  <div th:each="m : ${roundEntry.value}"
       x-show="gsOpen"
       ...
       th:class="'lb-game-cell ' + ...">
```

Then add a GS total cell (visible when collapsed) after the last `th:block` for groupRounds in the data row, before the knockout phase cells:

```html
<!-- GS total cell — visible only when GS is collapsed -->
<div class="lb-psc-cell"
     x-show="!gsOpen" x-cloak
     th:with="pts=${phasePoints != null ? phasePoints.get(entry.userId)?.get(gsStage) : null}"
     th:class="'lb-psc-cell ' + (${pts == null} ? 'cell-pending' : (${pts >= 3} ? 'cell-exact' : (${pts == 2} ? 'cell-draw' : (${pts == 1} ? 'cell-winner' : 'cell-zero'))))">
  <span th:text="${pts != null ? pts : '·'}">·</span>
</div>
```

- [ ] **Step 6: Add `x-cloak` CSS rule**

Check if `[x-cloak] { display: none; }` already exists in `leaderboard.html`'s `<style>` block. If not, add it.

- [ ] **Step 7: Build**

```bash
./mvnw compile -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/community/CommunityLeaderboardController.java \
        src/main/resources/templates/community/leaderboard.html
git commit -m "feat: add collapsible Group Stage to leaderboard, default collapsed after GS ends"
```

---

## Task 6: Cumulative Exact Score Heroes Service

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/repository/PredictionRepository.java`
- Modify: `src/main/java/com/worldcup/prediction/service/DailyExactPredictorService.java`
- Create: `src/test/java/com/worldcup/prediction/service/DailyExactPredictorServiceCumulativeTest.java`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/com/worldcup/prediction/service/DailyExactPredictorServiceCumulativeTest.java`:

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.PredictionScore;
import com.worldcup.prediction.dto.DailyExactPredictorDto;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyExactPredictorServiceCumulativeTest {

    @Mock PredictionRepository predictionRepository;
    @Mock MatchRepository matchRepository;
    @InjectMocks DailyExactPredictorService service;

    private User userA;
    private User userB;
    private Match groupMatch;
    private Match knockoutMatch;

    @BeforeEach
    void setUp() {
        userA = new User(); userA.setId(1L); userA.setFirstName("Alice"); userA.setLastName("Smith");
        userB = new User(); userB.setId(2L); userB.setFirstName("Bob");   userB.setLastName("Jones");

        groupMatch = new Match();
        groupMatch.setId(10L);
        groupMatch.setStage(MatchStage.GROUP);
        groupMatch.setHomeScore(2); groupMatch.setAwayScore(1);
        groupMatch.setKickoffTime(LocalDateTime.now().minusDays(5));

        knockoutMatch = new Match();
        knockoutMatch.setId(20L);
        knockoutMatch.setStage(MatchStage.ROUND_OF_32);
        knockoutMatch.setHomeScore(1); knockoutMatch.setAwayScore(0);
        knockoutMatch.setKickoffTime(LocalDateTime.now().minusDays(1));
    }

    private Prediction exactPred(User user, Match match) {
        Prediction p = new Prediction();
        p.setUser(user);
        p.setMatch(match);
        p.setScoreResult(PredictionScore.EXACT);
        p.setPredictedHome(match.getHomeScore());
        p.setPredictedAway(match.getAwayScore());
        return p;
    }

    @Test
    void getCumulativeHeroes_all_returnsBothStages() {
        List<Prediction> preds = List.of(
                exactPred(userA, groupMatch),
                exactPred(userA, knockoutMatch),
                exactPred(userB, groupMatch));

        when(predictionRepository.findCumulativeExactPredictions(1L, "all")).thenReturn(preds);

        List<DailyExactPredictorDto> result = service.getCumulativeHeroes(1L, "all");

        assertThat(result).hasSize(2);
        DailyExactPredictorDto alice = result.stream()
                .filter(d -> d.getUserId().equals(1L)).findFirst().orElseThrow();
        assertThat(alice.getExactCount()).isEqualTo(2);
    }

    @Test
    void getCumulativeHeroes_group_returnsOnlyGroupPreds() {
        List<Prediction> groupPreds = List.of(exactPred(userA, groupMatch));

        when(predictionRepository.findCumulativeExactPredictions(1L, "group")).thenReturn(groupPreds);

        List<DailyExactPredictorDto> result = service.getCumulativeHeroes(1L, "group");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExactCount()).isEqualTo(1);
    }

    @Test
    void getCumulativeHeroes_sortsByExactCountDesc() {
        List<Prediction> preds = List.of(
                exactPred(userB, groupMatch),
                exactPred(userA, groupMatch),
                exactPred(userA, knockoutMatch));

        when(predictionRepository.findCumulativeExactPredictions(1L, "all")).thenReturn(preds);

        List<DailyExactPredictorDto> result = service.getCumulativeHeroes(1L, "all");

        assertThat(result.get(0).getUserId()).isEqualTo(1L); // Alice has 2
        assertThat(result.get(1).getUserId()).isEqualTo(2L); // Bob has 1
    }

    @Test
    void getCumulativeHeroes_emptyWhenNoExactPredictions() {
        when(predictionRepository.findCumulativeExactPredictions(1L, "all")).thenReturn(List.of());

        List<DailyExactPredictorDto> result = service.getCumulativeHeroes(1L, "all");

        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./mvnw test -pl . -Dtest=DailyExactPredictorServiceCumulativeTest -q 2>&1 | tail -20
```

Expected: compilation error — `findCumulativeExactPredictions` and `getCumulativeHeroes` don't exist yet.

- [ ] **Step 3: Add JPQL query to `PredictionRepository`**

Open `src/main/java/com/worldcup/prediction/repository/PredictionRepository.java`. Add at the end, before the closing `}`:

```java
@Query("""
        SELECT p FROM Prediction p
        JOIN FETCH p.user u
        JOIN FETCH p.match m
        LEFT JOIN FETCH m.homeTeam
        LEFT JOIN FETCH m.awayTeam
        WHERE p.community.id = :communityId
          AND p.scoreResult = 'EXACT'
          AND (
            :stageFilter = 'all'
            OR (:stageFilter = 'group'   AND m.stage = com.worldcup.prediction.domain.enums.MatchStage.GROUP)
            OR (:stageFilter = 'playoff' AND m.stage <> com.worldcup.prediction.domain.enums.MatchStage.GROUP)
          )
        ORDER BY u.id ASC
        """)
List<Prediction> findCumulativeExactPredictions(
        @Param("communityId") Long communityId,
        @Param("stageFilter") String stageFilter);
```

- [ ] **Step 4: Add `getCumulativeHeroes()` to `DailyExactPredictorService`**

Open `src/main/java/com/worldcup/prediction/service/DailyExactPredictorService.java`. Add after `getLastMatchdayDate()`:

```java
/**
 * Returns cumulative exact score heroes for a community, optionally filtered by stage.
 *
 * @param communityId  the community to query
 * @param stageFilter  "all", "group", or "playoff"
 */
public List<DailyExactPredictorDto> getCumulativeHeroes(Long communityId, String stageFilter) {
    List<Prediction> exactPredictions =
            predictionRepository.findCumulativeExactPredictions(communityId, stageFilter);

    if (exactPredictions.isEmpty()) {
        return List.of();
    }

    Map<Long, List<Prediction>> byUser = exactPredictions.stream()
            .collect(Collectors.groupingBy(p -> p.getUser().getId(), LinkedHashMap::new, Collectors.toList()));

    List<DailyExactPredictorDto> result = new ArrayList<>();
    for (var entry : byUser.entrySet()) {
        List<Prediction> userPredictions = entry.getValue();
        var user = userPredictions.get(0).getUser();

        List<DailyExactPredictorDto.ExactMatchDto> exactMatches = userPredictions.stream()
                .map(p -> {
                    Match m = p.getMatch();
                    Integer homeScore = m.getEffectiveHomeScore();
                    Integer awayScore = m.getEffectiveAwayScore();
                    if (homeScore == null || awayScore == null) return null;
                    return DailyExactPredictorDto.ExactMatchDto.builder()
                            .homeTeamName(m.getHomeTeam() != null ? m.getHomeTeam().getName() : m.getHomeTeamPlaceholder())
                            .awayTeamName(m.getAwayTeam() != null ? m.getAwayTeam().getName() : m.getAwayTeamPlaceholder())
                            .homeTeamFlagCode(m.getHomeTeam() != null ? m.getHomeTeam().getFlagCode() : null)
                            .awayTeamFlagCode(m.getAwayTeam() != null ? m.getAwayTeam().getFlagCode() : null)
                            .homeTeamFifaCode(m.getHomeTeam() != null ? m.getHomeTeam().getFifaCode() : null)
                            .awayTeamFifaCode(m.getAwayTeam() != null ? m.getAwayTeam().getFifaCode() : null)
                            .homeScore(homeScore)
                            .awayScore(awayScore)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();

        result.add(DailyExactPredictorDto.builder()
                .userId(user.getId())
                .displayName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .exactCount(userPredictions.size())
                .exactMatches(exactMatches)
                .build());
    }

    result.sort(Comparator.comparingInt(DailyExactPredictorDto::getExactCount).reversed()
            .thenComparing(DailyExactPredictorDto::getDisplayName));

    return result.stream().limit(20).toList();
}
```

Ensure `java.util.Objects` is imported (add if missing).

- [ ] **Step 5: Run tests**

```bash
./mvnw test -pl . -Dtest=DailyExactPredictorServiceCumulativeTest -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`, all 4 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/worldcup/prediction/repository/PredictionRepository.java \
        src/main/java/com/worldcup/prediction/service/DailyExactPredictorService.java \
        src/test/java/com/worldcup/prediction/service/DailyExactPredictorServiceCumulativeTest.java
git commit -m "feat: add getCumulativeHeroes() with group/playoff/all stage filter"
```

---

## Task 7: Exact Score Heroes Stage Tabs

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/controller/community/CommunityHomeController.java`
- Create: `src/main/resources/templates/fragments/heroes-content.html`
- Modify: `src/main/resources/templates/community/home.html`

- [ ] **Step 1: Create `fragments/heroes-content.html`**

Create `src/main/resources/templates/fragments/heroes-content.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
<div th:fragment="heroes-content">

  <div th:if="${exactPredictors == null or exactPredictors.isEmpty()}"
       class="text-center py-8 text-gray-400 text-sm">
    No exact score predictions yet for this filter.
  </div>

  <div th:unless="${exactPredictors == null or exactPredictors.isEmpty()}"
       class="hero-scroll-container">
    <div class="hero-scroll-content">
      <div th:each="predictor, iter : ${exactPredictors}"
           class="hero-card"
           th:classappend="${iter.index == 0} ? 'hero-rank-1' : (${iter.index == 1} ? 'hero-rank-2' : (${iter.index == 2} ? 'hero-rank-3' : ''))">

        <div class="hero-rank-badge"
             th:text="${iter.index == 0} ? '🥇' : (${iter.index == 1} ? '🥈' : (${iter.index == 2} ? '🥉' : ${iter.count}))">1</div>

        <div class="hero-header">
          <div class="hero-avatar-container">
            <div class="hero-avatar-ring" th:if="${iter.index < 3}"></div>
            <div class="hero-avatar">
              <img th:if="${predictor.avatarUrl != null}"
                   th:src="${predictor.avatarUrl}"
                   onerror="this.style.display='none';this.parentElement.classList.add('hero-avatar-fallback')"/>
              <span th:if="${predictor.avatarUrl == null}" class="hero-avatar-initials"
                    th:text="${predictor.initials}">AB</span>
            </div>
          </div>
          <div class="hero-user-info">
            <div class="hero-user-name" th:text="${predictor.displayName}">Player Name</div>
            <div class="hero-stat-badge">
              <svg class="hero-stat-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round"
                      d="M9 12l2 2 4-4M7.835 4.697a3.42 3.42 0 001.946-.806 3.42 3.42 0 014.438 0 3.42 3.42 0 001.946.806 3.42 3.42 0 013.138 3.138 3.42 3.42 0 00.806 1.946 3.42 3.42 0 010 4.438 3.42 3.42 0 00-.806 1.946 3.42 3.42 0 01-3.138 3.138 3.42 3.42 0 00-1.946.806 3.42 3.42 0 01-4.438 0 3.42 3.42 0 00-1.946-.806 3.42 3.42 0 01-3.138-3.138 3.42 3.42 0 00-.806-1.946 3.42 3.42 0 010-4.438 3.42 3.42 0 00.806-1.946 3.42 3.42 0 013.138-3.138z"/>
              </svg>
              <span class="hero-stat-value" th:text="${predictor.exactCount}">3</span>
              <span class="hero-stat-label"
                    th:text="${predictor.exactCount == 1 ? 'exact score' : 'exact scores'}">exact scores</span>
            </div>
          </div>
        </div>

        <div class="hero-matches">
          <div class="hero-matches-title">Correct Predictions</div>
          <div th:each="match : ${predictor.exactMatches}" class="hero-match">
            <div class="hero-match-teams">
              <div class="hero-match-team">
                <div class="hero-flag-wrap">
                  <img th:if="${match.homeTeamFlagCode != null}"
                       th:src="@{'/images/flags/' + ${match.homeTeamFlagCode} + '.svg'}"
                       class="hero-match-flag" th:alt="${match.homeTeamName}"/>
                  <span class="hero-match-tla" th:if="${match.homeTeamFifaCode != null}"
                        th:text="${match.homeTeamFifaCode}"></span>
                </div>
                <span class="hero-match-name" th:text="${match.homeTeamName}">Team</span>
              </div>
              <div class="hero-match-score"
                   th:text="${match.homeScore + '–' + match.awayScore}">2–1</div>
              <div class="hero-match-team hero-match-team-away">
                <span class="hero-match-name" th:text="${match.awayTeamName}">Team</span>
                <div class="hero-flag-wrap">
                  <img th:if="${match.awayTeamFlagCode != null}"
                       th:src="@{'/images/flags/' + ${match.awayTeamFlagCode} + '.svg'}"
                       class="hero-match-flag" th:alt="${match.awayTeamName}"/>
                  <span class="hero-match-tla" th:if="${match.awayTeamFifaCode != null}"
                        th:text="${match.awayTeamFifaCode}"></span>
                </div>
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>
  </div>

</div>
</body>
</html>
```

- [ ] **Step 2: Update `CommunityHomeController`**

Replace the entire `CommunityHomeController.java`:

```java
package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.dto.DailyExactPredictorDto;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.DailyExactPredictorService;
import com.worldcup.prediction.service.LeaderboardService;
import com.worldcup.prediction.service.MatchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/c/{slug}")
@RequiredArgsConstructor
public class CommunityHomeController {

    private final LeaderboardService leaderboardService;
    private final MatchService matchService;
    private final DailyExactPredictorService dailyExactPredictorService;

    @GetMapping("/home")
    public String home(@PathVariable String slug,
                       @AuthenticationPrincipal CustomOAuth2User currentUser,
                       HttpServletRequest request, Model model) {
        Community community = (Community) request.getAttribute("community");
        Long userId = currentUser.getUserId();
        Long communityId = community.getId();

        List<LeaderboardEntryDto> topTen = leaderboardService.getTopN(10, communityId);
        int userRank = leaderboardService.getEntryForUser(userId, communityId)
                .map(LeaderboardEntryDto::getRank).orElse(0);
        int openMatchCount = matchService.getOpenMatchCount();

        List<DailyExactPredictorDto> exactPredictors =
                dailyExactPredictorService.getCumulativeHeroes(communityId, "all");
        int heroTotalCount = exactPredictors.stream()
                .mapToInt(DailyExactPredictorDto::getExactCount).sum();

        model.addAttribute("community", community);
        model.addAttribute("slug", slug);
        model.addAttribute("topTen", topTen);
        model.addAttribute("userRank", userRank);
        model.addAttribute("openMatchCount", openMatchCount);
        model.addAttribute("exactPredictors", exactPredictors);
        model.addAttribute("heroStage", "all");
        model.addAttribute("heroTotalCount", heroTotalCount);
        model.addAttribute("pageTitle", community.getName() + " · Home");
        return "community/home";
    }

    @GetMapping("/heroes")
    public String heroesFragment(@PathVariable String slug,
                                  @RequestParam(defaultValue = "all") String stage,
                                  HttpServletRequest request, Model model) {
        Community community = (Community) request.getAttribute("community");
        List<DailyExactPredictorDto> exactPredictors =
                dailyExactPredictorService.getCumulativeHeroes(community.getId(), stage);
        int heroTotalCount = exactPredictors.stream()
                .mapToInt(DailyExactPredictorDto::getExactCount).sum();
        model.addAttribute("exactPredictors", exactPredictors);
        model.addAttribute("heroStage", stage);
        model.addAttribute("heroTotalCount", heroTotalCount);
        model.addAttribute("slug", slug);
        return "fragments/heroes-content :: heroes-content";
    }
}
```

- [ ] **Step 3: Update `community/home.html` — heroes section**

Open `src/main/resources/templates/community/home.html`.

Find the heroes section (starts at `<div th:if="${exactPredictors != null && !exactPredictors.isEmpty()}"`, around line 43). Replace the entire heroes section (from that div to its closing `</div>`) with:

```html
<div class="mb-10 animate-fade-up stagger-2">

  <!-- Section header -->
  <div class="flex items-center justify-between mb-3 px-1">
    <div>
      <h2 class="text-xl font-bold text-gray-800 flex items-center gap-2">
        <span class="hero-trophy-icon">🏆</span>
        Exact Score Heroes
      </h2>
      <p class="text-xs text-gray-400 mt-0.5">
        <span th:switch="${heroStage}">
          <span th:case="'group'">Group Stage</span>
          <span th:case="'playoff'">Play Off</span>
          <span th:case="*">All stages</span>
        </span>
        · <span th:text="${heroTotalCount}">0</span> exact predictions
      </p>
    </div>
  </div>

  <!-- Stage filter tabs -->
  <div class="flex gap-2 mb-4" id="hero-tabs">
    <button th:classappend="${heroStage == 'all'} ? 'bg-green-dark text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'"
            class="px-4 py-1.5 rounded-lg text-xs font-semibold transition-colors"
            hx-get="./heroes?stage=all"
            hx-target="#heroes-container"
            hx-swap="innerHTML"
            hx-on::after-request="updateHeroTabs('all')">
      All
    </button>
    <button th:classappend="${heroStage == 'group'} ? 'bg-green-dark text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'"
            class="px-4 py-1.5 rounded-lg text-xs font-semibold transition-colors"
            hx-get="./heroes?stage=group"
            hx-target="#heroes-container"
            hx-swap="innerHTML"
            hx-on::after-request="updateHeroTabs('group')">
      Group Stage
    </button>
    <button th:classappend="${heroStage == 'playoff'} ? 'bg-green-dark text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'"
            class="px-4 py-1.5 rounded-lg text-xs font-semibold transition-colors"
            hx-get="./heroes?stage=playoff"
            hx-target="#heroes-container"
            hx-swap="innerHTML"
            hx-on::after-request="updateHeroTabs('playoff')">
      Play Off
    </button>
  </div>

  <!-- Heroes content (HTMX swap target) -->
  <div id="heroes-container">
    <div th:replace="~{fragments/heroes-content :: heroes-content}"></div>
  </div>

</div>

<script>
function updateHeroTabs(active) {
  document.querySelectorAll('#hero-tabs button').forEach(function(btn) {
    var stage = btn.getAttribute('hx-get').split('stage=')[1];
    if (stage === active) {
      btn.classList.add('bg-green-dark', 'text-white');
      btn.classList.remove('bg-gray-100', 'text-gray-600', 'hover:bg-gray-200');
    } else {
      btn.classList.remove('bg-green-dark', 'text-white');
      btn.classList.add('bg-gray-100', 'text-gray-600', 'hover:bg-gray-200');
    }
  });
}
</script>
```

> **Note:** The `green-dark` Tailwind class must be available on the home page. Check the existing hero card styles in `home.html` — use whatever green class matches the page's active-tab styling if `green-dark` is not defined there.

- [ ] **Step 4: Build**

```bash
./mvnw compile -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Run full test suite**

```bash
./mvnw test -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/community/CommunityHomeController.java \
        src/main/resources/templates/fragments/heroes-content.html \
        src/main/resources/templates/community/home.html
git commit -m "feat: add All/Group Stage/Play Off tabs to Exact Score Heroes"
```

---

## Self-Review Checklist

- [x] **Spec coverage:** All 5 features covered: knockout sync (Task 1+2), Fixtures (auto-fixed by Task 1), bracket fragment (Task 3), Standing tabs (Task 4), Leaderboard collapse (Task 5), heroes filter (Tasks 6+7).
- [x] **No placeholders:** All code is complete. No TBDs.
- [x] **Type consistency:** `DailyExactPredictorDto` used consistently across Tasks 6 and 7. `MatchStage.GROUP` enum value referenced in both service and template. `bracketByStage` map type `Map<String, List<Match>>` matches `BracketController` pattern throughout.
- [x] **`FootballApiResponseDto` constructor:** Verified — record is `FootballApiResponseDto(Integer count, List<FootballApiMatchDto> matches)`. Tests use `new FootballApiResponseDto(N, List.of(...))` throughout.
