# Prediction Window Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Track per-user, per-community, per-matchday submission state explicitly; surface a countdown/confirmation banner on every community page; add Super Admin and Community Admin views to monitor and remind non-submitters.

**Architecture:** A new `round_submissions` table is the single source of truth for submission state. A `@ControllerAdvice` injects banner data into every community page model. Two new admin controllers (one super-admin, one community-admin) read from this table and trigger reminder emails.

**Tech Stack:** Spring Boot 3, JPA/Hibernate, SQLite with Flyway migrations, Thymeleaf, Alpine.js, Tailwind CSS (via CDN), AssertJ + Mockito for tests.

---

## File Map

| Action | Path |
|---|---|
| Create | `src/main/resources/db/migration/V9__round_submissions.sql` |
| Create | `src/main/java/com/worldcup/prediction/domain/RoundSubmission.java` |
| Create | `src/main/java/com/worldcup/prediction/repository/RoundSubmissionRepository.java` |
| Create | `src/main/java/com/worldcup/prediction/service/RoundSubmissionService.java` |
| Create | `src/test/java/com/worldcup/prediction/service/RoundSubmissionServiceTest.java` |
| Modify | `src/main/java/com/worldcup/prediction/service/PredictionViewService.java` |
| Create | `src/test/java/com/worldcup/prediction/service/PredictionViewServiceSubmissionTest.java` |
| Create | `src/main/java/com/worldcup/prediction/dto/WindowBannerDto.java` |
| Create | `src/main/java/com/worldcup/prediction/web/CommunityWindowBannerAdvice.java` |
| Create | `src/test/java/com/worldcup/prediction/web/CommunityWindowBannerAdviceTest.java` |
| Modify | `src/main/resources/templates/layout/community-base.html` |
| Create | `src/main/java/com/worldcup/prediction/dto/MemberSubmissionStatusDto.java` |
| Create | `src/main/java/com/worldcup/prediction/controller/admin/AdminMatchdayStatusController.java` |
| Create | `src/test/java/com/worldcup/prediction/controller/admin/AdminMatchdayStatusControllerTest.java` |
| Create | `src/main/resources/templates/admin/matchday-status.html` |
| Modify | `src/main/resources/templates/admin/communities.html` |
| Create | `src/main/java/com/worldcup/prediction/controller/community/CommunityAdminSubmissionController.java` |
| Create | `src/test/java/com/worldcup/prediction/controller/community/CommunityAdminSubmissionControllerTest.java` |
| Create | `src/main/resources/templates/community/admin/submission-status.html` |
| Modify | `src/main/resources/templates/layout/community-base.html` (second pass — nav links) |

---

## Task 1: Flyway Migration V9

**Files:**
- Create: `src/main/resources/db/migration/V9__round_submissions.sql`

- [ ] **Step 1: Create migration file**

```sql
-- V9__round_submissions.sql
-- Track per-user, per-community, per-round submission state.

CREATE TABLE round_submissions (
    id            INTEGER PRIMARY KEY,
    user_id       INTEGER      NOT NULL REFERENCES users(id),
    community_id  INTEGER      NOT NULL REFERENCES communities(id),
    round_label   VARCHAR(50)  NOT NULL,
    submitted_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_round_submissions UNIQUE (user_id, community_id, round_label)
);

CREATE INDEX rs_community_round_idx ON round_submissions(community_id, round_label);
CREATE INDEX rs_user_community_idx  ON round_submissions(user_id, community_id);

-- Backfill: one row per (user, community, round) that already has predictions.
-- Uses MIN(submitted_at) from predictions as the submission timestamp.
INSERT INTO round_submissions (user_id, community_id, round_label, submitted_at)
SELECT p.user_id, p.community_id, m.round_label, MIN(p.submitted_at)
FROM predictions p
JOIN matches m ON p.match_id = m.id
WHERE m.round_label IS NOT NULL
GROUP BY p.user_id, p.community_id, m.round_label;
```

- [ ] **Step 2: Verify migration runs**

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev" 2>&1 | grep -E "Flyway|V9|ERROR" | head -10
```

Expected: line containing `Successfully applied 1 migration to schema` with V9.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V9__round_submissions.sql
git commit -m "feat: add round_submissions migration with production backfill"
```

---

## Task 2: RoundSubmission Entity and Repository

**Files:**
- Create: `src/main/java/com/worldcup/prediction/domain/RoundSubmission.java`
- Create: `src/main/java/com/worldcup/prediction/repository/RoundSubmissionRepository.java`

- [ ] **Step 1: Create entity**

```java
package com.worldcup.prediction.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "round_submissions",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_round_submissions",
               columnNames = {"user_id", "community_id", "round_label"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RoundSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "community_id", nullable = false)
    private Long communityId;

    @Column(name = "round_label", nullable = false, length = 50)
    private String roundLabel;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
}
```

- [ ] **Step 2: Create repository**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.RoundSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoundSubmissionRepository extends JpaRepository<RoundSubmission, Long> {

    boolean existsByUserIdAndCommunityIdAndRoundLabel(Long userId, Long communityId, String roundLabel);

    Optional<RoundSubmission> findByUserIdAndCommunityIdAndRoundLabel(Long userId, Long communityId, String roundLabel);

    List<RoundSubmission> findByCommunityIdAndRoundLabel(Long communityId, String roundLabel);
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worldcup/prediction/domain/RoundSubmission.java \
        src/main/java/com/worldcup/prediction/repository/RoundSubmissionRepository.java
git commit -m "feat: add RoundSubmission entity and repository"
```

---

## Task 3: RoundSubmissionService

**Files:**
- Create: `src/main/java/com/worldcup/prediction/service/RoundSubmissionService.java`
- Create: `src/test/java/com/worldcup/prediction/service/RoundSubmissionServiceTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.RoundSubmission;
import com.worldcup.prediction.repository.RoundSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoundSubmissionServiceTest {

    @Mock private RoundSubmissionRepository repository;

    private RoundSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new RoundSubmissionService(repository);
    }

    @Test
    void upsert_createsNewRow_whenNoneExists() {
        when(repository.findByUserIdAndCommunityIdAndRoundLabel(1L, 2L, "Matchday 1"))
                .thenReturn(Optional.empty());

        service.upsert(1L, 2L, "Matchday 1");

        ArgumentCaptor<RoundSubmission> captor = ArgumentCaptor.forClass(RoundSubmission.class);
        verify(repository).save(captor.capture());
        RoundSubmission saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getCommunityId()).isEqualTo(2L);
        assertThat(saved.getRoundLabel()).isEqualTo("Matchday 1");
        assertThat(saved.getSubmittedAt()).isNotNull();
    }

    @Test
    void upsert_updatesSubmittedAt_whenRowExists() {
        LocalDateTime original = LocalDateTime.of(2026, 6, 1, 10, 0);
        RoundSubmission existing = RoundSubmission.builder()
                .id(99L).userId(1L).communityId(2L).roundLabel("Matchday 1")
                .submittedAt(original).build();
        when(repository.findByUserIdAndCommunityIdAndRoundLabel(1L, 2L, "Matchday 1"))
                .thenReturn(Optional.of(existing));

        service.upsert(1L, 2L, "Matchday 1");

        verify(repository).save(existing);
        assertThat(existing.getSubmittedAt()).isAfter(original);
    }

    @Test
    void hasSubmitted_delegatesToRepository() {
        when(repository.existsByUserIdAndCommunityIdAndRoundLabel(1L, 2L, "Matchday 1"))
                .thenReturn(true);
        assertThat(service.hasSubmitted(1L, 2L, "Matchday 1")).isTrue();

        when(repository.existsByUserIdAndCommunityIdAndRoundLabel(1L, 2L, "Matchday 2"))
                .thenReturn(false);
        assertThat(service.hasSubmitted(1L, 2L, "Matchday 2")).isFalse();
    }

    @Test
    void findStatusesForCommunityRound_returnsMapKeyedByUserId() {
        RoundSubmission rs1 = RoundSubmission.builder().id(1L).userId(10L).communityId(5L)
                .roundLabel("Matchday 1").submittedAt(LocalDateTime.now()).build();
        RoundSubmission rs2 = RoundSubmission.builder().id(2L).userId(20L).communityId(5L)
                .roundLabel("Matchday 1").submittedAt(LocalDateTime.now()).build();
        when(repository.findByCommunityIdAndRoundLabel(5L, "Matchday 1"))
                .thenReturn(List.of(rs1, rs2));

        Map<Long, RoundSubmission> result = service.findStatusesForCommunityRound(5L, "Matchday 1");

        assertThat(result).containsOnlyKeys(10L, 20L);
        assertThat(result.get(10L)).isSameAs(rs1);
        assertThat(result.get(20L)).isSameAs(rs2);
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./mvnw test -pl . -Dtest=RoundSubmissionServiceTest -q 2>&1 | tail -5
```

Expected: compilation error — `RoundSubmissionService` does not exist yet.

- [ ] **Step 3: Implement the service**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.RoundSubmission;
import com.worldcup.prediction.repository.RoundSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RoundSubmissionService {

    private final RoundSubmissionRepository repository;

    @Transactional
    public void upsert(Long userId, Long communityId, String roundLabel) {
        repository.findByUserIdAndCommunityIdAndRoundLabel(userId, communityId, roundLabel)
                .ifPresentOrElse(
                        rs -> {
                            rs.setSubmittedAt(LocalDateTime.now());
                            repository.save(rs);
                        },
                        () -> repository.save(RoundSubmission.builder()
                                .userId(userId)
                                .communityId(communityId)
                                .roundLabel(roundLabel)
                                .submittedAt(LocalDateTime.now())
                                .build())
                );
    }

    public boolean hasSubmitted(Long userId, Long communityId, String roundLabel) {
        return repository.existsByUserIdAndCommunityIdAndRoundLabel(userId, communityId, roundLabel);
    }

    public Map<Long, RoundSubmission> findStatusesForCommunityRound(Long communityId, String roundLabel) {
        return repository.findByCommunityIdAndRoundLabel(communityId, roundLabel)
                .stream()
                .collect(Collectors.toMap(RoundSubmission::getUserId, rs -> rs));
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=RoundSubmissionServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 4 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/RoundSubmissionService.java \
        src/test/java/com/worldcup/prediction/service/RoundSubmissionServiceTest.java
git commit -m "feat: add RoundSubmissionService with upsert, hasSubmitted, and status map"
```

---

## Task 4: Wire Submission Tracking into PredictionViewService

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/service/PredictionViewService.java`
- Create: `src/test/java/com/worldcup/prediction/service/PredictionViewServiceSubmissionTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.*;
import com.worldcup.prediction.dto.PredictionSubmitDto;
import com.worldcup.prediction.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictionViewServiceSubmissionTest {

    @Mock private MatchRepository matchRepository;
    @Mock private PredictionRepository predictionRepository;
    @Mock private UserRepository userRepository;
    @Mock private CommunityRepository communityRepository;
    @Mock private RoundWindowService roundWindowService;
    @Mock private RoundSubmissionService roundSubmissionService;

    private PredictionViewService service;

    private static final Long USER_ID = 1L;
    private static final Long COMMUNITY_ID = 10L;
    private static final String ROUND = "Matchday 1";

    @BeforeEach
    void setUp() {
        service = new PredictionViewService(matchRepository, predictionRepository,
                userRepository, communityRepository, roundWindowService, roundSubmissionService);
        // set timezone via reflection since @Value cannot inject in unit tests
        try {
            var f = PredictionViewService.class.getDeclaredField("timezoneId");
            f.setAccessible(true);
            f.set(service, "UTC");
            var init = PredictionViewService.class.getDeclaredMethod("initFormatters");
            init.setAccessible(true);
            init.invoke(service);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void submitPredictionsForRound_upsertsCalled_afterSavingPredictions() {
        LocalDateTime kickoff = LocalDateTime.now().plusHours(5);
        Match match = Match.builder().id(99L).kickoffTime(kickoff).roundLabel(ROUND).build();
        User user = User.builder().id(USER_ID).firstName("A").lastName("B").email("a@b.com").build();
        Community community = Community.builder().id(COMMUNITY_ID).build();

        when(matchRepository.findByRoundLabelWithTeams(ROUND)).thenReturn(List.of(match));
        when(roundWindowService.isRoundOpen(eq(ROUND), any())).thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
        when(predictionRepository.findByUserIdAndMatchIdAndCommunityId(USER_ID, 99L, COMMUNITY_ID))
                .thenReturn(Optional.empty());

        PredictionSubmitDto dto = new PredictionSubmitDto();
        dto.setRoundLabel(ROUND);
        PredictionSubmitDto.SinglePrediction sp = new PredictionSubmitDto.SinglePrediction();
        sp.setMatchId(99L);
        sp.setHomeScore(1);
        sp.setAwayScore(0);
        dto.setPredictions(List.of(sp));

        service.submitPredictionsForRound(USER_ID, dto, COMMUNITY_ID);

        verify(roundSubmissionService).upsert(USER_ID, COMMUNITY_ID, ROUND);
    }
}
```

- [ ] **Step 2: Run to confirm it fails**

```bash
./mvnw test -pl . -Dtest=PredictionViewServiceSubmissionTest -q 2>&1 | tail -5
```

Expected: compilation error — `PredictionViewService` constructor does not accept `RoundSubmissionService` yet.

- [ ] **Step 3: Add `RoundSubmissionService` to `PredictionViewService`**

In `PredictionViewService.java`, add the field to the constructor-injected fields (Lombok `@RequiredArgsConstructor` handles this automatically):

```java
// Add this field alongside the existing ones:
private final RoundSubmissionService roundSubmissionService;
```

Then add the upsert call at the end of `submitPredictionsForRound`, just before `return roundMatches.size();`:

```java
        roundSubmissionService.upsert(userId, communityId, dto.getRoundLabel());
        return roundMatches.size();
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=PredictionViewServiceSubmissionTest,RoundSubmissionServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 5 tests passing.

- [ ] **Step 5: Run full test suite to catch regressions**

```bash
./mvnw test -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/PredictionViewService.java \
        src/test/java/com/worldcup/prediction/service/PredictionViewServiceSubmissionTest.java
git commit -m "feat: upsert RoundSubmission after prediction submit"
```

---

## Task 5: WindowBannerDto and CommunityWindowBannerAdvice

**Files:**
- Create: `src/main/java/com/worldcup/prediction/dto/WindowBannerDto.java`
- Create: `src/main/java/com/worldcup/prediction/web/CommunityWindowBannerAdvice.java`
- Create: `src/test/java/com/worldcup/prediction/web/CommunityWindowBannerAdviceTest.java`

- [ ] **Step 1: Create `WindowBannerDto`**

```java
package com.worldcup.prediction.dto;

public record WindowBannerDto(
        String roundLabel,
        String closesAtIso,   // ISO-8601 with offset, null if FORCE_OPEN with no end time
        boolean submitted
) {}
```

- [ ] **Step 2: Write failing tests for the advice**

```java
package com.worldcup.prediction.web;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.dto.WindowBannerDto;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityWindowBannerAdviceTest {

    @Mock private RoundWindowService roundWindowService;
    @Mock private RoundSubmissionService roundSubmissionService;
    @Mock private HttpServletRequest request;
    @Mock private Authentication authentication;
    @Mock private CustomOAuth2User principal;

    private CommunityWindowBannerAdvice advice;

    private static final Long USER_ID = 7L;
    private static final Long COMMUNITY_ID = 3L;

    @BeforeEach
    void setUp() {
        advice = new CommunityWindowBannerAdvice(roundWindowService, roundSubmissionService);

        Community community = new Community();
        community.setId(COMMUNITY_ID);
        when(request.getAttribute("community")).thenReturn(community);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getRole()).thenReturn(UserRole.USER);
        when(principal.getUserId()).thenReturn(USER_ID);
    }

    @Test
    void returnsNull_whenNoOpenWindow() {
        when(roundWindowService.findAll()).thenReturn(List.of());

        WindowBannerDto result = advice.windowBanner(request, authentication);

        assertThat(result).isNull();
    }

    @Test
    void returnsNull_forSuperAdmin() {
        when(principal.getRole()).thenReturn(UserRole.SUPER_ADMIN);

        WindowBannerDto result = advice.windowBanner(request, authentication);

        assertThat(result).isNull();
        verifyNoInteractions(roundWindowService);
    }

    @Test
    void returnsBannerWithSubmittedTrue_whenUserHasSubmitted() {
        LocalDateTime now = LocalDateTime.now();
        RoundWindow rw = RoundWindow.builder()
                .roundLabel("Matchday 2")
                .autoOpensAt(now.minusHours(1))
                .autoClosesAt(now.plusHours(2))
                .build();
        when(roundWindowService.findAll()).thenReturn(List.of(rw));
        when(roundSubmissionService.hasSubmitted(USER_ID, COMMUNITY_ID, "Matchday 2")).thenReturn(true);

        WindowBannerDto result = advice.windowBanner(request, authentication);

        assertThat(result).isNotNull();
        assertThat(result.roundLabel()).isEqualTo("Matchday 2");
        assertThat(result.submitted()).isTrue();
        assertThat(result.closesAtIso()).isNotNull();
    }

    @Test
    void returnsBannerWithSubmittedFalse_whenUserHasNotSubmitted() {
        LocalDateTime now = LocalDateTime.now();
        RoundWindow rw = RoundWindow.builder()
                .roundLabel("Matchday 2")
                .autoOpensAt(now.minusHours(1))
                .autoClosesAt(now.plusHours(2))
                .build();
        when(roundWindowService.findAll()).thenReturn(List.of(rw));
        when(roundSubmissionService.hasSubmitted(USER_ID, COMMUNITY_ID, "Matchday 2")).thenReturn(false);

        WindowBannerDto result = advice.windowBanner(request, authentication);

        assertThat(result).isNotNull();
        assertThat(result.submitted()).isFalse();
    }

    @Test
    void returnsNullClosesAtIso_forForceOpenWindowWithNoAutoCloseTime() {
        RoundWindow rw = RoundWindow.builder()
                .roundLabel("Matchday 3")
                .overrideStatus(RoundOverrideStatus.FORCE_OPEN)
                .autoClosesAt(null)
                .build();
        when(roundWindowService.findAll()).thenReturn(List.of(rw));
        when(roundSubmissionService.hasSubmitted(anyLong(), anyLong(), anyString())).thenReturn(false);

        WindowBannerDto result = advice.windowBanner(request, authentication);

        assertThat(result).isNotNull();
        assertThat(result.closesAtIso()).isNull();
    }
}
```

- [ ] **Step 3: Run to confirm they fail**

```bash
./mvnw test -pl . -Dtest=CommunityWindowBannerAdviceTest -q 2>&1 | tail -5
```

Expected: compilation error — `CommunityWindowBannerAdvice` does not exist yet.

- [ ] **Step 4: Implement `CommunityWindowBannerAdvice`**

```java
package com.worldcup.prediction.web;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.RoundWindow;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.dto.WindowBannerDto;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@ControllerAdvice(basePackages = "com.worldcup.prediction.controller.community")
@RequiredArgsConstructor
public class CommunityWindowBannerAdvice {

    private final RoundWindowService roundWindowService;
    private final RoundSubmissionService roundSubmissionService;

    @Value("${app.timezone:UTC}")
    private String timezoneId;

    private ZoneId appZone;

    @PostConstruct
    private void init() {
        appZone = ZoneId.of(timezoneId);
    }

    @ModelAttribute("windowBanner")
    public WindowBannerDto windowBanner(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        if (!(authentication.getPrincipal() instanceof CustomOAuth2User user)) return null;
        if (user.getRole() == UserRole.SUPER_ADMIN) return null;

        Community community = (Community) request.getAttribute("community");
        if (community == null) return null;

        LocalDateTime now = LocalDateTime.now();
        return roundWindowService.findAll().stream()
                .filter(rw -> isOpen(rw, now))
                .findFirst()
                .map(rw -> {
                    String closesAtIso = rw.getAutoClosesAt() != null
                            ? rw.getAutoClosesAt().atZone(appZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            : null;
                    boolean submitted = roundSubmissionService.hasSubmitted(
                            user.getUserId(), community.getId(), rw.getRoundLabel());
                    return new WindowBannerDto(rw.getRoundLabel(), closesAtIso, submitted);
                })
                .orElse(null);
    }

    private boolean isOpen(RoundWindow rw, LocalDateTime now) {
        if (rw.getOverrideStatus() == RoundOverrideStatus.FORCE_OPEN) return true;
        if (rw.getOverrideStatus() == RoundOverrideStatus.FORCE_CLOSED) return false;
        if (rw.getAutoOpensAt() == null || rw.getAutoClosesAt() == null) return false;
        return !now.isBefore(rw.getAutoOpensAt()) && now.isBefore(rw.getAutoClosesAt());
    }
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=CommunityWindowBannerAdviceTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 5 tests passing.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/worldcup/prediction/dto/WindowBannerDto.java \
        src/main/java/com/worldcup/prediction/web/CommunityWindowBannerAdvice.java \
        src/test/java/com/worldcup/prediction/web/CommunityWindowBannerAdviceTest.java
git commit -m "feat: add CommunityWindowBannerAdvice to inject window status into community pages"
```

---

## Task 6: Countdown Banner in community-base.html

**Files:**
- Modify: `src/main/resources/templates/layout/community-base.html`

The banner slot goes between the `</nav>` closing tag and the `<main class="flex-1 pt-16">` opening tag.

- [ ] **Step 1: Add the countdown/confirmation banner**

Locate the line `<!-- PAGE CONTENT -->` followed by `<main class="flex-1 pt-16">` in `community-base.html`. Insert the following block immediately before `<main`:

```html
  <!-- Window banner: countdown if not submitted, confirmation if submitted -->
  <div th:if="${windowBanner != null}" class="relative z-40">
    <!-- Not submitted: show countdown -->
    <div th:if="${!windowBanner.submitted}"
         x-data="windowCountdown()"
         th:attr="x-init='start(\'' + ${windowBanner.closesAtIso} + '\')'"
         class="w-full bg-amber-500 text-white text-sm font-semibold px-4 py-2 flex items-center justify-center gap-3 flex-wrap">
      <span>⏱ <span th:text="${windowBanner.roundLabel}">Matchday</span> closes in</span>
      <span class="font-mono bg-black/20 px-2 py-0.5 rounded" x-text="formatted">--:--:--</span>
      <a th:href="@{'/c/' + ${slug} + '/predictions'}"
         class="underline hover:no-underline">Submit now →</a>
    </div>
    <!-- Submitted: show confirmation -->
    <div th:if="${windowBanner.submitted}"
         class="w-full bg-green-600 text-white text-sm font-semibold px-4 py-2 flex items-center justify-center gap-2">
      <span>✅ Predictions submitted for <span th:text="${windowBanner.roundLabel}">Matchday</span></span>
    </div>
  </div>

  <script>
    function windowCountdown() {
      return {
        formatted: '--:--:--',
        start(isoStr) {
          if (!isoStr || isoStr === 'null') return;
          const target = new Date(isoStr).getTime();
          const tick = () => {
            const diff = target - Date.now();
            if (diff <= 0) { this.formatted = '00:00:00'; return; }
            const h = Math.floor(diff / 3600000);
            const m = Math.floor((diff % 3600000) / 60000);
            const s = Math.floor((diff % 60000) / 1000);
            this.formatted = String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
            setTimeout(tick, 1000);
          };
          tick();
        }
      };
    }
  </script>
```

- [ ] **Step 2: Smoke-test in browser**

Start the app and visit a community home page while a round window is open. Verify the amber countdown bar appears and ticks down. Submit predictions and reload — verify the green confirmation bar replaces it.

```bash
./mvnw spring-boot:run 2>&1 | grep "Started\|ERROR" | head -5
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/layout/community-base.html
git commit -m "feat: add prediction window countdown/confirmation banner to community layout"
```

---

## Task 7: MemberSubmissionStatusDto and Super Admin Matchday Status Page

**Files:**
- Create: `src/main/java/com/worldcup/prediction/dto/MemberSubmissionStatusDto.java`
- Create: `src/main/java/com/worldcup/prediction/controller/admin/AdminMatchdayStatusController.java`
- Create: `src/test/java/com/worldcup/prediction/controller/admin/AdminMatchdayStatusControllerTest.java`
- Create: `src/main/resources/templates/admin/matchday-status.html`
- Modify: `src/main/resources/templates/admin/communities.html`

- [ ] **Step 1: Create `MemberSubmissionStatusDto`**

```java
package com.worldcup.prediction.dto;

import java.time.LocalDateTime;

public record MemberSubmissionStatusDto(
        Long userId,
        String displayName,
        String email,
        String avatarUrl,
        boolean submitted,
        LocalDateTime submittedAt
) {}
```

- [ ] **Step 2: Write failing tests for the admin controller**

```java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.*;
import com.worldcup.prediction.dto.MemberSubmissionStatusDto;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminMatchdayStatusControllerTest {

    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityMembershipRepository membershipRepository;
    @Mock private RoundWindowService roundWindowService;
    @Mock private RoundSubmissionService roundSubmissionService;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;

    private AdminMatchdayStatusController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminMatchdayStatusController(communityRepository, membershipRepository,
                roundWindowService, roundSubmissionService, userRepository, emailService);
    }

    @Test
    void statusPage_returnsCorrectModelAttributes() {
        Long communityId = 1L;
        String round = "Matchday 1";

        Community community = new Community();
        community.setId(communityId);
        community.setName("Test League");

        User submittedUser = User.builder().id(10L).firstName("Alice").lastName("Smith")
                .email("alice@example.com").build();
        User pendingUser = User.builder().id(20L).firstName("Bob").lastName("Jones")
                .email("bob@example.com").build();

        CommunityMembership m1 = new CommunityMembership();
        m1.setUser(submittedUser);
        CommunityMembership m2 = new CommunityMembership();
        m2.setUser(pendingUser);

        RoundWindow rw = RoundWindow.builder().roundLabel(round)
                .autoOpensAt(LocalDateTime.now().minusHours(1))
                .autoClosesAt(LocalDateTime.now().plusHours(3)).build();

        when(communityRepository.findById(communityId)).thenReturn(Optional.of(community));
        when(roundWindowService.findAll()).thenReturn(List.of(rw));
        when(membershipRepository.findByCommunityIdAndStatusWithUser(communityId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(m1, m2));

        com.worldcup.prediction.domain.RoundSubmission rs = com.worldcup.prediction.domain.RoundSubmission.builder()
                .userId(10L).communityId(communityId).roundLabel(round)
                .submittedAt(LocalDateTime.now().minusMinutes(30)).build();
        when(roundSubmissionService.findStatusesForCommunityRound(communityId, round))
                .thenReturn(Map.of(10L, rs));

        Model model = new ExtendedModelMap();
        String view = controller.statusPage(communityId, round, model);

        assertThat(view).isEqualTo("admin/matchday-status");

        @SuppressWarnings("unchecked")
        List<MemberSubmissionStatusDto> statuses = (List<MemberSubmissionStatusDto>) model.getAttribute("statuses");
        assertThat(statuses).hasSize(2);

        MemberSubmissionStatusDto alice = statuses.stream().filter(s -> s.userId().equals(10L)).findFirst().orElseThrow();
        assertThat(alice.submitted()).isTrue();

        MemberSubmissionStatusDto bob = statuses.stream().filter(s -> s.userId().equals(20L)).findFirst().orElseThrow();
        assertThat(bob.submitted()).isFalse();
    }

    @Test
    void remind_sendsEmailAndRedirects() {
        Long communityId = 1L;
        String round = "Matchday 1";
        Long userId = 10L;

        User user = User.builder().id(userId).firstName("Alice").lastName("Smith")
                .email("alice@example.com").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var redirectAttributes = new RedirectAttributesModelMap();
        String view = controller.remind(communityId, round, userId, redirectAttributes);

        verify(emailService).sendPredictionReminder(user, round);
        assertThat(view).isEqualTo("redirect:/admin/communities/" + communityId + "/matchday-status?round=" + round);
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("successMessage");
    }
}
```

- [ ] **Step 3: Run to confirm tests fail**

```bash
./mvnw test -pl . -Dtest=AdminMatchdayStatusControllerTest -q 2>&1 | tail -5
```

Expected: compilation error — controller does not exist.

- [ ] **Step 4: Implement `AdminMatchdayStatusController`**

```java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.dto.MemberSubmissionStatusDto;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/admin/communities/{communityId}/matchday-status")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminMatchdayStatusController {

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository membershipRepository;
    private final RoundWindowService roundWindowService;
    private final RoundSubmissionService roundSubmissionService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @GetMapping
    public String statusPage(@PathVariable Long communityId,
                             @RequestParam(required = false) String round,
                             Model model) {
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new IllegalArgumentException("Community not found: " + communityId));

        List<RoundWindow> allWindows = roundWindowService.findAll();

        String selectedRound = round;
        if (selectedRound == null) {
            selectedRound = allWindows.stream()
                    .filter(rw -> roundWindowService.isRoundOpen(rw.getRoundLabel(), java.time.LocalDateTime.now()))
                    .map(RoundWindow::getRoundLabel)
                    .findFirst()
                    .orElseGet(() -> allWindows.isEmpty() ? null : allWindows.get(0).getRoundLabel());
        }

        String closesAtIso = null;
        if (selectedRound != null) {
            final String finalRound = selectedRound;
            closesAtIso = allWindows.stream()
                    .filter(rw -> rw.getRoundLabel().equals(finalRound) && rw.getAutoClosesAt() != null)
                    .map(rw -> rw.getAutoClosesAt()
                            .atZone(ZoneId.of("UTC"))
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .findFirst().orElse(null);
        }

        List<MemberSubmissionStatusDto> statuses = List.of();
        if (selectedRound != null) {
            List<CommunityMembership> members = membershipRepository
                    .findByCommunityIdAndStatusWithUser(communityId, MembershipStatus.ACTIVE);
            Map<Long, RoundSubmission> submissionMap =
                    roundSubmissionService.findStatusesForCommunityRound(communityId, selectedRound);
            final String finalRound = selectedRound;
            statuses = members.stream()
                    .map(m -> {
                        RoundSubmission rs = submissionMap.get(m.getUser().getId());
                        return new MemberSubmissionStatusDto(
                                m.getUser().getId(), m.getUser().getFullName(),
                                m.getUser().getEmail(), m.getUser().getAvatarUrl(),
                                rs != null, rs != null ? rs.getSubmittedAt() : null);
                    })
                    .sorted(Comparator.comparing(MemberSubmissionStatusDto::submitted))
                    .toList();
        }

        model.addAttribute("community", community);
        model.addAttribute("allWindows", allWindows);
        model.addAttribute("selectedRound", selectedRound);
        model.addAttribute("closesAtIso", closesAtIso);
        model.addAttribute("statuses", statuses);
        model.addAttribute("communityId", communityId);
        return "admin/matchday-status";
    }

    @PostMapping("/{userId}/remind")
    public String remind(@PathVariable Long communityId,
                         @RequestParam String round,
                         @PathVariable Long userId,
                         RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        emailService.sendPredictionReminder(user, round);
        redirectAttributes.addFlashAttribute("successMessage",
                "Reminder sent to " + user.getFullName());
        return "redirect:/admin/communities/" + communityId + "/matchday-status?round=" + round;
    }
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=AdminMatchdayStatusControllerTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 2 tests passing.

- [ ] **Step 6: Create the admin template `admin/matchday-status.html`**

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{admin/layout}">
<head><title>Matchday Status</title></head>
<body>

<th:block layout:fragment="page-title">Matchday Submission Status</th:block>

<th:block layout:fragment="content">
  <div class="space-y-6">

    <!-- Header -->
    <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
      <h2 class="text-lg font-semibold text-gray-800 mb-1"
          th:text="${community.name} + ' · Submission Status'">Community</h2>

      <!-- Round selector -->
      <form method="get" class="flex items-center gap-3 mt-4 flex-wrap">
        <label class="text-sm font-medium text-gray-700">Matchday:</label>
        <select name="round"
                onchange="this.form.submit()"
                class="rounded-lg border-gray-300 text-sm px-3 py-2 border">
          <option value="" th:if="${selectedRound == null}">Select round…</option>
          <option th:each="rw : ${allWindows}"
                  th:value="${rw.roundLabel}"
                  th:text="${rw.roundLabel}"
                  th:selected="${rw.roundLabel == selectedRound}">Round</option>
        </select>
      </form>

      <!-- Countdown (only when window is currently open) -->
      <div th:if="${closesAtIso != null}"
           x-data="adminCountdown()"
           th:attr="x-init='start(\'' + ${closesAtIso} + '\')'"
           class="mt-4 inline-flex items-center gap-2 bg-amber-50 border border-amber-200 text-amber-800 text-sm font-semibold px-4 py-2 rounded-lg">
        <span>⏱ Window closes in</span>
        <span class="font-mono" x-text="formatted">--:--:--</span>
      </div>
    </div>

    <!-- Flash messages -->
    <div th:if="${successMessage}" class="p-3 bg-green-100 border border-green-300 text-green-800 rounded-lg text-sm"
         th:text="${successMessage}"></div>

    <!-- Member status table -->
    <div th:if="${selectedRound != null}" class="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
      <div class="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
        <h3 class="text-sm font-semibold text-gray-700">
          <span th:text="${selectedRound}">Round</span> ·
          <span th:text="${#lists.size(statuses)}">0</span> members
        </h3>
        <div class="flex gap-4 text-xs font-medium">
          <span class="text-green-700">✅ Submitted: <span th:text="${statuses.stream().filter(s -> s.submitted()).count()}">0</span></span>
          <span class="text-red-600">⏳ Pending: <span th:text="${statuses.stream().filter(s -> !s.submitted()).count()}">0</span></span>
        </div>
      </div>
      <table class="w-full text-sm">
        <thead class="bg-gray-50 border-b border-gray-100">
          <tr>
            <th class="px-4 py-3 text-left font-semibold text-gray-600">Member</th>
            <th class="px-4 py-3 text-center font-semibold text-gray-600">Status</th>
            <th class="px-4 py-3 text-center font-semibold text-gray-600">Submitted At</th>
            <th class="px-4 py-3 text-right font-semibold text-gray-600">Action</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr th:each="s : ${statuses}" class="hover:bg-gray-50">
            <td class="px-4 py-3">
              <div class="flex items-center gap-2">
                <img th:src="${s.avatarUrl()}"
                     th:alt="${s.displayName()}"
                     class="w-8 h-8 rounded-full object-cover border border-gray-200"
                     onerror="this.src='https://ui-avatars.com/api/?name='+encodeURIComponent(this.alt)+'&background=006b2a&color=fff'"/>
                <div>
                  <div class="font-semibold text-gray-800" th:text="${s.displayName()}">Name</div>
                  <div class="text-xs text-gray-400" th:text="${s.email()}">email</div>
                </div>
              </div>
            </td>
            <td class="px-4 py-3 text-center">
              <span th:if="${s.submitted()}" class="px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-700">✅ Submitted</span>
              <span th:unless="${s.submitted()}" class="px-2 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-700">⏳ Pending</span>
            </td>
            <td class="px-4 py-3 text-center text-xs text-gray-500"
                th:text="${s.submittedAt() != null ? #temporals.format(s.submittedAt(), 'dd MMM · HH:mm') : '—'}">—</td>
            <td class="px-4 py-3 text-right">
              <form th:unless="${s.submitted()}"
                    th:action="@{'/admin/communities/' + ${communityId} + '/matchday-status/' + ${s.userId()} + '/remind'}"
                    method="post" class="inline">
                <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
                <input type="hidden" name="round" th:value="${selectedRound}"/>
                <button type="submit"
                        class="px-3 py-1.5 rounded text-xs font-semibold bg-blue-100 text-blue-700 hover:bg-blue-200 transition-colors">
                  Send Reminder
                </button>
              </form>
              <span th:if="${s.submitted()}" class="text-xs text-gray-300">—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div th:unless="${selectedRound != null}" class="text-sm text-gray-500">Select a matchday to view submission status.</div>

  </div>

  <script>
    function adminCountdown() {
      return {
        formatted: '--:--:--',
        start(isoStr) {
          if (!isoStr || isoStr === 'null') return;
          const target = new Date(isoStr).getTime();
          const tick = () => {
            const diff = target - Date.now();
            if (diff <= 0) { this.formatted = '00:00:00'; return; }
            const h = Math.floor(diff / 3600000);
            const m = Math.floor((diff % 3600000) / 60000);
            const s = Math.floor((diff % 60000) / 1000);
            this.formatted = String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
            setTimeout(tick, 1000);
          };
          tick();
        }
      };
    }
  </script>
</th:block>
</body>
</html>
```

- [ ] **Step 7: Add "Matchday Status" link to `admin/communities.html`**

Locate the actions cell for each community row. Find the line:
```html
<a th:href="@{'/c/' + ${c.slug} + '/admin/members'}" class="px-2 py-1 rounded text-xs font-semibold bg-blue-100 text-blue-700 hover:bg-blue-200">Members</a>
```

Add immediately after it:
```html
<a th:href="@{'/admin/communities/' + ${c.id} + '/matchday-status'}" class="px-2 py-1 rounded text-xs font-semibold bg-purple-100 text-purple-700 hover:bg-purple-200">Matchday Status</a>
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/worldcup/prediction/dto/MemberSubmissionStatusDto.java \
        src/main/java/com/worldcup/prediction/controller/admin/AdminMatchdayStatusController.java \
        src/test/java/com/worldcup/prediction/controller/admin/AdminMatchdayStatusControllerTest.java \
        src/main/resources/templates/admin/matchday-status.html \
        src/main/resources/templates/admin/communities.html
git commit -m "feat: add super admin matchday submission status page with reminder action"
```

---

## Task 8: Community Admin Submission Status Page

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/community/CommunityAdminSubmissionController.java`
- Create: `src/test/java/com/worldcup/prediction/controller/community/CommunityAdminSubmissionControllerTest.java`
- Create: `src/main/resources/templates/community/admin/submission-status.html`
- Modify: `src/main/resources/templates/layout/community-base.html` (nav links — 3 places)

- [ ] **Step 1: Write failing tests**

```java
package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.dto.MemberSubmissionStatusDto;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityAdminSubmissionControllerTest {

    @Mock private CommunityMembershipRepository membershipRepository;
    @Mock private RoundWindowService roundWindowService;
    @Mock private RoundSubmissionService roundSubmissionService;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private HttpServletRequest request;

    private CommunityAdminSubmissionController controller;

    @BeforeEach
    void setUp() {
        controller = new CommunityAdminSubmissionController(membershipRepository,
                roundWindowService, roundSubmissionService, userRepository, emailService);
    }

    @Test
    void statusPage_populatesModelCorrectly() {
        Community community = new Community();
        community.setId(5L);
        community.setName("My League");
        when(request.getAttribute("community")).thenReturn(community);

        RoundWindow rw = RoundWindow.builder().roundLabel("Matchday 1")
                .autoOpensAt(LocalDateTime.now().minusHours(1))
                .autoClosesAt(LocalDateTime.now().plusHours(2)).build();
        when(roundWindowService.findAll()).thenReturn(List.of(rw));

        User user = User.builder().id(1L).firstName("Ana").lastName("Doe").email("ana@example.com").build();
        CommunityMembership m = new CommunityMembership();
        m.setUser(user);
        when(membershipRepository.findByCommunityIdAndStatusWithUser(5L, MembershipStatus.ACTIVE))
                .thenReturn(List.of(m));
        when(roundSubmissionService.findStatusesForCommunityRound(5L, "Matchday 1"))
                .thenReturn(Map.of());

        Model model = new ExtendedModelMap();
        String view = controller.statusPage("my-league", "Matchday 1", request, model);

        assertThat(view).isEqualTo("community/admin/submission-status");
        @SuppressWarnings("unchecked")
        List<MemberSubmissionStatusDto> statuses = (List<MemberSubmissionStatusDto>) model.getAttribute("statuses");
        assertThat(statuses).hasSize(1);
        assertThat(statuses.get(0).submitted()).isFalse();
    }

    @Test
    void remind_sendsEmailAndRedirects() {
        User user = User.builder().id(3L).firstName("Ana").lastName("Doe").email("ana@example.com").build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        var redirectAttributes = new RedirectAttributesModelMap();
        String view = controller.remind("my-league", "Matchday 1", 3L, redirectAttributes);

        verify(emailService).sendPredictionReminder(user, "Matchday 1");
        assertThat(view).isEqualTo("redirect:/c/my-league/admin/submission-status?round=Matchday 1");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("successMessage");
    }
}
```

- [ ] **Step 2: Run to confirm tests fail**

```bash
./mvnw test -pl . -Dtest=CommunityAdminSubmissionControllerTest -q 2>&1 | tail -5
```

Expected: compilation error.

- [ ] **Step 3: Implement `CommunityAdminSubmissionController`**

```java
package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.dto.MemberSubmissionStatusDto;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.service.RoundSubmissionService;
import com.worldcup.prediction.service.RoundWindowService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/c/{slug}/admin/submission-status")
@RequiredArgsConstructor
public class CommunityAdminSubmissionController {

    private final CommunityMembershipRepository membershipRepository;
    private final RoundWindowService roundWindowService;
    private final RoundSubmissionService roundSubmissionService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @GetMapping
    public String statusPage(@PathVariable String slug,
                             @RequestParam(required = false) String round,
                             HttpServletRequest request,
                             Model model) {
        Community community = (Community) request.getAttribute("community");
        Long communityId = community.getId();

        List<RoundWindow> allWindows = roundWindowService.findAll();

        String selectedRound = round;
        if (selectedRound == null) {
            selectedRound = allWindows.stream()
                    .filter(rw -> roundWindowService.isRoundOpen(rw.getRoundLabel(), LocalDateTime.now()))
                    .map(RoundWindow::getRoundLabel)
                    .findFirst()
                    .orElseGet(() -> allWindows.isEmpty() ? null : allWindows.get(0).getRoundLabel());
        }

        String closesAtIso = null;
        if (selectedRound != null) {
            final String finalRound = selectedRound;
            closesAtIso = allWindows.stream()
                    .filter(rw -> rw.getRoundLabel().equals(finalRound) && rw.getAutoClosesAt() != null)
                    .map(rw -> rw.getAutoClosesAt()
                            .atZone(ZoneId.of("UTC"))
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .findFirst().orElse(null);
        }

        List<MemberSubmissionStatusDto> statuses = List.of();
        if (selectedRound != null) {
            List<CommunityMembership> members = membershipRepository
                    .findByCommunityIdAndStatusWithUser(communityId, MembershipStatus.ACTIVE);
            Map<Long, RoundSubmission> submissionMap =
                    roundSubmissionService.findStatusesForCommunityRound(communityId, selectedRound);
            final String finalRound = selectedRound;
            statuses = members.stream()
                    .map(m -> {
                        RoundSubmission rs = submissionMap.get(m.getUser().getId());
                        return new MemberSubmissionStatusDto(
                                m.getUser().getId(), m.getUser().getFullName(),
                                m.getUser().getEmail(), m.getUser().getAvatarUrl(),
                                rs != null, rs != null ? rs.getSubmittedAt() : null);
                    })
                    .sorted(Comparator.comparing(MemberSubmissionStatusDto::submitted))
                    .toList();
        }

        model.addAttribute("community", community);
        model.addAttribute("slug", slug);
        model.addAttribute("allWindows", allWindows);
        model.addAttribute("selectedRound", selectedRound);
        model.addAttribute("closesAtIso", closesAtIso);
        model.addAttribute("statuses", statuses);
        model.addAttribute("pageTitle", community.getName() + " · Submission Status");
        return "community/admin/submission-status";
    }

    @PostMapping("/{userId}/remind")
    public String remind(@PathVariable String slug,
                         @RequestParam String round,
                         @PathVariable Long userId,
                         RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        emailService.sendPredictionReminder(user, round);
        redirectAttributes.addFlashAttribute("successMessage",
                "Reminder sent to " + user.getFullName());
        return "redirect:/c/" + slug + "/admin/submission-status?round=" + round;
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=CommunityAdminSubmissionControllerTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 2 tests passing.

- [ ] **Step 5: Create community admin template `community/admin/submission-status.html`**

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/community-base :: layout(~{::main-content})}">
<head><title>Submission Status</title></head>
<body>
<th:block th:fragment="main-content">

  <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-10">

    <div class="mb-6">
      <h1 class="font-display text-4xl text-white tracking-wider mb-1">SUBMISSION STATUS</h1>
      <p class="text-white/60 text-sm font-medium" th:text="${community.name} + ' · Admin'">Community Admin</p>
    </div>

    <!-- Flash messages -->
    <div th:if="${successMessage}" class="mb-4 p-3 bg-green-100 border border-green-300 text-green-800 rounded-lg text-sm" th:text="${successMessage}"></div>

    <!-- Round selector + countdown -->
    <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-5 mb-6">
      <form method="get" class="flex items-center gap-3 flex-wrap">
        <label class="text-sm font-semibold text-gray-700">Matchday:</label>
        <select name="round" onchange="this.form.submit()"
                class="rounded-xl border border-gray-200 text-sm px-3 py-2 focus:outline-none focus:ring-2 focus:ring-green-400">
          <option value="" th:if="${selectedRound == null}">Select round…</option>
          <option th:each="rw : ${allWindows}"
                  th:value="${rw.roundLabel}"
                  th:text="${rw.roundLabel}"
                  th:selected="${rw.roundLabel == selectedRound}">Round</option>
        </select>
      </form>

      <div th:if="${closesAtIso != null}"
           x-data="communityAdminCountdown()"
           th:attr="x-init='start(\'' + ${closesAtIso} + '\')'"
           class="mt-4 inline-flex items-center gap-2 bg-amber-50 border border-amber-200 text-amber-800 text-sm font-semibold px-4 py-2 rounded-xl">
        <span>⏱ Window closes in</span>
        <span class="font-mono" x-text="formatted">--:--:--</span>
      </div>
    </div>

    <!-- Summary row -->
    <div th:if="${selectedRound != null}" class="flex gap-4 mb-4 flex-wrap">
      <div class="bg-green-100 text-green-800 text-sm font-semibold px-4 py-2 rounded-xl">
        ✅ Submitted: <span th:text="${statuses.stream().filter(s -> s.submitted()).count()}">0</span>
      </div>
      <div class="bg-amber-100 text-amber-800 text-sm font-semibold px-4 py-2 rounded-xl">
        ⏳ Pending: <span th:text="${statuses.stream().filter(s -> !s.submitted()).count()}">0</span>
      </div>
    </div>

    <!-- Member table -->
    <div th:if="${selectedRound != null}" class="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-green-dark text-white">
          <tr>
            <th class="px-4 py-3 text-left font-semibold">Member</th>
            <th class="px-4 py-3 text-center font-semibold">Status</th>
            <th class="px-4 py-3 text-center font-semibold">Submitted At</th>
            <th class="px-4 py-3 text-right font-semibold">Action</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          <tr th:each="s : ${statuses}" class="hover:bg-green-light/20">
            <td class="px-4 py-3">
              <div class="flex items-center gap-2">
                <img th:src="${s.avatarUrl()}" th:alt="${s.displayName()}"
                     class="w-8 h-8 rounded-full object-cover border border-gray-200"
                     onerror="this.src='https://ui-avatars.com/api/?name='+encodeURIComponent(this.alt)+'&background=006b2a&color=fff'"/>
                <div>
                  <div class="font-semibold text-gray-800" th:text="${s.displayName()}">Name</div>
                  <div class="text-xs text-gray-400" th:text="${s.email()}">email</div>
                </div>
              </div>
            </td>
            <td class="px-4 py-3 text-center">
              <span th:if="${s.submitted()}" class="px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-700">✅ Submitted</span>
              <span th:unless="${s.submitted()}" class="px-2 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-700">⏳ Pending</span>
            </td>
            <td class="px-4 py-3 text-center text-xs text-gray-500"
                th:text="${s.submittedAt() != null ? #temporals.format(s.submittedAt(), 'dd MMM · HH:mm') : '—'}">—</td>
            <td class="px-4 py-3 text-right">
              <form th:unless="${s.submitted()}"
                    th:action="@{'/c/' + ${slug} + '/admin/submission-status/' + ${s.userId()} + '/remind'}"
                    method="post" class="inline">
                <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
                <input type="hidden" name="round" th:value="${selectedRound}"/>
                <button type="submit"
                        class="px-3 py-1.5 rounded-lg text-xs font-semibold bg-blue-100 text-blue-700 hover:bg-blue-200 transition-colors">
                  Send Reminder
                </button>
              </form>
              <span th:if="${s.submitted()}" class="text-xs text-gray-300">—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div th:unless="${selectedRound != null}" class="text-white/60 text-sm">Select a matchday to view submission status.</div>
  </div>

  <script>
    function communityAdminCountdown() {
      return {
        formatted: '--:--:--',
        start(isoStr) {
          if (!isoStr || isoStr === 'null') return;
          const target = new Date(isoStr).getTime();
          const tick = () => {
            const diff = target - Date.now();
            if (diff <= 0) { this.formatted = '00:00:00'; return; }
            const h = Math.floor(diff / 3600000);
            const m = Math.floor((diff % 3600000) / 60000);
            const s = Math.floor((diff % 60000) / 1000);
            this.formatted = String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
            setTimeout(tick, 1000);
          };
          tick();
        }
      };
    }
  </script>

</th:block>
</body>
</html>
```

- [ ] **Step 6: Add nav links to `community-base.html` in 3 places**

**Location 1** — Desktop nav bar (after the existing `Members` link, around line 117):

Find:
```html
          <a th:if="${communityMembership != null && communityMembership.role.name() == 'ADMIN'}"
             th:href="@{'/c/' + ${slug} + '/admin/members'}"
             class="px-4 py-2 rounded-lg text-sm font-semibold text-amber-300 hover:text-white hover:bg-white/10 transition-all duration-200">👥 Members</a>
```

Add immediately after:
```html
          <a th:if="${communityMembership != null && communityMembership.role.name() == 'ADMIN'}"
             th:href="@{'/c/' + ${slug} + '/admin/submission-status'}"
             class="px-4 py-2 rounded-lg text-sm font-semibold text-amber-300 hover:text-white hover:bg-white/10 transition-all duration-200">📊 Submissions</a>
```

**Location 2** — Dropdown menu (after the existing `Manage Members` link, around line 142):

Find:
```html
                <a th:if="${communityMembership != null && communityMembership.role.name() == 'ADMIN'}"
                   th:href="@{'/c/' + ${slug} + '/admin/members'}"
                   class="flex items-center gap-2 px-4 py-2.5 text-sm text-amber-700 hover:bg-amber-50 transition-colors">👥 Manage Members</a>
```

Add immediately after:
```html
                <a th:if="${communityMembership != null && communityMembership.role.name() == 'ADMIN'}"
                   th:href="@{'/c/' + ${slug} + '/admin/submission-status'}"
                   class="flex items-center gap-2 px-4 py-2.5 text-sm text-amber-700 hover:bg-amber-50 transition-colors">📊 Submissions</a>
```

**Location 3** — Mobile menu (after the existing `Manage Members` link, around line 173):

Find:
```html
        <a th:if="${communityMembership != null && communityMembership.role.name() == 'ADMIN'}"
           th:href="@{'/c/' + ${slug} + '/admin/members'}"
           class="block px-4 py-2.5 rounded-lg text-sm font-semibold text-amber-300 hover:text-white hover:bg-white/10 transition-colors">👥 Manage Members</a>
```

Add immediately after:
```html
        <a th:if="${communityMembership != null && communityMembership.role.name() == 'ADMIN'}"
           th:href="@{'/c/' + ${slug} + '/admin/submission-status'}"
           class="block px-4 py-2.5 rounded-lg text-sm font-semibold text-amber-300 hover:text-white hover:bg-white/10 transition-colors">📊 Submissions</a>
```

- [ ] **Step 7: Run full test suite**

```bash
./mvnw test -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/community/CommunityAdminSubmissionController.java \
        src/test/java/com/worldcup/prediction/controller/community/CommunityAdminSubmissionControllerTest.java \
        src/main/resources/templates/community/admin/submission-status.html \
        src/main/resources/templates/layout/community-base.html
git commit -m "feat: add community admin submission status page with reminder action and nav links"
```

---

## Summary

| Task | Deliverable |
|---|---|
| 1 | Flyway migration creates `round_submissions` table and backfills production data |
| 2 | `RoundSubmission` JPA entity + `RoundSubmissionRepository` |
| 3 | `RoundSubmissionService` with upsert, hasSubmitted, and status map |
| 4 | `PredictionViewService` calls `upsert` after saving predictions |
| 5 | `CommunityWindowBannerAdvice` injects open-window + submission state into every community page |
| 6 | `community-base.html` renders countdown or confirmation banner |
| 7 | Super Admin matchday status page + remind action + link from communities list |
| 8 | Community Admin submission status page + remind action + 3 nav links |
