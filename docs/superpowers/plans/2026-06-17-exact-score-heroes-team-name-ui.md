# Exact Score Heroes — Team Name UI Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix long team names overflowing on web and show compact flag+TLA on mobile in the EXACT SCORE HEROES section of the community home page.

**Architecture:** Add FIFA 3-letter code (TLA) fields to `ExactMatchDto` populated from `Team.fifaCode`, then update the Thymeleaf template with a CSS grid fix for web truncation and a mobile-only flag+TLA layout.

**Tech Stack:** Java 21, Spring Boot, Lombok, Thymeleaf, plain CSS (no JS changes needed)

---

## Files

| File | Change |
|------|--------|
| `src/main/java/com/worldcup/prediction/dto/DailyExactPredictorDto.java` | Add `homeTeamFifaCode`, `awayTeamFifaCode` to inner `ExactMatchDto` |
| `src/main/java/com/worldcup/prediction/service/DailyExactPredictorService.java` | Populate new fields from `team.getFifaCode()` |
| `src/main/resources/templates/community/home.html` | CSS fix + mobile layout |
| `src/test/java/com/worldcup/prediction/service/DailyExactPredictorServiceTest.java` | New test file |

---

### Task 1: Add FIFA code fields to ExactMatchDto

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/dto/DailyExactPredictorDto.java`
- Test: `src/test/java/com/worldcup/prediction/service/DailyExactPredictorServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/worldcup/prediction/service/DailyExactPredictorServiceTest.java`:

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyExactPredictorServiceTest {

    @Mock MatchRepository matchRepository;
    @Mock PredictionRepository predictionRepository;
    @InjectMocks DailyExactPredictorService service;

    @Test
    void exactMatchDto_includesFifaCodes() {
        Team home = Team.builder().id(1L).name("Brazil").fifaCode("BRA").flagCode("br").build();
        Team away = Team.builder().id(2L).name("Argentina").fifaCode("ARG").flagCode("ar").build();

        Match match = new Match();
        match.setId(10L);
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setHomeScore(2);
        match.setAwayScore(1);
        match.setStatus(MatchStatus.COMPLETED);
        match.setKickoffTime(LocalDateTime.now().minusHours(2));
        match.setRoundLabel("Group Stage");

        User user = new User();
        user.setId(99L);
        user.setFullName("Test User");

        Prediction prediction = new Prediction();
        prediction.setUser(user);
        prediction.setMatch(match);

        when(matchRepository.findByStatusWithTeams(MatchStatus.COMPLETED)).thenReturn(List.of(match));
        when(predictionRepository.findExactPredictionsByMatchIdsAndCommunityId(eq(List.of(10L)), eq(1L)))
                .thenReturn(List.of(prediction));

        var result = service.getLastMatchdayExactPredictors(1L, LocalDateTime.now());

        assertThat(result).hasSize(1);
        var exactMatch = result.get(0).getExactMatches().get(0);
        assertThat(exactMatch.getHomeTeamFifaCode()).isEqualTo("BRA");
        assertThat(exactMatch.getAwayTeamFifaCode()).isEqualTo("ARG");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./mvnw test -pl . -Dtest=DailyExactPredictorServiceTest -q
```

Expected: compilation failure — `getHomeTeamFifaCode()` / `getAwayTeamFifaCode()` not found, and `Match` setters may not exist.

- [ ] **Step 3: Add the two fields to ExactMatchDto**

In `src/main/java/com/worldcup/prediction/dto/DailyExactPredictorDto.java`, update the inner class:

```java
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExactMatchDto {
        private String homeTeamName;
        private String awayTeamName;
        private String homeTeamFlagCode;
        private String awayTeamFlagCode;
        private String homeTeamFifaCode;
        private String awayTeamFifaCode;
        private int homeScore;
        private int awayScore;
    }
```

- [ ] **Step 4: Run the test again — expect a different failure**

```bash
./mvnw test -pl . -Dtest=DailyExactPredictorServiceTest -q
```

Expected: now compiles but fails asserting `homeTeamFifaCode` is "BRA" (field exists but service doesn't populate it yet).

---

### Task 2: Populate FIFA codes in the service

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/service/DailyExactPredictorService.java`

- [ ] **Step 1: Add fifaCode population to the builder**

In `DailyExactPredictorService.java`, update the `ExactMatchDto.builder()` call (lines 58–65):

```java
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
```

- [ ] **Step 2: Run the test — expect pass**

```bash
./mvnw test -pl . -Dtest=DailyExactPredictorServiceTest -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worldcup/prediction/dto/DailyExactPredictorDto.java \
        src/main/java/com/worldcup/prediction/service/DailyExactPredictorService.java \
        src/test/java/com/worldcup/prediction/service/DailyExactPredictorServiceTest.java
git commit -m "feat: add homeTeamFifaCode/awayTeamFifaCode to ExactMatchDto"
```

---

### Task 3: Fix web truncation in community/home.html

**Files:**
- Modify: `src/main/resources/templates/community/home.html`

The existing `.hero-match-name` already has `white-space: nowrap; overflow: hidden; text-overflow: ellipsis` but the parent `.hero-match-team` flex container has no `min-width` constraint, so the `1fr` grid column doesn't cap the text width. Adding `min-width: 0` fixes this.

- [ ] **Step 1: Add min-width: 0 to .hero-match-team**

Find the existing `.hero-match-team` CSS block (around line 384):

```css
        .hero-match-team {
          display: flex;
          align-items: center;
          gap: 8px;
        }
```

Replace with:

```css
        .hero-match-team {
          display: flex;
          align-items: center;
          gap: 8px;
          min-width: 0;
        }
```

- [ ] **Step 2: Verify the app compiles**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`

---

### Task 4: Mobile layout — flag + always-visible TLA

**Files:**
- Modify: `src/main/resources/templates/community/home.html`

On mobile (≤768px): hide the `.hero-match-name` text spans and show a `.hero-match-tla` label below each flag instead.

- [ ] **Step 1: Add CSS for the flag wrapper and TLA label, plus mobile media query**

Find the closing `</style>` tag at the end of the EXACT SCORE HEROES `<style>` block (just before `</div>` that ends the style tag, around line 423). Insert before `</style>`:

```css
        .hero-match-tla {
          display: none;
          font-size: 10px;
          font-weight: 700;
          color: #006b2a;
          text-transform: uppercase;
          letter-spacing: 0.5px;
        }
        .hero-flag-wrap {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 3px;
        }

        @media (max-width: 768px) {
          .hero-match-name {
            display: none;
          }
          .hero-match-tla {
            display: block;
          }
        }
```

- [ ] **Step 2: Update the HTML — wrap home flag in hero-flag-wrap and add TLA span**

Find the home team block in the `th:each="match"` loop (around line 102):

```html
                <div class="hero-match-team">
                  <img th:if="${match.homeTeamFlagCode != null}"
                       th:src="'/images/flags/' + ${match.homeTeamFlagCode} + '.svg'"
                       class="hero-match-flag" th:alt="${match.homeTeamName}"/>
                  <span class="hero-match-name" th:text="${match.homeTeamName}">Home Team</span>
                </div>
```

Replace with:

```html
                <div class="hero-match-team">
                  <div class="hero-flag-wrap">
                    <img th:if="${match.homeTeamFlagCode != null}"
                         th:src="'/images/flags/' + ${match.homeTeamFlagCode} + '.svg'"
                         class="hero-match-flag" th:alt="${match.homeTeamName}"/>
                    <span class="hero-match-tla" th:text="${match.homeTeamFifaCode}"></span>
                  </div>
                  <span class="hero-match-name" th:text="${match.homeTeamName}">Home Team</span>
                </div>
```

- [ ] **Step 3: Update the HTML — wrap away flag in hero-flag-wrap and add TLA span**

Find the away team block (around line 113):

```html
                <div class="hero-match-team hero-match-team-away">
                  <span class="hero-match-name" th:text="${match.awayTeamName}">Away Team</span>
                  <img th:if="${match.awayTeamFlagCode != null}"
                       th:src="'/images/flags/' + ${match.awayTeamFlagCode} + '.svg'"
                       class="hero-match-flag" th:alt="${match.awayTeamName}"/>
                </div>
```

Replace with:

```html
                <div class="hero-match-team hero-match-team-away">
                  <span class="hero-match-name" th:text="${match.awayTeamName}">Away Team</span>
                  <div class="hero-flag-wrap">
                    <img th:if="${match.awayTeamFlagCode != null}"
                         th:src="'/images/flags/' + ${match.awayTeamFlagCode} + '.svg'"
                         class="hero-match-flag" th:alt="${match.awayTeamName}"/>
                    <span class="hero-match-tla" th:text="${match.awayTeamFifaCode}"></span>
                  </div>
                </div>
```

- [ ] **Step 4: Verify the app builds**

```bash
./mvnw compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/community/home.html
git commit -m "feat: fix exact score heroes team name truncation on web and show TLA on mobile"
```

---

### Task 5: Run full test suite

- [ ] **Step 1: Run all tests**

```bash
./mvnw test -q
```

Expected: `BUILD SUCCESS` with no failures. If `DailyExactPredictorServiceTest` fails with a `NullPointerException` on `match.getEffectiveHomeScore()`, add stub setup to the test's `match` object: `match.setHomeScore(2); match.setAwayScore(1);` — these are already included in the test above.
