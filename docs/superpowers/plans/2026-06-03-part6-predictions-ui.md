# Predictions UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the core predictions page where authenticated participants submit match score predictions per round, view past results, and manage their tournament winner pick.
**Architecture:** Spring MVC PredictionController serves a Thymeleaf template driven by Alpine.js for list/card view toggling and per-match score state; HTMX handles round tab switching without full page reload; all-or-nothing submission is enforced both client-side (Alpine.js disabled submit) and server-side (PredictionService atomic validation).
**Tech Stack:** Thymeleaf, HTMX, Alpine.js, Spring MVC, Spring Security, Tailwind CSS
**Depends on:** Part 1 (entities: Match, Prediction, Team, User), Part 2 (PredictionService, ScoringService), Part 3 (repositories: MatchRepository, PredictionRepository)
**Next parts:** none for this page

---

## File Structure

```
src/main/java/com/worldcup/prediction/
  controller/
    PredictionController.java                 CREATE
  dto/
    PredictionSubmitDto.java                  CREATE
    MatchPredictionDto.java                   CREATE
    RoundSummaryDto.java                      CREATE
    PastRoundDto.java                         CREATE
    PastMatchResultDto.java                   CREATE
    TournamentWinnerSubmitDto.java            CREATE
  service/
    PredictionService.java                    MODIFY (add methods)

src/main/resources/templates/
  predictions.html                            CREATE
  fragments/
    predictions-round-content.html           CREATE

src/main/resources/static/
  css/predictions.css                         CREATE
```

---

### Task 1: DTOs

**Files:**
- Create: `src/main/java/com/worldcup/prediction/dto/MatchPredictionDto.java`
- Create: `src/main/java/com/worldcup/prediction/dto/PredictionSubmitDto.java`
- Create: `src/main/java/com/worldcup/prediction/dto/RoundSummaryDto.java`
- Create: `src/main/java/com/worldcup/prediction/dto/PastRoundDto.java`
- Create: `src/main/java/com/worldcup/prediction/dto/PastMatchResultDto.java`
- Create: `src/main/java/com/worldcup/prediction/dto/TournamentWinnerSubmitDto.java`

- [ ] **Step 1: Create MatchPredictionDto**

```java
// src/main/java/com/worldcup/prediction/dto/MatchPredictionDto.java
package com.worldcup.prediction.dto;

import java.time.LocalDateTime;

/**
 * Represents one match in the open round, plus the user's existing prediction (if any).
 */
public class MatchPredictionDto {
    private Long matchId;
    private int matchday;
    private String stage;            // "GROUP", "R32", "R16", "QF", "SF", "FINAL"
    private String group;            // "A"–"L" for group stage, null for knockouts
    private LocalDateTime kickoff;
    private LocalDateTime lockTime;  // kickoff - 1 hour
    private boolean locked;          // lockTime is in the past

    private String homeTeamName;
    private String homeTeamCode;     // ISO 3166-1 alpha-2 lowercase, e.g. "fr"
    private String awayTeamName;
    private String awayTeamCode;

    private String venue;

    // User's existing prediction (null if not yet submitted)
    private Integer predictedHome;
    private Integer predictedAway;
    private boolean predictionSaved; // true if a Prediction entity exists for this match+user

    // Getters and setters
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }

    public int getMatchday() { return matchday; }
    public void setMatchday(int matchday) { this.matchday = matchday; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public LocalDateTime getKickoff() { return kickoff; }
    public void setKickoff(LocalDateTime kickoff) { this.kickoff = kickoff; }

    public LocalDateTime getLockTime() { return lockTime; }
    public void setLockTime(LocalDateTime lockTime) { this.lockTime = lockTime; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public String getHomeTeamName() { return homeTeamName; }
    public void setHomeTeamName(String homeTeamName) { this.homeTeamName = homeTeamName; }

    public String getHomeTeamCode() { return homeTeamCode; }
    public void setHomeTeamCode(String homeTeamCode) { this.homeTeamCode = homeTeamCode; }

    public String getAwayTeamName() { return awayTeamName; }
    public void setAwayTeamName(String awayTeamName) { this.awayTeamName = awayTeamName; }

    public String getAwayTeamCode() { return awayTeamCode; }
    public void setAwayTeamCode(String awayTeamCode) { this.awayTeamCode = awayTeamCode; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public Integer getPredictedHome() { return predictedHome; }
    public void setPredictedHome(Integer predictedHome) { this.predictedHome = predictedHome; }

    public Integer getPredictedAway() { return predictedAway; }
    public void setPredictedAway(Integer predictedAway) { this.predictedAway = predictedAway; }

    public boolean isPredictionSaved() { return predictionSaved; }
    public void setPredictionSaved(boolean predictionSaved) { this.predictionSaved = predictionSaved; }
}
```

- [ ] **Step 2: Create PredictionSubmitDto**

```java
// src/main/java/com/worldcup/prediction/dto/PredictionSubmitDto.java
package com.worldcup.prediction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Submitted from the predictions form. Contains all predictions for one matchday round.
 * All-or-nothing: the list must contain exactly one entry per open match in the round.
 */
public class PredictionSubmitDto {

    @NotNull
    private Integer matchday;

    @NotNull
    @Size(min = 1)
    @Valid
    private List<SinglePrediction> predictions;

    public Integer getMatchday() { return matchday; }
    public void setMatchday(Integer matchday) { this.matchday = matchday; }

    public List<SinglePrediction> getPredictions() { return predictions; }
    public void setPredictions(List<SinglePrediction> predictions) { this.predictions = predictions; }

    public static class SinglePrediction {
        @NotNull
        private Long matchId;

        @NotNull
        @Min(0)
        private Integer homeScore;

        @NotNull
        @Min(0)
        private Integer awayScore;

        public Long getMatchId() { return matchId; }
        public void setMatchId(Long matchId) { this.matchId = matchId; }

        public Integer getHomeScore() { return homeScore; }
        public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }

        public Integer getAwayScore() { return awayScore; }
        public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }
    }
}
```

- [ ] **Step 3: Create RoundSummaryDto**

```java
// src/main/java/com/worldcup/prediction/dto/RoundSummaryDto.java
package com.worldcup.prediction.dto;

/**
 * Summary of one matchday for round tab navigation.
 */
public class RoundSummaryDto {
    private int matchday;
    private String label;        // "Matchday 1", "R32 – Day 1", etc.
    private String status;       // "PAST", "OPEN", "FUTURE"
    private int totalMatches;
    private int predictedCount;  // how many this user has predicted (for OPEN round)
    private int pointsEarned;    // total points for PAST round

    public int getMatchday() { return matchday; }
    public void setMatchday(int matchday) { this.matchday = matchday; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalMatches() { return totalMatches; }
    public void setTotalMatches(int totalMatches) { this.totalMatches = totalMatches; }

    public int getPredictedCount() { return predictedCount; }
    public void setPredictedCount(int predictedCount) { this.predictedCount = predictedCount; }

    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }
}
```

- [ ] **Step 4: Create PastMatchResultDto and PastRoundDto**

```java
// src/main/java/com/worldcup/prediction/dto/PastMatchResultDto.java
package com.worldcup.prediction.dto;

import java.time.LocalDateTime;

/**
 * A completed match with the user's prediction result for display in past rounds accordion.
 */
public class PastMatchResultDto {
    private Long matchId;
    private String homeTeamName;
    private String homeTeamCode;
    private String awayTeamName;
    private String awayTeamCode;
    private LocalDateTime kickoff;

    // Actual result
    private int actualHome;
    private int actualAway;

    // User's prediction (null values mean user did not predict)
    private Integer predictedHome;
    private Integer predictedAway;

    // Scoring outcome: "EXACT", "DRAW", "WINNER", "WRONG", "NOT_PREDICTED"
    private String outcome;
    private int pointsEarned;

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }

    public String getHomeTeamName() { return homeTeamName; }
    public void setHomeTeamName(String homeTeamName) { this.homeTeamName = homeTeamName; }

    public String getHomeTeamCode() { return homeTeamCode; }
    public void setHomeTeamCode(String homeTeamCode) { this.homeTeamCode = homeTeamCode; }

    public String getAwayTeamName() { return awayTeamName; }
    public void setAwayTeamName(String awayTeamName) { this.awayTeamName = awayTeamName; }

    public String getAwayTeamCode() { return awayTeamCode; }
    public void setAwayTeamCode(String awayTeamCode) { this.awayTeamCode = awayTeamCode; }

    public LocalDateTime getKickoff() { return kickoff; }
    public void setKickoff(LocalDateTime kickoff) { this.kickoff = kickoff; }

    public int getActualHome() { return actualHome; }
    public void setActualHome(int actualHome) { this.actualHome = actualHome; }

    public int getActualAway() { return actualAway; }
    public void setActualAway(int actualAway) { this.actualAway = actualAway; }

    public Integer getPredictedHome() { return predictedHome; }
    public void setPredictedHome(Integer predictedHome) { this.predictedHome = predictedHome; }

    public Integer getPredictedAway() { return predictedAway; }
    public void setPredictedAway(Integer predictedAway) { this.predictedAway = predictedAway; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }
}
```

```java
// src/main/java/com/worldcup/prediction/dto/PastRoundDto.java
package com.worldcup.prediction.dto;

import java.util.List;

/**
 * All completed matches in one past round with aggregated points.
 */
public class PastRoundDto {
    private int matchday;
    private String label;
    private int totalPoints;
    private List<PastMatchResultDto> matches;

    public int getMatchday() { return matchday; }
    public void setMatchday(int matchday) { this.matchday = matchday; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }

    public List<PastMatchResultDto> getMatches() { return matches; }
    public void setMatches(List<PastMatchResultDto> matches) { this.matches = matches; }
}
```

- [ ] **Step 5: Create TournamentWinnerSubmitDto**

```java
// src/main/java/com/worldcup/prediction/dto/TournamentWinnerSubmitDto.java
package com.worldcup.prediction.dto;

import jakarta.validation.constraints.NotNull;

public class TournamentWinnerSubmitDto {
    @NotNull
    private Long teamId;

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
}
```

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/worldcup/prediction/dto/
git commit -m "feat: add prediction DTOs for Part 6 predictions UI"
```

---

### Task 2: PredictionController

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/PredictionController.java`

- [ ] **Step 1: Create PredictionController**

```java
// src/main/java/com/worldcup/prediction/controller/PredictionController.java
package com.worldcup.prediction.controller;

import com.worldcup.prediction.dto.*;
import com.worldcup.prediction.entity.Team;
import com.worldcup.prediction.entity.User;
import com.worldcup.prediction.repository.TeamRepository;
import com.worldcup.prediction.service.PredictionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/predictions")
@PreAuthorize("hasAnyRole('PARTICIPANT', 'ADMIN')")
public class PredictionController {

    private final PredictionService predictionService;
    private final TeamRepository teamRepository;

    public PredictionController(PredictionService predictionService,
                                 TeamRepository teamRepository) {
        this.predictionService = predictionService;
        this.teamRepository = teamRepository;
    }

    /**
     * GET /predictions
     * Main predictions page. Loads open round (or most recent if none open)
     * plus round tab summaries and past rounds accordion.
     */
    @GetMapping
    public String predictionsPage(@AuthenticationPrincipal User currentUser, Model model) {
        // All round summaries for tab navigation
        List<RoundSummaryDto> roundSummaries = predictionService.getRoundSummaries(currentUser);
        model.addAttribute("roundSummaries", roundSummaries);

        // Determine which matchday to show: first OPEN, else latest PAST
        int activeMatchday = roundSummaries.stream()
            .filter(r -> "OPEN".equals(r.getStatus()))
            .mapToInt(RoundSummaryDto::getMatchday)
            .findFirst()
            .orElseGet(() -> roundSummaries.stream()
                .filter(r -> "PAST".equals(r.getStatus()))
                .mapToInt(RoundSummaryDto::getMatchday)
                .max()
                .orElse(1));

        model.addAttribute("activeMatchday", activeMatchday);

        // Load content for active matchday
        populateRoundModel(currentUser, activeMatchday, model);

        // Past rounds accordion (all completed rounds)
        List<PastRoundDto> pastRounds = predictionService.getPastRoundsForUser(currentUser);
        model.addAttribute("pastRounds", pastRounds);

        // Tournament winner state
        Team winnerPick = predictionService.getTournamentWinnerPick(currentUser);
        model.addAttribute("winnerPick", winnerPick);
        model.addAttribute("winnerSubmitted", winnerPick != null);

        // All teams for winner search dropdown
        List<Team> allTeams = teamRepository.findAllByOrderByNameAsc();
        model.addAttribute("allTeams", allTeams);

        return "predictions";
    }

    /**
     * GET /predictions/round/{matchday}
     * HTMX partial: returns only the round content fragment for tab switching.
     */
    @GetMapping("/round/{matchday}")
    public String roundFragment(@PathVariable int matchday,
                                 @AuthenticationPrincipal User currentUser,
                                 Model model) {
        populateRoundModel(currentUser, matchday, model);
        model.addAttribute("activeMatchday", matchday);
        return "fragments/predictions-round-content :: roundContent";
    }

    /**
     * POST /predictions/submit
     * All-or-nothing submission of all predictions for one open matchday.
     * Accepts standard form POST (Thymeleaf) and responds with redirect + flash.
     */
    @PostMapping("/submit")
    public String submitPredictions(@Valid @ModelAttribute PredictionSubmitDto submitDto,
                                     BindingResult bindingResult,
                                     @AuthenticationPrincipal User currentUser,
                                     RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid prediction data. Please check all scores.");
            return "redirect:/predictions";
        }

        try {
            int count = predictionService.submitPredictions(currentUser, submitDto);
            redirectAttributes.addFlashAttribute("successMessage",
                "Predictions saved! " + count + " match" + (count == 1 ? "" : "es") + " locked in.");
            redirectAttributes.addFlashAttribute("submittedMatchday", submitDto.getMatchday());
        } catch (IllegalStateException e) {
            // Lock time passed, partial submission, or validation failure
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/predictions";
    }

    /**
     * POST /predictions/winner
     * Submit tournament winner prediction. Visible to all immediately.
     */
    @PostMapping("/winner")
    public String submitWinner(@Valid @ModelAttribute TournamentWinnerSubmitDto dto,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal User currentUser,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a team.");
            return "redirect:/predictions";
        }

        try {
            predictionService.submitTournamentWinner(currentUser, dto.getTeamId());
            redirectAttributes.addFlashAttribute("successMessage", "Tournament winner prediction saved!");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/predictions";
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private void populateRoundModel(User user, int matchday, Model model) {
        List<MatchPredictionDto> matches = predictionService.getMatchesForRound(user, matchday);
        model.addAttribute("roundMatches", matches);

        // Group matches by date string for list view
        Map<String, List<MatchPredictionDto>> matchesByDate =
            predictionService.groupMatchesByDate(matches);
        model.addAttribute("matchesByDate", matchesByDate);

        // Progress counts
        long filled = matches.stream().filter(MatchPredictionDto::isPredictionSaved).count();
        boolean roundLocked = matches.stream().allMatch(MatchPredictionDto::isLocked);
        boolean roundOpen = !roundLocked && matches.stream().anyMatch(m -> !m.isLocked());

        model.addAttribute("filledCount", filled);
        model.addAttribute("totalCount", matches.size());
        model.addAttribute("roundLocked", roundLocked);
        model.addAttribute("roundOpen", roundOpen);
        model.addAttribute("allFilled", filled == matches.size() && !matches.isEmpty());
    }
}
```

- [ ] **Step 2: Add required methods to PredictionService**

The controller calls these service methods. Add them to the existing `PredictionService`:

```java
// Methods to ADD to PredictionService.java

/**
 * Returns summary info for all matchdays (for round navigation tabs).
 */
public List<RoundSummaryDto> getRoundSummaries(User user) {
    // Query distinct matchdays from Match table
    // For each matchday: determine status (PAST/OPEN/FUTURE), count predictions vs total
    List<Integer> matchdays = matchRepository.findDistinctMatchdays();
    List<RoundSummaryDto> summaries = new ArrayList<>();
    LocalDateTime now = LocalDateTime.now();

    for (int matchday : matchdays) {
        List<Match> matches = matchRepository.findByMatchdayOrderByKickoffAsc(matchday);
        if (matches.isEmpty()) continue;

        RoundSummaryDto dto = new RoundSummaryDto();
        dto.setMatchday(matchday);
        dto.setLabel(buildMatchdayLabel(matchday, matches.get(0)));

        LocalDateTime firstKickoff = matches.get(0).getKickoff();
        LocalDateTime lastLockTime = matches.stream()
            .map(m -> m.getKickoff().minusHours(1))
            .max(LocalDateTime::compareTo).orElse(firstKickoff);

        boolean allComplete = matches.stream().allMatch(Match::isCompleted);
        boolean anyLockPassed = matches.stream()
            .anyMatch(m -> now.isAfter(m.getKickoff().minusHours(1)));

        if (allComplete) {
            dto.setStatus("PAST");
            // Sum points for this user
            int pts = predictionRepository.findByUserAndMatchIn(user, matches).stream()
                .mapToInt(p -> p.getPointsEarned() != null ? p.getPointsEarned() : 0)
                .sum();
            dto.setPointsEarned(pts);
        } else if (anyLockPassed || now.isAfter(firstKickoff.minusHours(24))) {
            dto.setStatus("OPEN");
            long predicted = predictionRepository.countByUserAndMatchIn(user, matches);
            dto.setPredictedCount((int) predicted);
        } else {
            dto.setStatus("FUTURE");
        }

        dto.setTotalMatches(matches.size());
        summaries.add(dto);
    }
    return summaries;
}

/**
 * Returns open matches for one matchday with user's existing predictions overlaid.
 */
public List<MatchPredictionDto> getMatchesForRound(User user, int matchday) {
    List<Match> matches = matchRepository.findByMatchdayOrderByKickoffAsc(matchday);
    List<Prediction> existing = predictionRepository.findByUserAndMatchIn(user, matches);

    Map<Long, Prediction> predMap = existing.stream()
        .collect(Collectors.toMap(p -> p.getMatch().getId(), p -> p));

    LocalDateTime now = LocalDateTime.now();
    return matches.stream().map(match -> {
        MatchPredictionDto dto = new MatchPredictionDto();
        dto.setMatchId(match.getId());
        dto.setMatchday(match.getMatchday());
        dto.setStage(match.getStage().name());
        dto.setGroup(match.getGroup());
        dto.setKickoff(match.getKickoff());
        dto.setLockTime(match.getKickoff().minusHours(1));
        dto.setLocked(now.isAfter(match.getKickoff().minusHours(1)));
        dto.setHomeTeamName(match.getHomeTeam() != null ? match.getHomeTeam().getName() : "TBD");
        dto.setHomeTeamCode(match.getHomeTeam() != null ? match.getHomeTeam().getCode() : "xx");
        dto.setAwayTeamName(match.getAwayTeam() != null ? match.getAwayTeam().getName() : "TBD");
        dto.setAwayTeamCode(match.getAwayTeam() != null ? match.getAwayTeam().getCode() : "xx");
        dto.setVenue(match.getVenue());

        Prediction pred = predMap.get(match.getId());
        if (pred != null) {
            dto.setPredictedHome(pred.getHomeScore());
            dto.setPredictedAway(pred.getAwayScore());
            dto.setPredictionSaved(true);
        }
        return dto;
    }).collect(Collectors.toList());
}

/**
 * Groups matches by date label for list view display.
 */
public Map<String, List<MatchPredictionDto>> groupMatchesByDate(List<MatchPredictionDto> matches) {
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH);
    return matches.stream().collect(Collectors.groupingBy(
        m -> m.getKickoff().format(fmt),
        LinkedHashMap::new,
        Collectors.toList()
    ));
}

/**
 * Atomically submits all predictions for one open matchday.
 * Throws IllegalStateException if: any match is locked, submission is partial,
 * or round is already fully submitted.
 */
@Transactional
public int submitPredictions(User user, PredictionSubmitDto dto) {
    List<Match> openMatches = matchRepository.findByMatchdayOrderByKickoffAsc(dto.getMatchday());
    LocalDateTime now = LocalDateTime.now();

    // Validate none are locked
    for (Match match : openMatches) {
        if (now.isAfter(match.getKickoff().minusHours(1))) {
            throw new IllegalStateException(
                "Match " + match.getHomeTeam().getName() + " vs " +
                match.getAwayTeam().getName() + " is already locked.");
        }
    }

    // Validate all-or-nothing: submitted set must match open matches exactly
    Set<Long> submittedIds = dto.getPredictions().stream()
        .map(PredictionSubmitDto.SinglePrediction::getMatchId)
        .collect(Collectors.toSet());
    Set<Long> openIds = openMatches.stream().map(Match::getId).collect(Collectors.toSet());

    if (!submittedIds.equals(openIds)) {
        throw new IllegalStateException(
            "You must predict all " + openIds.size() +
            " matches in this round at once (all-or-nothing).");
    }

    // Upsert predictions
    for (PredictionSubmitDto.SinglePrediction sp : dto.getPredictions()) {
        Match match = matchRepository.findById(sp.getMatchId())
            .orElseThrow(() -> new IllegalStateException("Match not found: " + sp.getMatchId()));

        Prediction prediction = predictionRepository
            .findByUserAndMatch(user, match)
            .orElseGet(() -> {
                Prediction p = new Prediction();
                p.setUser(user);
                p.setMatch(match);
                return p;
            });

        prediction.setHomeScore(sp.getHomeScore());
        prediction.setAwayScore(sp.getAwayScore());
        prediction.setSubmittedAt(now);
        predictionRepository.save(prediction);
    }

    return openMatches.size();
}

/**
 * Returns completed rounds with match-level results for the accordion.
 */
public List<PastRoundDto> getPastRoundsForUser(User user) {
    List<Integer> matchdays = matchRepository.findDistinctMatchdays();
    List<PastRoundDto> result = new ArrayList<>();

    for (int matchday : matchdays) {
        List<Match> matches = matchRepository.findByMatchdayOrderByKickoffAsc(matchday);
        boolean allComplete = matches.stream().allMatch(Match::isCompleted);
        if (!allComplete) continue;

        List<Prediction> preds = predictionRepository.findByUserAndMatchIn(user, matches);
        Map<Long, Prediction> predMap = preds.stream()
            .collect(Collectors.toMap(p -> p.getMatch().getId(), p -> p));

        List<PastMatchResultDto> matchResults = matches.stream().map(match -> {
            PastMatchResultDto mr = new PastMatchResultDto();
            mr.setMatchId(match.getId());
            mr.setHomeTeamName(match.getHomeTeam().getName());
            mr.setHomeTeamCode(match.getHomeTeam().getCode());
            mr.setAwayTeamName(match.getAwayTeam().getName());
            mr.setAwayTeamCode(match.getAwayTeam().getCode());
            mr.setKickoff(match.getKickoff());
            mr.setActualHome(match.getHomeScore());
            mr.setActualAway(match.getAwayScore());

            Prediction pred = predMap.get(match.getId());
            if (pred != null) {
                mr.setPredictedHome(pred.getHomeScore());
                mr.setPredictedAway(pred.getAwayScore());
                mr.setPointsEarned(pred.getPointsEarned() != null ? pred.getPointsEarned() : 0);
                mr.setOutcome(calculateOutcome(
                    match.getHomeScore(), match.getAwayScore(),
                    pred.getHomeScore(), pred.getAwayScore()));
            } else {
                mr.setOutcome("NOT_PREDICTED");
                mr.setPointsEarned(0);
            }
            return mr;
        }).collect(Collectors.toList());

        int totalPts = matchResults.stream().mapToInt(PastMatchResultDto::getPointsEarned).sum();

        PastRoundDto roundDto = new PastRoundDto();
        roundDto.setMatchday(matchday);
        roundDto.setLabel(buildMatchdayLabel(matchday, matches.get(0)));
        roundDto.setTotalPoints(totalPts);
        roundDto.setMatches(matchResults);
        result.add(roundDto);
    }

    // Most recent first
    result.sort((a, b) -> b.getMatchday() - a.getMatchday());
    return result;
}

private String calculateOutcome(int actualHome, int actualAway, int predHome, int predAway) {
    if (predHome == actualHome && predAway == actualAway) return "EXACT";
    boolean actualDraw = actualHome == actualAway;
    boolean predDraw   = predHome == predAway;
    if (actualDraw && predDraw) return "DRAW";
    boolean actualHomeWin = actualHome > actualAway;
    boolean predHomeWin   = predHome > predAway;
    if (actualHomeWin == predHomeWin && !actualDraw) return "WINNER";
    return "WRONG";
}

private String buildMatchdayLabel(int matchday, Match firstMatch) {
    String stage = firstMatch.getStage().name();
    return switch (stage) {
        case "GROUP"  -> "Matchday " + matchday;
        case "R32"    -> "Round of 32";
        case "R16"    -> "Round of 16";
        case "QF"     -> "Quarter-finals";
        case "SF"     -> "Semi-finals";
        case "FINAL"  -> "Final";
        default       -> "Matchday " + matchday;
    };
}

public Team getTournamentWinnerPick(User user) {
    return tournamentWinnerRepository.findByUser(user)
        .map(TournamentWinnerPrediction::getTeam)
        .orElse(null);
}

@Transactional
public void submitTournamentWinner(User user, Long teamId) {
    if (tournamentWinnerRepository.findByUser(user).isPresent()) {
        throw new IllegalStateException("Tournament winner prediction already submitted.");
    }
    Team team = teamRepository.findById(teamId)
        .orElseThrow(() -> new IllegalStateException("Team not found."));
    TournamentWinnerPrediction pick = new TournamentWinnerPrediction();
    pick.setUser(user);
    pick.setTeam(team);
    pick.setSubmittedAt(LocalDateTime.now());
    tournamentWinnerRepository.save(pick);
}
```

- [ ] **Step 3: Commit**
```bash
git add src/main/java/com/worldcup/prediction/controller/PredictionController.java
git add src/main/java/com/worldcup/prediction/service/PredictionService.java
git commit -m "feat: add PredictionController and service methods for predictions UI"
```

---

### Task 3: predictions.css

**Files:**
- Create: `src/main/resources/static/css/predictions.css`

- [ ] **Step 1: Create predictions.css**

```css
/* src/main/resources/static/css/predictions.css */

@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&family=Bebas+Neue&display=swap');

*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

:root {
  --green:   #00c853;
  --green-d: #006b2a;
  --green-m: #00a845;
  --orange:  #FF5722;
  --yellow:  #FFD600;
  --bg:      #f0fdf5;
  --card:    #ffffff;
  --text:    #0d1a10;
  --muted:   #667c70;
  --border:  rgba(0,160,70,0.15);
}

html, body {
  min-height: 100vh;
  font-family: 'Inter', sans-serif;
  background: var(--bg);
  color: var(--text);
}

/* ─── Background ─────────────────────────────────────── */
.bg-wrap { position: fixed; inset: 0; z-index: 0; pointer-events: none; }
.bg-ph {
  position: absolute; inset: 0;
  background: url('https://footiehound.com/wp-content/uploads/2025/08/cropped-football-prediction-today.jpg') center/cover;
  filter: brightness(1.05) saturate(1.1);
  animation: bgK 20s ease-in-out infinite alternate;
}
@keyframes bgK { from { transform: scale(1); } to { transform: scale(1.06) translateX(-15px); } }
.bg-ov { position: absolute; inset: 0; background: linear-gradient(rgba(240,253,245,.9), rgba(240,253,245,.86)); }
.bg-st {
  position: absolute; inset: 0;
  background-image: repeating-linear-gradient(180deg, rgba(0,180,60,.025) 0, rgba(0,180,60,.025) 60px, transparent 60px, transparent 120px);
}

/* ─── Animations ─────────────────────────────────────── */
@keyframes fUp    { from { opacity: 0; transform: translateY(18px); } to { opacity: 1; transform: translateY(0); } }
@keyframes blink  { 0%,100% { opacity: 1; } 50% { opacity: .3; } }
@keyframes shimmer {
  0%   { background-position: -200% center; }
  100% { background-position:  200% center; }
}
@keyframes flagWave3d {
  0%,100% { transform: perspective(200px) rotateY(0deg); }
  25%     { transform: perspective(200px) rotateY(-18deg); }
  75%     { transform: perspective(200px) rotateY(18deg); }
}
@keyframes flagBright {
  0%,100% { filter: brightness(1); }
  25%,75% { filter: brightness(.82); }
}
@keyframes submitPulse {
  0%,100% { box-shadow: 0 6px 24px rgba(200,160,0,.35), 0 0 0 0 rgba(255,214,0,.0); }
  50%     { box-shadow: 0 8px 36px rgba(200,160,0,.55), 0 0 0 12px rgba(255,214,0,.0); }
}
@keyframes goldGlow {
  0%,100% { box-shadow: 0 6px 28px rgba(200,160,0,.4); }
  50%     { box-shadow: 0 8px 40px rgba(200,160,0,.7), 0 0 20px rgba(255,214,0,.4); }
}
@keyframes progressFill { from { width: 0; } }
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes bounce { 0% { transform: scale(0); } 60% { transform: scale(1.2); } 100% { transform: scale(1); } }
@keyframes cardSlideIn { from { opacity: 0; transform: translateX(40px); } to { opacity: 1; transform: translateX(0); } }

/* ─── Page layout ────────────────────────────────────── */
.page {
  position: relative; z-index: 1;
  max-width: 900px; margin: 0 auto;
  padding: 32px 24px 80px;
}

/* ─── Page title ─────────────────────────────────────── */
.page-title {
  font-family: 'Bebas Neue', sans-serif;
  font-size: 42px; letter-spacing: 4px;
  color: var(--green-d);
  animation: fUp .5s ease .05s both;
}
.page-subtitle {
  font-size: 13px; font-weight: 600; color: var(--muted);
  margin-top: 2px; margin-bottom: 28px;
  animation: fUp .5s ease .1s both;
}

/* ─── Tournament Winner Banner ───────────────────────── */
.winner-banner {
  background: linear-gradient(135deg, rgba(255,214,0,.12), rgba(255,214,0,.06));
  border: 1.5px solid rgba(255,214,0,.4);
  border-radius: 20px;
  padding: 20px 24px;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
  animation: fUp .5s ease .15s both;
}
.winner-banner-icon {
  font-size: 36px;
  flex-shrink: 0;
}
.winner-banner-body { flex: 1; }
.winner-banner-title {
  font-family: 'Bebas Neue', sans-serif;
  font-size: 18px; letter-spacing: 2px;
  color: #7a5000;
  margin-bottom: 2px;
}
.winner-banner-sub { font-size: 12px; font-weight: 600; color: var(--muted); }
.winner-banner-form {
  display: flex; align-items: center; gap: 10px;
}
.winner-select {
  height: 40px; padding: 0 12px;
  border: 1.5px solid rgba(255,214,0,.4);
  border-radius: 10px;
  background: white;
  font-size: 13px; font-weight: 700; color: var(--text);
  cursor: pointer;
  outline: none;
}
.winner-select:focus { border-color: var(--yellow); box-shadow: 0 0 0 3px rgba(255,214,0,.2); }
.winner-btn {
  height: 40px; padding: 0 20px;
  background: linear-gradient(135deg, #e6ac00, var(--yellow));
  border: none; border-radius: 10px;
  font-size: 13px; font-weight: 800; color: #4a3000;
  cursor: pointer;
  box-shadow: 0 3px 12px rgba(200,160,0,.3);
  transition: all .2s;
}
.winner-btn:hover { transform: translateY(-1px); box-shadow: 0 5px 18px rgba(200,160,0,.45); }

.winner-picked {
  display: flex; align-items: center; gap: 12px;
}
.winner-flag {
  width: 40px; height: 40px; border-radius: 50%;
  overflow: hidden; border: 2px solid rgba(255,214,0,.5);
  box-shadow: 0 2px 8px rgba(0,0,0,.12);
}
.winner-flag img { width: 100%; height: 100%; object-fit: cover; }
.winner-team-name {
  font-family: 'Bebas Neue', sans-serif;
  font-size: 22px; letter-spacing: 2px; color: #5a3800;
}
.winner-pts-badge {
  background: rgba(255,214,0,.2); border: 1px solid rgba(255,214,0,.4);
  border-radius: 8px; padding: 4px 10px;
  font-size: 12px; font-weight: 800; color: #7a5000;
}

/* ─── Round Navigation Tabs ──────────────────────────── */
.round-tabs {
  display: flex; gap: 6px; flex-wrap: wrap;
  margin-bottom: 24px;
  animation: fUp .5s ease .2s both;
}
.round-tab {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 16px;
  border-radius: 99px;
  border: 1.5px solid var(--border);
  background: white;
  font-size: 12px; font-weight: 700; color: var(--muted);
  cursor: pointer;
  transition: all .2s;
  user-select: none;
}
.round-tab:hover { background: rgba(0,200,83,.07); border-color: rgba(0,200,83,.3); color: var(--green-d); }
.round-tab.active {
  background: var(--green-d); border-color: var(--green-d);
  color: white; box-shadow: 0 3px 12px rgba(0,100,42,.3);
}
.round-tab.future { opacity: .45; cursor: default; }
.tab-status { font-size: 14px; }

/* ─── Round content wrapper ──────────────────────────── */
#round-content { animation: fUp .4s ease; }

/* ─── View Toggle ────────────────────────────────────── */
.view-toggle {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 20px;
}
.view-toggle-btns {
  display: flex; gap: 4px;
  background: rgba(0,160,70,.07);
  border: 1px solid var(--border);
  border-radius: 10px; padding: 3px;
}
.view-btn {
  padding: 6px 14px; border-radius: 7px;
  font-size: 12px; font-weight: 700;
  border: none; cursor: pointer;
  transition: all .2s; color: var(--muted); background: transparent;
}
.view-btn.active { background: white; color: var(--green-d); box-shadow: 0 1px 6px rgba(0,0,0,.1); }

/* ─── Progress Banner ────────────────────────────────── */
.progress-banner {
  background: white;
  border: 1.5px solid var(--border);
  border-radius: 16px;
  padding: 16px 20px;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,.05);
}
.progress-banner-row {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 10px;
}
.progress-label { font-size: 13px; font-weight: 700; color: var(--text); }
.progress-fraction { font-size: 13px; font-weight: 800; color: var(--green-d); }
.progress-track {
  height: 8px; background: rgba(0,160,70,.1);
  border-radius: 99px; overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--green-m), var(--green));
  border-radius: 99px;
  transition: width .5s cubic-bezier(.4,0,.2,1);
  animation: progressFill .8s ease;
}

/* ─── Submit Button ──────────────────────────────────── */
.submit-area {
  display: flex; flex-direction: column; align-items: center;
  gap: 10px; margin-top: 24px;
}
.submit-btn {
  width: 100%; max-width: 420px; height: 56px;
  background: linear-gradient(135deg, #c89800, var(--yellow));
  color: #3a2400; font-size: 16px; font-weight: 900; letter-spacing: .5px;
  border: none; border-radius: 16px; cursor: pointer;
  display: flex; align-items: center; justify-content: center; gap: 10px;
  box-shadow: 0 6px 24px rgba(200,160,0,.35);
  transition: all .2s;
  animation: goldGlow 2.5s ease-in-out infinite;
}
.submit-btn:hover { transform: translateY(-2px); box-shadow: 0 10px 36px rgba(200,160,0,.55); }
.submit-btn:active { transform: translateY(0); }
.submit-btn:disabled {
  background: rgba(0,0,0,.08); color: var(--muted);
  box-shadow: none; cursor: not-allowed; animation: none;
  transform: none;
}
.submit-note { font-size: 11px; font-weight: 600; color: var(--muted); text-align: center; }

/* ─── List View: Date group ──────────────────────────── */
.date-group { margin-bottom: 28px; }
.date-header {
  font-size: 11px; font-weight: 800; letter-spacing: 2.5px; text-transform: uppercase;
  color: var(--green-d); margin-bottom: 12px;
  padding-bottom: 6px; border-bottom: 1px solid var(--border);
}

/* ─── Match Row (List View) ──────────────────────────── */
.match-row {
  background: white;
  border: 1.5px solid var(--border);
  border-radius: 16px;
  padding: 16px 20px;
  margin-bottom: 10px;
  display: flex; align-items: center; gap: 16px;
  box-shadow: 0 2px 10px rgba(0,0,0,.04);
  transition: border-color .2s, box-shadow .2s;
}
.match-row:hover { border-color: rgba(0,200,83,.3); box-shadow: 0 4px 18px rgba(0,160,70,.1); }
.match-row.filled { border-color: rgba(0,200,83,.3); background: rgba(0,200,83,.02); }
.match-row.locked { opacity: .7; }

/* Team column */
.mr-team {
  display: flex; align-items: center; gap: 8px;
  flex: 1; min-width: 0;
}
.mr-flag {
  width: 32px; height: 32px; border-radius: 50%;
  overflow: hidden; border: 2px solid white;
  box-shadow: 0 1px 6px rgba(0,0,0,.12); flex-shrink: 0;
  animation: flagWave3d 4s ease-in-out infinite;
}
.mr-flag img { width: 100%; height: 100%; object-fit: cover; animation: flagBright 4s ease-in-out infinite; }
.mr-flag-b { animation-delay: -2s !important; }
.mr-flag-b img { animation-delay: -2s !important; }
.mr-name { font-size: 13px; font-weight: 800; color: var(--text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mr-venue { font-size: 10px; font-weight: 600; color: var(--muted); margin-top: 1px; }

/* Score stepper */
.mr-stepper {
  display: flex; align-items: center; gap: 8px;
  flex-shrink: 0;
}
.stepper-team-name {
  font-size: 11px; font-weight: 700; color: var(--muted);
  text-align: center; min-width: 60px;
}
.score-widget {
  display: flex; align-items: center;
  background: white; border: 2px solid var(--border);
  border-radius: 12px; overflow: hidden;
  box-shadow: 0 1px 6px rgba(0,160,70,.08);
  transition: border-color .2s, box-shadow .2s;
}
.score-widget:focus-within {
  border-color: var(--green-m);
  box-shadow: 0 0 0 3px rgba(0,200,83,.12);
}
.score-btn {
  width: 36px; height: 50px;
  border: none; background: rgba(0,160,70,.06);
  font-size: 18px; font-weight: 900;
  color: var(--green-d); cursor: pointer;
  transition: background .15s;
  display: flex; align-items: center; justify-content: center;
  user-select: none;
}
.score-btn:hover { background: rgba(0,160,70,.14); }
.score-btn:active { background: rgba(0,160,70,.22); transform: scale(.94); }
.score-val {
  width: 52px; height: 50px;
  border: none; outline: none;
  font-family: 'Bebas Neue', sans-serif;
  font-size: 32px; letter-spacing: 1px;
  text-align: center; color: var(--text);
  background: transparent;
  -moz-appearance: textfield;
}
.score-val::-webkit-outer-spin-button,
.score-val::-webkit-inner-spin-button { -webkit-appearance: none; }
.score-colon {
  font-family: 'Bebas Neue', sans-serif;
  font-size: 28px; color: var(--muted);
  padding: 0 4px;
  animation: blink 1.2s ease-in-out infinite;
}

/* Status pill */
.mr-status {
  flex-shrink: 0;
}
.status-pill {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 10px; border-radius: 99px;
  font-size: 10px; font-weight: 800; letter-spacing: .5px;
  white-space: nowrap;
}
.pill-open    { background: rgba(0,200,83,.1);   color: var(--green-d); border: 1px solid rgba(0,200,83,.2); }
.pill-urgent  { background: rgba(255,87,34,.1);  color: var(--orange);  border: 1px solid rgba(255,87,34,.2); }
.pill-filled  { background: rgba(0,200,83,.15);  color: var(--green-d); border: 1px solid rgba(0,200,83,.3); }
.pill-locked  { background: rgba(102,124,112,.1); color: var(--muted);  border: 1px solid rgba(102,124,112,.2); }
.pill-dot { width: 6px; height: 6px; border-radius: 50%; animation: blink 1s ease infinite; }

/* ─── Card View Carousel ─────────────────────────────── */
.card-carousel {
  position: relative;
}
.carousel-card {
  background: white;
  border-radius: 24px;
  border: 1.5px solid var(--border);
  box-shadow: 0 8px 40px rgba(0,0,0,.1);
  overflow: hidden;
  animation: cardSlideIn .3s ease;
}

.card-ribbon {
  background: linear-gradient(135deg, var(--green-d), var(--green-m));
  padding: 12px 24px;
  display: flex; align-items: center; justify-content: space-between;
  color: white; position: relative; overflow: hidden;
}
.card-ribbon::before {
  content: ''; position: absolute; inset: 0;
  background: repeating-linear-gradient(45deg, transparent, transparent 10px, rgba(255,255,255,.03) 10px, rgba(255,255,255,.03) 20px);
}
.ribbon-stage { font-size: 10px; font-weight: 800; letter-spacing: 2.5px; text-transform: uppercase; opacity: .8; position: relative; }
.ribbon-info  { font-size: 12px; font-weight: 700; position: relative; margin-top: 2px; }
.lock-pill {
  display: flex; align-items: center; gap: 7px;
  background: rgba(255,87,34,.9); border-radius: 99px;
  padding: 5px 12px; font-size: 11px; font-weight: 800; color: white;
  position: relative;
}
.lock-dot { width: 6px; height: 6px; border-radius: 50%; background: white; animation: blink 1s ease infinite; }
.lock-time { font-family: 'Bebas Neue', sans-serif; font-size: 16px; letter-spacing: 2px; }

.card-teams {
  padding: 32px 24px 24px;
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
}
.card-team {
  display: flex; flex-direction: column; align-items: center; gap: 10px; flex: 1;
}
.card-flag {
  width: 88px; height: 88px; border-radius: 50%;
  overflow: hidden; border: 4px solid white;
  box-shadow: 0 6px 24px rgba(0,0,0,.18);
  animation: flagWave3d 3s ease-in-out infinite;
}
.card-flag img { width: 100%; height: 100%; object-fit: cover; animation: flagBright 3s ease-in-out infinite; }
.card-flag-b { animation-delay: -1.5s !important; }
.card-flag-b img { animation-delay: -1.5s !important; }
.card-team-name {
  font-family: 'Bebas Neue', sans-serif;
  font-size: 24px; letter-spacing: 3px; text-align: center;
}
.card-vs {
  display: flex; flex-direction: column; align-items: center; gap: 4px; flex-shrink: 0;
}
.card-vs-text {
  font-family: 'Bebas Neue', sans-serif;
  font-size: 28px; letter-spacing: 4px; color: var(--muted);
}
.card-vs-date { font-size: 10px; font-weight: 700; color: var(--muted); text-align: center; }

.card-score-section {
  background: rgba(0,160,70,.04); border-top: 1px solid var(--border);
  padding: 24px;
}
.card-score-label {
  text-align: center; font-size: 10px; font-weight: 800;
  letter-spacing: 2.5px; text-transform: uppercase;
  color: var(--green-d); margin-bottom: 16px;
}
.card-score-row {
  display: flex; align-items: center; justify-content: center; gap: 16px;
}
.card-score-widget {
  display: flex; align-items: center;
  background: white; border: 2px solid var(--border);
  border-radius: 14px; overflow: hidden;
  box-shadow: 0 2px 12px rgba(0,160,70,.1);
  transition: border-color .2s, box-shadow .2s;
}
.card-score-widget:focus-within {
  border-color: var(--green-m);
  box-shadow: 0 0 0 3px rgba(0,200,83,.12);
}
.card-score-btn {
  width: 44px; height: 60px;
  border: none; background: rgba(0,160,70,.06);
  font-size: 20px; font-weight: 900;
  color: var(--green-d); cursor: pointer;
  transition: background .15s;
  display: flex; align-items: center; justify-content: center;
  user-select: none;
}
.card-score-btn:hover { background: rgba(0,160,70,.14); }
.card-score-val {
  width: 66px; height: 60px;
  border: none; outline: none;
  font-family: 'Bebas Neue', sans-serif;
  font-size: 40px; letter-spacing: 2px;
  text-align: center; color: var(--text);
  background: transparent;
  -moz-appearance: textfield;
}
.card-score-val::-webkit-outer-spin-button,
.card-score-val::-webkit-inner-spin-button { -webkit-appearance: none; }
.card-score-colon {
  font-family: 'Bebas Neue', sans-serif;
  font-size: 36px; color: var(--muted);
  padding: 0 4px; animation: blink 1.2s ease-in-out infinite;
}

.card-nav {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 24px 24px;
}
.card-nav-btn {
  height: 42px; padding: 0 20px;
  border: 1.5px solid var(--border);
  border-radius: 10px; background: white;
  font-size: 13px; font-weight: 700; color: var(--green-d);
  cursor: pointer; transition: all .2s;
}
.card-nav-btn:hover { background: rgba(0,200,83,.07); border-color: rgba(0,200,83,.3); }
.card-nav-btn:disabled { opacity: .35; cursor: default; }
.card-save-next {
  height: 42px; padding: 0 24px;
  background: var(--green-d); color: white;
  border: none; border-radius: 10px;
  font-size: 13px; font-weight: 800;
  cursor: pointer; transition: all .2s;
  box-shadow: 0 3px 12px rgba(0,80,30,.25);
}
.card-save-next:hover { background: var(--green-m); box-shadow: 0 5px 18px rgba(0,80,30,.35); transform: translateY(-1px); }

/* Progress dots */
.progress-dots {
  display: flex; align-items: center; justify-content: center; gap: 6px;
}
.progress-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: rgba(0,160,70,.2);
  transition: all .3s;
}
.progress-dot.filled { background: var(--green); }
.progress-dot.current {
  background: var(--green-d);
  width: 22px; border-radius: 99px;
}

/* ─── Past Rounds Accordion ──────────────────────────── */
.past-rounds-section {
  margin-top: 40px;
  animation: fUp .5s ease .4s both;
}
.past-rounds-title {
  font-family: 'Bebas Neue', sans-serif;
  font-size: 26px; letter-spacing: 3px;
  color: var(--green-d); margin-bottom: 16px;
}
.past-round {
  background: white;
  border: 1.5px solid var(--border);
  border-radius: 16px;
  overflow: hidden;
  margin-bottom: 10px;
  box-shadow: 0 2px 10px rgba(0,0,0,.04);
}
.past-round-header {
  padding: 16px 20px;
  display: flex; align-items: center; justify-content: space-between;
  cursor: pointer;
  user-select: none;
  transition: background .2s;
}
.past-round-header:hover { background: rgba(0,200,83,.04); }
.prh-left { display: flex; align-items: center; gap: 10px; }
.prh-label { font-size: 14px; font-weight: 800; color: var(--text); }
.prh-count { font-size: 11px; font-weight: 600; color: var(--muted); }
.prh-right { display: flex; align-items: center; gap: 10px; }
.prh-pts {
  font-family: 'Bebas Neue', sans-serif;
  font-size: 20px; letter-spacing: 2px; color: var(--green-d);
}
.prh-chevron {
  font-size: 12px; color: var(--muted);
  transition: transform .25s;
}
.prh-chevron.open { transform: rotate(180deg); }

.past-round-body {
  border-top: 1px solid var(--border);
  overflow: hidden;
}

/* Past match result row */
.past-match-row {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 20px;
  border-bottom: 1px solid rgba(0,160,70,.06);
}
.past-match-row:last-child { border-bottom: none; }

.pm-teams {
  flex: 1; display: flex; align-items: center; gap: 8px; min-width: 0;
}
.pm-flag {
  width: 24px; height: 24px; border-radius: 50%;
  overflow: hidden; border: 1.5px solid white;
  box-shadow: 0 1px 4px rgba(0,0,0,.1); flex-shrink: 0;
}
.pm-flag img { width: 100%; height: 100%; object-fit: cover; }
.pm-name { font-size: 12px; font-weight: 700; color: var(--text); white-space: nowrap; }
.pm-vs   { font-size: 10px; font-weight: 600; color: var(--muted); }

/* Score comparison: actual ←•→ predicted */
.pm-scores {
  display: flex; align-items: center; gap: 8px; flex-shrink: 0;
}
.pm-actual {
  font-family: 'Bebas Neue', sans-serif;
  font-size: 20px; letter-spacing: 1px;
  color: var(--text); min-width: 40px; text-align: right;
}
.pm-sep { font-size: 10px; font-weight: 700; color: var(--muted); }
.pm-predicted {
  font-family: 'Bebas Neue', sans-serif;
  font-size: 20px; letter-spacing: 1px;
  min-width: 40px;
}
.pm-predicted.exact  { color: #15803d; }
.pm-predicted.draw   { color: #1e40af; }
.pm-predicted.winner { color: #92400e; }
.pm-predicted.wrong  { color: #b91c1c; }
.pm-predicted.none   { color: var(--muted); }

/* Points badge */
.pm-pts {
  display: inline-flex; align-items: center;
  width: 44px; height: 28px; border-radius: 8px;
  font-size: 12px; font-weight: 800;
  justify-content: center; flex-shrink: 0;
}
.pts-exact  { background: rgba(0,200,83,.15);  color: #15803d; }
.pts-draw   { background: rgba(60,120,255,.12); color: #1e40af; }
.pts-winner { background: rgba(234,179,8,.15);  color: #92400e; }
.pts-wrong  { background: rgba(185,28,28,.08);  color: #b91c1c; }
.pts-none   { background: rgba(102,124,112,.08); color: var(--muted); }

/* ─── Alert messages ─────────────────────────────────── */
.alert {
  padding: 14px 18px; border-radius: 12px;
  font-size: 13px; font-weight: 700;
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 20px; animation: fUp .4s ease;
}
.alert-success {
  background: rgba(0,200,83,.1); border: 1px solid rgba(0,200,83,.25); color: var(--green-d);
}
.alert-error {
  background: rgba(255,87,34,.08); border: 1px solid rgba(255,87,34,.25); color: var(--orange);
}

/* ─── Empty states ───────────────────────────────────── */
.empty-round {
  text-align: center; padding: 48px 20px;
  color: var(--muted); font-size: 14px; font-weight: 600;
}
.empty-round-icon { font-size: 40px; margin-bottom: 12px; }

/* ─── Responsive ─────────────────────────────────────── */
@media (max-width: 640px) {
  .page { padding: 20px 16px 60px; }
  .page-title { font-size: 32px; }
  .mr-stepper { gap: 4px; }
  .stepper-team-name { display: none; }
  .card-teams { padding: 20px 16px 16px; gap: 8px; }
  .card-flag  { width: 64px; height: 64px; }
  .card-team-name { font-size: 18px; }
  .round-tabs { gap: 4px; }
  .round-tab  { padding: 6px 12px; font-size: 11px; }
  .winner-banner { flex-direction: column; gap: 12px; }
  .winner-banner-form { flex-direction: column; width: 100%; }
  .winner-select { width: 100%; }
  .winner-btn { width: 100%; }
}
```

- [ ] **Step 2: Commit**
```bash
git add src/main/resources/static/css/predictions.css
git commit -m "feat: add predictions page CSS with card carousel and list view styles"
```

---

### Task 4: predictions-round-content.html fragment

**Files:**
- Create: `src/main/resources/templates/fragments/predictions-round-content.html`

- [ ] **Step 1: Create the round content fragment**

This fragment is returned by the HTMX tab switch endpoint and is also included in the main page.

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" th:fragment="roundContent">
<head th:if="false"><!-- standalone fragment, head ignored when included --></head>
<body>

<!--/*
  Fragment: roundContent
  Used at: GET /predictions (full page include) and GET /predictions/round/{n} (HTMX swap)
  Model attributes expected:
    roundMatches     : List<MatchPredictionDto>
    matchesByDate    : Map<String, List<MatchPredictionDto>>
    filledCount      : long
    totalCount       : int
    roundLocked      : boolean
    roundOpen        : boolean
    allFilled        : boolean
    activeMatchday   : int
*/-->

<div id="round-content"
     th:with="allLocked=${roundLocked}, hasMatches=${not #lists.isEmpty(roundMatches)}">

  <!-- Empty state: no matches in this round yet -->
  <div th:if="${not hasMatches}" class="empty-round">
    <div class="empty-round-icon">📅</div>
    <div>No matches scheduled for this round yet.</div>
  </div>

  <!-- Locked round notice -->
  <div th:if="${hasMatches and allLocked}" class="alert alert-error" style="margin-bottom:20px;">
    <span>🔒</span>
    <span>All matches in this round have passed the prediction lock time. Results will appear below when completed.</span>
  </div>

  <!-- Active round: Alpine.js component for view mode + state management -->
  <div th:if="${hasMatches and not allLocked}"
       x-data="predictionsApp([[
           /*[# th:each='m : ${roundMatches}' ]*/
           {
             matchId:        /*[[${m.matchId}]]*/ 0,
             homeTeam:       /*[[${m.homeTeamName}]]*/ 'Team A',
             homeCode:       /*[[${m.homeTeamCode}]]*/ 'xx',
             awayTeam:       /*[[${m.awayTeamName}]]*/ 'Team B',
             awayCode:       /*[[${m.awayTeamCode}]]*/ 'xx',
             kickoff:        /*[[${m.kickoff}]]*/ '',
             lockTime:       /*[[${m.lockTime}]]*/ '',
             locked:         /*[[${m.locked}]]*/ false,
             venue:          /*[[${m.venue}]]*/ '',
             predHome:       /*[[${m.predictedHome != null ? m.predictedHome : -1}]]*/ -1,
             predAway:       /*[[${m.predictedAway != null ? m.predictedAway : -1}]]*/ -1,
             saved:          /*[[${m.predictionSaved}]]*/ false
           }/*[# th:if="${!matchesStat.last}"]*/,/*[/]*/
           /*[/]*/
       ]])">

    <!-- View toggle + progress banner -->
    <div class="view-toggle">
      <div>
        <div class="progress-label" style="font-size:14px;font-weight:800;color:var(--green-d);"
             x-text="`Matchday ${activeMatchday}`"
             th:attr="x-text='`Matchday ${activeMatchday}`'"
             th:inline="none">
          <!-- filled by Alpine -->
        </div>
      </div>
      <div class="view-toggle-btns" x-data>
        <button class="view-btn" :class="{ active: $root.viewMode === 'list' }"
                @click="$root.viewMode = 'list'">
          ☰ List
        </button>
        <button class="view-btn" :class="{ active: $root.viewMode === 'card' }"
                @click="$root.viewMode = 'card'">
          ⬜ Card
        </button>
      </div>
    </div>

    <!-- Progress banner -->
    <div class="progress-banner">
      <div class="progress-banner-row">
        <span class="progress-label">Predictions filled</span>
        <span class="progress-fraction" x-text="`${filledCount} / ${matches.length}`"></span>
      </div>
      <div class="progress-track">
        <div class="progress-fill"
             :style="`width: ${matches.length > 0 ? (filledCount / matches.length * 100) : 0}%`">
        </div>
      </div>
    </div>

    <!-- ═══════════════════════════════════════════════════
         LIST VIEW
    ═══════════════════════════════════════════════════ -->
    <div x-show="viewMode === 'list'" x-transition>

      <!-- Hidden form used for submission (populated by Alpine) -->
      <form id="predictions-form-list"
            th:action="@{/predictions/submit}" method="post"
            @submit.prevent="submitPredictions">
        <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
        <input type="hidden" name="matchday" th:value="${activeMatchday}"/>
        <!-- Prediction inputs rendered dynamically below; the JS adds hidden inputs on submit -->
      </form>

      <!-- Matches grouped by date -->
      <template x-for="(dateGroup, dateKey) in groupedByDate" :key="dateKey">
        <div class="date-group">
          <div class="date-header" x-text="dateKey"></div>

          <template x-for="match in dateGroup" :key="match.matchId">
            <div class="match-row"
                 :class="{ filled: isFilled(match), locked: match.locked }">

              <!-- Home team -->
              <div class="mr-team">
                <div class="mr-flag">
                  <img :src="`https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/${match.homeCode}.svg`"
                       :alt="match.homeTeam"
                       :onerror="`this.src='https://flagcdn.com/w80/${match.homeCode}.png'`">
                </div>
                <div>
                  <div class="mr-name" x-text="match.homeTeam"></div>
                  <div class="mr-venue" x-text="match.venue"></div>
                </div>
              </div>

              <!-- Score stepper -->
              <div class="mr-stepper" x-show="!match.locked">
                <div class="stepper-team-name" x-text="match.homeTeam"></div>
                <div class="score-widget">
                  <button type="button" class="score-btn"
                          @click="adjustScore(match, 'home', -1)">−</button>
                  <input class="score-val" type="number" min="0" max="20"
                         :value="match.predHome >= 0 ? match.predHome : ''"
                         @input="setScore(match, 'home', $event.target.value)"
                         :placeholder="match.predHome >= 0 ? match.predHome : '–'"
                         autocomplete="off">
                  <button type="button" class="score-btn"
                          @click="adjustScore(match, 'home', 1)">+</button>
                </div>
                <div class="score-colon">:</div>
                <div class="score-widget">
                  <button type="button" class="score-btn"
                          @click="adjustScore(match, 'away', -1)">−</button>
                  <input class="score-val" type="number" min="0" max="20"
                         :value="match.predAway >= 0 ? match.predAway : ''"
                         @input="setScore(match, 'away', $event.target.value)"
                         :placeholder="match.predAway >= 0 ? match.predAway : '–'"
                         autocomplete="off">
                  <button type="button" class="score-btn"
                          @click="adjustScore(match, 'away', 1)">+</button>
                </div>
                <div class="stepper-team-name" x-text="match.awayTeam"></div>
              </div>

              <!-- Locked score display -->
              <div x-show="match.locked" style="font-family:'Bebas Neue',sans-serif;font-size:22px;color:var(--muted);"
                   x-text="isFilled(match) ? `${match.predHome} : ${match.predAway}` : '–'">
              </div>

              <!-- Away team -->
              <div class="mr-team" style="justify-content: flex-end; text-align:right;">
                <div>
                  <div class="mr-name" x-text="match.awayTeam"></div>
                </div>
                <div class="mr-flag mr-flag-b">
                  <img :src="`https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/${match.awayCode}.svg`"
                       :alt="match.awayTeam"
                       :onerror="`this.src='https://flagcdn.com/w80/${match.awayCode}.png'`">
                </div>
              </div>

              <!-- Status pill -->
              <div class="mr-status">
                <span class="status-pill pill-filled" x-show="isFilled(match)">
                  <span>✓</span> Saved
                </span>
                <span class="status-pill pill-locked" x-show="match.locked && !isFilled(match)">
                  🔒 Locked
                </span>
                <span class="status-pill pill-urgent"
                      x-show="!match.locked && !isFilled(match) && isUrgent(match)">
                  <span class="pill-dot" style="background:var(--orange)"></span>⚡ Urgent
                </span>
                <span class="status-pill pill-open"
                      x-show="!match.locked && !isFilled(match) && !isUrgent(match)">
                  <span class="pill-dot" style="background:var(--green)"></span>⚡ Open
                </span>
              </div>

            </div>
          </template>
        </div>
      </template>

      <!-- Submit area (list view) -->
      <div class="submit-area">
        <button type="button" class="submit-btn"
                :disabled="!allFilled"
                @click="submitPredictions">
          <span>🔒</span>
          <span x-text="allFilled ? 'Lock In All Predictions' : `Fill ${matches.length - filledCount} more match${matches.length - filledCount === 1 ? '' : 'es'}`"></span>
        </button>
        <div class="submit-note">
          All-or-nothing · lock 1 hour before kickoff · cannot change after lock
        </div>
      </div>
    </div>

    <!-- ═══════════════════════════════════════════════════
         CARD VIEW CAROUSEL
    ═══════════════════════════════════════════════════ -->
    <div x-show="viewMode === 'card'" x-transition>

      <!-- Progress dots -->
      <div class="progress-dots" style="margin-bottom:20px;">
        <template x-for="(match, idx) in matches" :key="match.matchId">
          <div class="progress-dot"
               :class="{ filled: isFilled(match), current: idx === currentCardIndex }"
               @click="currentCardIndex = idx"
               style="cursor:pointer;">
          </div>
        </template>
      </div>

      <!-- Card -->
      <div class="card-carousel" x-show="matches.length > 0">
        <div class="carousel-card" :key="currentCardIndex">

          <!-- Ribbon -->
          <div class="card-ribbon">
            <div>
              <div class="ribbon-stage"
                   x-text="currentMatch.stage + (currentMatch.group ? ' · Group ' + currentMatch.group : '')">
              </div>
              <div class="ribbon-info"
                   x-text="formatKickoff(currentMatch.kickoff) + ' · ' + currentMatch.venue">
              </div>
            </div>
            <div class="lock-pill" x-show="!currentMatch.locked">
              <div class="lock-dot"></div>
              <span>Closes in</span>
              <span class="lock-time" x-text="countdown(currentMatch.lockTime)"></span>
            </div>
            <div class="lock-pill" x-show="currentMatch.locked"
                 style="background:rgba(102,124,112,.7);">
              🔒 Locked
            </div>
          </div>

          <!-- Teams area -->
          <div class="card-teams">
            <div class="card-team">
              <div class="card-flag">
                <img :src="`https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/${currentMatch.homeCode}.svg`"
                     :alt="currentMatch.homeTeam"
                     :onerror="`this.src='https://flagcdn.com/w80/${currentMatch.homeCode}.png'`">
              </div>
              <div class="card-team-name" x-text="currentMatch.homeTeam"></div>
            </div>
            <div class="card-vs">
              <div class="card-vs-text">VS</div>
              <div class="card-vs-date" x-text="formatDate(currentMatch.kickoff)"></div>
            </div>
            <div class="card-team">
              <div class="card-flag card-flag-b">
                <img :src="`https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/${currentMatch.awayCode}.svg`"
                     :alt="currentMatch.awayTeam"
                     :onerror="`this.src='https://flagcdn.com/w80/${currentMatch.awayCode}.png'`">
              </div>
              <div class="card-team-name" x-text="currentMatch.awayTeam"></div>
            </div>
          </div>

          <!-- Score input (card view) -->
          <div class="card-score-section" x-show="!currentMatch.locked">
            <div class="card-score-label">Your Predicted Score</div>
            <div class="card-score-row">
              <div class="card-score-widget">
                <button type="button" class="card-score-btn"
                        @click="adjustScore(currentMatch, 'home', -1)">−</button>
                <input class="card-score-val" type="number" min="0" max="20"
                       :value="currentMatch.predHome >= 0 ? currentMatch.predHome : ''"
                       @input="setScore(currentMatch, 'home', $event.target.value)"
                       autocomplete="off">
                <button type="button" class="card-score-btn"
                        @click="adjustScore(currentMatch, 'home', 1)">+</button>
              </div>
              <div class="card-score-colon">:</div>
              <div class="card-score-widget">
                <button type="button" class="card-score-btn"
                        @click="adjustScore(currentMatch, 'away', -1)">−</button>
                <input class="card-score-val" type="number" min="0" max="20"
                       :value="currentMatch.predAway >= 0 ? currentMatch.predAway : ''"
                       @input="setScore(currentMatch, 'away', $event.target.value)"
                       autocomplete="off">
                <button type="button" class="card-score-btn"
                        @click="adjustScore(currentMatch, 'away', 1)">+</button>
              </div>
            </div>
          </div>

          <!-- Locked display (card view) -->
          <div class="card-score-section" x-show="currentMatch.locked"
               style="text-align:center;">
            <div class="card-score-label">Match Locked</div>
            <div style="font-family:'Bebas Neue',sans-serif;font-size:48px;color:var(--muted);"
                 x-text="isFilled(currentMatch) ? `${currentMatch.predHome} : ${currentMatch.predAway}` : 'No prediction'">
            </div>
          </div>

          <!-- Card navigation -->
          <div class="card-nav">
            <button type="button" class="card-nav-btn"
                    :disabled="currentCardIndex === 0"
                    @click="currentCardIndex--">
              ← Prev
            </button>
            <span style="font-size:12px;font-weight:700;color:var(--muted);"
                  x-text="`${currentCardIndex + 1} of ${matches.length}`">
            </span>
            <button type="button" class="card-save-next"
                    x-show="currentCardIndex < matches.length - 1"
                    @click="saveAndNext">
              Save &amp; Next →
            </button>
            <button type="button" class="submit-btn"
                    x-show="currentCardIndex === matches.length - 1"
                    :disabled="!allFilled"
                    @click="submitPredictions"
                    style="max-width:200px;height:42px;font-size:13px;">
              🔒 Submit All
            </button>
          </div>

        </div>
      </div>
    </div>

    <!-- Hidden submission form (shared by both views) -->
    <form id="predictions-form" th:action="@{/predictions/submit}" method="post" style="display:none;">
      <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
      <input type="hidden" name="matchday" th:value="${activeMatchday}"/>
      <!-- inputs injected dynamically by Alpine submitPredictions() -->
    </form>

  </div><!-- end Alpine component -->

</div><!-- end round-content -->

<script>
/**
 * Alpine.js component for the predictions round content.
 * Receives match data from Thymeleaf inline JSON.
 */
function predictionsApp(matchData) {
  return {
    // State
    viewMode: 'list',
    currentCardIndex: 0,
    matches: matchData.map(m => ({
      ...m,
      // Initialize score from saved prediction; -1 means unfilled
      predHome: m.predHome >= 0 ? m.predHome : -1,
      predAway: m.predAway >= 0 ? m.predAway : -1,
    })),

    // Computed
    get filledCount() {
      return this.matches.filter(m => this.isFilled(m)).length;
    },
    get allFilled() {
      return this.matches.filter(m => !m.locked).every(m => this.isFilled(m));
    },
    get currentMatch() {
      return this.matches[this.currentCardIndex] || this.matches[0];
    },
    get groupedByDate() {
      const groups = {};
      for (const match of this.matches) {
        const date = this.formatDateKey(match.kickoff);
        if (!groups[date]) groups[date] = [];
        groups[date].push(match);
      }
      return groups;
    },

    // Score manipulation
    adjustScore(match, side, delta) {
      if (match.locked) return;
      const current = side === 'home' ? match.predHome : match.predAway;
      const next = Math.max(0, Math.min(20, (current >= 0 ? current : 0) + delta));
      if (side === 'home') match.predHome = next;
      else match.predAway = next;
    },
    setScore(match, side, value) {
      const num = parseInt(value);
      const val = isNaN(num) ? -1 : Math.max(0, Math.min(20, num));
      if (side === 'home') match.predHome = val;
      else match.predAway = val;
    },
    isFilled(match) {
      return match.predHome >= 0 && match.predAway >= 0;
    },
    isUrgent(match) {
      // Urgent = within 3 hours of lock
      if (!match.lockTime) return false;
      const lock = new Date(match.lockTime);
      const now = new Date();
      return lock - now < 3 * 3600 * 1000 && lock > now;
    },

    // Card carousel
    saveAndNext() {
      // Mark current match as interacted (score stays), advance index
      if (this.currentCardIndex < this.matches.length - 1) {
        this.currentCardIndex++;
      }
    },

    // Submission
    submitPredictions() {
      if (!this.allFilled) return;
      const form = document.getElementById('predictions-form');
      // Clear previous dynamic inputs
      form.querySelectorAll('.dynamic-input').forEach(el => el.remove());
      // Add prediction inputs
      this.matches.filter(m => !m.locked).forEach((match, idx) => {
        const mkInput = (name, val) => {
          const inp = document.createElement('input');
          inp.type = 'hidden';
          inp.name = name;
          inp.value = val;
          inp.className = 'dynamic-input';
          return inp;
        };
        form.appendChild(mkInput(`predictions[${idx}].matchId`,   match.matchId));
        form.appendChild(mkInput(`predictions[${idx}].homeScore`, match.predHome));
        form.appendChild(mkInput(`predictions[${idx}].awayScore`, match.predAway));
      });
      form.submit();
    },

    // Countdown timer (updated each second via setInterval)
    countdowns: {},
    countdown(lockTimeStr) {
      if (!lockTimeStr) return '--:--:--';
      const lock = new Date(lockTimeStr);
      const diff = Math.max(0, lock - new Date());
      const h = Math.floor(diff / 3600000);
      const m = Math.floor((diff % 3600000) / 60000);
      const s = Math.floor((diff % 60000) / 1000);
      return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`;
    },

    // Date formatting
    formatKickoff(dtStr) {
      if (!dtStr) return '';
      const d = new Date(dtStr);
      return d.toLocaleDateString('en-US', { month:'short', day:'numeric', year:'numeric' }) +
             ' · ' +
             d.toLocaleTimeString('en-US', { hour:'2-digit', minute:'2-digit' });
    },
    formatDate(dtStr) {
      if (!dtStr) return '';
      return new Date(dtStr).toLocaleDateString('en-US', { weekday:'short', month:'short', day:'numeric' });
    },
    formatDateKey(dtStr) {
      if (!dtStr) return 'TBD';
      return new Date(dtStr).toLocaleDateString('en-US', { weekday:'long', month:'long', day:'numeric' });
    },

    // Tick countdowns every second
    init() {
      setInterval(() => {
        // Force Alpine reactivity by touching a reactive property
        this.matches = [...this.matches];
      }, 1000);
    }
  };
}
</script>

</body>
</html>
```

- [ ] **Step 2: Commit**
```bash
git add src/main/resources/templates/fragments/predictions-round-content.html
git commit -m "feat: add predictions round content HTMX fragment with Alpine.js list+card views"
```

---

### Task 5: predictions.html (main template)

**Files:**
- Create: `src/main/resources/templates/predictions.html`

- [ ] **Step 1: Create predictions.html**

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>My Predictions — WC Predict 2026</title>

  <!-- Google Fonts -->
  <link rel="preconnect" href="https://fonts.googleapis.com"/>
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin="anonymous"/>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&family=Bebas+Neue&display=swap"
        rel="stylesheet"/>

  <!-- HTMX -->
  <script src="https://unpkg.com/htmx.org@1.9.12" defer></script>

  <!-- Alpine.js (must NOT be deferred — needs to be available before DOM parse) -->
  <script src="https://cdn.jsdelivr.net/npm/alpinejs@3.x.x/dist/cdn.min.js" defer></script>

  <!-- Custom CSS -->
  <link rel="stylesheet" th:href="@{/css/predictions.css}"/>

  <!-- CSRF meta for HTMX -->
  <meta name="_csrf"        th:content="${_csrf.token}"/>
  <meta name="_csrf_header" th:content="${_csrf.headerName}"/>

  <script>
    // Configure HTMX to send CSRF token with every request
    document.addEventListener('htmx:configRequest', (e) => {
      e.detail.headers['X-CSRF-TOKEN'] =
        document.querySelector('meta[name="_csrf"]').getAttribute('content');
    });
  </script>
</head>
<body>

<!-- Background -->
<div class="bg-wrap">
  <div class="bg-ph"></div>
  <div class="bg-ov"></div>
  <div class="bg-st"></div>
</div>

<!-- ─── Navbar ──────────────────────────────────────────── -->
<nav style="position:sticky;top:0;z-index:300;width:100%;height:60px;padding:0 36px;
            display:flex;align-items:center;justify-content:space-between;
            background:rgba(240,253,245,.93);backdrop-filter:blur(20px);
            border-bottom:1px solid var(--border);box-shadow:0 2px 14px rgba(0,160,70,.07);">
  <a th:href="@{/}" style="display:flex;align-items:center;gap:10px;text-decoration:none;
     font-family:'Bebas Neue',sans-serif;font-size:24px;letter-spacing:3px;color:var(--green-d);">
    <div style="width:34px;height:34px;border-radius:50%;background:linear-gradient(135deg,var(--green-m),var(--green-d));
                display:flex;align-items:center;justify-content:center;font-size:18px;
                animation:spin 12s linear infinite;box-shadow:0 2px 12px rgba(0,200,83,.4);">⚽</div>
    WC PREDICT&nbsp;<span style="color:var(--orange);">2026</span>
  </a>

  <div style="display:flex;gap:2px;">
    <a th:href="@{/}" style="padding:7px 13px;border-radius:8px;font-size:13px;font-weight:600;
       color:var(--muted);text-decoration:none;">🏠 Home</a>
    <a th:href="@{/predictions}" style="padding:7px 13px;border-radius:8px;font-size:13px;font-weight:600;
       background:rgba(0,200,83,.13);color:var(--green-d);border:1px solid var(--border);text-decoration:none;">
       🎯 Predictions</a>
    <a th:href="@{/leaderboard}" style="padding:7px 13px;border-radius:8px;font-size:13px;font-weight:600;
       color:var(--muted);text-decoration:none;">🏆 Leaderboard</a>
    <a th:href="@{/bracket}" style="padding:7px 13px;border-radius:8px;font-size:13px;font-weight:600;
       color:var(--muted);text-decoration:none;">🗂 Bracket</a>
  </div>

  <div style="display:flex;align-items:center;gap:10px;">
    <div style="display:flex;align-items:center;gap:8px;background:rgba(0,200,83,.1);
                border:1px solid var(--border);border-radius:99px;padding:4px 13px 4px 4px;
                font-size:13px;font-weight:700;color:var(--green-d);">
      <img th:if="${#authentication.principal.avatarUrl != null}"
           th:src="${#authentication.principal.avatarUrl}"
           style="width:28px;height:28px;border-radius:50%;object-fit:cover;"
           alt="avatar"/>
      <div th:unless="${#authentication.principal.avatarUrl != null}"
           style="width:28px;height:28px;border-radius:50%;background:linear-gradient(135deg,var(--green-m),var(--green-d));
                  display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:800;color:white;"
           th:text="${#authentication.principal.firstName?.substring(0,1) + #authentication.principal.lastName?.substring(0,1)}">
        AH
      </div>
      <span th:text="${#authentication.principal.firstName}">User</span>
    </div>
    <form th:action="@{/logout}" method="post" style="margin:0;">
      <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
      <button type="submit" style="padding:6px 12px;border-radius:8px;font-size:12px;font-weight:700;
              background:transparent;border:1px solid var(--border);color:var(--muted);cursor:pointer;">
        Sign out
      </button>
    </form>
  </div>
</nav>

<!-- ─── Flash messages ──────────────────────────────────── -->
<div style="position:relative;z-index:1;max-width:900px;margin:0 auto;padding:0 24px;">
  <div th:if="${successMessage}" class="alert alert-success" style="margin-top:20px;">
    <span>✅</span>
    <span th:text="${successMessage}"></span>
  </div>
  <div th:if="${errorMessage}" class="alert alert-error" style="margin-top:20px;">
    <span>⚠️</span>
    <span th:text="${errorMessage}"></span>
  </div>
</div>

<!-- ─── Main page ───────────────────────────────────────── -->
<div class="page">

  <!-- Page title -->
  <div class="page-title">My Predictions</div>
  <div class="page-subtitle">All-or-nothing per round · Predictions lock 1 hour before kickoff</div>

  <!-- ─── Tournament Winner Banner ──────────────────────── -->
  <div class="winner-banner">
    <div class="winner-banner-icon">🏆</div>
    <div class="winner-banner-body">
      <div class="winner-banner-title">Tournament Winner Prediction</div>

      <!-- Already submitted -->
      <div th:if="${winnerSubmitted}" class="winner-picked" style="margin-top:8px;">
        <div class="winner-flag">
          <img th:src="|https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/${winnerPick.code}.svg|"
               th:alt="${winnerPick.name}"
               th:onerror="|this.src='https://flagcdn.com/w80/${winnerPick.code}.png'|"/>
        </div>
        <div>
          <div class="winner-banner-sub">Your pick</div>
          <div class="winner-team-name" th:text="${winnerPick.name}">Brazil</div>
        </div>
        <div class="winner-pts-badge">+10 pts if correct</div>
      </div>

      <!-- Not yet submitted -->
      <div th:unless="${winnerSubmitted}" style="margin-top:8px;">
        <div class="winner-banner-sub">Visible to everyone immediately. Pick wisely!</div>
        <form th:action="@{/predictions/winner}" method="post"
              class="winner-banner-form" style="margin-top:12px;">
          <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
          <select name="teamId" class="winner-select" required>
            <option value="">Select a team…</option>
            <option th:each="team : ${allTeams}"
                    th:value="${team.id}"
                    th:text="${team.name}">
              Team Name
            </option>
          </select>
          <button type="submit" class="winner-btn">🏆 Submit Pick</button>
        </form>
      </div>
    </div>
  </div>

  <!-- ─── Round Navigation Tabs ───────────────────────────── -->
  <div class="round-tabs" id="round-tabs">
    <div th:each="round : ${roundSummaries}"
         class="round-tab"
         th:classappend="${round.matchday == activeMatchday} ? 'active' : (${round.status == 'FUTURE'} ? 'future' : '')"
         th:if="${round.status != 'FUTURE'}"
         hx-get="${'/predictions/round/' + round.matchday}"
         hx-target="#round-content"
         hx-swap="innerHTML"
         hx-push-url="false"
         th:attr="hx-get=@{'/predictions/round/' + ${round.matchday}}"
         style="cursor:pointer;">
      <span class="tab-status"
            th:text="${round.status == 'PAST'} ? '✅' : (${round.status == 'OPEN'} ? '⚡' : '○')"
            th:attr="title=${round.status}">
      </span>
      <span th:text="${round.label}">Matchday 1</span>
      <span th:if="${round.status == 'PAST'}"
            style="background:rgba(0,200,83,.15);border-radius:6px;padding:1px 6px;font-size:10px;color:var(--green-d);"
            th:text="|${round.pointsEarned}pts|">
      </span>
      <span th:if="${round.status == 'OPEN' and round.predictedCount > 0}"
            style="background:rgba(255,214,0,.2);border-radius:6px;padding:1px 6px;font-size:10px;color:#7a5000;"
            th:text="|${round.predictedCount}/${round.totalMatches}|">
      </span>
    </div>

    <!-- Future rounds shown as greyed chips (no HTMX) -->
    <div th:each="round : ${roundSummaries}"
         th:if="${round.status == 'FUTURE'}"
         class="round-tab future"
         title="Not yet open for predictions">
      <span class="tab-status">○</span>
      <span th:text="${round.label}">Matchday 2</span>
    </div>
  </div>

  <!-- ─── Round Content (HTMX swap target) ─────────────────── -->
  <div id="round-content">
    <div th:replace="~{fragments/predictions-round-content :: roundContent}"></div>
  </div>

  <!-- ─── Past Rounds Accordion ───────────────────────────── -->
  <div class="past-rounds-section" th:if="${not #lists.isEmpty(pastRounds)}">
    <div class="past-rounds-title">Past Rounds</div>

    <div th:each="round : ${pastRounds}" class="past-round"
         x-data="{ open: false }">

      <!-- Accordion header -->
      <div class="past-round-header" @click="open = !open">
        <div class="prh-left">
          <div>
            <div class="prh-label" th:text="${round.label}">Matchday 1</div>
            <div class="prh-count"
                 th:text="|${round.matches.size()} match${round.matches.size() == 1 ? '' : 'es'}|">
              6 matches
            </div>
          </div>
        </div>
        <div class="prh-right">
          <span class="prh-pts" th:text="|${round.totalPoints} pts|">9 pts</span>
          <span class="prh-chevron" :class="{ open: open }">▼</span>
        </div>
      </div>

      <!-- Accordion body -->
      <div class="past-round-body" x-show="open" x-collapse x-transition>
        <div th:each="match : ${round.matches}" class="past-match-row">

          <!-- Teams -->
          <div class="pm-teams">
            <div class="pm-flag">
              <img th:src="|https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/${match.homeTeamCode}.svg|"
                   th:alt="${match.homeTeamName}"
                   th:onerror="|this.src='https://flagcdn.com/w80/${match.homeTeamCode}.png'|"/>
            </div>
            <span class="pm-name" th:text="${match.homeTeamName}">France</span>
            <span class="pm-vs">vs</span>
            <span class="pm-name" th:text="${match.awayTeamName}">Germany</span>
            <div class="pm-flag">
              <img th:src="|https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/${match.awayTeamCode}.svg|"
                   th:alt="${match.awayTeamName}"
                   th:onerror="|this.src='https://flagcdn.com/w80/${match.awayTeamCode}.png'|"/>
            </div>
          </div>

          <!-- Score comparison -->
          <div class="pm-scores">
            <!-- Actual score -->
            <span class="pm-actual"
                  th:text="|${match.actualHome}–${match.actualAway}|">2–1</span>
            <span class="pm-sep">actual · pred</span>
            <!-- Predicted score (color-coded by outcome) -->
            <span class="pm-predicted"
                  th:classappend="${match.outcome == 'EXACT'} ? 'exact' :
                                  (${match.outcome == 'DRAW'} ? 'draw' :
                                  (${match.outcome == 'WINNER'} ? 'winner' :
                                  (${match.outcome == 'WRONG'} ? 'wrong' : 'none')))"
                  th:text="${match.predictedHome != null} ? |${match.predictedHome}–${match.predictedAway}| : '—'">
              2–1
            </span>
          </div>

          <!-- Points badge -->
          <div class="pm-pts"
               th:classappend="${match.outcome == 'EXACT'} ? 'pts-exact' :
                               (${match.outcome == 'DRAW'} ? 'pts-draw' :
                               (${match.outcome == 'WINNER'} ? 'pts-winner' :
                               (${match.outcome == 'WRONG'} ? 'pts-wrong' : 'pts-none')))"
               th:text="${match.outcome == 'NOT_PREDICTED'} ? '—' : |+${match.pointsEarned}|">
            +3
          </div>

        </div>
      </div>
    </div>
  </div>

  <!-- No past rounds message -->
  <div th:if="${#lists.isEmpty(pastRounds)}"
       class="past-rounds-section"
       style="text-align:center;padding:32px;color:var(--muted);font-size:13px;font-weight:600;">
    Past round results will appear here after matches are completed.
  </div>

</div><!-- end .page -->

<!-- Alpine x-collapse plugin for accordion -->
<script src="https://cdn.jsdelivr.net/npm/@alpinejs/collapse@3.x.x/dist/cdn.min.js" defer></script>

</body>
</html>
```

- [ ] **Step 2: Commit**
```bash
git add src/main/resources/templates/predictions.html
git commit -m "feat: add main predictions.html template with round tabs, winner banner, and past rounds accordion"
```

---

### Task 6: Security Configuration

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/config/SecurityConfig.java`

- [ ] **Step 1: Add predictions routes to SecurityConfig**

```java
// In SecurityConfig.java, inside the HttpSecurity configuration chain,
// ensure these routes are secured to PARTICIPANT and ADMIN only:

.authorizeHttpRequests(auth -> auth
    // ... existing rules ...
    .requestMatchers("/predictions/**").hasAnyRole("PARTICIPANT", "ADMIN")
    // ... rest of rules ...
)
```

- [ ] **Step 2: Commit**
```bash
git add src/main/java/com/worldcup/prediction/config/SecurityConfig.java
git commit -m "feat: secure /predictions/** routes to PARTICIPANT and ADMIN roles"
```

---

### Task 7: Integration tests

**Files:**
- Create: `src/test/java/com/worldcup/prediction/controller/PredictionControllerTest.java`

- [ ] **Step 1: Create controller integration test**

```java
// src/test/java/com/worldcup/prediction/controller/PredictionControllerTest.java
package com.worldcup.prediction.controller;

import com.worldcup.prediction.dto.PredictionSubmitDto;
import com.worldcup.prediction.entity.User;
import com.worldcup.prediction.service.PredictionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PredictionController.class)
class PredictionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PredictionService predictionService;

    @MockBean
    com.worldcup.prediction.repository.TeamRepository teamRepository;

    @Test
    @WithMockUser(roles = "PARTICIPANT")
    void predictionsPage_returnsOk() throws Exception {
        when(predictionService.getRoundSummaries(any())).thenReturn(Collections.emptyList());
        when(predictionService.getPastRoundsForUser(any())).thenReturn(Collections.emptyList());
        when(predictionService.getMatchesForRound(any(), any(int.class))).thenReturn(Collections.emptyList());
        when(predictionService.groupMatchesByDate(any())).thenReturn(Collections.emptyMap());
        when(predictionService.getTournamentWinnerPick(any())).thenReturn(null);
        when(teamRepository.findAllByOrderByNameAsc()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/predictions"))
            .andExpect(status().isOk())
            .andExpect(view().name("predictions"));
    }

    @Test
    void predictionsPage_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/predictions"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "PARTICIPANT")
    void submitPredictions_allOrNothing_mismatch_redirectsWithError() throws Exception {
        when(predictionService.submitPredictions(any(), any()))
            .thenThrow(new IllegalStateException("You must predict all 3 matches in this round at once (all-or-nothing)."));

        mockMvc.perform(post("/predictions/submit")
                .with(csrf())
                .param("matchday", "1")
                .param("predictions[0].matchId", "1")
                .param("predictions[0].homeScore", "2")
                .param("predictions[0].awayScore", "1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/predictions"))
            .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @WithMockUser(roles = "PARTICIPANT")
    void submitPredictions_success_redirectsWithSuccessMessage() throws Exception {
        when(predictionService.submitPredictions(any(), any())).thenReturn(3);

        mockMvc.perform(post("/predictions/submit")
                .with(csrf())
                .param("matchday", "1")
                .param("predictions[0].matchId", "1")
                .param("predictions[0].homeScore", "2")
                .param("predictions[0].awayScore", "1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/predictions"))
            .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @WithMockUser(roles = "PARTICIPANT")
    void roundFragment_returnsFragment() throws Exception {
        when(predictionService.getMatchesForRound(any(), any(int.class))).thenReturn(Collections.emptyList());
        when(predictionService.groupMatchesByDate(any())).thenReturn(Collections.emptyMap());

        mockMvc.perform(get("/predictions/round/1"))
            .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Commit**
```bash
git add src/test/java/com/worldcup/prediction/controller/PredictionControllerTest.java
git commit -m "test: add PredictionController integration tests"
```

---

## Business Rules Checklist

The following rules must be verified during manual QA:

- [ ] **Lock enforcement**: submitting after `kickoff - 1h` returns error message
- [ ] **All-or-nothing**: submitting partial prediction set returns error, full set succeeds
- [ ] **Round tab HTMX**: clicking a tab loads only the content div, no full page reload
- [ ] **Card carousel**: Save & Next advances index, final card shows Submit All button
- [ ] **Progress bar**: animates as scores are filled in
- [ ] **Submit button**: disabled (grey) when any match unfilled; gold pulsing when all filled
- [ ] **Tournament winner**: submit form visible only if not yet submitted; flag shown after submit
- [ ] **Past rounds accordion**: color coding correct (green=exact, blue=draw, yellow=winner, red=wrong)
- [ ] **Score widget**: clamped 0–20, stepper buttons work, direct input works
- [ ] **Locked match display**: no score inputs shown, pill says "🔒 Locked"
- [ ] **CSRF**: all POST requests include CSRF token (form hidden field + HTMX header)
- [ ] **Mobile responsive**: card view usable on 375px width, stepper inputs reachable

---

## Key Design Decisions

1. **Alpine.js inline JSON bootstrap**: Thymeleaf renders match data as a JSON array directly into the `x-data="predictionsApp([...])"` attribute. This avoids a separate AJAX fetch and keeps data server-authoritative on page load.

2. **Hidden form for submission**: Rather than building a dynamic `application/json` fetch, the Alpine `submitPredictions()` method injects hidden inputs into a real `<form>` and calls `.submit()`. This works with Spring MVC's standard form binding and avoids AJAX CSRF complications.

3. **HTMX fragment for round tabs**: Tab switching uses `hx-get="/predictions/round/{n}"` swapping `#round-content` innerHTML. The fragment re-renders the Alpine component with fresh data. Alpine re-initialises automatically on DOM replacement.

4. **Countdown timer via Alpine reactivity**: The `init()` method ticks `this.matches = [...this.matches]` every second to force Alpine to re-evaluate the `countdown()` getter — simple and avoids per-element `setInterval` leaks.

5. **Past rounds most-recent-first**: `PredictionService.getPastRoundsForUser` returns rounds sorted descending so the most relevant recent round is at the top of the accordion.
