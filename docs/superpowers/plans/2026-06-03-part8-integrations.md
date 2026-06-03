# Part 8: Integrations & Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire up the football-data.org API for automated result sync, implement real email notifications via JavaMailSender, build the knockout bracket page, and package everything for Docker deployment.

**Architecture:** A `FootballApiClient` polls `api.football-data.org` every 5 minutes via a `@Scheduled` job; newly-finished matches trigger `ScoringService.scoreMatch()`. `EmailService` wraps `JavaMailSender` and degrades gracefully (log-only) when no SMTP host is configured. The knockout bracket page is a pure server-rendered Thymeleaf template using CSS grid/flexbox. All components are packaged in a multi-stage Docker build with two deployment modes: SQLite (default, zero external dependencies, `docker-compose.yml`) and PostgreSQL (production, `docker-compose.postgres.yml`).

**Tech Stack:** Spring Boot 3.3.x, `@Scheduled`, `JavaMailSender`, `RestTemplate` with `MockRestServiceServer` (tests), Mockito, Thymeleaf, Docker, Docker Compose

**Depends on:** Part 1–7 (entities, repositories, ScoringService.scoreMatch(matchId), Match/User entities, NotificationService interface stubs from Part 7)

**Next parts:** none — this is the final part

---

## File Structure

```
src/
  main/
    java/com/worldcup/prediction/
      config/
        SchedulerConfig.java                         # @EnableScheduling
        MailConfig.java                              # conditional JavaMailSender bean
      integration/
        football/
          FootballApiClient.java                     # RestTemplate wrapper
          dto/
            FootballApiMatchDto.java                 # API response DTO
            FootballApiTeamDto.java                  # API response team DTO
            FootballApiScoreDto.java                 # API score DTO
            FootballApiFullTimeDto.java              # API fullTime DTO
            FootballApiResponseDto.java              # top-level matches[] wrapper
          FootballApiSyncService.java                # maps API → Match entities
      scheduler/
        MatchResultScheduler.java                    # @Scheduled every 5 min
      notification/
        EmailService.java                            # real JavaMailSender impl
    resources/
      templates/
        bracket.html                                 # knockout bracket page
      application-docker.properties                  # docker datasource config

  test/
    java/com/worldcup/prediction/
      integration/football/
        FootballApiClientTest.java                   # MockRestServiceServer tests
      scheduler/
        MatchResultSchedulerTest.java                # Mockito scheduler tests
      notification/
        EmailServiceTest.java                        # Mockito mail sender tests

Dockerfile                                           # multi-stage build (SQLite default, Postgres via env override)
docker-compose.yml                                   # SQLite mode — simple, no external DB
docker-compose.postgres.yml                          # PostgreSQL mode — production with external DB
.env.example                                         # env var template (covers both modes)
```

---

### Task 1: Football API DTOs

**Files:**
- Create: `src/main/java/com/worldcup/prediction/integration/football/dto/FootballApiTeamDto.java`
- Create: `src/main/java/com/worldcup/prediction/integration/football/dto/FootballApiFullTimeDto.java`
- Create: `src/main/java/com/worldcup/prediction/integration/football/dto/FootballApiScoreDto.java`
- Create: `src/main/java/com/worldcup/prediction/integration/football/dto/FootballApiMatchDto.java`
- Create: `src/main/java/com/worldcup/prediction/integration/football/dto/FootballApiResponseDto.java`

- [ ] **Step 1: Create FootballApiTeamDto**

```java
// src/main/java/com/worldcup/prediction/integration/football/dto/FootballApiTeamDto.java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiTeamDto(
    Long id,
    String name,
    String shortName,
    String tla,
    String crest
) {}
```

- [ ] **Step 2: Create FootballApiFullTimeDto**

```java
// src/main/java/com/worldcup/prediction/integration/football/dto/FootballApiFullTimeDto.java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiFullTimeDto(
    Integer home,
    Integer away
) {}
```

- [ ] **Step 3: Create FootballApiScoreDto**

```java
// src/main/java/com/worldcup/prediction/integration/football/dto/FootballApiScoreDto.java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiScoreDto(
    String winner,
    String duration,
    FootballApiFullTimeDto fullTime,
    FootballApiFullTimeDto halfTime
) {}
```

- [ ] **Step 4: Create FootballApiMatchDto**

```java
// src/main/java/com/worldcup/prediction/integration/football/dto/FootballApiMatchDto.java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiMatchDto(
    Long id,
    String utcDate,       // ISO-8601 string, e.g. "2026-06-11T19:00:00Z"
    String status,        // "SCHEDULED", "IN_PLAY", "PAUSED", "FINISHED", "SUSPENDED", "POSTPONED", "CANCELLED", "AWARDED"
    Integer matchday,
    String stage,         // "GROUP_STAGE", "ROUND_OF_32", "ROUND_OF_16", "QUARTER_FINALS", "SEMI_FINALS", "THIRD_PLACE", "FINAL"
    String group,
    FootballApiTeamDto homeTeam,
    FootballApiTeamDto awayTeam,
    FootballApiScoreDto score
) {}
```

- [ ] **Step 5: Create FootballApiResponseDto**

```java
// src/main/java/com/worldcup/prediction/integration/football/dto/FootballApiResponseDto.java
package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiResponseDto(
    Integer count,
    List<FootballApiMatchDto> matches
) {}
```

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(part8): add football-data.org API response DTOs"
```

---

### Task 2: FootballApiClient

**Files:**
- Create: `src/main/java/com/worldcup/prediction/integration/football/FootballApiClient.java`
- Create: `src/test/java/com/worldcup/prediction/integration/football/FootballApiClientTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/worldcup/prediction/integration/football/FootballApiClientTest.java
package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.integration.football.dto.FootballApiResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FootballApiClientTest {

    private MockRestServiceServer mockServer;
    private FootballApiClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        client = new FootballApiClient(restTemplate, "test-api-key");
    }

    @Test
    void fetchMatches_returnsParsedMatches() {
        String json = """
            {
              "count": 1,
              "matches": [
                {
                  "id": 123,
                  "utcDate": "2026-06-11T19:00:00Z",
                  "status": "FINISHED",
                  "matchday": 1,
                  "stage": "GROUP_STAGE",
                  "group": "GROUP_A",
                  "homeTeam": { "id": 1, "name": "Mexico", "shortName": "Mexico", "tla": "MEX", "crest": "" },
                  "awayTeam": { "id": 2, "name": "Canada", "shortName": "Canada", "tla": "CAN", "crest": "" },
                  "score": {
                    "winner": "HOME_TEAM",
                    "duration": "REGULAR",
                    "fullTime": { "home": 2, "away": 1 },
                    "halfTime": { "home": 1, "away": 0 }
                  }
                }
              ]
            }
            """;

        mockServer.expect(requestTo("https://api.football-data.org/v4/competitions/WC/matches"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Auth-Token", "test-api-key"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        FootballApiResponseDto response = client.fetchMatches();

        mockServer.verify();
        assertThat(response).isNotNull();
        assertThat(response.count()).isEqualTo(1);
        assertThat(response.matches()).hasSize(1);
        assertThat(response.matches().get(0).status()).isEqualTo("FINISHED");
        assertThat(response.matches().get(0).score().fullTime().home()).isEqualTo(2);
        assertThat(response.matches().get(0).score().fullTime().away()).isEqualTo(1);
    }

    @Test
    void fetchMatches_onHttpError_returnsNull() {
        mockServer.expect(requestTo("https://api.football-data.org/v4/competitions/WC/matches"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        FootballApiResponseDto response = client.fetchMatches();

        mockServer.verify();
        assertThat(response).isNull();
    }

    @Test
    void fetchMatches_onEmptyApiKey_returnsNull() {
        RestTemplate restTemplate2 = new RestTemplate();
        FootballApiClient clientNoKey = new FootballApiClient(restTemplate2, "");

        FootballApiResponseDto response = clientNoKey.fetchMatches();

        assertThat(response).isNull();
    }
}
```

- [ ] **Step 2: Implement FootballApiClient**

```java
// src/main/java/com/worldcup/prediction/integration/football/FootballApiClient.java
package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.integration.football.dto.FootballApiResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class FootballApiClient {

    private static final Logger log = LoggerFactory.getLogger(FootballApiClient.class);
    private static final String API_URL = "https://api.football-data.org/v4/competitions/WC/matches";

    private final RestTemplate restTemplate;
    private final String apiKey;

    public FootballApiClient(RestTemplate restTemplate,
                             @Value("${football.api.key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /**
     * Fetches all WC matches from football-data.org.
     * Returns null if the API key is not configured or if the request fails.
     */
    public FootballApiResponseDto fetchMatches() {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("football.api.key not configured — skipping API fetch");
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", apiKey);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<FootballApiResponseDto> response =
                restTemplate.exchange(API_URL, HttpMethod.GET, request, FootballApiResponseDto.class);
            return response.getBody();
        } catch (RestClientException e) {
            log.warn("Failed to fetch matches from football-data.org: {}", e.getMessage());
            return null;
        }
    }
}
```

- [ ] **Step 3: Register RestTemplate bean** (add to existing `AppConfig.java` or create a new one if it doesn't exist)

```java
// In src/main/java/com/worldcup/prediction/config/AppConfig.java
// Add (or create the class with):

import org.springframework.web.client.RestTemplate;

@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();
}
```

- [ ] **Step 4: Add property to application.properties**

```properties
# Football data API (leave blank to disable automatic sync)
football.api.key=${FOOTBALL_API_KEY:}
```

- [ ] **Step 5: Run tests**

```bash
./mvnw test -pl . -Dtest=FootballApiClientTest
```

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(part8): implement FootballApiClient with RestTemplate and unit tests"
```

---

### Task 3: FootballApiSyncService

**Files:**
- Create: `src/main/java/com/worldcup/prediction/integration/football/FootballApiSyncService.java`

The sync service correlates API matches to database `Match` entities. Matching strategy: look up by `externalId` (a field that stores the API's `id`) first; fall back to matching by `homeTeam.tla + awayTeam.tla + kickoffTime date`. Only update matches with `status = FINISHED` from the API. After updating a match, fire `ScoringService.scoreMatch(match.getId())` for newly-finished matches (those that were not already `FINISHED` in the DB).

- [ ] **Step 1: Ensure Match entity has externalId field**

Add to `Match.java` if not already present:

```java
// In Match entity — add field:
@Column(name = "external_id")
private Long externalId;
```

Add getter/setter or use Lombok `@Getter @Setter` (consistent with existing entity style).

Add migration `V8__add_match_external_id.sql`:

```sql
ALTER TABLE matches ADD COLUMN IF NOT EXISTS external_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_matches_external_id ON matches(external_id);
```

- [ ] **Step 2: Implement FootballApiSyncService**

```java
// src/main/java/com/worldcup/prediction/integration/football/FootballApiSyncService.java
package com.worldcup.prediction.integration.football;

import com.worldcup.prediction.integration.football.dto.FootballApiMatchDto;
import com.worldcup.prediction.integration.football.dto.FootballApiResponseDto;
import com.worldcup.prediction.model.Match;
import com.worldcup.prediction.model.MatchStatus;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.service.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FootballApiSyncService {

    private static final Logger log = LoggerFactory.getLogger(FootballApiSyncService.class);

    private final FootballApiClient apiClient;
    private final MatchRepository matchRepository;
    private final ScoringService scoringService;

    public FootballApiSyncService(FootballApiClient apiClient,
                                  MatchRepository matchRepository,
                                  ScoringService scoringService) {
        this.apiClient = apiClient;
        this.matchRepository = matchRepository;
        this.scoringService = scoringService;
    }

    /**
     * Fetches results from football-data.org, updates FINISHED matches,
     * and triggers scoring for any match that transitioned to FINISHED.
     *
     * @return list of match IDs that were newly finished and scored
     */
    @Transactional
    public List<Long> syncResults() {
        FootballApiResponseDto response = apiClient.fetchMatches();
        if (response == null || response.matches() == null) {
            log.debug("No API response to sync");
            return List.of();
        }

        List<Long> newlyFinished = new ArrayList<>();

        for (FootballApiMatchDto apiMatch : response.matches()) {
            if (!"FINISHED".equals(apiMatch.status())) {
                continue;
            }

            Optional<Match> matchOpt = resolveMatch(apiMatch);
            if (matchOpt.isEmpty()) {
                log.debug("Could not resolve API match id={} to a DB match", apiMatch.id());
                continue;
            }

            Match match = matchOpt.get();

            // Store external ID for future lookups
            if (match.getExternalId() == null) {
                match.setExternalId(apiMatch.id());
            }

            boolean wasAlreadyFinished = MatchStatus.FINISHED.equals(match.getStatus());

            Integer homeScore = apiMatch.score() != null && apiMatch.score().fullTime() != null
                ? apiMatch.score().fullTime().home() : null;
            Integer awayScore = apiMatch.score() != null && apiMatch.score().fullTime() != null
                ? apiMatch.score().fullTime().away() : null;

            if (homeScore == null || awayScore == null) {
                log.warn("FINISHED match id={} has null fullTime score — skipping", apiMatch.id());
                continue;
            }

            match.setHomeScore(homeScore);
            match.setAwayScore(awayScore);
            match.setStatus(MatchStatus.FINISHED);
            matchRepository.save(match);

            if (!wasAlreadyFinished) {
                log.info("Match id={} newly finished ({} {}:{} {}) — triggering scoring",
                    match.getId(),
                    apiMatch.homeTeam() != null ? apiMatch.homeTeam().tla() : "?",
                    homeScore, awayScore,
                    apiMatch.awayTeam() != null ? apiMatch.awayTeam().tla() : "?");
                scoringService.scoreMatch(match.getId());
                newlyFinished.add(match.getId());
            }
        }

        log.info("Sync complete: {} API matches processed, {} newly finished",
            response.matches().size(), newlyFinished.size());
        return newlyFinished;
    }

    private Optional<Match> resolveMatch(FootballApiMatchDto apiMatch) {
        // Primary: look up by stored external ID
        if (apiMatch.id() != null) {
            Optional<Match> byExternalId = matchRepository.findByExternalId(apiMatch.id());
            if (byExternalId.isPresent()) {
                return byExternalId;
            }
        }

        // Fallback: match by home tla + away tla + kickoff date prefix (yyyy-MM-dd)
        if (apiMatch.homeTeam() != null && apiMatch.awayTeam() != null && apiMatch.utcDate() != null) {
            String datePart = apiMatch.utcDate().substring(0, 10); // "2026-06-11"
            return matchRepository.findByHomeTeamTlaAndAwayTeamTlaAndKickoffDate(
                apiMatch.homeTeam().tla(),
                apiMatch.awayTeam().tla(),
                datePart
            );
        }

        return Optional.empty();
    }
}
```

- [ ] **Step 3: Add MatchRepository methods**

Add to `MatchRepository.java`:

```java
Optional<Match> findByExternalId(Long externalId);

@Query("SELECT m FROM Match m WHERE m.homeTeam.tla = :homeTla AND m.awayTeam.tla = :awayTla " +
       "AND CAST(m.kickoffTime AS date) = CAST(:date AS date)")
Optional<Match> findByHomeTeamTlaAndAwayTeamTlaAndKickoffDate(
    @Param("homeTla") String homeTla,
    @Param("awayTla") String awayTla,
    @Param("date") String date
);
```

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(part8): implement FootballApiSyncService with result sync and scoring trigger"
```

---

### Task 4: MatchResultScheduler + SchedulerConfig

**Files:**
- Create: `src/main/java/com/worldcup/prediction/config/SchedulerConfig.java`
- Create: `src/main/java/com/worldcup/prediction/scheduler/MatchResultScheduler.java`
- Create: `src/test/java/com/worldcup/prediction/scheduler/MatchResultSchedulerTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/worldcup/prediction/scheduler/MatchResultSchedulerTest.java
package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.integration.football.FootballApiSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchResultSchedulerTest {

    @Mock
    FootballApiSyncService syncService;

    @InjectMocks
    MatchResultScheduler scheduler;

    @Test
    void syncAndScore_callsSyncService() {
        when(syncService.syncResults()).thenReturn(List.of(1L, 2L));

        scheduler.syncAndScore();

        verify(syncService, times(1)).syncResults();
    }

    @Test
    void syncAndScore_whenSyncReturnsEmpty_doesNotThrow() {
        when(syncService.syncResults()).thenReturn(List.of());

        scheduler.syncAndScore(); // must not throw

        verify(syncService, times(1)).syncResults();
    }

    @Test
    void syncAndScore_whenSyncThrows_doesNotPropagate() {
        when(syncService.syncResults()).thenThrow(new RuntimeException("network failure"));

        scheduler.syncAndScore(); // scheduler must swallow exceptions so the job keeps running

        verify(syncService, times(1)).syncResults();
    }
}
```

- [ ] **Step 2: Implement SchedulerConfig**

```java
// src/main/java/com/worldcup/prediction/config/SchedulerConfig.java
package com.worldcup.prediction.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulerConfig {
    // Activates @Scheduled processing across the application context
}
```

- [ ] **Step 3: Implement MatchResultScheduler**

```java
// src/main/java/com/worldcup/prediction/scheduler/MatchResultScheduler.java
package com.worldcup.prediction.scheduler;

import com.worldcup.prediction.integration.football.FootballApiSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MatchResultScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchResultScheduler.class);

    private final FootballApiSyncService syncService;

    public MatchResultScheduler(FootballApiSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Polls football-data.org every 5 minutes for new results.
     * fixedDelay means the next run starts 5 minutes after the previous run completes
     * (prevents pile-up if the API is slow).
     */
    @Scheduled(fixedDelay = 300_000)
    public void syncAndScore() {
        try {
            List<Long> finished = syncService.syncResults();
            if (!finished.isEmpty()) {
                log.info("Scheduler: {} match(es) newly finished and scored: {}", finished.size(), finished);
            }
        } catch (Exception e) {
            log.error("Scheduler: unexpected error during match result sync — will retry next cycle", e);
        }
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./mvnw test -pl . -Dtest=MatchResultSchedulerTest
```

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(part8): add MatchResultScheduler with @Scheduled fixed-delay 5-minute polling"
```

---

### Task 5: EmailService

**Files:**
- Create: `src/main/java/com/worldcup/prediction/notification/EmailService.java`
- Create: `src/test/java/com/worldcup/prediction/notification/EmailServiceTest.java`

The `EmailService` replaces any `NotificationService` stub from Part 7. If `spring.mail.host` is blank the service logs instead of sending. All four required methods are implemented.

- [ ] **Step 1: Write failing tests**

```java
// src/test/java/com/worldcup/prediction/notification/EmailServiceTest.java
package com.worldcup.prediction.notification;

import com.worldcup.prediction.model.Match;
import com.worldcup.prediction.model.Team;
import com.worldcup.prediction.model.User;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    JavaMailSender mailSender;

    @Mock
    MimeMessage mimeMessage;

    EmailService emailService;

    User testUser;
    Match testMatch;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService = new EmailService(mailSender, "noreply@worldcup.example.com", true);

        testUser = new User();
        testUser.setEmail("player@example.com");
        testUser.setFirstName("Alice");
        testUser.setLastName("Smith");

        Team home = new Team();
        home.setName("Mexico");
        home.setTla("MEX");

        Team away = new Team();
        away.setName("Canada");
        away.setTla("CAN");

        testMatch = new Match();
        testMatch.setHomeTeam(home);
        testMatch.setAwayTeam(away);
        testMatch.setKickoffTime(Instant.parse("2026-06-11T19:00:00Z"));
    }

    @Test
    void sendApprovalEmail_invokesMailSender() throws Exception {
        emailService.sendApprovalEmail(testUser);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendRejectionEmail_invokesMailSender() throws Exception {
        emailService.sendRejectionEmail(testUser);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void sendPredictionReminder_invokesMailSenderForEachUser() throws Exception {
        User user2 = new User();
        user2.setEmail("bob@example.com");
        user2.setFirstName("Bob");
        user2.setLastName("Jones");

        emailService.sendPredictionReminder(List.of(testUser, user2), testMatch);

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void sendResultsPublished_invokesMailSenderForEachUser() throws Exception {
        emailService.sendResultsPublished(List.of(testUser), testMatch);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void whenMailDisabled_noMailSent() throws Exception {
        EmailService disabledService = new EmailService(mailSender, "noreply@worldcup.example.com", false);
        disabledService.sendApprovalEmail(testUser);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
```

- [ ] **Step 2: Add mail config to application.properties**

```properties
# Email (SMTP) — leave host blank to disable email sending (log-only mode)
spring.mail.host=${SMTP_HOST:}
spring.mail.port=${SMTP_PORT:587}
spring.mail.username=${SMTP_USERNAME:}
spring.mail.password=${SMTP_PASSWORD:}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true

# Sender identity
app.mail.from=${MAIL_FROM:noreply@worldcup.example.com}
```

- [ ] **Step 3: Create MailConfig — conditional JavaMailSender bean**

```java
// src/main/java/com/worldcup/prediction/config/MailConfig.java
package com.worldcup.prediction.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    /**
     * Provides a real JavaMailSender only when spring.mail.host is non-empty.
     * When no SMTP host is configured, EmailService falls back to log-only mode.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.mail.host", matchIfMissing = false)
    public JavaMailSender javaMailSender(
            org.springframework.boot.autoconfigure.mail.MailProperties props) {
        // Spring Boot auto-configures JavaMailSenderAutoConfiguration when host is set,
        // so this bean is only needed as a documentation anchor.
        // Spring Boot's MailSenderAutoConfiguration already wires the sender — this
        // config class exists to document the conditional and provide a fallback stub.
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(props.getHost());
        sender.setPort(props.getPort());
        sender.setUsername(props.getUsername());
        sender.setPassword(props.getPassword());

        Properties javaMailProps = sender.getJavaMailProperties();
        javaMailProps.put("mail.smtp.auth", "true");
        javaMailProps.put("mail.smtp.starttls.enable", "true");
        javaMailProps.put("mail.smtp.starttls.required", "true");
        return sender;
    }
}
```

> **Note:** Spring Boot's `MailSenderAutoConfiguration` already creates a `JavaMailSenderImpl` bean when `spring.mail.host` is non-blank. The `MailConfig` above is only needed if you want to override that auto-configuration. In most cases, rely on Boot's autoconfiguration and simply inject `Optional<JavaMailSender>` into `EmailService` to handle the no-host case cleanly.

- [ ] **Step 4: Implement EmailService**

```java
// src/main/java/com/worldcup/prediction/notification/EmailService.java
package com.worldcup.prediction.notification;

import com.worldcup.prediction.model.Match;
import com.worldcup.prediction.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("EEE, MMM d 'at' HH:mm 'UTC'").withZone(ZoneId.of("UTC"));

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean enabled;

    /**
     * Primary constructor for Spring injection.
     * If no JavaMailSender bean exists (SMTP not configured) the Optional is empty
     * and the service runs in log-only mode.
     */
    public EmailService(Optional<JavaMailSender> mailSenderOpt,
                        @Value("${app.mail.from:noreply@worldcup.example.com}") String fromAddress) {
        this.mailSender = mailSenderOpt.orElse(null);
        this.fromAddress = fromAddress;
        this.enabled = (this.mailSender != null);
        if (!this.enabled) {
            log.info("EmailService: no SMTP host configured — running in log-only mode");
        }
    }

    /**
     * Package-private constructor for unit tests (inject mock directly).
     */
    EmailService(JavaMailSender mailSender, String fromAddress, boolean enabled) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.enabled = enabled;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Sends "Your registration has been approved" email. */
    public void sendApprovalEmail(User user) {
        String subject = "Welcome to World Cup 2026 Predictions — You're approved!";
        String body = """
            <html><body style="font-family: Inter, sans-serif; background: #f0fdf5; padding: 32px;">
              <h2 style="color: #006b2a;">You're in! 🎉</h2>
              <p>Hi %s,</p>
              <p>Your registration for the <strong>World Cup 2026 Prediction Game</strong> has been approved.</p>
              <p>Sign in with your Google or LinkedIn account to start making predictions.</p>
              <p style="margin-top: 32px; font-size: 12px; color: #666;">World Cup 2026 Prediction Game</p>
            </body></html>
            """.formatted(user.getFirstName());
        send(user.getEmail(), subject, body);
    }

    /** Sends "Your registration was not approved" email. */
    public void sendRejectionEmail(User user) {
        String subject = "World Cup 2026 Predictions — Registration update";
        String body = """
            <html><body style="font-family: Inter, sans-serif; background: #f0fdf5; padding: 32px;">
              <h2 style="color: #006b2a;">Registration Update</h2>
              <p>Hi %s,</p>
              <p>Unfortunately your registration for the <strong>World Cup 2026 Prediction Game</strong>
                 was not approved at this time.</p>
              <p>If you believe this is an error, please contact your administrator.</p>
              <p style="margin-top: 32px; font-size: 12px; color: #666;">World Cup 2026 Prediction Game</p>
            </body></html>
            """.formatted(user.getFirstName());
        send(user.getEmail(), subject, body);
    }

    /** Sends "Predictions close in 2 hours" reminder to a list of users. */
    public void sendPredictionReminder(List<User> users, Match match) {
        String matchLabel = matchLabel(match);
        String subject = "⚽ Predictions close in 2 hours — " + matchLabel;
        String body = """
            <html><body style="font-family: Inter, sans-serif; background: #f0fdf5; padding: 32px;">
              <h2 style="color: #FF5722;">⏰ Time is running out!</h2>
              <p>Predictions for <strong>%s</strong> (%s) close in <strong>2 hours</strong>.</p>
              <p>Log in now to submit your predictions before the window closes.</p>
              <p style="margin-top: 32px; font-size: 12px; color: #666;">World Cup 2026 Prediction Game</p>
            </body></html>
            """.formatted(matchLabel, DATE_FMT.format(match.getKickoffTime()));
        users.forEach(user -> send(user.getEmail(), subject, body));
    }

    /** Sends "Match results published, leaderboard updated" to a list of users. */
    public void sendResultsPublished(List<User> users, Match match) {
        String matchLabel = matchLabel(match);
        String subject = "📊 Results published — " + matchLabel;
        String body = """
            <html><body style="font-family: Inter, sans-serif; background: #f0fdf5; padding: 32px;">
              <h2 style="color: #006b2a;">Results are in!</h2>
              <p>The results for <strong>%s</strong> have been published and the leaderboard has been updated.</p>
              <p>Check the leaderboard to see where you stand.</p>
              <p style="margin-top: 32px; font-size: 12px; color: #666;">World Cup 2026 Prediction Game</p>
            </body></html>
            """.formatted(matchLabel);
        users.forEach(user -> send(user.getEmail(), subject, body));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void send(String to, String subject, String htmlBody) {
        if (!enabled || mailSender == null) {
            log.info("[EMAIL LOG-ONLY] To: {} | Subject: {}", to, subject);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.debug("Email sent to {} — {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }

    private String matchLabel(Match match) {
        String home = match.getHomeTeam() != null ? match.getHomeTeam().getName() : "TBD";
        String away = match.getAwayTeam() != null ? match.getAwayTeam().getName() : "TBD";
        return home + " vs " + away;
    }
}
```

- [ ] **Step 5: Wire EmailService into admin flows (replace Part 7 stubs)**

In `AdminUserController.java` (or wherever Part 7 placed the approve/reject logic), replace any `notificationService.notifyApproval(user)` stubs with:

```java
// inject EmailService
@Autowired
private EmailService emailService;

// in approveUser():
emailService.sendApprovalEmail(user);

// in rejectUser():
emailService.sendRejectionEmail(user);
```

In `AdminMatchController.java` (or wherever Part 7 placed reminder sending), replace stubs with:

```java
// reminder trigger:
List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
emailService.sendPredictionReminder(activeUsers, match);

// results published:
emailService.sendResultsPublished(activeUsers, match);
```

- [ ] **Step 6: Run tests**

```bash
./mvnw test -pl . -Dtest=EmailServiceTest
```

- [ ] **Step 7: Commit**

```bash
git commit -m "feat(part8): implement real EmailService with JavaMailSender and graceful SMTP fallback"
```

---

### Task 6: Knockout Bracket Page

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/BracketController.java`
- Create: `src/main/resources/templates/bracket.html`

- [ ] **Step 1: Implement BracketController**

```java
// src/main/java/com/worldcup/prediction/controller/BracketController.java
package com.worldcup.prediction.controller;

import com.worldcup.prediction.model.Match;
import com.worldcup.prediction.model.MatchStage;
import com.worldcup.prediction.repository.MatchRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/bracket")
public class BracketController {

    // Display order for knockout rounds (left → right on bracket)
    private static final List<MatchStage> BRACKET_STAGES = List.of(
        MatchStage.ROUND_OF_32,
        MatchStage.ROUND_OF_16,
        MatchStage.QUARTER_FINALS,
        MatchStage.SEMI_FINALS,
        MatchStage.FINAL,
        MatchStage.THIRD_PLACE
    );

    private final MatchRepository matchRepository;

    public BracketController(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    @GetMapping
    public String bracket(Model model) {
        // Build an ordered map: stage label → list of matches in that stage
        Map<String, List<Match>> bracketByStage = new LinkedHashMap<>();

        for (MatchStage stage : BRACKET_STAGES) {
            List<Match> stageMatches = matchRepository.findByStageOrderByKickoffTime(stage);
            if (!stageMatches.isEmpty()) {
                bracketByStage.put(stageName(stage), stageMatches);
            }
        }

        model.addAttribute("bracketByStage", bracketByStage);
        model.addAttribute("pageTitle", "Knockout Bracket");
        return "bracket";
    }

    private String stageName(MatchStage stage) {
        return switch (stage) {
            case ROUND_OF_32    -> "Round of 32";
            case ROUND_OF_16    -> "Round of 16";
            case QUARTER_FINALS -> "Quarter-Finals";
            case SEMI_FINALS    -> "Semi-Finals";
            case FINAL          -> "Final";
            case THIRD_PLACE    -> "3rd Place";
            default             -> stage.name();
        };
    }
}
```

Add to `MatchRepository.java`:

```java
List<Match> findByStageOrderByKickoffTime(MatchStage stage);
```

- [ ] **Step 2: Create bracket.html**

```html
<!DOCTYPE html>
<!-- src/main/resources/templates/bracket.html -->
<html lang="en" xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>Knockout Bracket — World Cup 2026</title>
  <link rel="preconnect" href="https://fonts.googleapis.com"/>
  <link href="https://fonts.googleapis.com/css2?family=Bebas+Neue&family=Inter:wght@400;500;600;700&display=swap"
        rel="stylesheet"/>
  <script src="https://cdn.tailwindcss.com"></script>
  <style>
    :root {
      --green-primary: #00c853;
      --green-dark:    #006b2a;
      --orange:        #FF5722;
      --gold:          #FFD600;
      --bg:            #f0fdf5;
    }

    body { background: var(--bg); font-family: 'Inter', sans-serif; }

    /* ── Navbar slide-in ────────────────────────────────────────────────── */
    nav { animation: slideDown 0.5s ease-out both; }
    @keyframes slideDown { from { transform: translateY(-100%); opacity: 0; } to { transform: translateY(0); opacity: 1; } }

    /* ── Page fade-up ───────────────────────────────────────────────────── */
    .fade-up { animation: fadeUp 0.6s ease-out both; }
    @keyframes fadeUp { from { transform: translateY(24px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }

    /* ── Bracket layout ────────────────────────────────────────────────── */
    .bracket-scroll { overflow-x: auto; padding-bottom: 16px; }
    .bracket-grid   { display: flex; gap: 40px; align-items: flex-start; min-width: max-content; padding: 24px 16px; }

    /* ── Round column ───────────────────────────────────────────────────── */
    .round-col { display: flex; flex-direction: column; gap: 0; }
    .round-header {
      font-family: 'Bebas Neue', sans-serif;
      font-size: 1.1rem;
      letter-spacing: 0.08em;
      color: var(--green-dark);
      text-align: center;
      padding: 6px 16px;
      background: linear-gradient(135deg, var(--green-primary), var(--green-dark));
      color: white;
      border-radius: 8px 8px 0 0;
      position: sticky;
      top: 0;
      z-index: 1;
    }

    .matches-col {
      display: flex;
      flex-direction: column;
      justify-content: space-around;
      flex: 1;
      gap: 12px;
      padding: 12px 0;
    }

    /* ── Match card ─────────────────────────────────────────────────────── */
    .match-card {
      background: white;
      border-radius: 12px;
      border: 2px solid #e2f5ea;
      padding: 10px 14px;
      min-width: 180px;
      box-shadow: 0 2px 8px rgba(0,107,42,0.08);
      transition: box-shadow 0.2s, transform 0.2s;
    }
    .match-card:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,107,42,0.15); }

    .match-card.finished {
      border-color: var(--green-primary);
      animation: glowIn 0.8s ease-out both;
    }
    @keyframes glowIn {
      0%   { box-shadow: 0 0 0 0 rgba(0,200,83,0); }
      50%  { box-shadow: 0 0 16px 4px rgba(0,200,83,0.45); }
      100% { box-shadow: 0 0 6px 2px rgba(0,200,83,0.2); }
    }

    /* ── Team row inside match card ─────────────────────────────────────── */
    .team-row {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 4px 0;
    }
    .team-row + .team-row { border-top: 1px solid #f0fdf5; }

    .flag-circle {
      width: 28px; height: 28px;
      border-radius: 50%;
      object-fit: cover;
      border: 1.5px solid #e2f5ea;
      flex-shrink: 0;
      animation: flagWave 4s ease-in-out infinite;
    }
    @keyframes flagWave {
      0%, 100% { transform: perspective(60px) rotateY(0deg); }
      25%       { transform: perspective(60px) rotateY(12deg); }
      75%       { transform: perspective(60px) rotateY(-12deg); }
    }

    .team-name {
      flex: 1;
      font-size: 0.85rem;
      font-weight: 600;
      color: #1a3a2a;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .team-name.tbd { color: #94a3b8; font-style: italic; font-weight: 400; }

    .score {
      font-family: 'Bebas Neue', sans-serif;
      font-size: 1.2rem;
      color: var(--green-dark);
      min-width: 20px;
      text-align: right;
    }
    .score.winner { color: var(--green-primary); }

    /* ── TBD pulsing dots ───────────────────────────────────────────────── */
    .tbd-dots { display: flex; gap: 3px; align-items: center; padding-left: 2px; }
    .tbd-dot {
      width: 5px; height: 5px;
      background: #94a3b8;
      border-radius: 50%;
      animation: pulse-dot 1.4s ease-in-out infinite;
    }
    .tbd-dot:nth-child(2) { animation-delay: 0.2s; }
    .tbd-dot:nth-child(3) { animation-delay: 0.4s; }
    @keyframes pulse-dot {
      0%, 80%, 100% { transform: scale(0.8); opacity: 0.5; }
      40%           { transform: scale(1.2); opacity: 1.0; }
    }

    /* ── Connector lines between rounds ─────────────────────────────────── */
    .connector { width: 20px; flex-shrink: 0; align-self: stretch; }

    /* ── Final / 3rd place special styling ──────────────────────────────── */
    .round-col.final .match-card {
      border-color: var(--gold);
      box-shadow: 0 4px 20px rgba(255,214,0,0.25);
    }
    .round-col.final .round-header {
      background: linear-gradient(135deg, #FFD600, #FF8F00);
      color: #1a1a1a;
    }
    .round-col.third-place .round-header {
      background: linear-gradient(135deg, #90a4ae, #546e7a);
    }

    /* ── Shimmer on round headers ───────────────────────────────────────── */
    .round-header {
      position: relative;
      overflow: hidden;
    }
    .round-header::after {
      content: '';
      position: absolute; top: 0; left: -100%;
      width: 60%; height: 100%;
      background: linear-gradient(90deg, transparent, rgba(255,255,255,0.3), transparent);
      animation: shimmer 2.5s infinite;
    }
    @keyframes shimmer { to { left: 200%; } }
  </style>
</head>
<body>

<!-- ── Navbar ──────────────────────────────────────────────────────────── -->
<nav class="bg-green-900 text-white shadow-lg">
  <div class="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">
    <a href="/" class="font-['Bebas_Neue'] text-2xl tracking-widest text-green-300">
      ⚽ WC2026 PREDICTIONS
    </a>
    <div class="flex items-center gap-4 text-sm">
      <a href="/fixtures"    class="hover:text-green-300 transition-colors">Fixtures</a>
      <a href="/groups"      class="hover:text-green-300 transition-colors">Groups</a>
      <a href="/bracket"     class="text-green-300 font-semibold border-b border-green-300">Bracket</a>
      <a href="/leaderboard" class="hover:text-green-300 transition-colors">Leaderboard</a>
      <div sec:authorize="isAuthenticated()">
        <a href="/predictions" class="hover:text-green-300 transition-colors">My Predictions</a>
      </div>
      <div sec:authorize="isAuthenticated()">
        <a href="/logout" class="bg-green-700 hover:bg-green-600 px-3 py-1 rounded transition-colors">Sign out</a>
      </div>
      <div sec:authorize="!isAuthenticated()">
        <a href="/login" class="bg-green-600 hover:bg-green-500 px-3 py-1 rounded transition-colors">Sign in</a>
      </div>
    </div>
  </div>
</nav>

<!-- ── Page Header ─────────────────────────────────────────────────────── -->
<div class="max-w-full px-6 py-8">
  <div class="fade-up" style="animation-delay:0.1s">
    <h1 class="font-['Bebas_Neue'] text-4xl md:text-5xl text-green-900 tracking-wider mb-1">
      KNOCKOUT BRACKET
    </h1>
    <p class="text-green-700 text-sm">FIFA World Cup 2026 — Knockout Rounds</p>
  </div>

  <!-- ── Bracket scroll container ────────────────────────────────────────── -->
  <div class="bracket-scroll fade-up mt-6" style="animation-delay:0.2s">
    <div class="bracket-grid">

      <!-- Render each round as a column -->
      <div th:each="entry, iterStat : ${bracketByStage}"
           th:with="roundName=${entry.key}, matches=${entry.value}"
           th:classappend="${roundName == 'Final'} ? 'round-col final' : (${roundName == '3rd Place'} ? 'round-col third-place' : 'round-col')"
           class="round-col"
           th:style="'animation-delay:' + (${iterStat.index} * 0.1) + 's'"
           style="animation: fadeUp 0.6s ease-out both;">

        <!-- Round header -->
        <div class="round-header" th:text="${roundName}">Round</div>

        <!-- Matches in this round -->
        <div class="matches-col">
          <div th:each="match : ${matches}"
               th:classappend="${match.status?.name() == 'FINISHED'} ? ' finished' : ''"
               class="match-card">

            <!-- Home team row -->
            <div class="team-row">
              <!-- Flag -->
              <img th:if="${match.homeTeam != null}"
                   th:src="'https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/' + ${#strings.toLowerCase(match.homeTeam.tla)} + '.svg'"
                   th:onerror="'this.src=\'https://flagcdn.com/w40/' + ${#strings.toLowerCase(match.homeTeam.tla)} + '.png\''"
                   th:alt="${match.homeTeam.name}"
                   class="flag-circle"/>
              <div th:unless="${match.homeTeam != null}" class="flag-circle bg-slate-200 flex items-center justify-center text-slate-400 text-xs">?</div>

              <!-- Team name -->
              <span th:if="${match.homeTeam != null}"
                    th:text="${match.homeTeam.name}"
                    class="team-name"></span>
              <span th:unless="${match.homeTeam != null}" class="team-name tbd">TBD</span>

              <!-- Score (finished) or TBD dots -->
              <span th:if="${match.status?.name() == 'FINISHED' and match.homeScore != null}"
                    th:text="${match.homeScore}"
                    th:classappend="${match.homeScore > match.awayScore} ? ' winner' : ''"
                    class="score"></span>
              <div th:unless="${match.status?.name() == 'FINISHED'}" class="tbd-dots">
                <div class="tbd-dot"></div>
                <div class="tbd-dot"></div>
                <div class="tbd-dot"></div>
              </div>
            </div>

            <!-- Away team row -->
            <div class="team-row">
              <!-- Flag -->
              <img th:if="${match.awayTeam != null}"
                   th:src="'https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/' + ${#strings.toLowerCase(match.awayTeam.tla)} + '.svg'"
                   th:onerror="'this.src=\'https://flagcdn.com/w40/' + ${#strings.toLowerCase(match.awayTeam.tla)} + '.png\''"
                   th:alt="${match.awayTeam.name}"
                   class="flag-circle"/>
              <div th:unless="${match.awayTeam != null}" class="flag-circle bg-slate-200"></div>

              <!-- Team name -->
              <span th:if="${match.awayTeam != null}"
                    th:text="${match.awayTeam.name}"
                    class="team-name"></span>
              <span th:unless="${match.awayTeam != null}" class="team-name tbd">TBD</span>

              <!-- Score -->
              <span th:if="${match.status?.name() == 'FINISHED' and match.awayScore != null}"
                    th:text="${match.awayScore}"
                    th:classappend="${match.awayScore > match.homeScore} ? ' winner' : ''"
                    class="score"></span>
              <div th:unless="${match.status?.name() == 'FINISHED'}" class="tbd-dots">
                <div class="tbd-dot"></div>
                <div class="tbd-dot"></div>
                <div class="tbd-dot"></div>
              </div>
            </div>

            <!-- Match date chip -->
            <div class="mt-2 text-center">
              <span th:if="${match.kickoffTime != null}"
                    th:text="${#temporals.format(match.kickoffTime, 'MMM d', #locale)}"
                    class="text-xs text-slate-400 font-medium"></span>
            </div>

          </div><!-- /match-card -->
        </div><!-- /matches-col -->
      </div><!-- /round-col -->

    </div><!-- /bracket-grid -->
  </div><!-- /bracket-scroll -->

  <!-- Empty state when no knockout matches yet -->
  <div th:if="${bracketByStage.isEmpty()}"
       class="fade-up text-center py-20 text-slate-400"
       style="animation-delay:0.3s">
    <div class="text-6xl mb-4">🏆</div>
    <p class="text-xl font-semibold">Knockout rounds not started yet</p>
    <p class="text-sm mt-2">Check back after the Group Stage!</p>
  </div>

</div>

<!-- ── Mobile hint ────────────────────────────────────────────────────── -->
<div class="md:hidden text-center py-2 text-xs text-slate-400 fade-up" style="animation-delay:0.4s">
  ← Scroll horizontally to see all rounds →
</div>

</body>
</html>
```

- [ ] **Step 3: Add bracket link to nav in other pages**

Ensure `bracket` nav link is present in `layout.html` / fragments (or the shared navbar fragment from Part 1).

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(part8): add BracketController and bracket.html knockout bracket page"
```

---

### Task 7: Docker Deployment — Dockerfile & Application Properties

**Files:**
- Create: `Dockerfile`
- Create: `src/main/resources/application-docker.properties`

- [ ] **Step 1: Create application-docker.properties**

```properties
# src/main/resources/application-docker.properties
# Activated by SPRING_PROFILES_ACTIVE=docker (set in docker-compose.yml)

# PostgreSQL via Docker Compose service name
spring.datasource.url=jdbc:postgresql://db:5432/worldcup
spring.datasource.username=${DB_USERNAME:worldcup}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate

# Flyway migrations
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# Logging
logging.level.com.worldcup=INFO
logging.level.org.springframework.security=WARN
```

- [ ] **Step 2: Create Dockerfile (multi-stage, dual-profile)**

```dockerfile
# Dockerfile
# Stage 1: Build with Maven + JDK 21
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy Maven wrapper and pom.xml first (layer caching)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

# Copy source and build
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime — JRE 21 slim
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy JAR from build stage
COPY --from=builder /workspace/target/*.jar app.jar

# ── Default profile: SQLite ──────────────────────────────────────────────────
# Override APP_PROFILE=postgres (via env var) to switch to PostgreSQL mode.
ENV APP_PROFILE=sqlite

# SQLite database path — mount a named volume at /data to persist across restarts.
# To use a custom path: -e SQLITE_PATH=/data/myapp.db
ENV SQLITE_PATH=/data/worldcup.db

# Named volume mount point for SQLite file persistence
VOLUME /data

# Expose Spring Boot default port
EXPOSE 8080

# Health-check endpoint (Spring Actuator)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

- [ ] **Step 3: Add Spring Actuator dependency** (if not already present in `pom.xml`)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

And expose health endpoint in `application.properties`:

```properties
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never
```

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(part8): add Dockerfile multi-stage build (SQLite default) and application-docker.properties"
```

---

### Task 7.5: Dual-mode Docker Setup

**Files:**
- Create: `docker-compose.yml`
- Create: `docker-compose.postgres.yml`
- Create: `.env.example`

- [ ] **Step 1: Write `docker-compose.yml` — SQLite mode (complete file)**

```yaml
# docker-compose.yml
# Default deployment mode: SQLite — no external database required.
# Usage: docker compose up --build
version: '3.9'

services:
  app:
    build: .
    ports:
      - "8080:8080"
    volumes:
      - worldcup-data:/data
    environment:
      APP_PROFILE: sqlite
      SQLITE_PATH: /data/worldcup.db
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET}
      LINKEDIN_CLIENT_ID: ${LINKEDIN_CLIENT_ID}
      LINKEDIN_CLIENT_SECRET: ${LINKEDIN_CLIENT_SECRET}
      FOOTBALL_API_KEY: ${FOOTBALL_API_KEY:-}
      SMTP_HOST: ${SMTP_HOST:-}
      SMTP_USERNAME: ${SMTP_USERNAME:-}
      SMTP_PASSWORD: ${SMTP_PASSWORD:-}
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 90s

volumes:
  worldcup-data:
```

- [ ] **Step 2: Write `docker-compose.postgres.yml` — PostgreSQL mode (complete file)**

```yaml
# docker-compose.postgres.yml
# Production deployment mode: PostgreSQL external database.
# Usage: docker compose -f docker-compose.postgres.yml up --build
version: '3.9'

services:

  # ── PostgreSQL ──────────────────────────────────────────────────────────
  db:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: worldcup
      POSTGRES_USER: ${DB_USERNAME:-worldcup}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME:-worldcup} -d worldcup"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s
    ports:
      - "5432:5432"   # expose for local dev access; remove in production

  # ── Spring Boot Application ─────────────────────────────────────────────
  app:
    build: .
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    ports:
      - "8080:8080"
    environment:
      APP_PROFILE: postgres
      # Database
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/worldcup
      SPRING_DATASOURCE_USERNAME: ${DB_USERNAME:-worldcup}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      # OAuth2
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET}
      LINKEDIN_CLIENT_ID: ${LINKEDIN_CLIENT_ID}
      LINKEDIN_CLIENT_SECRET: ${LINKEDIN_CLIENT_SECRET}
      # Football API
      FOOTBALL_API_KEY: ${FOOTBALL_API_KEY:-}
      # Mail
      SMTP_HOST: ${SMTP_HOST:-}
      SMTP_USERNAME: ${SMTP_USERNAME:-}
      SMTP_PASSWORD: ${SMTP_PASSWORD:-}
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 90s

volumes:
  postgres_data:
    driver: local
```

- [ ] **Step 3: Write `.env.example` — covers both SQLite and PostgreSQL modes**

```bash
# .env.example
# Copy to .env and fill in values before running docker compose up.
#
# ── Deployment mode ──────────────────────────────────────────────────────
# SQLite mode (default):  docker compose up --build
# PostgreSQL mode:        docker compose -f docker-compose.postgres.yml up --build
#
# Required for SQLite mode: GOOGLE_*, LINKEDIN_* (OAuth2 only)
# Required for PostgreSQL mode: above + DB_USERNAME, DB_PASSWORD

# ── Database (PostgreSQL mode only) ─────────────────────────────────────
DB_USERNAME=worldcup
DB_PASSWORD=changeme_secure_password

# ── OAuth2 — Google ──────────────────────────────────────────────────────
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret

# ── OAuth2 — LinkedIn ────────────────────────────────────────────────────
LINKEDIN_CLIENT_ID=your-linkedin-client-id
LINKEDIN_CLIENT_SECRET=your-linkedin-client-secret

# ── Football Data API ────────────────────────────────────────────────────
# Get a free key at https://www.football-data.org/client/register
# Leave blank to disable automatic result sync (admin manual entry only)
FOOTBALL_API_KEY=

# ── Email / SMTP ─────────────────────────────────────────────────────────
# Leave SMTP_HOST blank to disable email sending (log-only mode)
SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
MAIL_FROM=noreply@worldcup.example.com
```

- [ ] **Step 4: Test SQLite mode locally**

```bash
# Copy example env (only OAuth2 credentials required for SQLite mode)
cp .env.example .env
# Edit .env — set GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, LINKEDIN_CLIENT_ID, LINKEDIN_CLIENT_SECRET

docker compose up --build

# In a separate terminal, verify the app responds:
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/
# Expected: 200 (or 302 redirect to login)

docker compose logs app --tail=20
# Should see: Started WorldCupPredictionApplication, no datasource errors
```

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(part8): add dual-mode Docker compose — SQLite (default) and PostgreSQL, plus .env.example"
```

---

### Task 8: Final Wiring & Smoke Test

- [ ] **Step 1: Run full test suite**

```bash
./mvnw test
```

All tests must pass, including:
- `FootballApiClientTest`
- `MatchResultSchedulerTest`
- `EmailServiceTest`

- [ ] **Step 2: Verify scheduler does not fire in tests**

Ensure `SchedulerConfig` is excluded from `@SpringBootTest` slices or that `@Scheduled` is disabled in tests:

```java
// In test application.properties (src/test/resources/application.properties):
spring.task.scheduling.pool.size=0
# or use @MockBean MatchResultScheduler in integration tests to prevent real scheduling
```

Alternatively, add a test-profile that disables scheduling:

```properties
# src/test/resources/application-test.properties
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
```

- [ ] **Step 3: Verify graceful degradation**

Confirm that with both `football.api.key` and `spring.mail.host` left blank:
- Application starts without error
- Scheduler runs every 5 min but `FootballApiClient.fetchMatches()` returns null immediately (log at DEBUG)
- `EmailService` logs instead of throwing

```bash
# Start locally without API key or SMTP:
./mvnw spring-boot:run
# Check logs — should see no errors, scheduler running every 5 min with "No API response to sync"
```

- [ ] **Step 4: Final commit**

```bash
git commit -m "feat(part8): complete integrations & deployment — football API sync, email, bracket, Docker"
```

---

## Summary of All New Files

| File | Purpose |
|---|---|
| `src/main/java/.../integration/football/dto/FootballApiTeamDto.java` | API team DTO |
| `src/main/java/.../integration/football/dto/FootballApiFullTimeDto.java` | API fullTime score DTO |
| `src/main/java/.../integration/football/dto/FootballApiScoreDto.java` | API score wrapper DTO |
| `src/main/java/.../integration/football/dto/FootballApiMatchDto.java` | API match DTO |
| `src/main/java/.../integration/football/dto/FootballApiResponseDto.java` | API top-level response DTO |
| `src/main/java/.../integration/football/FootballApiClient.java` | RestTemplate API client |
| `src/main/java/.../integration/football/FootballApiSyncService.java` | Result sync logic + scoring trigger |
| `src/main/java/.../scheduler/MatchResultScheduler.java` | `@Scheduled` every 5 min |
| `src/main/java/.../config/SchedulerConfig.java` | `@EnableScheduling` |
| `src/main/java/.../config/MailConfig.java` | Conditional JavaMailSender |
| `src/main/java/.../notification/EmailService.java` | Real email implementation |
| `src/main/java/.../controller/BracketController.java` | `/bracket` page controller |
| `src/main/resources/templates/bracket.html` | Knockout bracket Thymeleaf page |
| `src/main/resources/application-docker.properties` | Docker datasource config (PostgreSQL mode) |
| `src/main/resources/db/migration/V8__add_match_external_id.sql` | Add `external_id` column |
| `Dockerfile` | Multi-stage Maven → JRE 21 build; `ENV APP_PROFILE=sqlite`, `VOLUME /data`, `ENV SQLITE_PATH` |
| `docker-compose.yml` | SQLite mode — simple, no external DB (default) |
| `docker-compose.postgres.yml` | PostgreSQL mode — app + PostgreSQL with health checks |
| `.env.example` | Environment variable template covering both SQLite and PostgreSQL modes |
| `src/test/java/.../FootballApiClientTest.java` | MockRestServiceServer tests |
| `src/test/java/.../MatchResultSchedulerTest.java` | Mockito scheduler tests |
| `src/test/java/.../EmailServiceTest.java` | Mockito mail sender tests |
