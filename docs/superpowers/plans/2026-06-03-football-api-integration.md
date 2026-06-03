# Football API Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enrich the WC2026 app with full football-data.org integration — teams, squads, match results, lineups, goal scorers, standings — collected via a one-time bootstrap script and kept current by rate-limited cron jobs.

**Architecture:** A shared `FootballApiRateLimiter` bean (Guava, 10 calls/min) gates all outbound API calls. Five domain-scoped sync services (teams, matches, standings, lineups, scorers) each implement a skip-if-nothing-to-do check and are callable from both scheduled jobs and admin panel buttons. A `bootstrap` Spring profile runs a `CommandLineRunner` that seeds all static data in ~3 API calls.

**Tech Stack:** Spring Boot 3.3, Spring Data JPA, Flyway, Thymeleaf + Layout Dialect, HTMX, TailwindCSS, Guava 33.x, JUnit 5, Mockito, MockRestServiceServer.

---

## File Map

**New files:**
- `src/main/resources/db/migration/V2__football_api_schema.sql`
- `src/main/java/.../domain/GroupStanding.java`
- `src/main/java/.../domain/Player.java`
- `src/main/java/.../domain/MatchLineup.java`
- `src/main/java/.../domain/MatchGoal.java`
- `src/main/java/.../domain/enums/GoalType.java`
- `src/main/java/.../repository/GroupStandingRepository.java`
- `src/main/java/.../repository/PlayerRepository.java`
- `src/main/java/.../repository/MatchLineupRepository.java`
- `src/main/java/.../repository/MatchGoalRepository.java`
- `src/main/java/.../integration/football/dto/FootballApiTeamsResponseDto.java`
- `src/main/java/.../integration/football/dto/FootballApiTeamWithSquadDto.java`
- `src/main/java/.../integration/football/dto/FootballApiPlayerDto.java`
- `src/main/java/.../integration/football/dto/FootballApiStandingsResponseDto.java`
- `src/main/java/.../integration/football/dto/FootballApiStandingGroupDto.java`
- `src/main/java/.../integration/football/dto/FootballApiStandingEntryDto.java`
- `src/main/java/.../integration/football/dto/FootballApiMatchDetailDto.java`
- `src/main/java/.../integration/football/dto/FootballApiLineupDto.java`
- `src/main/java/.../integration/football/dto/FootballApiLineupPlayerDto.java`
- `src/main/java/.../integration/football/dto/FootballApiGoalDto.java`
- `src/main/java/.../integration/football/dto/FootballApiGoalPersonDto.java`
- `src/main/java/.../integration/football/dto/FootballApiScorersResponseDto.java`
- `src/main/java/.../integration/football/dto/FootballApiScorerEntryDto.java`
- `src/main/java/.../integration/football/FootballApiRateLimiter.java`
- `src/main/java/.../integration/football/SyncResult.java`
- `src/main/java/.../integration/football/TeamSyncService.java`
- `src/main/java/.../integration/football/MatchSyncService.java`
- `src/main/java/.../integration/football/StandingSyncService.java`
- `src/main/java/.../integration/football/LineupSyncService.java`
- `src/main/java/.../integration/football/ScorersService.java`
- `src/main/java/.../scheduler/LineupSyncScheduler.java`
- `src/main/java/.../scheduler/StandingSyncScheduler.java`
- `src/main/java/.../scheduler/ScorersSyncScheduler.java`
- `src/main/java/.../bootstrap/FootballApiBootstrapRunner.java`
- `src/main/java/.../controller/admin/AdminSyncController.java`
- `src/main/java/.../controller/MatchPreviewController.java`
- `src/main/java/.../controller/TeamController.java`
- `src/main/java/.../controller/ScorersController.java`
- `src/main/resources/templates/admin/sync.html`
- `src/main/resources/templates/match-preview.html`
- `src/main/resources/templates/team.html`
- `src/main/resources/templates/scorers.html`
- `src/main/resources/application-bootstrap.properties`

**Modified files:**
- `pom.xml` — add Guava dependency
- `src/main/java/.../domain/Team.java` — add `externalId` field
- `src/main/java/.../domain/Match.java` — add `lineupFetched` field
- `src/main/java/.../repository/TeamRepository.java` — add `findByExternalId`
- `src/main/java/.../repository/MatchRepository.java` — add two queries
- `src/main/java/.../integration/football/FootballApiClient.java` — rename + 4 new methods
- `src/main/java/.../integration/football/FootballApiSyncService.java` — use renamed method + rate limiter + skip check
- `src/main/java/.../scheduler/MatchResultScheduler.java` — skip logic
- `src/main/java/.../service/GroupService.java` — add `getMatchesByGroup()`
- `src/main/java/.../service/GroupServiceImpl.java` — implement `getMatchesByGroup()`
- `src/main/java/.../controller/GroupController.java` — add matches to model
- `src/main/resources/templates/admin/layout.html` — add Sync sidebar link
- `src/main/resources/templates/groups.html` — add match list per group card

---

### Task 1: Add Guava dependency + V2 Flyway migration

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/db/migration/V2__football_api_schema.sql`

- [ ] **Step 1: Add Guava to pom.xml**

In `pom.xml`, inside `<dependencies>`, after the Lombok block add:
```xml
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>33.2.1-jre</version>
</dependency>
```

- [ ] **Step 2: Write V2 migration**

Create `src/main/resources/db/migration/V2__football_api_schema.sql`:
```sql
-- V2__football_api_schema.sql
-- Adds football API enrichment tables and columns
-- ANSI SQL — compatible with SQLite and PostgreSQL

ALTER TABLE teams ADD COLUMN external_id INTEGER;
CREATE UNIQUE INDEX teams_external_id_idx ON teams(external_id);

ALTER TABLE matches ADD COLUMN lineup_fetched INTEGER NOT NULL DEFAULT 0;
CREATE INDEX matches_lineup_fetched_idx ON matches(lineup_fetched);

ALTER TABLE group_standings ADD COLUMN position INTEGER NOT NULL DEFAULT 0;

CREATE TABLE players (
    id               INTEGER PRIMARY KEY,
    external_id      INTEGER NOT NULL,
    team_id          INTEGER NOT NULL REFERENCES teams(id),
    name             VARCHAR(100) NOT NULL,
    position         VARCHAR(20),
    nationality      VARCHAR(100),
    date_of_birth    DATE,
    shirt_number     INTEGER,
    tournament_goals INTEGER NOT NULL DEFAULT 0,
    UNIQUE(external_id)
);
CREATE INDEX players_team_id_idx ON players(team_id);
CREATE INDEX players_goals_idx ON players(tournament_goals);

CREATE TABLE match_lineups (
    id                 INTEGER PRIMARY KEY,
    match_id           INTEGER NOT NULL REFERENCES matches(id),
    team_id            INTEGER NOT NULL REFERENCES teams(id),
    player_id          INTEGER NOT NULL REFERENCES players(id),
    starting           INTEGER NOT NULL DEFAULT 0,
    shirt_number       INTEGER,
    formation_position VARCHAR(50)
);
CREATE INDEX match_lineups_match_id_idx ON match_lineups(match_id);

CREATE TABLE match_goals (
    id        INTEGER PRIMARY KEY,
    match_id  INTEGER NOT NULL REFERENCES matches(id),
    team_id   INTEGER NOT NULL REFERENCES teams(id),
    player_id INTEGER REFERENCES players(id),
    minute    INTEGER NOT NULL,
    type      VARCHAR(20) NOT NULL DEFAULT 'REGULAR'
);
CREATE INDEX match_goals_match_id_idx ON match_goals(match_id);
```

- [ ] **Step 3: Build and verify migration runs**
```bash
cd /Users/arthurho/dev/tools/world-cup-prediction
./mvnw compile -q
```
Expected: BUILD SUCCESS with no Flyway errors in output.

- [ ] **Step 4: Commit**
```bash
git add pom.xml src/main/resources/db/migration/V2__football_api_schema.sql
git commit -m "feat: add Guava, V2 migration for players/lineups/goals/standings position"
```

---

### Task 2: GroupStanding entity + repository

**Files:**
- Create: `src/main/java/com/worldcup/prediction/domain/GroupStanding.java`
- Create: `src/main/java/com/worldcup/prediction/repository/GroupStandingRepository.java`
- Test: `src/test/java/com/worldcup/prediction/repository/GroupStandingRepositoryTest.java`

- [ ] **Step 1: Create GroupStanding entity**

```java
package com.worldcup.prediction.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_standings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GroupStanding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    private int position;
    private int played;
    private int won;
    private int drawn;
    private int lost;

    @Column(name = "goals_for")
    private int goalsFor;

    @Column(name = "goals_against")
    private int goalsAgainst;

    @Column(name = "goal_difference")
    private int goalDifference;

    private int points;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: Create GroupStandingRepository**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.GroupStanding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupStandingRepository extends JpaRepository<GroupStanding, Long> {

    List<GroupStanding> findByGroupIdOrderByPositionAsc(Long groupId);

    Optional<GroupStanding> findByGroupIdAndTeamId(Long groupId, Long teamId);

    @Query("SELECT MAX(gs.updatedAt) FROM GroupStanding gs")
    Optional<LocalDateTime> findMostRecentUpdateTime();
}
```

- [ ] **Step 3: Write repository test**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Group;
import com.worldcup.prediction.domain.GroupStanding;
import com.worldcup.prediction.domain.Team;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("sqlite")
class GroupStandingRepositoryTest {

    @Autowired GroupStandingRepository standingRepo;
    @Autowired GroupRepository groupRepo;
    @Autowired TeamRepository teamRepo;

    @Test
    void findByGroupIdAndTeamId_returnsCorrectRow() {
        Group g = groupRepo.findByNameIgnoreCase("A").orElseThrow();
        Team t = teamRepo.findAll().get(0);

        GroupStanding s = GroupStanding.builder()
                .group(g).team(t).position(1).points(9).played(3).won(3).build();
        standingRepo.save(s);

        Optional<GroupStanding> found = standingRepo.findByGroupIdAndTeamId(g.getId(), t.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getPoints()).isEqualTo(9);
    }
}
```

- [ ] **Step 4: Run test**
```bash
./mvnw test -pl . -Dtest=GroupStandingRepositoryTest -q
```
Expected: BUILD SUCCESS, 1 test passing.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/worldcup/prediction/domain/GroupStanding.java \
        src/main/java/com/worldcup/prediction/repository/GroupStandingRepository.java \
        src/test/java/com/worldcup/prediction/repository/GroupStandingRepositoryTest.java
git commit -m "feat: GroupStanding entity and repository"
```

---

### Task 3: Player entity + repository

**Files:**
- Create: `src/main/java/com/worldcup/prediction/domain/Player.java`
- Create: `src/main/java/com/worldcup/prediction/repository/PlayerRepository.java`
- Test: `src/test/java/com/worldcup/prediction/repository/PlayerRepositoryTest.java`

- [ ] **Step 1: Create Player entity**

```java
package com.worldcup.prediction.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "players")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private Long externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String position;

    @Column(length = 100)
    private String nationality;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "shirt_number")
    private Integer shirtNumber;

    @Column(name = "tournament_goals", nullable = false)
    @Builder.Default
    private int tournamentGoals = 0;
}
```

- [ ] **Step 2: Create PlayerRepository**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByExternalId(Long externalId);

    List<Player> findByTeamIdOrderByShirtNumberAsc(Long teamId);

    List<Player> findByTournamentGoalsGreaterThanOrderByTournamentGoalsDesc(int minGoals);
}
```

- [ ] **Step 3: Write test**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Player;
import com.worldcup.prediction.domain.Team;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("sqlite")
class PlayerRepositoryTest {

    @Autowired PlayerRepository playerRepo;
    @Autowired TeamRepository teamRepo;

    @Test
    void findByExternalId_returnsPlayer() {
        Team team = teamRepo.findAll().get(0);
        Player p = Player.builder().externalId(9999L).team(team).name("Test Player").build();
        playerRepo.save(p);

        assertThat(playerRepo.findByExternalId(9999L)).isPresent();
    }

    @Test
    void findByTournamentGoalsGreaterThan_onlyReturnsScorers() {
        Team team = teamRepo.findAll().get(0);
        playerRepo.save(Player.builder().externalId(1L).team(team).name("Scorer").tournamentGoals(3).build());
        playerRepo.save(Player.builder().externalId(2L).team(team).name("NoGoals").tournamentGoals(0).build());

        List<Player> scorers = playerRepo.findByTournamentGoalsGreaterThanOrderByTournamentGoalsDesc(0);
        assertThat(scorers).hasSize(1);
        assertThat(scorers.get(0).getName()).isEqualTo("Scorer");
    }
}
```

- [ ] **Step 4: Run test**
```bash
./mvnw test -pl . -Dtest=PlayerRepositoryTest -q
```
Expected: BUILD SUCCESS, 2 tests passing.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/worldcup/prediction/domain/Player.java \
        src/main/java/com/worldcup/prediction/repository/PlayerRepository.java \
        src/test/java/com/worldcup/prediction/repository/PlayerRepositoryTest.java
git commit -m "feat: Player entity and repository"
```

---

### Task 4: MatchLineup, MatchGoal, GoalType + repositories

**Files:**
- Create: `src/main/java/com/worldcup/prediction/domain/enums/GoalType.java`
- Create: `src/main/java/com/worldcup/prediction/domain/MatchLineup.java`
- Create: `src/main/java/com/worldcup/prediction/domain/MatchGoal.java`
- Create: `src/main/java/com/worldcup/prediction/repository/MatchLineupRepository.java`
- Create: `src/main/java/com/worldcup/prediction/repository/MatchGoalRepository.java`

- [ ] **Step 1: Create GoalType enum**

```java
package com.worldcup.prediction.domain.enums;

public enum GoalType {
    REGULAR, OWN_GOAL, PENALTY
}
```

- [ ] **Step 2: Create MatchLineup entity**

```java
package com.worldcup.prediction.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "match_lineups")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MatchLineup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(nullable = false)
    private boolean starting;

    @Column(name = "shirt_number")
    private Integer shirtNumber;

    @Column(name = "formation_position", length = 50)
    private String formationPosition;
}
```

- [ ] **Step 3: Create MatchGoal entity**

```java
package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.GoalType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "match_goals")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MatchGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private Player player;  // nullable — own goals have no attributed scorer

    @Column(nullable = false)
    private int minute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GoalType type = GoalType.REGULAR;
}
```

- [ ] **Step 4: Create repositories**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.MatchLineup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchLineupRepository extends JpaRepository<MatchLineup, Long> {

    List<MatchLineup> findByMatchIdAndTeamIdOrderByStartingDescShirtNumberAsc(Long matchId, Long teamId);

    boolean existsByMatchId(Long matchId);
}
```

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.MatchGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchGoalRepository extends JpaRepository<MatchGoal, Long> {

    List<MatchGoal> findByMatchIdOrderByMinuteAsc(Long matchId);

    boolean existsByMatchId(Long matchId);
}
```

- [ ] **Step 5: Build to verify no compilation errors**
```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/worldcup/prediction/domain/enums/GoalType.java \
        src/main/java/com/worldcup/prediction/domain/MatchLineup.java \
        src/main/java/com/worldcup/prediction/domain/MatchGoal.java \
        src/main/java/com/worldcup/prediction/repository/MatchLineupRepository.java \
        src/main/java/com/worldcup/prediction/repository/MatchGoalRepository.java
git commit -m "feat: MatchLineup, MatchGoal, GoalType, lineup/goal repositories"
```

---

### Task 5: Add externalId to Team, lineupFetched to Match, update repositories

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/domain/Team.java`
- Modify: `src/main/java/com/worldcup/prediction/domain/Match.java`
- Modify: `src/main/java/com/worldcup/prediction/repository/TeamRepository.java`
- Modify: `src/main/java/com/worldcup/prediction/repository/MatchRepository.java`

- [ ] **Step 1: Add externalId to Team**

In `Team.java`, after the `confederation` field add:
```java
@Column(name = "external_id")
private Long externalId;
```

- [ ] **Step 2: Add lineupFetched to Match**

In `Match.java`, after the `awayScore90` field add:
```java
@Column(name = "lineup_fetched", nullable = false)
@Builder.Default
private boolean lineupFetched = false;
```

- [ ] **Step 3: Add findByExternalId to TeamRepository**

In `TeamRepository.java` add:
```java
Optional<Team> findByExternalId(Long externalId);
```

- [ ] **Step 4: Add two queries to MatchRepository**

In `MatchRepository.java` add:
```java
List<Match> findByStatusAndLineupFetchedFalse(MatchStatus status);

long countByStatusAndKickoffTimeBefore(MatchStatus status, LocalDateTime time);

long countByStatusAndUpdatedAtAfter(MatchStatus status, LocalDateTime time);
```

- [ ] **Step 5: Build**
```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 6: Run existing tests to catch regressions**
```bash
./mvnw test -q
```
Expected: all tests pass.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/worldcup/prediction/domain/Team.java \
        src/main/java/com/worldcup/prediction/domain/Match.java \
        src/main/java/com/worldcup/prediction/repository/TeamRepository.java \
        src/main/java/com/worldcup/prediction/repository/MatchRepository.java
git commit -m "feat: add externalId to Team, lineupFetched to Match, extend repos"
```

---

### Task 6: SyncResult + FootballApiRateLimiter

**Files:**
- Create: `src/main/java/com/worldcup/prediction/integration/football/SyncResult.java`
- Create: `src/main/java/com/worldcup/prediction/integration/football/FootballApiRateLimiter.java`
- Test: `src/test/java/com/worldcup/prediction/integration/football/FootballApiRateLimiterTest.java`

- [ ] **Step 1: Create SyncResult**

```java
package com.worldcup.prediction.integration.football;

public record SyncResult(boolean skipped, String message) {

    public static SyncResult success(String message) {
        return new SyncResult(false, message);
    }

    public static SyncResult skipped(String reason) {
        return new SyncResult(true, "Skipped: " + reason);
    }
}
```

- [ ] **Step 2: Write failing test for FootballApiRateLimiter**

```java
package com.worldcup.prediction.integration.football;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FootballApiRateLimiterTest {

    @Test
    void call_executesAndReturnsResult() {
        FootballApiRateLimiter limiter = new FootballApiRateLimiter();
        String result = limiter.call(() -> "hello");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void call_propagatesRuntimeException() {
        FootballApiRateLimiter limiter = new FootballApiRateLimiter();
        assertThatThrownBy(() -> limiter.call(() -> { throw new RuntimeException("boom"); }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");
    }
}
```

- [ ] **Step 3: Run test to confirm it fails**
```bash
./mvnw test -Dtest=FootballApiRateLimiterTest -q 2>&1 | tail -5
```
Expected: compilation error — `FootballApiRateLimiter` not found.

- [ ] **Step 4: Create FootballApiRateLimiter**

```java
package com.worldcup.prediction.integration.football;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@Slf4j
public class FootballApiRateLimiter {

    private final RateLimiter rateLimiter = RateLimiter.create(10.0 / 60.0);

    public <T> T call(Supplier<T> apiCall) {
        double waited = rateLimiter.acquire();
        if (waited > 0.5) {
            log.debug("Rate limiter: waited {:.1f}s before API call", waited);
        }
        return apiCall.get();
    }
}
```

- [ ] **Step 5: Run test to confirm it passes**
```bash
./mvnw test -Dtest=FootballApiRateLimiterTest -q
```
Expected: BUILD SUCCESS, 2 tests passing.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/worldcup/prediction/integration/football/SyncResult.java \
        src/main/java/com/worldcup/prediction/integration/football/FootballApiRateLimiter.java \
        src/test/java/com/worldcup/prediction/integration/football/FootballApiRateLimiterTest.java
git commit -m "feat: SyncResult record and FootballApiRateLimiter (Guava 10/min)"
```

---

### Task 7: New DTOs + expand FootballApiClient

**Files:**
- Create: 9 new DTO files in `src/main/java/com/worldcup/prediction/integration/football/dto/`
- Modify: `src/main/java/com/worldcup/prediction/integration/football/FootballApiClient.java`
- Test: `src/test/java/com/worldcup/prediction/integration/football/FootballApiClientTest.java` (extend existing)

- [ ] **Step 1: Create teams DTOs**

`FootballApiTeamsResponseDto.java`:
```java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiTeamsResponseDto(Integer count, List<FootballApiTeamWithSquadDto> teams) {}
```

`FootballApiTeamWithSquadDto.java`:
```java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiTeamWithSquadDto(Long id, String name, String shortName, String tla,
                                          List<FootballApiPlayerDto> squad) {}
```

`FootballApiPlayerDto.java`:
```java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiPlayerDto(Long id, String name, String position,
                                   String dateOfBirth, String nationality, Integer shirtNumber) {}
```

- [ ] **Step 2: Create standings DTOs**

`FootballApiStandingsResponseDto.java`:
```java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiStandingsResponseDto(List<FootballApiStandingGroupDto> standings) {}
```

`FootballApiStandingGroupDto.java`:
```java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiStandingGroupDto(String stage, String type, String group,
                                          List<FootballApiStandingEntryDto> table) {}
```

`FootballApiStandingEntryDto.java`:
```java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiStandingEntryDto(Integer position, FootballApiTeamDto team,
                                          Integer playedGames, Integer won, Integer draw,
                                          Integer lost, Integer points, Integer goalsFor,
                                          Integer goalsAgainst, Integer goalDifference) {}
```

- [ ] **Step 3: Create match detail DTOs**

`FootballApiGoalPersonDto.java`:
```java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiGoalPersonDto(Long id, String name) {}
```

`FootballApiGoalDto.java`:
```java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiGoalDto(Integer minute, String type,
                                 FootballApiTeamDto team, FootballApiGoalPersonDto scorer) {}
```

`FootballApiLineupPlayerDto.java`:
```java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiLineupPlayerDto(FootballApiGoalPersonDto player,
                                         String position, Integer shirtNumber) {}
```

`FootballApiLineupDto.java`:
```java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiLineupDto(FootballApiTeamDto team,
                                   List<FootballApiLineupPlayerDto> startXI,
                                   List<FootballApiLineupPlayerDto> substitutes) {}
```

`FootballApiMatchDetailDto.java`:
```java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiMatchDetailDto(Long id, String utcDate, String status,
                                        Integer matchday, String stage, String group,
                                        FootballApiTeamDto homeTeam, FootballApiTeamDto awayTeam,
                                        FootballApiScoreDto score,
                                        List<FootballApiGoalDto> goals,
                                        List<FootballApiLineupDto> lineups) {}
```

- [ ] **Step 4: Create scorers DTOs**

`FootballApiScorersResponseDto.java`:
```java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiScorersResponseDto(Integer count, List<FootballApiScorerEntryDto> scorers) {}
```

`FootballApiScorerEntryDto.java`:
```java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiScorerEntryDto(FootballApiGoalPersonDto player,
                                        FootballApiTeamDto team, Integer goals) {}
```

- [ ] **Step 5: Expand FootballApiClient**

Replace `FootballApiClient.java` with the full expanded version:
```java
package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.integration.football.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class FootballApiClient {

    private static final String BASE = "https://api.football-data.org/v4";
    private static final String MATCHES_URL   = BASE + "/competitions/WC/matches";
    private static final String TEAMS_URL     = BASE + "/competitions/WC/teams";
    private static final String STANDINGS_URL = BASE + "/competitions/WC/standings";
    private static final String MATCH_URL     = BASE + "/matches/{id}";
    private static final String SCORERS_URL   = BASE + "/competitions/WC/scorers?limit=20";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final boolean enabled;

    public FootballApiClient(RestTemplate restTemplate,
                             @Value("${app.football.api.enabled:false}") boolean enabled,
                             @Value("${football.api.key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.apiKey = apiKey;
        if (!enabled) log.info("FootballApiClient: disabled — live sync off");
    }

    public FootballApiResponseDto fetchAllMatches() {
        return get(MATCHES_URL, FootballApiResponseDto.class);
    }

    public FootballApiTeamsResponseDto fetchTeamsWithSquads() {
        return get(TEAMS_URL, FootballApiTeamsResponseDto.class);
    }

    public FootballApiStandingsResponseDto fetchStandings() {
        return get(STANDINGS_URL, FootballApiStandingsResponseDto.class);
    }

    public FootballApiMatchDetailDto fetchMatchDetail(long matchId) {
        if (!enabled || apiKey.isBlank()) return null;
        HttpEntity<Void> req = new HttpEntity<>(authHeaders());
        try {
            return restTemplate.exchange(MATCH_URL, HttpMethod.GET, req,
                    FootballApiMatchDetailDto.class, matchId).getBody();
        } catch (RestClientException e) {
            log.warn("Failed to fetch match detail id={}: {}", matchId, e.getMessage());
            return null;
        }
    }

    public FootballApiScorersResponseDto fetchTopScorers() {
        return get(SCORERS_URL, FootballApiScorersResponseDto.class);
    }

    private <T> T get(String url, Class<T> type) {
        if (!enabled) { log.debug("Football API disabled — skipping"); return null; }
        if (apiKey.isBlank()) { log.debug("No API key — skipping"); return null; }
        try {
            return restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), type).getBody();
        } catch (RestClientException e) {
            log.warn("Football API call failed [{}]: {}", url, e.getMessage());
            return null;
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Auth-Token", apiKey);
        return h;
    }
}
```

- [ ] **Step 6: Fix FootballApiSyncService — update method call from fetchMatches() to fetchAllMatches()**

In `FootballApiSyncService.java` line 37, change:
```java
FootballApiResponseDto response = apiClient.fetchMatches();
```
to:
```java
FootballApiResponseDto response = apiClient.fetchAllMatches();
```

- [ ] **Step 7: Fix FootballApiClientTest — update test to use fetchAllMatches()**

In `FootballApiClientTest.java`, change all three calls from `client.fetchMatches()` to `client.fetchAllMatches()`.

- [ ] **Step 8: Add new client tests**

Append to `FootballApiClientTest.java`:
```java
@Test
void fetchTeamsWithSquads_returnsParsedTeams() {
    String json = """
        { "count": 1, "teams": [{
          "id": 773, "name": "Germany", "shortName": "Germany", "tla": "GER",
          "squad": [{ "id": 3359, "name": "Neuer", "position": "Goalkeeper",
                      "dateOfBirth": "1986-03-27", "nationality": "Germany", "shirtNumber": 1 }]
        }]}
        """;
    mockServer.expect(requestTo("https://api.football-data.org/v4/competitions/WC/teams"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

    FootballApiTeamsResponseDto resp = client.fetchTeamsWithSquads();

    mockServer.verify();
    assertThat(resp.teams()).hasSize(1);
    assertThat(resp.teams().get(0).tla()).isEqualTo("GER");
    assertThat(resp.teams().get(0).squad()).hasSize(1);
    assertThat(resp.teams().get(0).squad().get(0).name()).isEqualTo("Neuer");
}

@Test
void fetchMatchDetail_returnsParsedDetail() {
    String json = """
        { "id": 436780, "status": "FINISHED", "matchday": 1, "stage": "GROUP_STAGE",
          "group": "GROUP_A",
          "homeTeam": { "id": 773, "name": "Germany", "tla": "GER" },
          "awayTeam": { "id": 9,   "name": "Australia", "tla": "AUS" },
          "score": { "fullTime": { "home": 2, "away": 1 } },
          "goals": [{ "minute": 23, "type": "REGULAR",
                      "team": { "id": 773, "tla": "GER" },
                      "scorer": { "id": 3359, "name": "Müller" } }],
          "lineups": [
            { "team": { "id": 773, "tla": "GER" },
              "startXI": [{ "player": { "id": 3359, "name": "Neuer" }, "position": "Goalkeeper", "shirtNumber": 1 }],
              "substitutes": [] }
          ]
        }
        """;
    mockServer.expect(requestTo("https://api.football-data.org/v4/matches/436780"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

    FootballApiMatchDetailDto detail = client.fetchMatchDetail(436780L);

    mockServer.verify();
    assertThat(detail).isNotNull();
    assertThat(detail.goals()).hasSize(1);
    assertThat(detail.goals().get(0).scorer().name()).isEqualTo("Müller");
    assertThat(detail.lineups()).hasSize(1);
    assertThat(detail.lineups().get(0).startXI()).hasSize(1);
}
```

- [ ] **Step 9: Run all client tests**
```bash
./mvnw test -Dtest=FootballApiClientTest -q
```
Expected: BUILD SUCCESS, 6 tests passing.

- [ ] **Step 10: Commit**
```bash
git add src/main/java/com/worldcup/prediction/integration/football/dto/ \
        src/main/java/com/worldcup/prediction/integration/football/FootballApiClient.java \
        src/main/java/com/worldcup/prediction/integration/football/FootballApiSyncService.java \
        src/test/java/com/worldcup/prediction/integration/football/FootballApiClientTest.java
git commit -m "feat: expand FootballApiClient with 4 new endpoints and DTOs"
```

---

### Task 8: TeamSyncService

**Files:**
- Create: `src/main/java/com/worldcup/prediction/integration/football/TeamSyncService.java`
- Test: `src/test/java/com/worldcup/prediction/integration/football/TeamSyncServiceTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.integration.football.dto.*;
import com.worldcup.prediction.repository.PlayerRepository;
import com.worldcup.prediction.repository.TeamRepository;
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
class TeamSyncServiceTest {

    @Mock FootballApiClient client;
    @Mock FootballApiRateLimiter rateLimiter;
    @Mock TeamRepository teamRepo;
    @Mock PlayerRepository playerRepo;
    @InjectMocks TeamSyncService service;

    @Test
    void syncTeamsAndSquads_upsetsTeamExternalIdAndPlayers() {
        FootballApiPlayerDto playerDto = new FootballApiPlayerDto(
                3359L, "Neuer", "Goalkeeper", "1986-03-27", "Germany", 1);
        FootballApiTeamWithSquadDto teamDto = new FootballApiTeamWithSquadDto(
                773L, "Germany", "Germany", "GER", List.of(playerDto));
        FootballApiTeamsResponseDto response = new FootballApiTeamsResponseDto(1, List.of(teamDto));

        when(rateLimiter.call(any())).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(client.fetchTeamsWithSquads()).thenReturn(response);

        Team team = new Team();
        team.setId(1L);
        team.setFifaCode("GER");
        when(teamRepo.findByExternalId(773L)).thenReturn(Optional.empty());
        when(teamRepo.findByFifaCodeIgnoreCase("GER")).thenReturn(Optional.of(team));
        when(playerRepo.findByExternalId(3359L)).thenReturn(Optional.empty());

        SyncResult result = service.syncTeamsAndSquads();

        assertThat(result.skipped()).isFalse();
        verify(teamRepo).save(argThat(t -> Long.valueOf(773L).equals(t.getExternalId())));
        verify(playerRepo).save(argThat(p -> p.getExternalId().equals(3359L) && p.getName().equals("Neuer")));
    }

    @Test
    void syncTeamsAndSquads_whenNullResponse_returnsSkipped() {
        when(rateLimiter.call(any())).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        when(client.fetchTeamsWithSquads()).thenReturn(null);

        SyncResult result = service.syncTeamsAndSquads();

        assertThat(result.skipped()).isTrue();
        verifyNoInteractions(teamRepo, playerRepo);
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**
```bash
./mvnw test -Dtest=TeamSyncServiceTest -q 2>&1 | tail -5
```
Expected: compilation error — `TeamSyncService` not found.

- [ ] **Step 3: Create TeamSyncService**

```java
package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.Player;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.integration.football.dto.*;
import com.worldcup.prediction.repository.PlayerRepository;
import com.worldcup.prediction.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class TeamSyncService {

    private final FootballApiClient client;
    private final FootballApiRateLimiter rateLimiter;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    public SyncResult syncTeamsAndSquads() {
        FootballApiTeamsResponseDto response = rateLimiter.call(client::fetchTeamsWithSquads);
        if (response == null || response.teams() == null) {
            return SyncResult.skipped("No API response");
        }

        int teamsUpdated = 0;
        int playersUpserted = 0;

        for (FootballApiTeamWithSquadDto apiTeam : response.teams()) {
            Team team = teamRepository.findByExternalId(apiTeam.id())
                    .or(() -> teamRepository.findByFifaCodeIgnoreCase(apiTeam.tla()))
                    .orElse(null);

            if (team == null) {
                log.warn("No team found for tla={} externalId={} — skipping", apiTeam.tla(), apiTeam.id());
                continue;
            }

            team.setExternalId(apiTeam.id());
            team.setName(apiTeam.name());
            team.setShortName(apiTeam.shortName());
            teamRepository.save(team);
            teamsUpdated++;

            if (apiTeam.squad() == null) continue;

            for (FootballApiPlayerDto p : apiTeam.squad()) {
                Player player = playerRepository.findByExternalId(p.id()).orElse(new Player());
                player.setExternalId(p.id());
                player.setTeam(team);
                player.setName(p.name());
                player.setPosition(p.position());
                player.setNationality(p.nationality());
                player.setShirtNumber(p.shirtNumber());
                if (p.dateOfBirth() != null && p.dateOfBirth().length() >= 10) {
                    player.setDateOfBirth(LocalDate.parse(p.dateOfBirth().substring(0, 10)));
                }
                playerRepository.save(player);
                playersUpserted++;
            }
        }

        return SyncResult.success(teamsUpdated + " teams, " + playersUpserted + " players upserted");
    }
}
```

- [ ] **Step 4: Run test**
```bash
./mvnw test -Dtest=TeamSyncServiceTest -q
```
Expected: BUILD SUCCESS, 2 tests passing.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/worldcup/prediction/integration/football/TeamSyncService.java \
        src/test/java/com/worldcup/prediction/integration/football/TeamSyncServiceTest.java
git commit -m "feat: TeamSyncService — upsert teams + squads from API"
```

---

### Task 9: MatchSyncService, StandingSyncService, LineupSyncService, ScorersService

**Files:**
- Create: `src/main/java/com/worldcup/prediction/integration/football/MatchSyncService.java`
- Create: `src/main/java/com/worldcup/prediction/integration/football/StandingSyncService.java`
- Create: `src/main/java/com/worldcup/prediction/integration/football/LineupSyncService.java`
- Create: `src/main/java/com/worldcup/prediction/integration/football/ScorersService.java`

- [ ] **Step 1: Create MatchSyncService**

```java
package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.integration.football.dto.FootballApiMatchDto;
import com.worldcup.prediction.integration.football.dto.FootballApiResponseDto;
import com.worldcup.prediction.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class MatchSyncService {

    private final FootballApiClient client;
    private final FootballApiRateLimiter rateLimiter;
    private final MatchRepository matchRepository;

    public SyncResult syncMatchExternalIds() {
        FootballApiResponseDto response = rateLimiter.call(client::fetchAllMatches);
        if (response == null || response.matches() == null) {
            return SyncResult.skipped("No API response");
        }

        int linked = 0;
        for (FootballApiMatchDto apiMatch : response.matches()) {
            if (!"GROUP_STAGE".equals(apiMatch.stage())) continue;
            if (apiMatch.id() == null) continue;

            String extId = String.valueOf(apiMatch.id());
            if (matchRepository.findByExternalId(extId).isPresent()) continue;

            Optional<Match> matchOpt = resolveByTeamsAndDate(apiMatch);
            if (matchOpt.isEmpty()) {
                log.debug("Could not resolve match id={} tla={} vs {}", apiMatch.id(),
                        apiMatch.homeTeam() != null ? apiMatch.homeTeam().tla() : "?",
                        apiMatch.awayTeam() != null ? apiMatch.awayTeam().tla() : "?");
                continue;
            }

            matchOpt.get().setExternalId(extId);
            matchRepository.save(matchOpt.get());
            linked++;
        }

        return SyncResult.success(linked + " match external IDs linked");
    }

    private Optional<Match> resolveByTeamsAndDate(FootballApiMatchDto apiMatch) {
        if (apiMatch.homeTeam() == null || apiMatch.awayTeam() == null || apiMatch.utcDate() == null
                || apiMatch.utcDate().length() < 10) {
            return Optional.empty();
        }
        LocalDate date = LocalDate.parse(apiMatch.utcDate().substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
        return matchRepository.findByHomeTeamFifaCodeAndAwayTeamFifaCodeAndKickoffBetween(
                apiMatch.homeTeam().tla(), apiMatch.awayTeam().tla(),
                date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }
}
```

- [ ] **Step 2: Create StandingSyncService**

```java
package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.Group;
import com.worldcup.prediction.domain.GroupStanding;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.integration.football.dto.*;
import com.worldcup.prediction.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class StandingSyncService {

    private final FootballApiClient client;
    private final FootballApiRateLimiter rateLimiter;
    private final GroupStandingRepository standingRepository;
    private final GroupRepository groupRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;

    public SyncResult syncStandings() {
        LocalDateTime lastUpdate = standingRepository.findMostRecentUpdateTime()
                .orElse(LocalDateTime.MIN);
        long recentCompletions = matchRepository.countByStatusAndUpdatedAtAfter(
                MatchStatus.COMPLETED, lastUpdate);
        if (recentCompletions == 0 && standingRepository.count() > 0) {
            return SyncResult.skipped("No matches completed since last standings update");
        }

        FootballApiStandingsResponseDto response = rateLimiter.call(client::fetchStandings);
        if (response == null || response.standings() == null) {
            return SyncResult.skipped("No API response");
        }

        int upserted = 0;
        for (FootballApiStandingGroupDto groupDto : response.standings()) {
            if (!"TOTAL".equals(groupDto.type()) || groupDto.group() == null) continue;

            // API returns "GROUP_A" → we store "A"
            String groupName = groupDto.group().replace("GROUP_", "");
            Optional<Group> groupOpt = groupRepository.findByNameIgnoreCase(groupName);
            if (groupOpt.isEmpty()) {
                log.warn("Group not found for name={}", groupName);
                continue;
            }
            Group group = groupOpt.get();

            for (FootballApiStandingEntryDto entry : groupDto.table()) {
                if (entry.team() == null) continue;
                Optional<Team> teamOpt = teamRepository.findByExternalId(entry.team().id() != null
                        ? Long.parseLong(String.valueOf(entry.team().id())) : null);
                if (teamOpt.isEmpty()) {
                    teamOpt = teamRepository.findByFifaCodeIgnoreCase(entry.team().tla());
                }
                if (teamOpt.isEmpty()) {
                    log.warn("Team not found for tla={}", entry.team().tla());
                    continue;
                }

                GroupStanding standing = standingRepository
                        .findByGroupIdAndTeamId(group.getId(), teamOpt.get().getId())
                        .orElse(GroupStanding.builder().group(group).team(teamOpt.get()).build());

                standing.setPosition(entry.position() != null ? entry.position() : 0);
                standing.setPlayed(entry.playedGames() != null ? entry.playedGames() : 0);
                standing.setWon(entry.won() != null ? entry.won() : 0);
                standing.setDrawn(entry.draw() != null ? entry.draw() : 0);
                standing.setLost(entry.lost() != null ? entry.lost() : 0);
                standing.setPoints(entry.points() != null ? entry.points() : 0);
                standing.setGoalsFor(entry.goalsFor() != null ? entry.goalsFor() : 0);
                standing.setGoalsAgainst(entry.goalsAgainst() != null ? entry.goalsAgainst() : 0);
                standing.setGoalDifference(entry.goalDifference() != null ? entry.goalDifference() : 0);
                standingRepository.save(standing);
                upserted++;
            }
        }

        return SyncResult.success(upserted + " standing rows upserted");
    }
}
```

- [ ] **Step 3: Create LineupSyncService**

```java
package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.GoalType;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.integration.football.dto.*;
import com.worldcup.prediction.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class LineupSyncService {

    private final FootballApiClient client;
    private final FootballApiRateLimiter rateLimiter;
    private final MatchRepository matchRepository;
    private final MatchLineupRepository lineupRepository;
    private final MatchGoalRepository goalRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    public SyncResult syncLineups() {
        List<Match> pending = matchRepository.findByStatusAndLineupFetchedFalse(MatchStatus.COMPLETED);
        if (pending.isEmpty()) {
            return SyncResult.skipped("No completed matches without lineups");
        }

        int fetched = 0;
        for (Match match : pending) {
            if (match.getExternalId() == null) {
                log.warn("Match id={} has no externalId — skipping lineup fetch", match.getId());
                continue;
            }
            long extId = Long.parseLong(match.getExternalId());
            FootballApiMatchDetailDto detail = rateLimiter.call(() -> client.fetchMatchDetail(extId));
            if (detail == null) continue;

            persistLineups(match, detail);
            persistGoals(match, detail);
            match.setLineupFetched(true);
            matchRepository.save(match);
            fetched++;
        }

        return SyncResult.success(fetched + " match lineups fetched");
    }

    private void persistLineups(Match match, FootballApiMatchDetailDto detail) {
        if (detail.lineups() == null) return;
        for (FootballApiLineupDto lineupDto : detail.lineups()) {
            if (lineupDto.team() == null) continue;
            Optional<Team> teamOpt = resolveTeam(lineupDto.team());
            if (teamOpt.isEmpty()) continue;
            Team team = teamOpt.get();

            if (lineupDto.startXI() != null) {
                for (FootballApiLineupPlayerDto lp : lineupDto.startXI()) {
                    saveLineupEntry(match, team, lp, true);
                }
            }
            if (lineupDto.substitutes() != null) {
                for (FootballApiLineupPlayerDto lp : lineupDto.substitutes()) {
                    saveLineupEntry(match, team, lp, false);
                }
            }
        }
    }

    private void saveLineupEntry(Match match, Team team, FootballApiLineupPlayerDto lp, boolean starting) {
        if (lp.player() == null || lp.player().id() == null) return;
        playerRepository.findByExternalId(lp.player().id()).ifPresent(player -> {
            MatchLineup entry = MatchLineup.builder()
                    .match(match).team(team).player(player)
                    .starting(starting)
                    .shirtNumber(lp.shirtNumber())
                    .formationPosition(lp.position())
                    .build();
            lineupRepository.save(entry);
        });
    }

    private void persistGoals(Match match, FootballApiMatchDetailDto detail) {
        if (detail.goals() == null) return;
        for (FootballApiGoalDto goalDto : detail.goals()) {
            if (goalDto.team() == null || goalDto.minute() == null) continue;
            Optional<Team> teamOpt = resolveTeam(goalDto.team());
            if (teamOpt.isEmpty()) continue;

            Player scorer = null;
            if (goalDto.scorer() != null && goalDto.scorer().id() != null) {
                scorer = playerRepository.findByExternalId(goalDto.scorer().id()).orElse(null);
            }

            GoalType type = parseGoalType(goalDto.type());
            MatchGoal goal = MatchGoal.builder()
                    .match(match).team(teamOpt.get()).player(scorer)
                    .minute(goalDto.minute()).type(type).build();
            goalRepository.save(goal);
        }
    }

    private GoalType parseGoalType(String raw) {
        if ("OWN_GOAL".equals(raw)) return GoalType.OWN_GOAL;
        if ("PENALTY".equals(raw)) return GoalType.PENALTY;
        return GoalType.REGULAR;
    }

    private Optional<Team> resolveTeam(FootballApiTeamDto dto) {
        if (dto == null) return Optional.empty();
        if (dto.id() != null) {
            Optional<Team> t = teamRepository.findByExternalId(dto.id());
            if (t.isPresent()) return t;
        }
        if (dto.tla() != null) return teamRepository.findByFifaCodeIgnoreCase(dto.tla());
        return Optional.empty();
    }
}
```

- [ ] **Step 4: Create ScorersService**

```java
package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.integration.football.dto.FootballApiScorersResponseDto;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ScorersService {

    private final FootballApiClient client;
    private final FootballApiRateLimiter rateLimiter;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;

    public SyncResult syncScorers() {
        long recentCompletions = matchRepository.countByStatusAndUpdatedAtAfter(
                MatchStatus.COMPLETED, LocalDateTime.now().minusHours(24));
        if (recentCompletions == 0) {
            return SyncResult.skipped("No matches completed in last 24h");
        }

        FootballApiScorersResponseDto response = rateLimiter.call(client::fetchTopScorers);
        if (response == null || response.scorers() == null) {
            return SyncResult.skipped("No API response");
        }

        int updated = 0;
        for (var entry : response.scorers()) {
            if (entry.player() == null || entry.player().id() == null) continue;
            var playerOpt = playerRepository.findByExternalId(entry.player().id());
            if (playerOpt.isPresent()) {
                playerOpt.get().setTournamentGoals(entry.goals() != null ? entry.goals() : 0);
                playerRepository.save(playerOpt.get());
                updated++;
            }
        }

        return SyncResult.success(updated + " scorer records updated");
    }
}
```

- [ ] **Step 5: Build**
```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/worldcup/prediction/integration/football/MatchSyncService.java \
        src/main/java/com/worldcup/prediction/integration/football/StandingSyncService.java \
        src/main/java/com/worldcup/prediction/integration/football/LineupSyncService.java \
        src/main/java/com/worldcup/prediction/integration/football/ScorersService.java
git commit -m "feat: MatchSyncService, StandingSyncService, LineupSyncService, ScorersService"
```

---

### Task 10: Update MatchResultScheduler + add 3 new schedulers

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/scheduler/MatchResultScheduler.java`
- Modify: `src/main/java/com/worldcup/prediction/integration/football/FootballApiSyncService.java`
- Create: `src/main/java/com/worldcup/prediction/scheduler/LineupSyncScheduler.java`
- Create: `src/main/java/com/worldcup/prediction/scheduler/StandingSyncScheduler.java`
- Create: `src/main/java/com/worldcup/prediction/scheduler/ScorersSyncScheduler.java`

- [ ] **Step 1: Add skip check to FootballApiSyncService**

In `FootballApiSyncService.java`, add this method before `syncResults()`:
```java
public boolean hasActionableMatches() {
    return matchRepository.countByStatusAndKickoffTimeBefore(
            MatchStatus.SCHEDULED, LocalDateTime.now()) > 0;
}
```
Also add `import java.time.LocalDateTime;` and `import com.worldcup.prediction.domain.enums.MatchStatus;` if not present.

- [ ] **Step 2: Update MatchResultScheduler with skip logic**

Replace `MatchResultScheduler.java`:
```java
package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.integration.football.FootballApiSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class MatchResultScheduler {

    private final FootballApiSyncService syncService;

    @Scheduled(fixedDelay = 300_000)
    public void syncAndScore() {
        try {
            if (!syncService.hasActionableMatches()) {
                log.debug("MatchResultScheduler: no actionable matches — skipping");
                return;
            }
            List<Long> finished = syncService.syncResults();
            if (!finished.isEmpty()) {
                log.info("Scheduler: {} match(es) newly finished and scored: {}", finished.size(), finished);
            }
        } catch (Exception e) {
            log.error("Scheduler: unexpected error — will retry next cycle", e);
        }
    }
}
```

- [ ] **Step 3: Create LineupSyncScheduler**

```java
package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.integration.football.LineupSyncService;
import com.worldcup.prediction.integration.football.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LineupSyncScheduler {

    private final LineupSyncService syncService;

    @Scheduled(fixedDelay = 1_800_000)
    public void syncLineups() {
        try {
            SyncResult result = syncService.syncLineups();
            if (!result.skipped()) log.info("LineupSync: {}", result.message());
            else log.debug("LineupSync: {}", result.message());
        } catch (Exception e) {
            log.error("LineupSyncScheduler: unexpected error — will retry next cycle", e);
        }
    }
}
```

- [ ] **Step 4: Create StandingSyncScheduler**

```java
package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.integration.football.StandingSyncService;
import com.worldcup.prediction.integration.football.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class StandingSyncScheduler {

    private final StandingSyncService syncService;

    @Scheduled(cron = "0 0 */6 * * *")
    public void syncStandings() {
        try {
            SyncResult result = syncService.syncStandings();
            if (!result.skipped()) log.info("StandingSync: {}", result.message());
            else log.debug("StandingSync: {}", result.message());
        } catch (Exception e) {
            log.error("StandingSyncScheduler: unexpected error — will retry next cycle", e);
        }
    }
}
```

- [ ] **Step 5: Create ScorersSyncScheduler**

```java
package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.integration.football.ScorersService;
import com.worldcup.prediction.integration.football.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ScorersSyncScheduler {

    private final ScorersService scorersService;

    @Scheduled(cron = "0 0 2 * * *")
    public void syncScorers() {
        try {
            SyncResult result = scorersService.syncScorers();
            if (!result.skipped()) log.info("ScorersSync: {}", result.message());
            else log.debug("ScorersSync: {}", result.message());
        } catch (Exception e) {
            log.error("ScorersSyncScheduler: unexpected error — will retry next cycle", e);
        }
    }
}
```

- [ ] **Step 6: Update MatchResultSchedulerTest for the new skip logic**

Add a new test to `MatchResultSchedulerTest.java`:
```java
@Test
void syncAndScore_whenNoActionableMatches_skips() {
    when(syncService.hasActionableMatches()).thenReturn(false);
    scheduler.syncAndScore();
    verify(syncService, never()).syncResults();
}
```
Also update the existing tests that call `syncResults` to first stub `hasActionableMatches()`:
```java
// In syncAndScore_callsSyncService:
when(syncService.hasActionableMatches()).thenReturn(true);
when(syncService.syncResults()).thenReturn(List.of(1L, 2L));

// In syncAndScore_whenSyncReturnsEmpty_doesNotThrow:
when(syncService.hasActionableMatches()).thenReturn(true);
when(syncService.syncResults()).thenReturn(List.of());

// In syncAndScore_whenSyncThrows_doesNotPropagate:
when(syncService.hasActionableMatches()).thenReturn(true);
when(syncService.syncResults()).thenThrow(new RuntimeException("network failure"));
```

- [ ] **Step 7: Run scheduler tests**
```bash
./mvnw test -Dtest=MatchResultSchedulerTest -q
```
Expected: BUILD SUCCESS, 4 tests passing.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/worldcup/prediction/scheduler/ \
        src/main/java/com/worldcup/prediction/integration/football/FootballApiSyncService.java \
        src/test/java/com/worldcup/prediction/scheduler/MatchResultSchedulerTest.java
git commit -m "feat: add skip logic to MatchResultScheduler, add Lineup/Standing/Scorers schedulers"
```

---

### Task 11: Bootstrap runner

**Files:**
- Create: `src/main/java/com/worldcup/prediction/bootstrap/FootballApiBootstrapRunner.java`
- Create: `src/main/resources/application-bootstrap.properties`

- [ ] **Step 1: Create bootstrap properties**

`src/main/resources/application-bootstrap.properties`:
```properties
# Bootstrap profile — run once to seed all static API data
# Usage: java -jar app.jar --spring.profiles.active=sqlite,bootstrap
app.football.api.enabled=true
```

- [ ] **Step 2: Create FootballApiBootstrapRunner**

```java
package com.worldcup.prediction.bootstrap;

import com.worldcup.prediction.integration.football.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("bootstrap")
@Slf4j
@RequiredArgsConstructor
public class FootballApiBootstrapRunner implements CommandLineRunner {

    private final TeamSyncService teamSyncService;
    private final MatchSyncService matchSyncService;
    private final StandingSyncService standingSyncService;

    @Override
    public void run(String... args) {
        log.info("=== Football API Bootstrap starting ===");

        SyncResult teams = teamSyncService.syncTeamsAndSquads();
        log.info("Teams + Squads: {}", teams.message());

        SyncResult matches = matchSyncService.syncMatchExternalIds();
        log.info("Matches:        {}", matches.message());

        SyncResult standings = standingSyncService.syncStandings();
        log.info("Standings:      {}", standings.message());

        log.info("=== Bootstrap complete. Export DB and commit as R__wc2026_data.sql ===");
    }
}
```

- [ ] **Step 3: Build**
```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**
```bash
git add src/main/java/com/worldcup/prediction/bootstrap/FootballApiBootstrapRunner.java \
        src/main/resources/application-bootstrap.properties
git commit -m "feat: FootballApiBootstrapRunner — one-shot data seed via bootstrap profile"
```

---

### Task 12: AdminSyncController + admin sync page

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/admin/AdminSyncController.java`
- Create: `src/main/resources/templates/admin/sync.html`
- Modify: `src/main/resources/templates/admin/layout.html`

- [ ] **Step 1: Create AdminSyncController**

```java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.integration.football.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/sync")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSyncController {

    private final TeamSyncService teamSyncService;
    private final MatchSyncService matchSyncService;
    private final StandingSyncService standingSyncService;
    private final LineupSyncService lineupSyncService;
    private final ScorersService scorersService;

    @GetMapping
    public String syncPage() {
        return "admin/sync";
    }

    @PostMapping("/teams")
    public String syncTeams(RedirectAttributes ra) {
        SyncResult r = teamSyncService.syncTeamsAndSquads();
        ra.addFlashAttribute("successMessage", "Teams/Squads: " + r.message());
        return "redirect:/admin/sync";
    }

    @PostMapping("/matches")
    public String syncMatches(RedirectAttributes ra) {
        SyncResult r = matchSyncService.syncMatchExternalIds();
        ra.addFlashAttribute("successMessage", "Matches: " + r.message());
        return "redirect:/admin/sync";
    }

    @PostMapping("/standings")
    public String syncStandings(RedirectAttributes ra) {
        SyncResult r = standingSyncService.syncStandings();
        ra.addFlashAttribute("successMessage", "Standings: " + r.message());
        return "redirect:/admin/sync";
    }

    @PostMapping("/lineups")
    public String syncLineups(RedirectAttributes ra) {
        SyncResult r = lineupSyncService.syncLineups();
        ra.addFlashAttribute("successMessage", "Lineups: " + r.message());
        return "redirect:/admin/sync";
    }

    @PostMapping("/scorers")
    public String syncScorers(RedirectAttributes ra) {
        SyncResult r = scorersService.syncScorers();
        ra.addFlashAttribute("successMessage", "Scorers: " + r.message());
        return "redirect:/admin/sync";
    }
}
```

- [ ] **Step 2: Create admin/sync.html**

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{admin/layout}">
<head><title>Data Sync</title></head>
<body>
<th:block layout:fragment="page-title">Data Sync</th:block>
<th:block layout:fragment="content">

  <div class="max-w-2xl space-y-4">
    <p class="text-sm text-gray-500">Manually trigger API sync jobs. Each button respects the rate limiter (10 calls/min). Skipped jobs return instantly.</p>

    <div th:each="job : ${ {'teams','matches','standings','lineups','scorers'} }"
         class="bg-white rounded-xl border border-gray-100 shadow-sm p-5 flex items-center justify-between">
      <div>
        <p class="font-semibold text-gray-800 capitalize" th:text="${job}">teams</p>
        <p class="text-xs text-gray-400 mt-0.5"
           th:text="${job == 'teams'} ? 'Upsert 48 teams + full squads' :
                    (${job == 'matches'} ? 'Link match external IDs from API' :
                    (${job == 'standings'} ? 'Upsert group standings from API' :
                    (${job == 'lineups'} ? 'Fetch lineups + goals for completed matches' :
                    'Update top scorer goal counts')))">description</p>
      </div>
      <form th:action="@{'/admin/sync/' + ${job}}" method="post">
        <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
        <button type="submit"
                class="px-4 py-2 text-sm font-semibold bg-admin-dark text-white rounded-lg hover:bg-admin-mid transition-colors duration-150">
          Run
        </button>
      </form>
    </div>
  </div>

</th:block>
</body>
</html>
```

- [ ] **Step 3: Add Sync link to admin sidebar**

In `admin/layout.html`, after the Predictions nav link (around line 81), add:
```html
<a th:href="@{/admin/sync}"
   th:classappend="${currentUri.startsWith('/admin/sync')} ? ' bg-admin-mid text-white' : ' text-green-200 hover:bg-admin-mid hover:text-white'"
   class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors duration-150">
  <svg class="w-5 h-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
    <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
  </svg>
  Data Sync
</a>
```

- [ ] **Step 4: Build**
```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/worldcup/prediction/controller/admin/AdminSyncController.java \
        src/main/resources/templates/admin/sync.html \
        src/main/resources/templates/admin/layout.html
git commit -m "feat: admin Data Sync page with manual triggers for all 5 sync jobs"
```

---

### Task 13: Match preview page

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/MatchPreviewController.java`
- Create: `src/main/resources/templates/match-preview.html`

- [ ] **Step 1: Create MatchPreviewController**

```java
package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/matches")
@RequiredArgsConstructor
public class MatchPreviewController {

    private final MatchRepository matchRepository;
    private final MatchLineupRepository lineupRepository;
    private final MatchGoalRepository goalRepository;

    @GetMapping("/{id}")
    public String matchPreview(@PathVariable Long id, Model model) {
        Match match = matchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("match", match);
        model.addAttribute("goals", goalRepository.findByMatchIdOrderByMinuteAsc(id));
        model.addAttribute("hasLineup", lineupRepository.existsByMatchId(id));

        if (match.getHomeTeam() != null && match.getAwayTeam() != null) {
            model.addAttribute("homeLineup",
                    lineupRepository.findByMatchIdAndTeamIdOrderByStartingDescShirtNumberAsc(
                            id, match.getHomeTeam().getId()));
            model.addAttribute("awayLineup",
                    lineupRepository.findByMatchIdAndTeamIdOrderByStartingDescShirtNumberAsc(
                            id, match.getAwayTeam().getId()));
        }

        return "match-preview";
    }
}
```

- [ ] **Step 2: Create match-preview.html**

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/base :: layout(~{::main-content})}">
<head><title>Match Preview</title></head>
<body>
<th:block th:fragment="main-content">

<div class="max-w-4xl mx-auto px-4 py-10 space-y-8">

  <!-- Match header -->
  <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 text-center space-y-2">
    <p class="text-xs font-semibold text-gray-400 uppercase tracking-widest"
       th:text="${match.roundLabel}">Group Stage</p>

    <div class="flex items-center justify-center gap-6 mt-3">
      <!-- Home team -->
      <div class="flex flex-col items-center gap-2 w-36">
        <img th:if="${match.homeTeam != null}"
             th:src="@{'https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/' + ${match.homeTeam.flagCode} + '.svg'}"
             class="w-16 h-16 rounded-full shadow" th:alt="${match.homeTeam.name}"/>
        <span class="font-bold text-gray-800 text-sm text-center"
              th:text="${match.homeTeam != null ? match.homeTeam.name : match.homeTeamPlaceholder}">Home</span>
      </div>

      <!-- Score / time -->
      <div class="text-center">
        <div th:if="${match.homeScore != null}" class="font-display text-5xl text-gray-900 tracking-wider"
             th:text="${match.homeScore + ' – ' + match.awayScore}">0 – 0</div>
        <div th:unless="${match.homeScore != null}" class="font-display text-3xl text-gray-400"
             th:text="${#temporals.format(match.kickoffTime, 'HH:mm')}">19:00</div>
        <div class="text-xs text-gray-400 mt-1"
             th:text="${#temporals.format(match.kickoffTime, 'EEE d MMM yyyy')}">Mon 11 Jun 2026</div>
        <div th:if="${match.venue != null}" class="text-xs text-gray-400 mt-0.5"
             th:text="${match.venue + ', ' + match.city}">Stadium, City</div>
      </div>

      <!-- Away team -->
      <div class="flex flex-col items-center gap-2 w-36">
        <img th:if="${match.awayTeam != null}"
             th:src="@{'https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/' + ${match.awayTeam.flagCode} + '.svg'}"
             class="w-16 h-16 rounded-full shadow" th:alt="${match.awayTeam.name}"/>
        <span class="font-bold text-gray-800 text-sm text-center"
              th:text="${match.awayTeam != null ? match.awayTeam.name : match.awayTeamPlaceholder}">Away</span>
      </div>
    </div>
  </div>

  <!-- Goals timeline -->
  <div th:if="${not #lists.isEmpty(goals)}"
       class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
    <h2 class="font-bold text-gray-700 text-sm uppercase tracking-wide mb-4">Goals</h2>
    <div class="space-y-2">
      <div th:each="goal : ${goals}"
           class="flex items-center gap-3 text-sm">
        <span class="w-10 text-right font-mono text-gray-500"
              th:text="${goal.minute + '''"}">23'</span>
        <span th:if="${goal.player != null}" class="font-medium text-gray-800"
              th:text="${goal.player.name}">Scorer</span>
        <span th:unless="${goal.player != null}" class="text-gray-400 italic">Own goal</span>
        <span th:if="${goal.type.name() != 'REGULAR'}"
              class="px-1.5 py-0.5 text-xs rounded bg-gray-100 text-gray-500"
              th:text="${goal.type.name() == 'OWN_GOAL' ? 'OG' : 'PEN'}">OG</span>
        <span class="text-gray-400 text-xs" th:text="${goal.team.name}">Team</span>
      </div>
    </div>
  </div>

  <!-- Lineups -->
  <div th:if="${hasLineup}"
       class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
    <h2 class="font-bold text-gray-700 text-sm uppercase tracking-wide mb-4">Lineups</h2>
    <div class="grid grid-cols-2 gap-8">
      <!-- Home lineup -->
      <div th:if="${homeLineup != null}">
        <p class="font-semibold text-gray-700 mb-3"
           th:text="${match.homeTeam?.name}">Home</p>
        <div class="space-y-1">
          <div th:each="entry : ${homeLineup}"
               class="flex items-center gap-2 text-sm"
               th:classappend="${entry.starting} ? '' : 'text-gray-400'">
            <span class="w-6 text-right font-mono text-gray-400 text-xs"
                  th:text="${entry.shirtNumber}">1</span>
            <span th:text="${entry.player.name}">Player</span>
            <span class="text-xs text-gray-400" th:text="${entry.formationPosition}">GK</span>
            <span th:unless="${entry.starting}" class="text-xs text-gray-300">(sub)</span>
          </div>
        </div>
      </div>
      <!-- Away lineup -->
      <div th:if="${awayLineup != null}">
        <p class="font-semibold text-gray-700 mb-3"
           th:text="${match.awayTeam?.name}">Away</p>
        <div class="space-y-1">
          <div th:each="entry : ${awayLineup}"
               class="flex items-center gap-2 text-sm"
               th:classappend="${entry.starting} ? '' : 'text-gray-400'">
            <span class="w-6 text-right font-mono text-gray-400 text-xs"
                  th:text="${entry.shirtNumber}">1</span>
            <span th:text="${entry.player.name}">Player</span>
            <span class="text-xs text-gray-400" th:text="${entry.formationPosition}">GK</span>
            <span th:unless="${entry.starting}" class="text-xs text-gray-300">(sub)</span>
          </div>
        </div>
      </div>
    </div>
  </div>

  <div th:unless="${hasLineup}" class="text-center text-sm text-gray-400 py-4">
    Lineup not yet available.
  </div>

</div>

</th:block>
</body>
</html>
```

- [ ] **Step 3: Build**
```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**
```bash
git add src/main/java/com/worldcup/prediction/controller/MatchPreviewController.java \
        src/main/resources/templates/match-preview.html
git commit -m "feat: match preview page — score, lineup, goals timeline"
```

---

### Task 14: Team page

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/TeamController.java`
- Create: `src/main/resources/templates/team.html`
- Modify: `src/main/java/com/worldcup/prediction/repository/MatchRepository.java`

- [ ] **Step 1: Add team match query to MatchRepository**

In `MatchRepository.java` add:
```java
@Query("""
        SELECT m FROM Match m
        LEFT JOIN FETCH m.homeTeam
        LEFT JOIN FETCH m.awayTeam
        WHERE m.homeTeam.id = :teamId OR m.awayTeam.id = :teamId
        ORDER BY m.kickoffTime ASC
        """)
List<Match> findByTeamIdOrderByKickoffTimeAsc(@Param("teamId") Long teamId);
```

- [ ] **Step 2: Create TeamController**

```java
package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;

    @GetMapping("/{id}")
    public String teamPage(@PathVariable Long id, Model model) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("team", team);
        model.addAttribute("players", playerRepository.findByTeamIdOrderByShirtNumberAsc(id));
        model.addAttribute("matches", matchRepository.findByTeamIdOrderByKickoffTimeAsc(id));
        return "team";
    }
}
```

- [ ] **Step 3: Create team.html**

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/base :: layout(~{::main-content})}">
<head><title>Team</title></head>
<body>
<th:block th:fragment="main-content">

<div class="max-w-4xl mx-auto px-4 py-10 space-y-8">

  <!-- Team header -->
  <div class="flex items-center gap-6">
    <img th:src="@{'https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/' + ${team.flagCode} + '.svg'}"
         class="w-20 h-20 rounded-full shadow-lg" th:alt="${team.name}"/>
    <div>
      <h1 class="font-display text-4xl text-gray-900" th:text="${team.name}">Team Name</h1>
      <p class="text-sm text-gray-500 mt-1" th:text="${team.confederation}">UEFA</p>
    </div>
  </div>

  <!-- Matches -->
  <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
    <h2 class="font-bold text-gray-700 text-sm uppercase tracking-wide mb-4">Matches</h2>
    <div th:if="${#lists.isEmpty(matches)}" class="text-sm text-gray-400">No matches found.</div>
    <div class="space-y-2">
      <a th:each="m : ${matches}"
         th:href="@{'/matches/' + ${m.id}}"
         class="flex items-center gap-4 p-3 rounded-xl hover:bg-gray-50 transition-colors text-sm">
        <span class="text-xs text-gray-400 w-24 flex-shrink-0"
              th:text="${#temporals.format(m.kickoffTime, 'd MMM HH:mm')}">11 Jun 19:00</span>
        <span class="font-medium text-gray-800"
              th:text="${(m.homeTeam != null ? m.homeTeam.name : m.homeTeamPlaceholder) + ' vs ' + (m.awayTeam != null ? m.awayTeam.name : m.awayTeamPlaceholder)}">Home vs Away</span>
        <span th:if="${m.homeScore != null}"
              class="ml-auto font-mono font-bold text-gray-900"
              th:text="${m.homeScore + ' – ' + m.awayScore}">2 – 1</span>
        <span th:unless="${m.homeScore != null}"
              class="ml-auto text-xs text-gray-400">Upcoming</span>
      </a>
    </div>
  </div>

  <!-- Squad -->
  <div th:if="${not #lists.isEmpty(players)}"
       class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
    <h2 class="font-bold text-gray-700 text-sm uppercase tracking-wide mb-4">Squad</h2>
    <div th:each="pos : ${ {'Goalkeeper','Defender','Midfielder','Forward'} }" class="mb-4">
      <p class="text-xs font-semibold text-gray-400 uppercase mb-2" th:text="${pos}">Position</p>
      <div class="space-y-1">
        <div th:each="p : ${players}"
             th:if="${p.position != null and p.position.contains(pos.substring(0,3))}"
             class="flex items-center gap-3 text-sm py-1">
          <span class="w-6 text-right font-mono text-gray-400 text-xs"
                th:text="${p.shirtNumber != null ? p.shirtNumber : '–'}">1</span>
          <span class="font-medium text-gray-800" th:text="${p.name}">Player Name</span>
          <span th:if="${p.tournamentGoals > 0}"
                class="ml-auto text-xs font-semibold text-green-700"
                th:text="${p.tournamentGoals + ' ⚽'}">3 ⚽</span>
        </div>
      </div>
    </div>
  </div>
  <div th:if="${#lists.isEmpty(players)}" class="text-sm text-gray-400 text-center py-4">
    Squad data not yet available. Run bootstrap to populate.
  </div>

</div>

</th:block>
</body>
</html>
```

- [ ] **Step 4: Build**
```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/worldcup/prediction/controller/TeamController.java \
        src/main/resources/templates/team.html \
        src/main/java/com/worldcup/prediction/repository/MatchRepository.java
git commit -m "feat: team page — squad, confederation, match list with links"
```

---

### Task 15: Top Scorers page

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/ScorersController.java`
- Create: `src/main/resources/templates/scorers.html`

- [ ] **Step 1: Create ScorersController**

```java
package com.worldcup.prediction.controller;

import com.worldcup.prediction.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/scorers")
@RequiredArgsConstructor
public class ScorersController {

    private final PlayerRepository playerRepository;

    @GetMapping
    public String scorers(Model model) {
        model.addAttribute("scorers",
                playerRepository.findByTournamentGoalsGreaterThanOrderByTournamentGoalsDesc(0));
        return "scorers";
    }
}
```

- [ ] **Step 2: Create scorers.html**

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/base :: layout(~{::main-content})}">
<head><title>Top Scorers</title></head>
<body>
<th:block th:fragment="main-content">

<div class="max-w-2xl mx-auto px-4 py-10">

  <div class="mb-8">
    <h1 class="font-display text-5xl text-green-dark tracking-wider mb-1">TOP SCORERS</h1>
    <p class="text-gray-500 text-sm">Updated daily during the tournament</p>
  </div>

  <div class="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
    <div th:if="${#lists.isEmpty(scorers)}"
         class="p-8 text-center text-sm text-gray-400">
      No goals scored yet.
    </div>
    <table th:unless="${#lists.isEmpty(scorers)}" class="w-full text-sm">
      <thead>
        <tr class="border-b border-gray-100 text-xs text-gray-400 uppercase tracking-wide">
          <th class="px-4 py-3 text-left w-10">#</th>
          <th class="px-4 py-3 text-left">Player</th>
          <th class="px-4 py-3 text-left">Team</th>
          <th class="px-4 py-3 text-right">Goals</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-gray-50">
        <tr th:each="p, pStat : ${scorers}" class="hover:bg-gray-50 transition-colors">
          <td class="px-4 py-3 font-mono text-gray-400 text-xs" th:text="${pStat.count}">1</td>
          <td class="px-4 py-3 font-medium text-gray-800" th:text="${p.name}">Player Name</td>
          <td class="px-4 py-3 text-gray-500">
            <div class="flex items-center gap-2">
              <img th:if="${p.team != null}"
                   th:src="@{'https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/' + ${p.team.flagCode} + '.svg'}"
                   class="w-5 h-5 rounded-full flex-shrink-0" th:alt="${p.team.name}"/>
              <span th:text="${p.team?.name}">Country</span>
            </div>
          </td>
          <td class="px-4 py-3 text-right font-bold text-green-700"
              th:text="${p.tournamentGoals}">5</td>
        </tr>
      </tbody>
    </table>
  </div>

</div>

</th:block>
</body>
</html>
```

- [ ] **Step 3: Build**
```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**
```bash
git add src/main/java/com/worldcup/prediction/controller/ScorersController.java \
        src/main/resources/templates/scorers.html
git commit -m "feat: top scorers page"
```

---

### Task 16: Enhance Groups page with match list per group

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/service/GroupService.java`
- Modify: `src/main/java/com/worldcup/prediction/service/GroupServiceImpl.java`
- Modify: `src/main/java/com/worldcup/prediction/controller/GroupController.java`
- Modify: `src/main/resources/templates/groups.html`

- [ ] **Step 1: Add getMatchesByGroup to GroupService interface**

In `GroupService.java` add:
```java
import com.worldcup.prediction.domain.Match;
import java.util.Map;
import java.util.List;

Map<String, List<Match>> getMatchesByGroup();
```

- [ ] **Step 2: Implement getMatchesByGroup in GroupServiceImpl**

In `GroupServiceImpl.java` add:
```java
@Override
public Map<String, List<Match>> getMatchesByGroup() {
    List<com.worldcup.prediction.domain.Group> groups = groupRepository.findAllWithTeams();
    List<Match> groupMatches = matchRepository.findByStageWithTeams(MatchStage.GROUP);
    Map<String, List<Match>> result = new LinkedHashMap<>();
    for (com.worldcup.prediction.domain.Group g : groups) {
        result.put("Group " + g.getName(), groupMatches.stream()
                .filter(m -> m.getGroup() != null && m.getGroup().getId().equals(g.getId()))
                .toList());
    }
    return result;
}
```

- [ ] **Step 3: Add matches to GroupController model**

In `GroupController.java`, update the `groups()` method:
```java
@GetMapping("/groups")
public String groups(Model model) {
    Map<String, List<GroupStandingDto>> groups = groupService.getAllGroupStandings();
    List<String> qualifiedThirdGroups = groupService.getQualifiedThirdPlaceGroups();
    Map<String, List<Match>> matchesByGroup = groupService.getMatchesByGroup();

    model.addAttribute("groups", groups);
    model.addAttribute("qualifiedThirdGroups", qualifiedThirdGroups);
    model.addAttribute("matchesByGroup", matchesByGroup);
    model.addAttribute("pageTitle", "Groups");
    return "groups";
}
```

Add import: `import com.worldcup.prediction.domain.Match;`

- [ ] **Step 4: Add match list to groups.html**

At the bottom of each group card `<div th:each="groupEntry...">`, just before the closing `</div>` of the card, add:
```html
<!-- Matches for this group -->
<div th:if="${matchesByGroup != null and matchesByGroup[groupEntry.key] != null}"
     class="border-t border-gray-50 divide-y divide-gray-50">
  <a th:each="m : ${matchesByGroup[groupEntry.key]}"
     th:href="@{'/matches/' + ${m.id}}"
     class="flex items-center gap-3 px-4 py-2.5 hover:bg-gray-50 transition-colors text-xs">
    <span class="text-gray-400 w-16 flex-shrink-0"
          th:text="${#temporals.format(m.kickoffTime, 'd MMM HH:mm')}">11 Jun</span>
    <span class="flex-1 font-medium text-gray-700"
          th:text="${(m.homeTeam != null ? m.homeTeam.name : m.homeTeamPlaceholder) + ' – ' + (m.awayTeam != null ? m.awayTeam.name : m.awayTeamPlaceholder)}">Home – Away</span>
    <span th:if="${m.homeScore != null}"
          class="font-mono font-bold text-gray-800"
          th:text="${m.homeScore + ':' + m.awayScore}">2:1</span>
    <span th:unless="${m.homeScore != null}"
          class="text-gray-300">—</span>
  </a>
</div>
```

- [ ] **Step 5: Build and run all tests**
```bash
./mvnw test -q
```
Expected: BUILD SUCCESS, all tests passing.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/worldcup/prediction/service/GroupService.java \
        src/main/java/com/worldcup/prediction/service/GroupServiceImpl.java \
        src/main/java/com/worldcup/prediction/controller/GroupController.java \
        src/main/resources/templates/groups.html
git commit -m "feat: groups page — add clickable match list per group card"
```

---

### Task 17: Full test suite run + security config check

- [ ] **Step 1: Run full test suite**
```bash
./mvnw test
```
Expected: BUILD SUCCESS, all tests passing. Note any failures.

- [ ] **Step 2: Verify new routes are accessible to authenticated users**

Check `SecurityConfig` (or equivalent) allows `/matches/**`, `/teams/**`, `/scorers` for authenticated users. If the existing security config uses `anyRequest().authenticated()` these routes are covered automatically. Verify by searching:
```bash
grep -r "matches\|teams\|scorers" src/main/java/com/worldcup/prediction/config/
```
If no explicit permit/deny rules exist for these paths, they fall under the default authenticated rule — no change needed.

- [ ] **Step 3: Verify `/admin/sync` is protected**

Confirm `AdminSyncController` has `@PreAuthorize("hasRole('ADMIN')")` — already added in Task 12.

- [ ] **Step 4: Final commit if any last fixes**
```bash
git add -p  # stage only intentional changes
git commit -m "fix: post-integration cleanup"
```

---

## Post-Implementation: Running the Bootstrap

Once the app builds and all tests pass, to populate real data:

```bash
# 1. Set your API key in environment
export FOOTBALL_API_KEY=your_key_here

# 2. Run bootstrap against local SQLite
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=sqlite,bootstrap"

# 3. After completion, export the SQLite DB contents
# The populated DB file is at ./world-cup-prediction.db (or configured path)
# Export teams, players, matches, group_standings as INSERT statements
# and replace the relevant sections of R__wc2026_data.sql
```
