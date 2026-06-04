# Multi-Community Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the single-tenant prediction game into a multi-community platform where users belong to communities with independent predictions and leaderboards, governed by a SUPER_ADMIN/COMMUNITY_ADMIN/USER role hierarchy.

**Architecture:** New `Community` and `CommunityMembership` entities introduce multi-tenancy. Predictions, leaderboards, and notifications become community-scoped via a `community_id` FK. URL routing uses `/c/{slug}/` prefix for community pages. Super Admin bootstraps via form-based login from `application.properties`; all other users remain OAuth2.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Security (form + OAuth2), Spring Data JPA, Flyway, Thymeleaf + HTMX + Alpine.js + Tailwind CSS, BCrypt (via `spring-boot-starter-security`), SQLite/PostgreSQL.

---

## File Map

### New Files

| File | Responsibility |
|---|---|
| `domain/Community.java` | Community JPA entity |
| `domain/CommunityMembership.java` | Join table: user <-> community with role, status, stats |
| `domain/enums/CommunityRole.java` | `ADMIN`, `MEMBER` |
| `domain/enums/MembershipStatus.java` | `PENDING`, `ACTIVE`, `DISABLED` |
| `repository/CommunityRepository.java` | Community CRUD + slug lookup |
| `repository/CommunityMembershipRepository.java` | Membership queries |
| `service/CommunityService.java` | Community CRUD, membership management, slug resolution |
| `security/CommunityInterceptor.java` | Resolves community from URL slug, verifies membership |
| `security/SuperAdminBootstrap.java` | `CommandLineRunner` to seed SUPER_ADMIN on first startup |
| `security/SuperAdminAuthenticationProvider.java` | Custom `AuthenticationProvider` for form-based Super Admin login |
| `controller/CommunityController.java` | Community selector page, join request |
| `controller/community/CommunityHomeController.java` | `/c/{slug}/home` |
| `controller/community/CommunityPredictionController.java` | `/c/{slug}/predictions` |
| `controller/community/CommunityLeaderboardController.java` | `/c/{slug}/leaderboard` |
| `controller/community/CommunityAdminDashboardController.java` | `/c/{slug}/admin/dashboard` |
| `controller/community/CommunityAdminMemberController.java` | `/c/{slug}/admin/members` |
| `controller/community/CommunityAdminPredictionController.java` | `/c/{slug}/admin/predictions` |
| `controller/community/CommunityAdminNotificationController.java` | `/c/{slug}/admin/notifications` |
| `controller/admin/AdminCommunityController.java` | Super Admin community CRUD |
| `controller/admin/AdminSettingsController.java` | Super Admin password change |
| `config/WebMvcConfig.java` | Register `CommunityInterceptor` |
| `db/migration/V5__multi_community.sql` | Schema migration |
| `templates/communities.html` | Community selector page |
| `templates/community/home.html` | Community home |
| `templates/community/predictions.html` | Community predictions |
| `templates/community/leaderboard.html` | Community leaderboard |
| `templates/community/admin/dashboard.html` | Community admin dashboard |
| `templates/community/admin/members.html` | Community admin members |
| `templates/community/admin/predictions.html` | Community admin predictions |
| `templates/community/admin/notifications.html` | Community admin notifications |
| `templates/community/admin/layout.html` | Community admin layout |
| `templates/admin/communities.html` | Super Admin community management |
| `templates/admin/settings.html` | Super Admin password change |
| `templates/layout/community-base.html` | Base layout with community header dropdown |

### Modified Files

| File | Changes |
|---|---|
| `domain/enums/UserRole.java` | `PARTICIPANT, ADMIN` -> `SUPER_ADMIN, USER` |
| `domain/User.java` | Add `passwordHash`, remove stat columns |
| `domain/Prediction.java` | Add `community` ManyToOne + `community_id` FK |
| `domain/TournamentWinnerPrediction.java` | Add `community` ManyToOne + `community_id` FK |
| `domain/Invitation.java` | Add `community` ManyToOne + `community_id` FK |
| `domain/NotificationLog.java` | Add `community` ManyToOne + `community_id` FK (nullable) |
| `repository/PredictionRepository.java` | Add community-scoped query methods |
| `repository/UserRepository.java` | Remove leaderboard queries that reference stat columns |
| `repository/TournamentWinnerPredictionRepository.java` | Add community-scoped queries |
| `service/LeaderboardService.java` | Add `communityId` parameter to all methods |
| `service/PredictionService.java` | Add `communityId` parameter to submission and retrieval |
| `service/PredictionViewService.java` | Add `communityId` parameter throughout |
| `service/TournamentWinnerPredictionService.java` | Add `communityId` parameter |
| `service/UserService.java` | Community-scoped approve/reject, remove stat-based methods |
| `service/MatchAdminService.java` | `scoreAllPredictions` scores across all communities, updates membership stats |
| `service/NotificationService.java` | Add `communityId` to all notification methods |
| `config/SecurityConfig.java` | Add `formLogin()`, split `/admin/**` vs `/c/{slug}/**` auth rules |
| `security/CustomOAuth2User.java` | Add `getAuthorities()` for `ROLE_USER` / `ROLE_SUPER_ADMIN` |
| `security/CustomOAuth2UserService.java` | New users get `UserRole.USER`, invitation includes community |
| `security/OAuth2AuthenticationSuccessHandler.java` | Redirect to `/communities` or `/c/{slug}/home` based on memberships |
| `security/AccountStatusFilter.java` | Add community membership check for `/c/{slug}/**` URLs |
| `controller/LandingController.java` | Redirect logic updated for communities |
| `controller/LoginController.java` | Support form login error params |
| `controller/admin/AdminDashboardController.java` | `@PreAuthorize("hasRole('SUPER_ADMIN')")`, add community stats |
| `controller/admin/AdminUserController.java` | `hasRole('SUPER_ADMIN')` |
| `controller/admin/AdminMatchController.java` | `hasRole('SUPER_ADMIN')` |
| `controller/admin/AdminSyncController.java` | `hasRole('SUPER_ADMIN')` |
| `controller/admin/AdminPredictionController.java` | Remove (replaced by community admin) |
| `controller/admin/AdminNotificationController.java` | Remove (replaced by community admin) |
| `controller/HomeController.java` | Remove (replaced by community home) |
| `controller/PredictionController.java` | Remove (replaced by community predictions) |
| `controller/LeaderboardController.java` | Remove community-specific logic (keep public fixture-only) |
| `templates/login.html` | Add Super Admin form login section |
| `templates/layout/base.html` | Add community dropdown to navbar |
| `templates/admin/dashboard.html` | Update for SUPER_ADMIN, show community list |
| `application.properties` | Add super-admin bootstrap config |
| Tests: multiple test files updated to match new enum values and service signatures |

---

## Task 1: Database Migration

**Files:**
- Create: `src/main/resources/db/migration/V5__multi_community.sql`

- [ ] **Step 1: Write the migration SQL**

```sql
-- =============================================================================
-- V5__multi_community.sql
-- Multi-community architecture: new tables, altered tables, data truncation
-- =============================================================================

-- Phase 1: Truncate community-related data (order matters for FK constraints)
DELETE FROM notification_log;
DELETE FROM invitations;
DELETE FROM tournament_winner_predictions;
DELETE FROM predictions;
DELETE FROM oauth_identities;
DELETE FROM audit_logs;
DELETE FROM users;

-- Phase 2: Create new tables

CREATE TABLE communities (
    id              INTEGER PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    slug            VARCHAR(50)  NOT NULL,
    description     VARCHAR(500),
    created_by_id   INTEGER REFERENCES users(id),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX communities_slug_idx ON communities(slug);

CREATE TABLE community_memberships (
    id                   INTEGER PRIMARY KEY,
    community_id         INTEGER     NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    user_id              INTEGER     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role                 VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    status               VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_points         INTEGER     NOT NULL DEFAULT 0,
    exact_score_count    INTEGER     NOT NULL DEFAULT 0,
    correct_winner_count INTEGER     NOT NULL DEFAULT 0,
    correct_draw_count   INTEGER     NOT NULL DEFAULT 0,
    joined_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(community_id, user_id)
);

CREATE INDEX community_memberships_user_id_idx ON community_memberships(user_id);
CREATE INDEX community_memberships_community_id_idx ON community_memberships(community_id);
CREATE INDEX community_memberships_status_idx ON community_memberships(status);

-- Phase 3: Alter users table
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);

-- Drop stat columns from users (SQLite: recreate without them is complex,
-- so we leave them as unused columns for SQLite compat; PostgreSQL can DROP COLUMN).
-- For now, columns remain but are no longer read by the application.

-- Phase 4: Add community_id to predictions
ALTER TABLE predictions ADD COLUMN community_id INTEGER REFERENCES communities(id);

-- Drop old unique constraint and add new one
-- SQLite doesn't support DROP CONSTRAINT, so we create a new index
-- The old unique index predictions_user_match_idx is from the JPA annotation
-- We'll rely on JPA entity-level constraint for the new unique (user_id, match_id, community_id)
CREATE UNIQUE INDEX predictions_user_match_community_idx ON predictions(user_id, match_id, community_id);

-- Phase 5: Add community_id to tournament_winner_predictions
ALTER TABLE tournament_winner_predictions ADD COLUMN community_id INTEGER REFERENCES communities(id);
CREATE UNIQUE INDEX twp_user_community_idx ON tournament_winner_predictions(user_id, community_id);

-- Phase 6: Add community_id to invitations
ALTER TABLE invitations ADD COLUMN community_id INTEGER REFERENCES communities(id);

-- Phase 7: Add community_id to notification_log
ALTER TABLE notification_log ADD COLUMN community_id INTEGER REFERENCES communities(id);
```

- [ ] **Step 2: Verify migration runs**

Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=sqlite`
Expected: Application starts without Flyway errors. Check logs for `Successfully applied 1 migration`.
Stop the application.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V5__multi_community.sql
git commit -m "feat: V5 migration — multi-community schema (tables, FKs, data truncation)"
```

---

## Task 2: New Enums and Domain Entities

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/domain/enums/UserRole.java`
- Create: `src/main/java/com/worldcup/prediction/domain/enums/CommunityRole.java`
- Create: `src/main/java/com/worldcup/prediction/domain/enums/MembershipStatus.java`
- Create: `src/main/java/com/worldcup/prediction/domain/Community.java`
- Create: `src/main/java/com/worldcup/prediction/domain/CommunityMembership.java`
- Modify: `src/main/java/com/worldcup/prediction/domain/User.java`
- Modify: `src/main/java/com/worldcup/prediction/domain/Prediction.java`
- Modify: `src/main/java/com/worldcup/prediction/domain/TournamentWinnerPrediction.java`
- Modify: `src/main/java/com/worldcup/prediction/domain/Invitation.java`
- Modify: `src/main/java/com/worldcup/prediction/domain/NotificationLog.java`

- [ ] **Step 1: Update UserRole enum**

Replace contents of `UserRole.java`:
```java
package com.worldcup.prediction.domain.enums;

public enum UserRole {
    SUPER_ADMIN,
    USER
}
```

- [ ] **Step 2: Create CommunityRole enum**

```java
package com.worldcup.prediction.domain.enums;

public enum CommunityRole {
    ADMIN,
    MEMBER
}
```

- [ ] **Step 3: Create MembershipStatus enum**

```java
package com.worldcup.prediction.domain.enums;

public enum MembershipStatus {
    PENDING,
    ACTIVE,
    DISABLED
}
```

- [ ] **Step 4: Create Community entity**

```java
package com.worldcup.prediction.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "communities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@ToString(exclude = {"memberships", "createdBy"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Community {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CommunityMembership> memberships = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 5: Create CommunityMembership entity**

```java
package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "community_memberships",
       uniqueConstraints = @UniqueConstraint(
               name = "community_memberships_community_user_idx",
               columnNames = {"community_id", "user_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@ToString(exclude = {"community", "user"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CommunityMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private CommunityRole role = CommunityRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.PENDING;

    @Column(name = "total_points", nullable = false)
    @Builder.Default
    private int totalPoints = 0;

    @Column(name = "exact_score_count", nullable = false)
    @Builder.Default
    private int exactScoreCount = 0;

    @Column(name = "correct_winner_count", nullable = false)
    @Builder.Default
    private int correctWinnerCount = 0;

    @Column(name = "correct_draw_count", nullable = false)
    @Builder.Default
    private int correctDrawCount = 0;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 6: Modify User entity**

Add `passwordHash` field. Change `isAdmin()` to check `SUPER_ADMIN`. Remove stat column references from any business logic (columns remain in DB for SQLite compat but are unused).

Add after the `avatarUrl` field:
```java
@Column(name = "password_hash")
private String passwordHash;
```

Change the `isAdmin()` method:
```java
public boolean isAdmin() {
    return role == UserRole.SUPER_ADMIN;
}

public boolean isSuperAdmin() {
    return role == UserRole.SUPER_ADMIN;
}
```

Change the default role:
```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 50)
private UserRole role = UserRole.USER;
```

- [ ] **Step 7: Modify Prediction entity**

Add after the `match` field:
```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "community_id", nullable = false)
private Community community;
```

Update the `@Table` uniqueConstraints:
```java
@Table(name = "predictions",
       uniqueConstraints = @UniqueConstraint(
               name = "predictions_user_match_community_idx",
               columnNames = {"user_id", "match_id", "community_id"}))
```

- [ ] **Step 8: Modify TournamentWinnerPrediction entity**

Add after the `team` field:
```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "community_id", nullable = false)
private Community community;
```

Update the `@Table` uniqueConstraints:
```java
@Table(name = "tournament_winner_predictions",
       uniqueConstraints = @UniqueConstraint(
               name = "twp_user_community_idx",
               columnNames = {"user_id", "community_id"}))
```

Remove the old `@OneToOne` on user and change to `@ManyToOne`:
```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id", nullable = false)
private User user;
```

- [ ] **Step 9: Modify Invitation entity**

Add after the `invitedBy` field:
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "community_id")
private Community community;
```

- [ ] **Step 10: Modify NotificationLog entity**

Add a community field (nullable):
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "community_id")
private Community community;
```

- [ ] **Step 11: Update User entity — remove old TournamentWinnerPrediction OneToOne**

The `User` entity has `@OneToOne(mappedBy = "user") private TournamentWinnerPrediction tournamentWinnerPrediction;`. Since tournament winner predictions are now per-community, change to:
```java
@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
@Builder.Default
private List<TournamentWinnerPrediction> tournamentWinnerPredictions = new ArrayList<>();
```

Update `@ToString(exclude = {...})` to include `"tournamentWinnerPredictions"` instead of `"tournamentWinnerPrediction"`.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/worldcup/prediction/domain/
git commit -m "feat: community domain model — Community, CommunityMembership, updated enums and entities"
```

---

## Task 3: Repositories

**Files:**
- Create: `src/main/java/com/worldcup/prediction/repository/CommunityRepository.java`
- Create: `src/main/java/com/worldcup/prediction/repository/CommunityMembershipRepository.java`
- Modify: `src/main/java/com/worldcup/prediction/repository/PredictionRepository.java`
- Modify: `src/main/java/com/worldcup/prediction/repository/UserRepository.java`
- Modify: `src/main/java/com/worldcup/prediction/repository/TournamentWinnerPredictionRepository.java`

- [ ] **Step 1: Create CommunityRepository**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {

    Optional<Community> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Community> findAllByOrderByNameAsc();
}
```

- [ ] **Step 2: Create CommunityMembershipRepository**

```java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityMembershipRepository extends JpaRepository<CommunityMembership, Long> {

    Optional<CommunityMembership> findByCommunityIdAndUserId(Long communityId, Long userId);

    boolean existsByCommunityIdAndUserId(Long communityId, Long userId);

    List<CommunityMembership> findByUserId(Long userId);

    List<CommunityMembership> findByUserIdAndStatus(Long userId, MembershipStatus status);

    List<CommunityMembership> findByCommunityIdAndStatus(Long communityId, MembershipStatus status);

    List<CommunityMembership> findByCommunityId(Long communityId);

    long countByCommunityIdAndStatus(Long communityId, MembershipStatus status);

    @Query("""
            SELECT cm FROM CommunityMembership cm
            JOIN FETCH cm.community
            WHERE cm.user.id = :userId AND cm.status = :status
            ORDER BY cm.community.name ASC
            """)
    List<CommunityMembership> findByUserIdAndStatusWithCommunity(
            @Param("userId") Long userId, @Param("status") MembershipStatus status);

    @Query("""
            SELECT cm FROM CommunityMembership cm
            JOIN FETCH cm.user
            WHERE cm.community.id = :communityId AND cm.status = :status
            ORDER BY cm.totalPoints DESC, cm.exactScoreCount DESC
            """)
    List<CommunityMembership> findByCommunityIdAndStatusWithUser(
            @Param("communityId") Long communityId, @Param("status") MembershipStatus status);

    Optional<CommunityMembership> findByCommunityIdAndUserIdAndRole(
            Long communityId, Long userId, CommunityRole role);
}
```

- [ ] **Step 3: Add community-scoped methods to PredictionRepository**

Add these methods to the existing `PredictionRepository`:
```java
Optional<Prediction> findByUserIdAndMatchIdAndCommunityId(Long userId, Long matchId, Long communityId);

@Query("SELECT p FROM Prediction p WHERE p.user.id = :userId AND p.match.id IN :matchIds AND p.community.id = :communityId")
List<Prediction> findByUserIdAndMatchIdInAndCommunityId(
        @Param("userId") Long userId,
        @Param("matchIds") java.util.Collection<Long> matchIds,
        @Param("communityId") Long communityId);

@Query("SELECT COUNT(p) FROM Prediction p WHERE p.user.id = :userId AND p.match.id IN :matchIds AND p.community.id = :communityId")
long countByUserIdAndMatchIdInAndCommunityId(
        @Param("userId") Long userId,
        @Param("matchIds") java.util.Collection<Long> matchIds,
        @Param("communityId") Long communityId);

List<Prediction> findByMatchIdAndCommunityId(Long matchId, Long communityId);

List<Prediction> findByUserIdAndCommunityId(Long userId, Long communityId);

boolean existsByUserIdAndMatchIdAndCommunityId(Long userId, Long matchId, Long communityId);

@Query("""
        SELECT p.user.id, p.match.stage, SUM(p.pointsAwarded)
        FROM Prediction p
        WHERE p.community.id = :communityId AND p.match.stage IS NOT NULL
        GROUP BY p.user.id, p.match.stage
        """)
List<Object[]> sumPointsByUserAndStageAndCommunityId(@Param("communityId") Long communityId);
```

- [ ] **Step 4: Update UserRepository**

Remove `findLeaderboard()` and `findTop10Leaderboard()` methods (they reference `u.totalPoints` which is no longer used). The existing `findByStatus`, `findByRole`, `countByStatus` methods remain.

Add:
```java
Optional<User> findByEmailAndRole(String email, UserRole role);
```

- [ ] **Step 5: Update TournamentWinnerPredictionRepository**

Add community-scoped methods (check existing file first for current methods, then add):
```java
Optional<TournamentWinnerPrediction> findByUserIdAndCommunityId(Long userId, Long communityId);

List<TournamentWinnerPrediction> findByCommunityId(Long communityId);

@Query("""
        SELECT twp FROM TournamentWinnerPrediction twp
        JOIN FETCH twp.user JOIN FETCH twp.team
        WHERE twp.community.id = :communityId
        """)
List<TournamentWinnerPrediction> findAllWithDetailsByCommunityId(@Param("communityId") Long communityId);

List<TournamentWinnerPrediction> findByTeamIdAndCommunityId(Long teamId, Long communityId);
```

- [ ] **Step 6: Compile check**

Run: `./mvnw compile -q`
Expected: Compilation succeeds (tests may fail — that's fine for now).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/worldcup/prediction/repository/
git commit -m "feat: community repositories + community-scoped prediction/TWP queries"
```

---

## Task 4: CommunityService

**Files:**
- Create: `src/main/java/com/worldcup/prediction/service/CommunityService.java`
- Test: `src/test/java/com/worldcup/prediction/service/CommunityServiceTest.java`

- [ ] **Step 1: Write tests for CommunityService**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityMembershipRepository membershipRepository;
    @Mock private UserRepository userRepository;

    private CommunityService communityService;

    private User superAdmin;
    private User regularUser;

    @BeforeEach
    void setUp() {
        communityService = new CommunityService(communityRepository, membershipRepository, userRepository);
        superAdmin = User.builder().id(1L).email("admin@test.com")
                .firstName("Super").lastName("Admin")
                .role(UserRole.SUPER_ADMIN).status(UserStatus.ACTIVE).build();
        regularUser = User.builder().id(2L).email("user@test.com")
                .firstName("Regular").lastName("User")
                .role(UserRole.USER).status(UserStatus.ACTIVE).build();
    }

    @Nested @DisplayName("createCommunity")
    class CreateCommunity {

        @Test @DisplayName("creates community with valid name and slug")
        void createsCommunity() {
            when(communityRepository.existsBySlug("acme-corp")).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(superAdmin));
            when(communityRepository.save(any(Community.class))).thenAnswer(inv -> {
                Community c = inv.getArgument(0);
                c.setId(10L);
                return c;
            });

            Community result = communityService.createCommunity("Acme Corp", "acme-corp", "A company group", 1L);

            assertThat(result.getName()).isEqualTo("Acme Corp");
            assertThat(result.getSlug()).isEqualTo("acme-corp");
            verify(communityRepository).save(any(Community.class));
        }

        @Test @DisplayName("throws when slug already exists")
        void throwsOnDuplicateSlug() {
            when(communityRepository.existsBySlug("acme-corp")).thenReturn(true);

            assertThatThrownBy(() -> communityService.createCommunity("Acme Corp", "acme-corp", null, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("slug");
        }
    }

    @Nested @DisplayName("addMember")
    class AddMember {

        @Test @DisplayName("adds member with PENDING status")
        void addsMember() {
            Community community = Community.builder().id(10L).name("Test").slug("test").build();
            when(communityRepository.findById(10L)).thenReturn(Optional.of(community));
            when(userRepository.findById(2L)).thenReturn(Optional.of(regularUser));
            when(membershipRepository.existsByCommunityIdAndUserId(10L, 2L)).thenReturn(false);
            when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CommunityMembership result = communityService.addMember(10L, 2L, CommunityRole.MEMBER, MembershipStatus.PENDING);

            assertThat(result.getRole()).isEqualTo(CommunityRole.MEMBER);
            assertThat(result.getStatus()).isEqualTo(MembershipStatus.PENDING);
        }

        @Test @DisplayName("throws when already a member")
        void throwsOnDuplicate() {
            when(communityRepository.findById(10L)).thenReturn(Optional.of(Community.builder().id(10L).build()));
            when(userRepository.findById(2L)).thenReturn(Optional.of(regularUser));
            when(membershipRepository.existsByCommunityIdAndUserId(10L, 2L)).thenReturn(true);

            assertThatThrownBy(() -> communityService.addMember(10L, 2L, CommunityRole.MEMBER, MembershipStatus.PENDING))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already a member");
        }
    }

    @Nested @DisplayName("findBySlug")
    class FindBySlug {

        @Test @DisplayName("returns community when slug exists")
        void returnsCommunity() {
            Community c = Community.builder().id(10L).slug("test").build();
            when(communityRepository.findBySlug("test")).thenReturn(Optional.of(c));

            assertThat(communityService.findBySlug("test")).isPresent();
        }

        @Test @DisplayName("returns empty when slug not found")
        void returnsEmpty() {
            when(communityRepository.findBySlug("nope")).thenReturn(Optional.empty());
            assertThat(communityService.findBySlug("nope")).isEmpty();
        }
    }

    @Nested @DisplayName("isMember")
    class IsMember {

        @Test @DisplayName("returns true for active member")
        void activeReturnsTrue() {
            CommunityMembership m = CommunityMembership.builder()
                    .status(MembershipStatus.ACTIVE).build();
            when(membershipRepository.findByCommunityIdAndUserId(10L, 2L))
                    .thenReturn(Optional.of(m));

            assertThat(communityService.isActiveMember(10L, 2L)).isTrue();
        }

        @Test @DisplayName("returns false for pending member")
        void pendingReturnsFalse() {
            CommunityMembership m = CommunityMembership.builder()
                    .status(MembershipStatus.PENDING).build();
            when(membershipRepository.findByCommunityIdAndUserId(10L, 2L))
                    .thenReturn(Optional.of(m));

            assertThat(communityService.isActiveMember(10L, 2L)).isFalse();
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest=CommunityServiceTest -q`
Expected: FAIL — `CommunityService` class doesn't exist yet.

- [ ] **Step 3: Implement CommunityService**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Transactional
    public Community createCommunity(String name, String slug, String description, Long createdById) {
        if (communityRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Community with slug '" + slug + "' already exists");
        }
        User creator = userRepository.findById(createdById)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + createdById));

        Community community = Community.builder()
                .name(name)
                .slug(slug)
                .description(description)
                .createdBy(creator)
                .build();
        return communityRepository.save(community);
    }

    @Transactional
    public void deleteCommunity(Long communityId) {
        communityRepository.deleteById(communityId);
        log.info("Deleted community id={}", communityId);
    }

    @Transactional(readOnly = true)
    public Optional<Community> findBySlug(String slug) {
        return communityRepository.findBySlug(slug);
    }

    @Transactional(readOnly = true)
    public Community findById(Long communityId) {
        return communityRepository.findById(communityId)
                .orElseThrow(() -> new IllegalArgumentException("Community not found: " + communityId));
    }

    @Transactional(readOnly = true)
    public List<Community> findAll() {
        return communityRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public CommunityMembership addMember(Long communityId, Long userId, CommunityRole role, MembershipStatus status) {
        Community community = findById(communityId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (membershipRepository.existsByCommunityIdAndUserId(communityId, userId)) {
            throw new IllegalArgumentException("User " + userId + " is already a member of community " + communityId);
        }

        CommunityMembership membership = CommunityMembership.builder()
                .community(community)
                .user(user)
                .role(role)
                .status(status)
                .build();
        log.info("Added user {} to community {} as {} ({})", userId, communityId, role, status);
        return membershipRepository.save(membership);
    }

    @Transactional
    public CommunityMembership approveMember(Long communityId, Long userId) {
        CommunityMembership m = getMembership(communityId, userId);
        m.setStatus(MembershipStatus.ACTIVE);
        log.info("Approved membership: user={} community={}", userId, communityId);
        return membershipRepository.save(m);
    }

    @Transactional
    public CommunityMembership rejectMember(Long communityId, Long userId) {
        CommunityMembership m = getMembership(communityId, userId);
        m.setStatus(MembershipStatus.DISABLED);
        log.info("Rejected membership: user={} community={}", userId, communityId);
        return membershipRepository.save(m);
    }

    @Transactional
    public CommunityMembership setMemberRole(Long communityId, Long userId, CommunityRole role) {
        CommunityMembership m = getMembership(communityId, userId);
        m.setRole(role);
        log.info("Changed role: user={} community={} role={}", userId, communityId, role);
        return membershipRepository.save(m);
    }

    @Transactional(readOnly = true)
    public boolean isActiveMember(Long communityId, Long userId) {
        return membershipRepository.findByCommunityIdAndUserId(communityId, userId)
                .map(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isCommunityAdmin(Long communityId, Long userId) {
        return membershipRepository.findByCommunityIdAndUserId(communityId, userId)
                .map(m -> m.getStatus() == MembershipStatus.ACTIVE && m.getRole() == CommunityRole.ADMIN)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<CommunityMembership> getActiveMembershipsForUser(Long userId) {
        return membershipRepository.findByUserIdAndStatusWithCommunity(userId, MembershipStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<CommunityMembership> getMembershipsForCommunity(Long communityId) {
        return membershipRepository.findByCommunityId(communityId);
    }

    @Transactional(readOnly = true)
    public CommunityMembership getMembership(Long communityId, Long userId) {
        return membershipRepository.findByCommunityIdAndUserId(communityId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Membership not found: user=" + userId + " community=" + communityId));
    }

    @Transactional(readOnly = true)
    public long countActiveMembers(Long communityId) {
        return membershipRepository.countByCommunityIdAndStatus(communityId, MembershipStatus.ACTIVE);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -pl . -Dtest=CommunityServiceTest -q`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/CommunityService.java \
        src/test/java/com/worldcup/prediction/service/CommunityServiceTest.java
git commit -m "feat: CommunityService — CRUD, membership management, slug resolution"
```

---

## Task 5: Super Admin Bootstrap + Form Login Security

**Files:**
- Create: `src/main/java/com/worldcup/prediction/security/SuperAdminBootstrap.java`
- Create: `src/main/java/com/worldcup/prediction/security/SuperAdminAuthenticationProvider.java`
- Modify: `src/main/java/com/worldcup/prediction/config/SecurityConfig.java`
- Modify: `src/main/java/com/worldcup/prediction/security/CustomOAuth2User.java`
- Modify: `src/main/java/com/worldcup/prediction/security/AccountStatusFilter.java`
- Modify: `src/main/java/com/worldcup/prediction/security/OAuth2AuthenticationSuccessHandler.java`
- Modify: `src/main/java/com/worldcup/prediction/security/CustomOAuth2UserService.java`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Add super-admin config to application.properties**

Append to `application.properties`:
```properties
# Super Admin bootstrap (only used on first startup)
app.super-admin.username=admin
app.super-admin.password=changeme123
app.super-admin.email=admin@worldcup.local
```

- [ ] **Step 2: Create SuperAdminBootstrap**

```java
package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.super-admin.username:admin}")
    private String username;

    @Value("${app.super-admin.password:changeme123}")
    private String password;

    @Value("${app.super-admin.email:admin@worldcup.local}")
    private String email;

    @Override
    public void run(String... args) {
        if (userRepository.findByRole(UserRole.SUPER_ADMIN).isEmpty()) {
            User superAdmin = User.builder()
                    .email(email)
                    .firstName(username)
                    .lastName("Admin")
                    .passwordHash(passwordEncoder.encode(password))
                    .role(UserRole.SUPER_ADMIN)
                    .status(UserStatus.ACTIVE)
                    .build();
            userRepository.save(superAdmin);
            log.info("Super Admin created: email={}", email);
        } else {
            log.debug("Super Admin already exists, skipping bootstrap");
        }
    }
}
```

- [ ] **Step 3: Create SuperAdminAuthenticationProvider**

```java
package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SuperAdminAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String password = (String) authentication.getCredentials();

        User user = userRepository.findByEmailIgnoreCase(email)
                .filter(u -> u.getRole() == UserRole.SUPER_ADMIN)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        CustomOAuth2User principal = new CustomOAuth2User(user, Map.of("sub", "super-admin"));
        return new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
```

- [ ] **Step 4: Update SecurityConfig**

Replace the entire `SecurityConfig` class:
```java
package com.worldcup.prediction.config;

import com.worldcup.prediction.security.AccountStatusFilter;
import com.worldcup.prediction.security.CustomOAuth2UserService;
import com.worldcup.prediction.security.OAuth2AuthenticationFailureHandler;
import com.worldcup.prediction.security.OAuth2AuthenticationSuccessHandler;
import com.worldcup.prediction.security.SuperAdminAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final OAuth2AuthenticationFailureHandler failureHandler;
    private final AccountStatusFilter accountStatusFilter;
    private final SuperAdminAuthenticationProvider superAdminAuthenticationProvider;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(superAdminAuthenticationProvider)
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()
                .requestMatchers("/", "/login", "/error").permitAll()
                .requestMatchers("/dev/**").permitAll()
                .requestMatchers("/leaderboard/**").permitAll()
                .requestMatchers("/fixtures/**").permitAll()
                .requestMatchers("/groups/**").permitAll()
                .requestMatchers("/bracket/**").permitAll()
                .requestMatchers("/teams/**").permitAll()
                .requestMatchers("/scorers/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/pending").authenticated()
                .requestMatchers("/admin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/c/*/admin/**").authenticated()
                .requestMatchers("/c/**").authenticated()
                .requestMatchers("/communities/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login/form")
                .defaultSuccessUrl("/admin", true)
                .failureUrl("/login?error")
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .successHandler(successHandler)
                .failureHandler(failureHandler)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .sessionManagement(session -> session
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
            );

        http.addFilterAfter(accountStatusFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

- [ ] **Step 5: Update CustomOAuth2User — support both USER and SUPER_ADMIN authorities**

The `getAuthorities()` method already returns `"ROLE_" + user.getRole().name()` which will now correctly return `ROLE_USER` or `ROLE_SUPER_ADMIN`. No changes needed here.

- [ ] **Step 6: Update CustomOAuth2UserService — new users get UserRole.USER**

In `CustomOAuth2UserService.loadUser()`, the new user creation block already has `role(UserRole.PARTICIPANT)`. Change to:
```java
.role(UserRole.USER)
```

- [ ] **Step 7: Update OAuth2AuthenticationSuccessHandler — redirect based on communities**

Replace the class:
```java
package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final CommunityMembershipRepository membershipRepository;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        if (!(authentication.getPrincipal() instanceof CustomOAuth2User customUser)) {
            log.warn("Unexpected principal type: {}", authentication.getPrincipal().getClass());
            getRedirectStrategy().sendRedirect(request, response, "/communities");
            return;
        }

        UserStatus status = customUser.getStatus();
        log.info("Authentication success for user={} status={}", customUser.getEmail(), status);

        String targetUrl = switch (status) {
            case PENDING  -> "/pending";
            case ACTIVE   -> resolveActiveRedirect(customUser);
            case DISABLED -> "/login?disabled";
        };

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String resolveActiveRedirect(CustomOAuth2User customUser) {
        if (customUser.getRole() == UserRole.SUPER_ADMIN) {
            return "/admin";
        }
        List<CommunityMembership> memberships = membershipRepository
                .findByUserIdAndStatus(customUser.getUserId(), MembershipStatus.ACTIVE);
        if (memberships.size() == 1) {
            return "/c/" + memberships.get(0).getCommunity().getSlug() + "/home";
        }
        return "/communities";
    }
}
```

- [ ] **Step 8: Update AccountStatusFilter — add community membership check for /c/ URLs**

The filter currently checks global `User.status`. Add community membership verification for `/c/{slug}/**` paths. Replace `doFilterInternal`:

```java
@Override
protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
) throws ServletException, IOException {

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof CustomOAuth2User customUser)) {
        filterChain.doFilter(request, response);
        return;
    }

    Optional<User> userOpt = userRepository.findByEmail(customUser.getEmail());
    if (userOpt.isEmpty()) {
        log.warn("Authenticated user not found in DB, invalidating session: {}", customUser.getEmail());
        invalidateAndRedirect(request, response);
        return;
    }

    User user = userOpt.get();
    if (user.getStatus() == UserStatus.DISABLED) {
        log.info("Disabled user attempted access, invalidating session: {}", customUser.getEmail());
        invalidateAndRedirect(request, response);
        return;
    }

    filterChain.doFilter(request, response);
}
```

Note: Community-level membership checks will be handled by the `CommunityInterceptor` (Task 6), not the `AccountStatusFilter`. The filter remains responsible only for global account status.

- [ ] **Step 9: Compile check**

Run: `./mvnw compile -q`
Expected: Compilation succeeds.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/worldcup/prediction/security/ \
        src/main/java/com/worldcup/prediction/config/SecurityConfig.java \
        src/main/resources/application.properties
git commit -m "feat: Super Admin bootstrap + form login + updated security config"
```

---

## Task 6: Community Interceptor + WebMvc Config

**Files:**
- Create: `src/main/java/com/worldcup/prediction/security/CommunityInterceptor.java`
- Create: `src/main/java/com/worldcup/prediction/config/WebMvcConfig.java`

- [ ] **Step 1: Create CommunityInterceptor**

This interceptor runs on all `/c/{slug}/**` requests. It resolves the community from the slug, verifies user membership, and injects the `Community` and `CommunityMembership` into request attributes for controllers to use.

```java
package com.worldcup.prediction.security;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.CommunityRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommunityInterceptor implements HandlerInterceptor {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^/c/([^/]+)");
    private static final Pattern ADMIN_PATTERN = Pattern.compile("^/c/[^/]+/admin");

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository membershipRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String path = request.getServletPath();
        Matcher matcher = SLUG_PATTERN.matcher(path);
        if (!matcher.find()) {
            return true;
        }

        String slug = matcher.group(1);
        Optional<Community> communityOpt = communityRepository.findBySlug(slug);
        if (communityOpt.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Community not found");
            return false;
        }

        Community community = communityOpt.get();
        request.setAttribute("community", community);
        request.setAttribute("communitySlug", slug);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomOAuth2User customUser)) {
            response.sendRedirect("/login");
            return false;
        }

        if (customUser.getRole() == UserRole.SUPER_ADMIN) {
            request.setAttribute("communityMembership", null);
            return true;
        }

        Optional<CommunityMembership> membershipOpt =
                membershipRepository.findByCommunityIdAndUserId(community.getId(), customUser.getUserId());

        if (membershipOpt.isEmpty() || membershipOpt.get().getStatus() != MembershipStatus.ACTIVE) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Not a member of this community");
            return false;
        }

        CommunityMembership membership = membershipOpt.get();
        request.setAttribute("communityMembership", membership);

        if (ADMIN_PATTERN.matcher(path).find() && membership.getRole() != CommunityRole.ADMIN) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Community admin access required");
            return false;
        }

        return true;
    }
}
```

- [ ] **Step 2: Create WebMvcConfig**

```java
package com.worldcup.prediction.config;

import com.worldcup.prediction.security.CommunityInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CommunityInterceptor communityInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(communityInterceptor)
                .addPathPatterns("/c/**");
    }
}
```

- [ ] **Step 3: Compile check**

Run: `./mvnw compile -q`
Expected: Compilation succeeds.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worldcup/prediction/security/CommunityInterceptor.java \
        src/main/java/com/worldcup/prediction/config/WebMvcConfig.java
git commit -m "feat: CommunityInterceptor — slug resolution, membership + admin checks"
```

---

## Task 7: Update Service Layer for Community Scope

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/service/LeaderboardService.java`
- Modify: `src/main/java/com/worldcup/prediction/service/PredictionService.java`
- Modify: `src/main/java/com/worldcup/prediction/service/PredictionViewService.java`
- Modify: `src/main/java/com/worldcup/prediction/service/TournamentWinnerPredictionService.java`
- Modify: `src/main/java/com/worldcup/prediction/service/MatchAdminService.java`
- Modify: `src/main/java/com/worldcup/prediction/service/ScoringService.java`
- Modify: `src/main/java/com/worldcup/prediction/service/UserService.java`
- Modify: `src/main/java/com/worldcup/prediction/service/NotificationService.java`

This is the largest task. Each service gets `communityId` added to its method signatures.

- [ ] **Step 1: Update LeaderboardService**

Replace the entire class. Key changes: all methods take `communityId`, query `CommunityMembership` for stats, query predictions with community filter.

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.TournamentWinnerPrediction;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.domain.enums.PredictionScore;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.repository.TournamentWinnerPredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaderboardService {

    private final CommunityMembershipRepository membershipRepository;
    private final PredictionRepository predictionRepository;
    private final TournamentWinnerPredictionRepository twpRepository;

    public List<LeaderboardEntryDto> getFullLeaderboard(Long communityId) {
        List<CommunityMembership> members = membershipRepository
                .findByCommunityIdAndStatusWithUser(communityId, MembershipStatus.ACTIVE);

        List<LeaderboardEntryDto> unsorted = members.stream()
                .map(m -> buildEntry(m, communityId))
                .sorted(leaderboardComparator())
                .toList();

        return assignRanks(unsorted);
    }

    public List<LeaderboardEntryDto> getTopN(int n, Long communityId) {
        List<LeaderboardEntryDto> full = getFullLeaderboard(communityId);
        return full.subList(0, Math.min(n, full.size()));
    }

    public Optional<LeaderboardEntryDto> getEntryForUser(Long userId, Long communityId) {
        return getFullLeaderboard(communityId).stream()
                .filter(e -> e.getUserId().equals(userId))
                .findFirst();
    }

    private LeaderboardEntryDto buildEntry(CommunityMembership membership, Long communityId) {
        var user = membership.getUser();

        Optional<TournamentWinnerPrediction> twp = twpRepository
                .findByUserIdAndCommunityId(user.getId(), communityId);
        boolean tournamentWinnerCorrect = twp.map(TournamentWinnerPrediction::isScored).orElse(false);
        String predictedWinnerFlagCode = twp
                .map(t -> t.getTeam().getFlagCode())
                .orElse(null);

        return new LeaderboardEntryDto(
                0,
                user.getId(),
                user.getFullName(),
                user.getAvatarUrl(),
                predictedWinnerFlagCode,
                membership.getTotalPoints(),
                membership.getExactScoreCount(),
                membership.getCorrectWinnerCount(),
                membership.getCorrectDrawCount(),
                tournamentWinnerCorrect,
                0
        );
    }

    private Comparator<LeaderboardEntryDto> leaderboardComparator() {
        return Comparator
                .<LeaderboardEntryDto>comparingInt(e -> -e.getTotalPoints())
                .thenComparingInt(e -> -e.getExactCount())
                .thenComparingInt(e -> -e.getCorrectWinnerCount())
                .thenComparingInt(e -> e.isTournamentWinnerCorrect() ? 0 : 1);
    }

    private List<LeaderboardEntryDto> assignRanks(List<LeaderboardEntryDto> sorted) {
        List<LeaderboardEntryDto> ranked = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            LeaderboardEntryDto e = sorted.get(i);
            ranked.add(new LeaderboardEntryDto(
                    i + 1, e.getUserId(), e.getDisplayName(), e.getAvatarUrl(),
                    e.getPredictedWinnerFlagCode(), e.getTotalPoints(), e.getExactCount(),
                    e.getCorrectWinnerCount(), e.getDrawCount(), e.isTournamentWinnerCorrect(),
                    e.getRankChange()));
        }
        return ranked;
    }
}
```

- [ ] **Step 2: Update PredictionViewService**

Add `Long communityId` parameter to every method. Replace every call to `predictionRepository.findByUserIdAndMatchIdIn(userId, matchIds)` with `predictionRepository.findByUserIdAndMatchIdInAndCommunityId(userId, matchIds, communityId)`. Replace `countByUserIdAndMatchIdIn` with `countByUserIdAndMatchIdInAndCommunityId`. This is a mechanical change — add the parameter and append `AndCommunityId` to all repository calls.

Key method signature changes:
- `getRoundSummaries(Long userId)` -> `getRoundSummaries(Long userId, Long communityId)`
- `getMatchesForRound(Long userId, String roundLabel)` -> `getMatchesForRound(Long userId, String roundLabel, Long communityId)`
- `submitPredictionsForRound(Long userId, PredictionSubmitDto dto)` -> `submitPredictionsForRound(Long userId, PredictionSubmitDto dto, Long communityId)`
- `getPastRoundsForUser(Long userId)` -> `getPastRoundsForUser(Long userId, Long communityId)`

In `submitPredictionsForRound`, when creating a new `Prediction`, add:
```java
prediction.setCommunity(communityRepository.findById(communityId).orElseThrow());
```

Add `CommunityRepository` as a dependency (add to constructor).

- [ ] **Step 3: Update PredictionService**

Add `communityId` to `submitPredictions` and retrieval methods. When creating predictions:
```java
Community community = communityRepository.findById(communityId)
        .orElseThrow(() -> new IllegalArgumentException("Community not found: " + communityId));
// ... in prediction builder:
.community(community)
```

Update `findByUserIdAndMatchId` calls to `findByUserIdAndMatchIdAndCommunityId`.
Add `CommunityRepository` as a dependency.

- [ ] **Step 4: Update TournamentWinnerPredictionService**

Add `communityId` to `submitOrUpdate` and `getForUser`:
- `submitOrUpdate(Long userId, TournamentWinnerPredictionDto dto, Long communityId)`
- `getForUser(Long userId, Long communityId)` -> uses `findByUserIdAndCommunityId`
- `getAll()` stays but add `getAllByCommunity(Long communityId)` -> uses `findAllWithDetailsByCommunityId`
- `awardPoints(String actualWinnerFlagCode)` -> scores across ALL communities (iterates all TWPs)

- [ ] **Step 5: Update MatchAdminService — scoreAllPredictions across communities**

The `scoreAllPredictions(Long matchId)` method currently calls `predictionRepository.findByMatchId(matchId)`. This already fetches ALL predictions for a match regardless of community, which is correct — scoring runs globally. After scoring, update denormalized stats on `CommunityMembership`.

Add `CommunityMembershipRepository` dependency. After scoring each prediction, update the membership stats:
```java
@Transactional
public void scoreAllPredictions(Long matchId) {
    Match match = findById(matchId);
    if (match.getHomeScore() == null || match.getAwayScore() == null) return;

    int actualHome = match.getEffectiveHomeScore();
    int actualAway = match.getEffectiveAwayScore();

    List<Prediction> predictions = predictionRepository.findByMatchId(matchId);
    Set<Long> updatedMembershipKeys = new HashSet<>();

    for (Prediction p : predictions) {
        int pts = scoringService.calculatePoints(
                actualHome, actualAway,
                p.getPredictedHome(), p.getPredictedAway());
        p.setPointsAwarded(pts);
        p.setScoreResult(scoringService.determineScoreResult(
                actualHome, actualAway, p.getPredictedHome(), p.getPredictedAway()));
        predictionRepository.save(p);

        // Update community membership stats
        if (p.getCommunity() != null) {
            String key = p.getUser().getId() + ":" + p.getCommunity().getId();
            if (updatedMembershipKeys.add(key)) {
                recalculateMembershipStats(p.getUser().getId(), p.getCommunity().getId());
            }
        }
    }
}

private void recalculateMembershipStats(Long userId, Long communityId) {
    membershipRepository.findByCommunityIdAndUserId(communityId, userId).ifPresent(m -> {
        List<Prediction> userPreds = predictionRepository.findByUserIdAndCommunityId(userId, communityId);
        m.setTotalPoints(userPreds.stream().mapToInt(Prediction::getPointsAwarded).sum());
        m.setExactScoreCount((int) userPreds.stream()
                .filter(p -> p.getScoreResult() == PredictionScore.EXACT).count());
        m.setCorrectWinnerCount((int) userPreds.stream()
                .filter(p -> p.getScoreResult() == PredictionScore.CORRECT_WINNER).count());
        m.setCorrectDrawCount((int) userPreds.stream()
                .filter(p -> p.getScoreResult() == PredictionScore.CORRECT_DRAW).count());
        membershipRepository.save(m);
    });
}
```

Note: `ScoringService` needs a `determineScoreResult` method that returns the `PredictionScore` enum. Add it:
```java
public PredictionScore determineScoreResult(int actualHome, int actualAway,
                                             int predictedHome, int predictedAway) {
    if (isExactScore(actualHome, actualAway, predictedHome, predictedAway)) return PredictionScore.EXACT;
    if (isCorrectDraw(actualHome, actualAway, predictedHome, predictedAway)) return PredictionScore.CORRECT_DRAW;
    if (isCorrectOutcome(actualHome, actualAway, predictedHome, predictedAway)) return PredictionScore.CORRECT_WINNER;
    return PredictionScore.WRONG;
}
```

- [ ] **Step 6: Update NotificationService — add community context**

Add `Community community` parameter to methods where applicable. Update `logNotification` to include community. The dedup `referenceKey` should include community ID, e.g.:
`"PREDICTION_REMINDER:community:" + communityId + ":user:" + userId + ":match:" + matchId`

- [ ] **Step 7: Compile check**

Run: `./mvnw compile -q`
Expected: Compilation succeeds. Fix any remaining references.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/
git commit -m "feat: community-scoped services — leaderboard, predictions, scoring, notifications"
```

---

## Task 8: Update Existing Admin Controllers for SUPER_ADMIN

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/controller/admin/AdminDashboardController.java`
- Modify: `src/main/java/com/worldcup/prediction/controller/admin/AdminUserController.java`
- Modify: `src/main/java/com/worldcup/prediction/controller/admin/AdminMatchController.java`
- Modify: `src/main/java/com/worldcup/prediction/controller/admin/AdminSyncController.java`
- Create: `src/main/java/com/worldcup/prediction/controller/admin/AdminCommunityController.java`
- Create: `src/main/java/com/worldcup/prediction/controller/admin/AdminSettingsController.java`
- Remove (or gut): `src/main/java/com/worldcup/prediction/controller/admin/AdminPredictionController.java`
- Remove (or gut): `src/main/java/com/worldcup/prediction/controller/admin/AdminNotificationController.java`

- [ ] **Step 1: Update @PreAuthorize on all existing admin controllers**

Change `@PreAuthorize("hasRole('ADMIN')")` to `@PreAuthorize("hasRole('SUPER_ADMIN')")` in:
- `AdminDashboardController`
- `AdminUserController`
- `AdminMatchController`
- `AdminSyncController`

- [ ] **Step 2: Update AdminDashboardController — show community stats**

Add `CommunityService` dependency. In the `dashboard()` method, add:
```java
model.addAttribute("communities", communityService.findAll());
```

Remove `pendingCount` (no longer global) or keep as a total across all communities.

- [ ] **Step 3: Create AdminCommunityController**

```java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.CommunityRole;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.CommunityService;
import com.worldcup.prediction.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/communities")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminCommunityController {

    private final CommunityService communityService;
    private final UserService userService;

    @GetMapping
    public String listCommunities(Model model) {
        List<Community> communities = communityService.findAll();
        model.addAttribute("communities", communities);
        return "admin/communities";
    }

    @PostMapping("/create")
    public String createCommunity(@RequestParam String name,
                                   @RequestParam String slug,
                                   @RequestParam(required = false) String description,
                                   @AuthenticationPrincipal CustomOAuth2User admin,
                                   RedirectAttributes redirectAttributes) {
        try {
            communityService.createCommunity(name, slug.toLowerCase().trim(), description, admin.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Community '" + name + "' created.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/communities";
    }

    @PostMapping("/{id}/delete")
    public String deleteCommunity(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        communityService.deleteCommunity(id);
        redirectAttributes.addFlashAttribute("successMessage", "Community deleted.");
        return "redirect:/admin/communities";
    }

    @GetMapping("/{id}/admins")
    public String manageCommunityAdmins(@PathVariable Long id, Model model) {
        Community community = communityService.findById(id);
        List<CommunityMembership> memberships = communityService.getMembershipsForCommunity(id);
        List<User> allUsers = userService.findAll();
        model.addAttribute("community", community);
        model.addAttribute("memberships", memberships);
        model.addAttribute("allUsers", allUsers);
        return "admin/community-admins";
    }

    @PostMapping("/{id}/admins/add")
    public String addCommunityAdmin(@PathVariable Long id,
                                     @RequestParam Long userId,
                                     RedirectAttributes redirectAttributes) {
        try {
            communityService.addMember(id, userId, CommunityRole.ADMIN, MembershipStatus.ACTIVE);
            redirectAttributes.addFlashAttribute("successMessage", "Admin added to community.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/communities/" + id + "/admins";
    }

    @PostMapping("/{communityId}/admins/{userId}/promote")
    public String promoteToAdmin(@PathVariable Long communityId,
                                  @PathVariable Long userId,
                                  RedirectAttributes redirectAttributes) {
        communityService.setMemberRole(communityId, userId, CommunityRole.ADMIN);
        redirectAttributes.addFlashAttribute("successMessage", "User promoted to community admin.");
        return "redirect:/admin/communities/" + communityId + "/admins";
    }
}
```

- [ ] **Step 4: Create AdminSettingsController**

```java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.security.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public String settingsPage(Model model) {
        return "admin/settings";
    }

    @PostMapping("/password")
    public String changePassword(@RequestParam String currentPassword,
                                  @RequestParam String newPassword,
                                  @AuthenticationPrincipal CustomOAuth2User admin,
                                  RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(admin.getUserId()).orElseThrow();
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Current password is incorrect.");
            return "redirect:/admin/settings";
        }
        if (newPassword.length() < 8) {
            redirectAttributes.addFlashAttribute("errorMessage", "New password must be at least 8 characters.");
            return "redirect:/admin/settings";
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully.");
        return "redirect:/admin/settings";
    }
}
```

- [ ] **Step 5: Remove old AdminPredictionController and AdminNotificationController**

Delete these files (functionality moves to community admin controllers in Task 9):
- `src/main/java/com/worldcup/prediction/controller/admin/AdminPredictionController.java`
- `src/main/java/com/worldcup/prediction/controller/admin/AdminNotificationController.java`

- [ ] **Step 6: Update AdminControllerAdvice (if referencing old role)**

Check `AdminControllerAdvice.java` for any `ADMIN` role references and update to `SUPER_ADMIN`.

- [ ] **Step 7: Compile check**

Run: `./mvnw compile -q`
Expected: Compilation succeeds.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/admin/
git commit -m "feat: Super Admin controllers — community CRUD, settings, updated role checks"
```

---

## Task 9: Community-Scoped Controllers

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/community/CommunityHomeController.java`
- Create: `src/main/java/com/worldcup/prediction/controller/community/CommunityPredictionController.java`
- Create: `src/main/java/com/worldcup/prediction/controller/community/CommunityLeaderboardController.java`
- Create: `src/main/java/com/worldcup/prediction/controller/community/CommunityAdminDashboardController.java`
- Create: `src/main/java/com/worldcup/prediction/controller/community/CommunityAdminMemberController.java`
- Create: `src/main/java/com/worldcup/prediction/controller/community/CommunityAdminPredictionController.java`
- Create: `src/main/java/com/worldcup/prediction/controller/community/CommunityAdminNotificationController.java`
- Create: `src/main/java/com/worldcup/prediction/controller/CommunityController.java`
- Modify: `src/main/java/com/worldcup/prediction/controller/LandingController.java`
- Remove: `src/main/java/com/worldcup/prediction/controller/HomeController.java`
- Remove: `src/main/java/com/worldcup/prediction/controller/PredictionController.java`

Each community controller reads the `Community` from `request.getAttribute("community")` (injected by CommunityInterceptor).

- [ ] **Step 1: Create CommunityController (community selector page)**

```java
package com.worldcup.prediction.controller;

import com.worldcup.prediction.domain.CommunityMembership;
import com.worldcup.prediction.domain.enums.MembershipStatus;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/communities")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @GetMapping
    public String myCommunities(@AuthenticationPrincipal CustomOAuth2User principal, Model model) {
        Long userId = principal.getUserId();
        List<CommunityMembership> active = communityService.getActiveMembershipsForUser(userId);
        model.addAttribute("memberships", active);
        model.addAttribute("pageTitle", "My Communities");
        return "communities";
    }
}
```

- [ ] **Step 2: Create CommunityHomeController**

```java
package com.worldcup.prediction.controller.community;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.security.CustomOAuth2User;
import com.worldcup.prediction.service.LeaderboardService;
import com.worldcup.prediction.service.MatchService;
import com.worldcup.prediction.service.UserStatsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/c/{slug}")
@RequiredArgsConstructor
public class CommunityHomeController {

    private final LeaderboardService leaderboardService;
    private final MatchService matchService;

    @GetMapping("/home")
    public String home(@PathVariable String slug,
                       @AuthenticationPrincipal CustomOAuth2User currentUser,
                       HttpServletRequest request,
                       Model model) {
        Community community = (Community) request.getAttribute("community");
        Long userId = currentUser.getUserId();
        Long communityId = community.getId();

        List<LeaderboardEntryDto> topTen = leaderboardService.getTopN(10, communityId);
        int userRank = leaderboardService.getEntryForUser(userId, communityId)
                .map(LeaderboardEntryDto::getRank).orElse(0);

        model.addAttribute("community", community);
        model.addAttribute("slug", slug);
        model.addAttribute("topTen", topTen);
        model.addAttribute("userRank", userRank);
        model.addAttribute("pageTitle", community.getName() + " · Home");

        return "community/home";
    }
}
```

- [ ] **Step 3: Create CommunityPredictionController**

Port the logic from the old `PredictionController`, adding community scope. The controller reads `community` from request attributes and passes `community.getId()` to all service calls. Template references change from `predictions` to `community/predictions`.

Key: all redirects use `/c/{slug}/predictions` instead of `/predictions`.

- [ ] **Step 4: Create CommunityLeaderboardController**

Port from `LeaderboardController`, pass `communityId` to `LeaderboardService.getFullLeaderboard(communityId)`.

- [ ] **Step 5: Create Community Admin Controllers**

Port the functionality from the removed `AdminPredictionController` and `AdminNotificationController`, scoped to community. These controllers check membership role via the interceptor (already verified by `CommunityInterceptor`).

- [ ] **Step 6: Update LandingController redirect**

```java
@GetMapping("/")
public String landing(Authentication authentication) {
    if (authentication != null && authentication.isAuthenticated()
            && authentication.getPrincipal() instanceof CustomOAuth2User customUser) {
        if (customUser.getRole() == UserRole.SUPER_ADMIN) {
            return "redirect:/admin";
        }
        return "redirect:/communities";
    }
    return "index";
}
```

- [ ] **Step 7: Remove old controllers**

Delete:
- `src/main/java/com/worldcup/prediction/controller/HomeController.java`
- `src/main/java/com/worldcup/prediction/controller/PredictionController.java`

The `LeaderboardController` at `/leaderboard` should be removed or repurposed (leaderboard now requires community context). Remove the public leaderboard route and update SecurityConfig.

- [ ] **Step 8: Compile check**

Run: `./mvnw compile -q`
Expected: Compilation succeeds.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/
git commit -m "feat: community-scoped controllers — home, predictions, leaderboard, community admin"
```

---

## Task 10: Templates

**Files:**
- Create: `src/main/resources/templates/communities.html`
- Create: `src/main/resources/templates/community/home.html`
- Create: `src/main/resources/templates/community/predictions.html`
- Create: `src/main/resources/templates/community/leaderboard.html`
- Create: `src/main/resources/templates/community/admin/dashboard.html`
- Create: `src/main/resources/templates/community/admin/members.html`
- Create: `src/main/resources/templates/community/admin/predictions.html`
- Create: `src/main/resources/templates/community/admin/notifications.html`
- Create: `src/main/resources/templates/community/admin/layout.html`
- Create: `src/main/resources/templates/admin/communities.html`
- Create: `src/main/resources/templates/admin/community-admins.html`
- Create: `src/main/resources/templates/admin/settings.html`
- Create: `src/main/resources/templates/layout/community-base.html`
- Modify: `src/main/resources/templates/login.html`
- Modify: `src/main/resources/templates/layout/base.html`
- Modify: `src/main/resources/templates/admin/dashboard.html`

- [ ] **Step 1: Create community-base.html**

Copy `layout/base.html`, add community dropdown in the navbar. The dropdown lists all communities the user belongs to and highlights the current one. Add community name display. Links in the nav change from `/predictions` to `/c/${slug}/predictions`.

- [ ] **Step 2: Create communities.html (selector page)**

Page showing cards for each community the user belongs to. Each card: community name, member count (if available), "Enter" button linking to `/c/{slug}/home`.

- [ ] **Step 3: Create community/home.html**

Copy structure from `home.html`, replace layout reference with `community-base.html`. Remove references to global data; use community-scoped model attributes.

- [ ] **Step 4: Create community/predictions.html**

Copy from `predictions.html`, use `community-base.html` layout. Replace form action URLs with `/c/${slug}/predictions/submit`. Adjust fragment references.

- [ ] **Step 5: Create community/leaderboard.html**

Copy from `leaderboard.html`, use `community-base.html` layout.

- [ ] **Step 6: Create community admin templates**

Create `community/admin/layout.html` (copy from `admin/layout.html`, adjust nav links to use `/c/{slug}/admin/...`).

Create dashboard, members, predictions, notifications templates following the admin pattern.

- [ ] **Step 7: Update login.html**

Add collapsible "Super Admin Login" section:
```html
<div x-data="{ showAdmin: false }" class="mt-8">
  <button @click="showAdmin = !showAdmin"
          class="text-sm text-gray-500 hover:text-gray-700 underline">
    Super Admin Login
  </button>
  <form x-show="showAdmin" th:action="@{/login/form}" method="post" class="mt-4 space-y-3">
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}" />
    <input type="email" name="username" placeholder="Email" required
           class="w-full px-4 py-2 border rounded-lg" />
    <input type="password" name="password" placeholder="Password" required
           class="w-full px-4 py-2 border rounded-lg" />
    <button type="submit"
            class="w-full bg-gray-700 hover:bg-gray-800 text-white font-bold py-2 rounded-lg">
      Sign in as Admin
    </button>
  </form>
</div>
```

- [ ] **Step 8: Update admin/dashboard.html**

Add community list section showing all communities with member counts and links to manage.

- [ ] **Step 9: Create admin/communities.html and admin/settings.html**

Standard admin pages following the existing admin template pattern.

- [ ] **Step 10: Update base.html — community dropdown**

In the navbar, conditionally show community dropdown when user is in community context (check if `communitySlug` model attribute is set).

- [ ] **Step 11: Compile check**

Run: `./mvnw compile -q`
Expected: Compilation succeeds.

- [ ] **Step 12: Commit**

```bash
git add src/main/resources/templates/
git commit -m "feat: community templates — selector, home, predictions, leaderboard, admin pages"
```

---

## Task 11: Update Tests

**Files:**
- Modify: All test files that reference `UserRole.PARTICIPANT` or `UserRole.ADMIN`
- Modify: Tests that call services without `communityId`
- Update: `CustomOAuth2UserServiceTest`, `AccountStatusFilterTest`, `OAuth2AuthenticationSuccessHandlerTest`

- [ ] **Step 1: Fix enum references in all tests**

Search and replace across all test files:
- `UserRole.PARTICIPANT` -> `UserRole.USER`
- `UserRole.ADMIN` -> `UserRole.SUPER_ADMIN` (where testing admin behavior)
- `hasRole('ADMIN')` -> `hasRole('SUPER_ADMIN')`

- [ ] **Step 2: Update service tests for community parameters**

- `LeaderboardServiceTest` — add `communityId` to all method calls
- `PredictionServiceTest` — add `communityId` to `submitPredictions` calls, mock `CommunityRepository`
- `TournamentWinnerPredictionServiceTest` — add `communityId`
- `NotificationServiceTest` — add community context

- [ ] **Step 3: Update security tests**

- `CustomOAuth2UserServiceTest` — verify new users get `UserRole.USER`
- `OAuth2AuthenticationSuccessHandlerTest` — mock `CommunityMembershipRepository`, test redirect to `/communities` vs `/c/{slug}/home`
- `AccountStatusFilterTest` — remains mostly the same (global status check)

- [ ] **Step 4: Update controller tests**

- `AdminUserControllerTest` — change role check to `SUPER_ADMIN`
- `AdminMatchControllerTest` — change role check to `SUPER_ADMIN`
- `LeaderboardControllerTest` — update or remove (leaderboard is now community-scoped)

- [ ] **Step 5: Update DevLoginController**

Change `UserRole.ADMIN` to `UserRole.SUPER_ADMIN`:
```java
.role(UserRole.SUPER_ADMIN)
```

- [ ] **Step 6: Run all tests**

Run: `./mvnw test -q`
Expected: All tests pass. Fix any failures.

- [ ] **Step 7: Commit**

```bash
git add src/test/ src/main/java/com/worldcup/prediction/controller/DevLoginController.java
git commit -m "test: update all tests for multi-community architecture"
```

---

## Task 12: OAuth2 Invitation Flow Update

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/security/CustomOAuth2UserService.java`
- Modify: `src/main/java/com/worldcup/prediction/domain/Invitation.java`

- [ ] **Step 1: Update invitation auto-approve flow in CustomOAuth2UserService**

In the "Brand-new user" section, after checking for invitation, also add the user to the invitation's community:

```java
// 3. Brand-new user — check invitation first
UserStatus initialStatus = UserStatus.PENDING;
Community invitedCommunity = null;
Optional<Invitation> invitation = invitationRepository.findByEmailIgnoreCase(email);
if (invitation.isPresent() && !invitation.get().isAccepted()) {
    initialStatus = UserStatus.ACTIVE;
    invitedCommunity = invitation.get().getCommunity();
    invitation.get().setAcceptedAt(LocalDateTime.now());
    invitationRepository.save(invitation.get());
    log.info("Invited user auto-approved: email={}", email);
}

// ... save user ...

// Auto-join invited community
if (invitedCommunity != null) {
    CommunityMembership membership = CommunityMembership.builder()
            .community(invitedCommunity)
            .user(saved)
            .role(CommunityRole.MEMBER)
            .status(MembershipStatus.ACTIVE)
            .build();
    membershipRepository.save(membership);
    log.info("Auto-joined user {} to community {}", saved.getEmail(), invitedCommunity.getSlug());
}
```

Add `CommunityMembershipRepository` to the constructor dependencies.

- [ ] **Step 2: Run tests**

Run: `./mvnw test -pl . -Dtest=CustomOAuth2UserServiceTest -q`
Expected: Tests pass (update test to verify community membership creation).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worldcup/prediction/security/CustomOAuth2UserService.java
git commit -m "feat: OAuth2 invitation flow — auto-join invited community on registration"
```

---

## Task 13: Final Integration Test + Full Build

- [ ] **Step 1: Delete the existing worldcup.db to start fresh**

```bash
rm -f worldcup.db
```

- [ ] **Step 2: Run full build**

Run: `./mvnw clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Run all tests**

Run: `./mvnw test -q`
Expected: All tests pass.

- [ ] **Step 4: Boot the application**

Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=sqlite`
Expected: Application starts. Logs show:
- Flyway migration V5 applied successfully
- "Super Admin created: email=admin@worldcup.local"
- No errors

Verify manually:
- Visit `http://localhost:8888/login` — see OAuth2 buttons + collapsible Super Admin form
- Login as Super Admin (admin@worldcup.local / changeme123) — redirects to `/admin`
- Create a community from `/admin/communities`
- Stop the app

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit -m "feat: multi-community architecture — complete implementation"
```
