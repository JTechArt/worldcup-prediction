# Admin Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a fully-functional admin panel with dashboard, user management, match/result management, prediction override, and an audit log service — all protected behind `ROLE_ADMIN` and wired with HTMX partial updates.

**Architecture:** All admin routes live under `/admin/**` and are secured by Spring Security's `hasRole("ADMIN")` rule. Controllers delegate to existing services (UserService, MatchService, ScoringService) and a new AuditLogService that persists every state-changing admin action to an `audit_logs` table. Templates use a shared admin sidebar layout (Thymeleaf layout dialect fragment) and HTMX `hx-swap="outerHTML"` row-replacement for inline table actions.

**Tech Stack:** Spring MVC, Thymeleaf, HTMX, Spring Security, Spring Data JPA, Tailwind CSS, Alpine.js

**Depends on:** Part 1 (domain entities, DB schema), Part 2 (Spring Security, OAuth2, UserService), Part 3 (MatchService, ScoringService, PredictionService)

**Next parts:** Part 8 (Integrations — email stubs in AdminUserController become real SMTP calls)

---

## File Structure

```
src/main/java/com/worldcup/prediction/
  controller/admin/
    AdminDashboardController.java
    AdminUserController.java
    AdminMatchController.java
    AdminPredictionController.java
  service/
    AuditLogService.java
  model/
    AuditLog.java
  repository/
    AuditLogRepository.java

src/main/resources/templates/admin/
  layout.html
  dashboard.html
  users.html
  matches.html
  predictions.html

src/test/java/com/worldcup/prediction/
  service/
    AuditLogServiceTest.java
  controller/admin/
    AdminUserControllerTest.java
    AdminMatchControllerTest.java
```

---

### Task 1: AuditLog Entity and Repository

**Files:**
- Create: `src/main/java/com/worldcup/prediction/model/AuditLog.java`
- Create: `src/main/java/com/worldcup/prediction/repository/AuditLogRepository.java`

- [ ] **Step 1: Create AuditLog entity**

```java
// src/main/java/com/worldcup/prediction/model/AuditLog.java
package com.worldcup.prediction.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long adminId;

    @Column(nullable = false, length = 100)
    private String action;           // e.g. "APPROVE_USER", "ENTER_RESULT", "OVERRIDE_PREDICTION"

    @Column(nullable = false, length = 50)
    private String targetType;       // e.g. "USER", "MATCH", "PREDICTION"

    @Column(nullable = false)
    private Long targetId;

    @Column(columnDefinition = "TEXT")
    private String details;          // free-form JSON or human-readable string

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public AuditLog() {}

    public AuditLog(Long adminId, String action, String targetType, Long targetId, String details) {
        this.adminId    = adminId;
        this.action     = action;
        this.targetType = targetType;
        this.targetId   = targetId;
        this.details    = details;
        this.createdAt  = Instant.now();
    }

    // --- getters ---

    public Long getId()          { return id; }
    public Long getAdminId()     { return adminId; }
    public String getAction()    { return action; }
    public String getTargetType(){ return targetType; }
    public Long getTargetId()    { return targetId; }
    public String getDetails()   { return details; }
    public Instant getCreatedAt(){ return createdAt; }

    // --- setters ---

    public void setId(Long id)                   { this.id = id; }
    public void setAdminId(Long adminId)         { this.adminId = adminId; }
    public void setAction(String action)         { this.action = action; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public void setTargetId(Long targetId)       { this.targetId = targetId; }
    public void setDetails(String details)       { this.details = details; }
    public void setCreatedAt(Instant createdAt)  { this.createdAt = createdAt; }
}
```

- [ ] **Step 2: Create AuditLogRepository**

```java
// src/main/java/com/worldcup/prediction/repository/AuditLogRepository.java
package com.worldcup.prediction.repository;

import com.worldcup.prediction.model.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Most recent N entries for the dashboard
    List<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
```

- [ ] **Step 3: Add Flyway migration for audit_logs table**

Create `src/main/resources/db/migration/V7__create_audit_logs.sql`:

```sql
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    admin_id    BIGINT      NOT NULL,
    action      VARCHAR(100) NOT NULL,
    target_type VARCHAR(50)  NOT NULL,
    target_id   BIGINT       NOT NULL,
    details     TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_admin_id   ON audit_logs (admin_id);
```

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(admin): add AuditLog entity, repository, and migration V7"
```

---

### Task 2: AuditLogService + Unit Test

**Files:**
- Create: `src/main/java/com/worldcup/prediction/service/AuditLogService.java`
- Create: `src/test/java/com/worldcup/prediction/service/AuditLogServiceTest.java`

- [ ] **Step 1: Create AuditLogService**

```java
// src/main/java/com/worldcup/prediction/service/AuditLogService.java
package com.worldcup.prediction.service;

import com.worldcup.prediction.model.AuditLog;
import com.worldcup.prediction.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Persists one audit log entry.
     *
     * @param adminId    ID of the admin performing the action
     * @param action     Short constant, e.g. "APPROVE_USER"
     * @param targetType Domain object type, e.g. "USER"
     * @param targetId   PK of the affected record
     * @param details    Human-readable or JSON detail string (may be null)
     * @return the saved AuditLog
     */
    @Transactional
    public AuditLog log(Long adminId, String action, String targetType, Long targetId, String details) {
        AuditLog entry = new AuditLog(adminId, action, targetType, targetId, details);
        return auditLogRepository.save(entry);
    }

    /**
     * Returns the most recent {@code limit} audit log entries, newest first.
     */
    public List<AuditLog> getRecent(int limit) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }
}
```

- [ ] **Step 2: Write unit test**

```java
// src/test/java/com/worldcup/prediction/service/AuditLogServiceTest.java
package com.worldcup.prediction.service;

import com.worldcup.prediction.model.AuditLog;
import com.worldcup.prediction.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuditLogServiceTest {

    private AuditLogRepository auditLogRepository;
    private AuditLogService    auditLogService;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        auditLogService    = new AuditLogService(auditLogRepository);
    }

    @Test
    void log_savesEntryWithCorrectFields() {
        // arrange
        AuditLog saved = new AuditLog(1L, "APPROVE_USER", "USER", 42L, "User approved");
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(saved);

        // act
        AuditLog result = auditLogService.log(1L, "APPROVE_USER", "USER", 42L, "User approved");

        // assert – repository received an entry with the right values
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog captured = captor.getValue();
        assertThat(captured.getAdminId()).isEqualTo(1L);
        assertThat(captured.getAction()).isEqualTo("APPROVE_USER");
        assertThat(captured.getTargetType()).isEqualTo("USER");
        assertThat(captured.getTargetId()).isEqualTo(42L);
        assertThat(captured.getDetails()).isEqualTo("User approved");
        assertThat(captured.getCreatedAt()).isNotNull();

        // and the returned value is what the repo returned
        assertThat(result.getAction()).isEqualTo("APPROVE_USER");
    }

    @Test
    void log_persistsNullDetailsWithoutThrowing() {
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AuditLog result = auditLogService.log(1L, "CLOSE_WINDOW", "MATCH", 7L, null);

        assertThat(result.getDetails()).isNull();
        verify(auditLogRepository).save(any(AuditLog.class));
    }
}
```

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(admin): add AuditLogService with unit tests"
```

---

### Task 3: Spring Security — Admin Route Protection

**Files:**
- Edit: `src/main/java/com/worldcup/prediction/config/SecurityConfig.java`

- [ ] **Step 1: Add `/admin/**` authorization rule**

In `SecurityConfig.java`, add to the `HttpSecurity` chain — this must appear **before** the authenticated-user catch-all:

```java
// Inside the .authorizeHttpRequests(...) block, before the general authenticated rule:
.requestMatchers("/admin/**").hasRole("ADMIN")
```

Full example block for context (adjust to match existing SecurityConfig structure):

```java
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/", "/login**", "/error", "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
        .requestMatchers("/leaderboard", "/fixtures", "/groups", "/bracket").permitAll()
        .requestMatchers("/admin/**").hasRole("ADMIN")
        .anyRequest().authenticated()
    )
    // ... rest of existing config unchanged
```

- [ ] **Step 2: Verify admin role is assigned in CustomOAuth2UserService**

Confirm that when a User with `role == Role.ADMIN` completes OAuth2 login, the `GrantedAuthority` list returned by `CustomOAuth2UserService` includes `ROLE_ADMIN`. The authority string must exactly match `ROLE_ADMIN` (Spring Security prefix convention). No code change needed if already correct — just verify and note in this step.

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(admin): secure /admin/** routes with ROLE_ADMIN"
```

---

### Task 4: Admin Thymeleaf Layout

**Files:**
- Create: `src/main/resources/templates/admin/layout.html`

- [ ] **Step 1: Create admin sidebar layout**

```html
<!-- src/main/resources/templates/admin/layout.html -->
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title layout:title-pattern="$CONTENT_TITLE - WC2026 Admin">WC2026 Admin</title>
    <!-- Tailwind CSS via CDN (replace with build output in production) -->
    <script src="https://cdn.tailwindcss.com"></script>
    <script>
        tailwind.config = {
            theme: {
                extend: {
                    colors: {
                        'admin-dark':  '#006b2a',
                        'admin-mid':   '#008f38',
                        'admin-light': '#00c853',
                        'admin-bg':    '#f0fdf5',
                    }
                }
            }
        }
    </script>
    <!-- HTMX -->
    <script src="https://unpkg.com/htmx.org@1.9.12"></script>
    <!-- Alpine.js -->
    <script defer src="https://unpkg.com/alpinejs@3.x.x/dist/cdn.min.js"></script>
    <!-- Google Fonts: Bebas Neue + Inter -->
    <link href="https://fonts.googleapis.com/css2?family=Bebas+Neue&family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet"/>
    <style>
        body { font-family: 'Inter', sans-serif; }
        .font-bebas { font-family: 'Bebas Neue', cursive; }
    </style>
    <th:block layout:fragment="head-extra"/>
</head>
<body class="bg-admin-bg min-h-screen flex" hx-headers='{"X-CSRF-TOKEN": "[[${_csrf.token}]]"}'>

    <!-- Sidebar -->
    <aside class="w-64 bg-admin-dark text-white flex flex-col min-h-screen shadow-xl flex-shrink-0">
        <!-- Logo / Title -->
        <div class="px-6 py-5 border-b border-admin-mid">
            <span class="font-bebas text-3xl tracking-widest text-admin-light">WC2026</span>
            <span class="block text-xs text-green-300 uppercase tracking-widest mt-0.5">Admin Panel</span>
        </div>

        <!-- Navigation -->
        <nav class="flex-1 px-4 py-6 space-y-1">
            <a th:href="@{/admin}"
               th:classappend="${#strings.startsWith(#httpServletRequest.requestURI, '/admin') and #strings.equals(#httpServletRequest.requestURI, '/admin')} ? 'bg-admin-mid text-white' : 'text-green-200 hover:bg-admin-mid hover:text-white'"
               class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors duration-150">
                <!-- Dashboard icon -->
                <svg class="w-5 h-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"/>
                </svg>
                Dashboard
            </a>

            <a th:href="@{/admin/users}"
               th:classappend="${#strings.startsWith(#httpServletRequest.requestURI, '/admin/users')} ? 'bg-admin-mid text-white' : 'text-green-200 hover:bg-admin-mid hover:text-white'"
               class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors duration-150">
                <!-- Users icon -->
                <svg class="w-5 h-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"/>
                </svg>
                Users
            </a>

            <a th:href="@{/admin/matches}"
               th:classappend="${#strings.startsWith(#httpServletRequest.requestURI, '/admin/matches')} ? 'bg-admin-mid text-white' : 'text-green-200 hover:bg-admin-mid hover:text-white'"
               class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors duration-150">
                <!-- Matches icon -->
                <svg class="w-5 h-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/>
                </svg>
                Matches
            </a>

            <a th:href="@{/admin/predictions}"
               th:classappend="${#strings.startsWith(#httpServletRequest.requestURI, '/admin/predictions')} ? 'bg-admin-mid text-white' : 'text-green-200 hover:bg-admin-mid hover:text-white'"
               class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors duration-150">
                <!-- Predictions icon -->
                <svg class="w-5 h-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/>
                </svg>
                Predictions
            </a>
        </nav>

        <!-- Admin user footer -->
        <div class="px-4 py-4 border-t border-admin-mid">
            <div class="flex items-center gap-3">
                <img th:src="${#authentication.principal.avatarUrl ?: '/images/default-avatar.png'}"
                     class="w-8 h-8 rounded-full object-cover ring-2 ring-admin-light"
                     alt="Admin avatar"/>
                <div class="min-w-0">
                    <p class="text-sm font-medium text-white truncate"
                       th:text="${#authentication.principal.displayName ?: 'Admin'}">Admin</p>
                    <p class="text-xs text-green-300">Administrator</p>
                </div>
            </div>
            <form th:action="@{/logout}" method="post" class="mt-3">
                <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
                <button type="submit"
                        class="w-full text-xs text-green-300 hover:text-white py-1.5 rounded border border-green-700 hover:border-green-400 transition-colors duration-150">
                    Sign out
                </button>
            </form>
        </div>
    </aside>

    <!-- Main content -->
    <div class="flex-1 flex flex-col min-h-screen overflow-auto">
        <!-- Top bar -->
        <header class="bg-white border-b border-gray-200 px-8 py-4 flex items-center justify-between">
            <h1 class="font-bebas text-2xl tracking-wide text-gray-800 layout:fragment='page-title'">
                <th:block layout:fragment="page-title">Admin</th:block>
            </h1>
            <div class="text-xs text-gray-400" th:text="${#temporals.format(#temporals.createNow(), 'EEE d MMM yyyy')}"></div>
        </header>

        <!-- Flash messages -->
        <div th:if="${successMessage}" class="mx-8 mt-4 p-3 bg-green-100 border border-green-300 text-green-800 rounded-lg text-sm"
             th:text="${successMessage}"></div>
        <div th:if="${errorMessage}" class="mx-8 mt-4 p-3 bg-red-100 border border-red-300 text-red-800 rounded-lg text-sm"
             th:text="${errorMessage}"></div>

        <!-- Page content -->
        <main class="flex-1 p-8">
            <th:block layout:fragment="content"/>
        </main>
    </div>

</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git commit -m "feat(admin): add admin sidebar layout template"
```

---

### Task 5: AdminDashboardController + Template

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/admin/AdminDashboardController.java`
- Create: `src/main/resources/templates/admin/dashboard.html`

- [ ] **Step 1: Create AdminDashboardController**

```java
// src/main/java/com/worldcup/prediction/controller/admin/AdminDashboardController.java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.model.AuditLog;
import com.worldcup.prediction.model.Match;
import com.worldcup.prediction.model.User;
import com.worldcup.prediction.service.AuditLogService;
import com.worldcup.prediction.service.MatchService;
import com.worldcup.prediction.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final UserService     userService;
    private final MatchService    matchService;
    private final AuditLogService auditLogService;

    public AdminDashboardController(UserService userService,
                                    MatchService matchService,
                                    AuditLogService auditLogService) {
        this.userService     = userService;
        this.matchService    = matchService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public String dashboard(Model model) {
        // Pending registrations
        long pendingCount = userService.countByStatus(User.Status.PENDING);
        model.addAttribute("pendingCount", pendingCount);

        // Today's matches
        List<Match> todayMatches = matchService.findByDate(LocalDate.now());
        model.addAttribute("todayMatches", todayMatches);

        // Open prediction windows
        List<Match> openWindows = matchService.findByPredictionWindowOpen(true);
        model.addAttribute("openWindows", openWindows);

        // Recent audit log (last 10 entries)
        List<AuditLog> recentAudit = auditLogService.getRecent(10);
        model.addAttribute("recentAudit", recentAudit);

        return "admin/dashboard";
    }
}
```

- [ ] **Step 2: Create dashboard template**

```html
<!-- src/main/resources/templates/admin/dashboard.html -->
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{admin/layout}">
<head>
    <title>Dashboard</title>
</head>
<body>

<th:block layout:fragment="page-title">Dashboard</th:block>

<th:block layout:fragment="content">

    <!-- Stat cards row -->
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-6 mb-8">

        <!-- Pending registrations -->
        <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6 flex items-center gap-4">
            <div class="w-12 h-12 rounded-full bg-amber-100 flex items-center justify-center flex-shrink-0">
                <svg class="w-6 h-6 text-amber-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/>
                </svg>
            </div>
            <div>
                <p class="text-2xl font-bold text-gray-900" th:text="${pendingCount}">0</p>
                <p class="text-sm text-gray-500">Pending Registrations</p>
            </div>
            <a th:href="@{/admin/users(status='PENDING')}"
               class="ml-auto text-xs text-admin-dark hover:underline font-medium">View →</a>
        </div>

        <!-- Today's matches -->
        <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6 flex items-center gap-4">
            <div class="w-12 h-12 rounded-full bg-blue-100 flex items-center justify-center flex-shrink-0">
                <svg class="w-6 h-6 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/>
                </svg>
            </div>
            <div>
                <p class="text-2xl font-bold text-gray-900" th:text="${#lists.size(todayMatches)}">0</p>
                <p class="text-sm text-gray-500">Matches Today</p>
            </div>
            <a th:href="@{/admin/matches}" class="ml-auto text-xs text-admin-dark hover:underline font-medium">View →</a>
        </div>

        <!-- Open prediction windows -->
        <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6 flex items-center gap-4">
            <div class="w-12 h-12 rounded-full bg-green-100 flex items-center justify-center flex-shrink-0">
                <svg class="w-6 h-6 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
                </svg>
            </div>
            <div>
                <p class="text-2xl font-bold text-gray-900" th:text="${#lists.size(openWindows)}">0</p>
                <p class="text-sm text-gray-500">Open Prediction Windows</p>
            </div>
            <a th:href="@{/admin/matches}" class="ml-auto text-xs text-admin-dark hover:underline font-medium">Manage →</a>
        </div>
    </div>

    <!-- Two-column lower section -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">

        <!-- Today's match schedule -->
        <div class="bg-white rounded-xl shadow-sm border border-gray-100">
            <div class="px-6 py-4 border-b border-gray-100">
                <h2 class="font-semibold text-gray-800">Today's Schedule</h2>
            </div>
            <div class="divide-y divide-gray-50">
                <div th:if="${#lists.isEmpty(todayMatches)}"
                     class="px-6 py-8 text-center text-sm text-gray-400">
                    No matches scheduled for today.
                </div>
                <div th:each="match : ${todayMatches}"
                     class="px-6 py-3 flex items-center justify-between text-sm">
                    <div class="flex items-center gap-2">
                        <img th:src="'https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/' + ${match.homeTeam.flagCode} + '.svg'"
                             th:onerror="'this.src=\'https://flagcdn.com/w40/' + ${match.homeTeam.flagCode} + \'.png\''"
                             class="w-5 h-5 rounded-full object-cover" alt=""/>
                        <span class="font-medium text-gray-800" th:text="${match.homeTeam.name}">Home</span>
                        <span class="text-gray-400 text-xs">vs</span>
                        <span class="font-medium text-gray-800" th:text="${match.awayTeam.name}">Away</span>
                        <img th:src="'https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/' + ${match.awayTeam.flagCode} + '.svg'"
                             th:onerror="'this.src=\'https://flagcdn.com/w40/' + ${match.awayTeam.flagCode} + \'.png\''"
                             class="w-5 h-5 rounded-full object-cover" alt=""/>
                    </div>
                    <div class="flex items-center gap-2">
                        <span class="text-gray-500 text-xs"
                              th:text="${#temporals.format(match.kickoffTime, 'HH:mm')}">00:00</span>
                        <span th:if="${match.predictionWindowOpen}"
                              class="px-2 py-0.5 rounded-full bg-green-100 text-green-700 text-xs font-medium">Open</span>
                        <span th:unless="${match.predictionWindowOpen}"
                              class="px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 text-xs font-medium">Closed</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- Recent audit log -->
        <div class="bg-white rounded-xl shadow-sm border border-gray-100">
            <div class="px-6 py-4 border-b border-gray-100">
                <h2 class="font-semibold text-gray-800">Recent Audit Log</h2>
            </div>
            <div class="divide-y divide-gray-50">
                <div th:if="${#lists.isEmpty(recentAudit)}"
                     class="px-6 py-8 text-center text-sm text-gray-400">
                    No admin actions recorded yet.
                </div>
                <div th:each="entry : ${recentAudit}"
                     class="px-6 py-3 flex items-start justify-between text-sm gap-2">
                    <div class="min-w-0">
                        <span class="inline-block px-2 py-0.5 rounded text-xs font-mono font-medium bg-gray-100 text-gray-700 mr-2"
                              th:text="${entry.action}">ACTION</span>
                        <span class="text-gray-600 text-xs"
                              th:text="${entry.targetType + ' #' + entry.targetId}">USER #1</span>
                        <p th:if="${entry.details}" class="text-xs text-gray-400 mt-0.5 truncate"
                           th:text="${entry.details}"></p>
                    </div>
                    <span class="text-xs text-gray-400 flex-shrink-0"
                          th:text="${#temporals.format(entry.createdAt, 'dd MMM HH:mm')}">01 Jun 12:00</span>
                </div>
            </div>
        </div>

    </div>

</th:block>
</body>
</html>
```

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(admin): add AdminDashboardController and dashboard template"
```

---

### Task 6: AdminUserController + Users Template

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/admin/AdminUserController.java`
- Create: `src/main/resources/templates/admin/users.html`

- [ ] **Step 1: Create AdminUserController**

Note: `EmailService.sendApprovalEmail(user)` and `EmailService.sendRejectionEmail(user)` are stubs that log to console — real implementation is Part 8.

```java
// src/main/java/com/worldcup/prediction/controller/admin/AdminUserController.java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.model.User;
import com.worldcup.prediction.service.AuditLogService;
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService     userService;
    private final AuditLogService auditLogService;
    private final EmailService    emailService;

    public AdminUserController(UserService userService,
                               AuditLogService auditLogService,
                               EmailService emailService) {
        this.userService     = userService;
        this.auditLogService = auditLogService;
        this.emailService    = emailService;
    }

    // --- List all users ---

    @GetMapping
    public String listUsers(@RequestParam(required = false) String status,
                            Model model) {
        List<User> users = (status != null && !status.isBlank())
                ? userService.findByStatus(User.Status.valueOf(status.toUpperCase()))
                : userService.findAll();

        model.addAttribute("users", users);
        model.addAttribute("statusFilter", status);
        return "admin/users";
    }

    // --- Approve ---

    @PostMapping("/{id}/approve")
    public String approveUser(@PathVariable Long id,
                              @AuthenticationPrincipal(expression = "id") Long adminId,
                              Model model) {
        User user = userService.setStatus(id, User.Status.ACTIVE);
        emailService.sendApprovalEmail(user);    // stub — logs only
        auditLogService.log(adminId, "APPROVE_USER", "USER", id,
                "User " + user.getEmail() + " approved");
        model.addAttribute("user", user);
        return "admin/users :: userRow";         // HTMX fragment
    }

    // --- Reject ---

    @PostMapping("/{id}/reject")
    public String rejectUser(@PathVariable Long id,
                             @AuthenticationPrincipal(expression = "id") Long adminId,
                             Model model) {
        User user = userService.setStatus(id, User.Status.REJECTED);
        emailService.sendRejectionEmail(user);   // stub — logs only
        auditLogService.log(adminId, "REJECT_USER", "USER", id,
                "User " + user.getEmail() + " rejected");
        model.addAttribute("user", user);
        return "admin/users :: userRow";
    }

    // --- Enable (re-activate a disabled account) ---

    @PostMapping("/{id}/enable")
    public String enableUser(@PathVariable Long id,
                             @AuthenticationPrincipal(expression = "id") Long adminId,
                             Model model) {
        User user = userService.setStatus(id, User.Status.ACTIVE);
        auditLogService.log(adminId, "ENABLE_USER", "USER", id,
                "User " + user.getEmail() + " enabled");
        model.addAttribute("user", user);
        return "admin/users :: userRow";
    }

    // --- Disable ---

    @PostMapping("/{id}/disable")
    public String disableUser(@PathVariable Long id,
                              @AuthenticationPrincipal(expression = "id") Long adminId,
                              Model model) {
        User user = userService.setStatus(id, User.Status.DISABLED);
        auditLogService.log(adminId, "DISABLE_USER", "USER", id,
                "User " + user.getEmail() + " disabled");
        model.addAttribute("user", user);
        return "admin/users :: userRow";
    }
}
```

- [ ] **Step 2: Ensure UserService has required methods**

Verify (or add) these methods to `UserService`:

```java
// Returns users by status
List<User> findByStatus(User.Status status);

// Returns all users, ordered by registration date desc
List<User> findAll();

// Sets user status, saves, returns updated user — throws if not found
User setStatus(Long userId, User.Status status);

// Counts users by status
long countByStatus(User.Status status);
```

Example `setStatus` implementation to add if not present:

```java
@Transactional
public User setStatus(Long userId, User.Status status) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    user.setStatus(status);
    return userRepository.save(user);
}
```

- [ ] **Step 3: Ensure EmailService stub exists**

Create or confirm `src/main/java/com/worldcup/prediction/service/EmailService.java`:

```java
// src/main/java/com/worldcup/prediction/service/EmailService.java
package com.worldcup.prediction.service;

import com.worldcup.prediction.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Email service stub. All methods log intent only.
 * Part 8 replaces these with real SMTP / transactional email.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public void sendApprovalEmail(User user) {
        log.info("[EMAIL STUB] Approval email would be sent to: {}", user.getEmail());
    }

    public void sendRejectionEmail(User user) {
        log.info("[EMAIL STUB] Rejection email would be sent to: {}", user.getEmail());
    }

    public void sendPredictionReminder(User user, String matchInfo) {
        log.info("[EMAIL STUB] Reminder would be sent to {} for match: {}", user.getEmail(), matchInfo);
    }
}
```

- [ ] **Step 4: Create users template with HTMX row replacement**

```html
<!-- src/main/resources/templates/admin/users.html -->
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{admin/layout}">
<head><title>Users</title></head>
<body>

<th:block layout:fragment="page-title">User Management</th:block>

<th:block layout:fragment="content">

    <!-- Filter bar -->
    <div class="flex items-center gap-2 mb-6 flex-wrap">
        <a th:href="@{/admin/users}"
           th:classappend="${statusFilter == null} ? 'bg-admin-dark text-white' : 'bg-white text-gray-600 hover:bg-gray-50 border border-gray-200'"
           class="px-4 py-2 rounded-lg text-sm font-medium transition-colors duration-150">All</a>
        <a th:href="@{/admin/users(status='PENDING')}"
           th:classappend="${statusFilter == 'PENDING'} ? 'bg-amber-500 text-white' : 'bg-white text-gray-600 hover:bg-gray-50 border border-gray-200'"
           class="px-4 py-2 rounded-lg text-sm font-medium transition-colors duration-150">Pending</a>
        <a th:href="@{/admin/users(status='ACTIVE')}"
           th:classappend="${statusFilter == 'ACTIVE'} ? 'bg-green-600 text-white' : 'bg-white text-gray-600 hover:bg-gray-50 border border-gray-200'"
           class="px-4 py-2 rounded-lg text-sm font-medium transition-colors duration-150">Active</a>
        <a th:href="@{/admin/users(status='DISABLED')}"
           th:classappend="${statusFilter == 'DISABLED'} ? 'bg-gray-600 text-white' : 'bg-white text-gray-600 hover:bg-gray-50 border border-gray-200'"
           class="px-4 py-2 rounded-lg text-sm font-medium transition-colors duration-150">Disabled</a>
        <a th:href="@{/admin/users(status='REJECTED')}"
           th:classappend="${statusFilter == 'REJECTED'} ? 'bg-red-600 text-white' : 'bg-white text-gray-600 hover:bg-gray-50 border border-gray-200'"
           class="px-4 py-2 rounded-lg text-sm font-medium transition-colors duration-150">Rejected</a>
        <span class="ml-auto text-sm text-gray-400" th:text="${#lists.size(users)} + ' users'"></span>
    </div>

    <!-- Users table -->
    <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
        <table class="w-full text-sm">
            <thead>
                <tr class="bg-gray-50 border-b border-gray-100 text-left">
                    <th class="px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">User</th>
                    <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Status</th>
                    <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Role</th>
                    <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Registered</th>
                    <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Provider</th>
                    <th class="px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider text-right">Actions</th>
                </tr>
            </thead>
            <tbody id="users-table-body" class="divide-y divide-gray-50">
                <tr th:if="${#lists.isEmpty(users)}" class="text-center text-gray-400 text-sm">
                    <td colspan="6" class="py-12">No users found.</td>
                </tr>

                <!-- User row — also used as HTMX fragment target -->
                <tr th:each="user : ${users}"
                    th:fragment="userRow"
                    th:id="'user-row-' + ${user.id}"
                    class="hover:bg-gray-50 transition-colors duration-100">

                    <!-- Avatar + name + email -->
                    <td class="px-6 py-3">
                        <div class="flex items-center gap-3">
                            <img th:src="${user.avatarUrl ?: '/images/default-avatar.png'}"
                                 class="w-8 h-8 rounded-full object-cover flex-shrink-0"
                                 alt=""/>
                            <div class="min-w-0">
                                <p class="font-medium text-gray-900 truncate"
                                   th:text="${user.displayName}">Name</p>
                                <p class="text-xs text-gray-400 truncate"
                                   th:text="${user.email}">email@example.com</p>
                            </div>
                        </div>
                    </td>

                    <!-- Status badge -->
                    <td class="px-4 py-3">
                        <span th:switch="${user.status.name()}">
                            <span th:case="'PENDING'"
                                  class="px-2 py-1 rounded-full text-xs font-medium bg-amber-100 text-amber-700">Pending</span>
                            <span th:case="'ACTIVE'"
                                  class="px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-700">Active</span>
                            <span th:case="'DISABLED'"
                                  class="px-2 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-600">Disabled</span>
                            <span th:case="'REJECTED'"
                                  class="px-2 py-1 rounded-full text-xs font-medium bg-red-100 text-red-700">Rejected</span>
                            <span th:case="*"
                                  class="px-2 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-500"
                                  th:text="${user.status}">Unknown</span>
                        </span>
                    </td>

                    <!-- Role -->
                    <td class="px-4 py-3">
                        <span th:if="${user.role.name() == 'ADMIN'}"
                              class="px-2 py-1 rounded text-xs font-medium bg-purple-100 text-purple-700">Admin</span>
                        <span th:unless="${user.role.name() == 'ADMIN'}"
                              class="px-2 py-1 rounded text-xs font-medium bg-blue-50 text-blue-600">Participant</span>
                    </td>

                    <!-- Registration date -->
                    <td class="px-4 py-3 text-xs text-gray-500"
                        th:text="${#temporals.format(user.createdAt, 'dd MMM yyyy')}">01 Jan 2026</td>

                    <!-- Provider -->
                    <td class="px-4 py-3 text-xs text-gray-500 capitalize"
                        th:text="${user.provider ?: '-'}">google</td>

                    <!-- Actions (HTMX) -->
                    <td class="px-6 py-3">
                        <div class="flex items-center justify-end gap-2 flex-wrap">

                            <!-- Approve (only when PENDING or REJECTED) -->
                            <button th:if="${user.status.name() == 'PENDING' or user.status.name() == 'REJECTED'}"
                                    th:hx-post="@{'/admin/users/' + ${user.id} + '/approve'}"
                                    hx-target="#user-row-[[${user.id}]]"
                                    hx-swap="outerHTML"
                                    hx-include="[name='_csrf']"
                                    class="px-3 py-1 text-xs font-medium rounded-md bg-green-600 hover:bg-green-700 text-white transition-colors duration-150">
                                Approve
                            </button>

                            <!-- Reject (only when PENDING) -->
                            <button th:if="${user.status.name() == 'PENDING'}"
                                    th:hx-post="@{'/admin/users/' + ${user.id} + '/reject'}"
                                    hx-target="#user-row-[[${user.id}]]"
                                    hx-swap="outerHTML"
                                    hx-include="[name='_csrf']"
                                    class="px-3 py-1 text-xs font-medium rounded-md bg-red-500 hover:bg-red-600 text-white transition-colors duration-150">
                                Reject
                            </button>

                            <!-- Disable (only when ACTIVE) -->
                            <button th:if="${user.status.name() == 'ACTIVE' and user.role.name() != 'ADMIN'}"
                                    th:hx-post="@{'/admin/users/' + ${user.id} + '/disable'}"
                                    hx-target="#user-row-[[${user.id}]]"
                                    hx-swap="outerHTML"
                                    hx-include="[name='_csrf']"
                                    class="px-3 py-1 text-xs font-medium rounded-md bg-gray-200 hover:bg-gray-300 text-gray-700 transition-colors duration-150">
                                Disable
                            </button>

                            <!-- Enable (only when DISABLED) -->
                            <button th:if="${user.status.name() == 'DISABLED'}"
                                    th:hx-post="@{'/admin/users/' + ${user.id} + '/enable'}"
                                    hx-target="#user-row-[[${user.id}]]"
                                    hx-swap="outerHTML"
                                    hx-include="[name='_csrf']"
                                    class="px-3 py-1 text-xs font-medium rounded-md bg-admin-dark hover:bg-admin-mid text-white transition-colors duration-150">
                                Enable
                            </button>

                        </div>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>

    <!-- Hidden CSRF for HTMX requests -->
    <input type="hidden" name="_csrf" th:value="${_csrf.token}"/>

</th:block>
</body>
</html>
```

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(admin): add AdminUserController, EmailService stub, and users template"
```

---

### Task 7: AdminMatchController + Matches Template

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/admin/AdminMatchController.java`
- Create: `src/main/resources/templates/admin/matches.html`

- [ ] **Step 1: Create AdminMatchController**

```java
// src/main/java/com/worldcup/prediction/controller/admin/AdminMatchController.java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.model.Match;
import com.worldcup.prediction.model.User;
import com.worldcup.prediction.service.AuditLogService;
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.service.MatchService;
import com.worldcup.prediction.service.ScoringService;
import com.worldcup.prediction.service.UserService;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

@Controller
@RequestMapping("/admin/matches")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMatchController {

    private final MatchService    matchService;
    private final ScoringService  scoringService;
    private final AuditLogService auditLogService;
    private final EmailService    emailService;
    private final UserService     userService;

    public AdminMatchController(MatchService matchService,
                                ScoringService scoringService,
                                AuditLogService auditLogService,
                                EmailService emailService,
                                UserService userService) {
        this.matchService    = matchService;
        this.scoringService  = scoringService;
        this.auditLogService = auditLogService;
        this.emailService    = emailService;
        this.userService     = userService;
    }

    // --- List all matches ---

    @GetMapping
    public String listMatches(Model model) {
        List<Match> matches = matchService.findAllOrderByKickoffAsc();
        model.addAttribute("matches", matches);
        return "admin/matches";
    }

    // --- Enter result ---

    @PostMapping("/{id}/result")
    public String enterResult(@PathVariable Long id,
                              @RequestParam @Min(0) int homeScore,
                              @RequestParam @Min(0) int awayScore,
                              @AuthenticationPrincipal(expression = "id") Long adminId,
                              RedirectAttributes redirectAttributes) {
        Match match = matchService.setResult(id, homeScore, awayScore);
        scoringService.scoreAllPredictions(id);
        auditLogService.log(adminId, "ENTER_RESULT", "MATCH", id,
                match.getHomeTeam().getName() + " " + homeScore
                + "-" + awayScore + " " + match.getAwayTeam().getName());
        redirectAttributes.addFlashAttribute("successMessage",
                "Result entered: " + match.getHomeTeam().getName()
                + " " + homeScore + "–" + awayScore + " " + match.getAwayTeam().getName());
        return "redirect:/admin/matches";
    }

    // --- Open prediction window ---

    @PostMapping("/{id}/open-window")
    public String openWindow(@PathVariable Long id,
                             @AuthenticationPrincipal(expression = "id") Long adminId,
                             RedirectAttributes redirectAttributes) {
        Match match = matchService.setPredictionWindowOpen(id, true);
        auditLogService.log(adminId, "OPEN_WINDOW", "MATCH", id,
                "Prediction window opened for: " + match.getHomeTeam().getName()
                + " vs " + match.getAwayTeam().getName());
        redirectAttributes.addFlashAttribute("successMessage",
                "Prediction window opened for " + match.getHomeTeam().getName()
                + " vs " + match.getAwayTeam().getName());
        return "redirect:/admin/matches";
    }

    // --- Close prediction window ---

    @PostMapping("/{id}/close-window")
    public String closeWindow(@PathVariable Long id,
                              @AuthenticationPrincipal(expression = "id") Long adminId,
                              RedirectAttributes redirectAttributes) {
        Match match = matchService.setPredictionWindowOpen(id, false);
        auditLogService.log(adminId, "CLOSE_WINDOW", "MATCH", id,
                "Prediction window closed for: " + match.getHomeTeam().getName()
                + " vs " + match.getAwayTeam().getName());
        redirectAttributes.addFlashAttribute("successMessage",
                "Prediction window closed for " + match.getHomeTeam().getName()
                + " vs " + match.getAwayTeam().getName());
        return "redirect:/admin/matches";
    }

    // --- Send reminder (stub) ---

    @PostMapping("/{id}/send-reminder")
    public String sendReminder(@PathVariable Long id,
                               @AuthenticationPrincipal(expression = "id") Long adminId,
                               RedirectAttributes redirectAttributes) {
        Match match = matchService.findById(id);
        String matchInfo = match.getHomeTeam().getName() + " vs " + match.getAwayTeam().getName();

        // Stub: log reminder intent per active participant
        List<User> activeUsers = userService.findByStatus(User.Status.ACTIVE);
        activeUsers.forEach(u -> emailService.sendPredictionReminder(u, matchInfo));

        auditLogService.log(adminId, "SEND_REMINDER", "MATCH", id,
                "Reminder triggered for: " + matchInfo
                + " (" + activeUsers.size() + " recipients)");
        redirectAttributes.addFlashAttribute("successMessage",
                "Reminder logged for " + activeUsers.size() + " participants (stub — no email sent yet).");
        return "redirect:/admin/matches";
    }
}
```

- [ ] **Step 2: Ensure MatchService has required methods**

Verify (or add) to `MatchService`:

```java
// Returns all matches ordered by kickoff ascending
List<Match> findAllOrderByKickoffAsc();

// Returns matches on a given date
List<Match> findByDate(LocalDate date);

// Returns matches with the given window-open state
List<Match> findByPredictionWindowOpen(boolean open);

// Sets homeScore / awayScore on the match and marks it completed
Match setResult(Long matchId, int homeScore, int awayScore);

// Opens or closes the prediction window flag
Match setPredictionWindowOpen(Long matchId, boolean open);

// Finds match by ID, throws if not found
Match findById(Long matchId);
```

- [ ] **Step 3: Ensure ScoringService has scoreAllPredictions**

Verify (or add) to `ScoringService`:

```java
/**
 * Scores all Prediction records for the given match against its stored result.
 * Calls scoreMatch(matchId, homeScore, awayScore) internally.
 */
void scoreAllPredictions(Long matchId);
```

- [ ] **Step 4: Create matches template**

```html
<!-- src/main/resources/templates/admin/matches.html -->
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{admin/layout}">
<head><title>Matches</title></head>
<body>

<th:block layout:fragment="page-title">Match Management</th:block>

<th:block layout:fragment="content">

    <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
        <div class="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
            <h2 class="font-semibold text-gray-800">All Matches</h2>
            <span class="text-sm text-gray-400" th:text="${#lists.size(matches)} + ' total'"></span>
        </div>

        <table class="w-full text-sm">
            <thead>
                <tr class="bg-gray-50 border-b border-gray-100 text-left">
                    <th class="px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Match</th>
                    <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Kickoff</th>
                    <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Phase</th>
                    <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Result</th>
                    <th class="px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Window</th>
                    <th class="px-6 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">Actions</th>
                </tr>
            </thead>
            <tbody class="divide-y divide-gray-50">
                <tr th:if="${#lists.isEmpty(matches)}" class="text-center">
                    <td colspan="6" class="py-12 text-sm text-gray-400">No matches found.</td>
                </tr>

                <tr th:each="match : ${matches}"
                    class="hover:bg-gray-50 transition-colors duration-100">

                    <!-- Match name -->
                    <td class="px-6 py-3">
                        <div class="flex items-center gap-2">
                            <img th:src="'https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/' + ${match.homeTeam.flagCode} + '.svg'"
                                 class="w-5 h-5 rounded-full object-cover" alt=""/>
                            <span class="font-medium text-gray-900" th:text="${match.homeTeam.name}">Home</span>
                            <span class="text-gray-400 text-xs">vs</span>
                            <span class="font-medium text-gray-900" th:text="${match.awayTeam.name}">Away</span>
                            <img th:src="'https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/' + ${match.awayTeam.flagCode} + '.svg'"
                                 class="w-5 h-5 rounded-full object-cover" alt=""/>
                        </div>
                    </td>

                    <!-- Kickoff time -->
                    <td class="px-4 py-3 text-xs text-gray-600"
                        th:text="${#temporals.format(match.kickoffTime, 'dd MMM HH:mm')}">01 Jun 18:00</td>

                    <!-- Phase -->
                    <td class="px-4 py-3 text-xs text-gray-500" th:text="${match.phase}">GS</td>

                    <!-- Result -->
                    <td class="px-4 py-3">
                        <span th:if="${match.completed}"
                              class="font-mono font-semibold text-gray-900"
                              th:text="${match.homeScore} + '–' + ${match.awayScore}">0–0</span>
                        <span th:unless="${match.completed}"
                              class="text-gray-400 text-xs">Pending</span>
                    </td>

                    <!-- Window status -->
                    <td class="px-4 py-3">
                        <span th:if="${match.predictionWindowOpen}"
                              class="px-2 py-0.5 rounded-full bg-green-100 text-green-700 text-xs font-medium">Open</span>
                        <span th:unless="${match.predictionWindowOpen}"
                              class="px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 text-xs font-medium">Closed</span>
                    </td>

                    <!-- Actions -->
                    <td class="px-6 py-3">
                        <div class="flex items-center gap-2 flex-wrap" x-data="{ showResultForm: false }">

                            <!-- Enter/update result -->
                            <button @click="showResultForm = !showResultForm"
                                    class="px-3 py-1 text-xs font-medium rounded-md bg-admin-dark hover:bg-admin-mid text-white transition-colors duration-150">
                                <span th:text="${match.completed} ? 'Update Result' : 'Enter Result'">Enter Result</span>
                            </button>

                            <!-- Inline result form (Alpine toggle) -->
                            <div x-show="showResultForm"
                                 x-transition
                                 class="w-full mt-2">
                                <form th:action="@{'/admin/matches/' + ${match.id} + '/result'}" method="post"
                                      class="flex items-center gap-2 bg-gray-50 rounded-lg px-3 py-2 border border-gray-200">
                                    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
                                    <label class="text-xs text-gray-500">Home</label>
                                    <input type="number" name="homeScore" min="0" max="99"
                                           th:value="${match.homeScore ?: 0}"
                                           class="w-14 text-center border border-gray-300 rounded px-2 py-1 text-sm focus:ring-1 focus:ring-admin-light focus:outline-none"/>
                                    <span class="text-gray-400 font-bold">–</span>
                                    <input type="number" name="awayScore" min="0" max="99"
                                           th:value="${match.awayScore ?: 0}"
                                           class="w-14 text-center border border-gray-300 rounded px-2 py-1 text-sm focus:ring-1 focus:ring-admin-light focus:outline-none"/>
                                    <label class="text-xs text-gray-500">Away</label>
                                    <button type="submit"
                                            class="px-3 py-1 text-xs font-medium rounded-md bg-green-600 hover:bg-green-700 text-white transition-colors duration-150">
                                        Save
                                    </button>
                                    <button type="button" @click="showResultForm = false"
                                            class="px-3 py-1 text-xs font-medium rounded-md bg-gray-200 hover:bg-gray-300 text-gray-700 transition-colors duration-150">
                                        Cancel
                                    </button>
                                </form>
                            </div>

                            <!-- Open / Close window -->
                            <form th:if="${!match.predictionWindowOpen}"
                                  th:action="@{'/admin/matches/' + ${match.id} + '/open-window'}" method="post"
                                  class="inline">
                                <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
                                <button type="submit"
                                        class="px-3 py-1 text-xs font-medium rounded-md bg-green-100 hover:bg-green-200 text-green-800 transition-colors duration-150">
                                    Open Window
                                </button>
                            </form>

                            <form th:if="${match.predictionWindowOpen}"
                                  th:action="@{'/admin/matches/' + ${match.id} + '/close-window'}" method="post"
                                  class="inline">
                                <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
                                <button type="submit"
                                        class="px-3 py-1 text-xs font-medium rounded-md bg-red-100 hover:bg-red-200 text-red-800 transition-colors duration-150">
                                    Close Window
                                </button>
                            </form>

                            <!-- Send reminder -->
                            <form th:action="@{'/admin/matches/' + ${match.id} + '/send-reminder'}" method="post"
                                  class="inline">
                                <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
                                <button type="submit"
                                        class="px-3 py-1 text-xs font-medium rounded-md bg-blue-100 hover:bg-blue-200 text-blue-800 transition-colors duration-150">
                                    Reminder
                                </button>
                            </form>

                        </div>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>

</th:block>
</body>
</html>
```

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(admin): add AdminMatchController and matches template"
```

---

### Task 8: AdminPredictionController + Predictions Template

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/admin/AdminPredictionController.java`
- Create: `src/main/resources/templates/admin/predictions.html`

- [ ] **Step 1: Create AdminPredictionController**

```java
// src/main/java/com/worldcup/prediction/controller/admin/AdminPredictionController.java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.model.Match;
import com.worldcup.prediction.model.Prediction;
import com.worldcup.prediction.service.AuditLogService;
import com.worldcup.prediction.service.MatchService;
import com.worldcup.prediction.service.PredictionService;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

@Controller
@RequestMapping("/admin/predictions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPredictionController {

    private final PredictionService predictionService;
    private final MatchService      matchService;
    private final AuditLogService   auditLogService;

    public AdminPredictionController(PredictionService predictionService,
                                     MatchService matchService,
                                     AuditLogService auditLogService) {
        this.predictionService = predictionService;
        this.matchService      = matchService;
        this.auditLogService   = auditLogService;
    }

    // --- View all predictions for a match ---

    @GetMapping
    public String listPredictions(@RequestParam(required = false) Long matchId,
                                  Model model) {
        List<Match> allMatches = matchService.findAllOrderByKickoffAsc();
        model.addAttribute("allMatches", allMatches);

        if (matchId != null) {
            Match selectedMatch = matchService.findById(matchId);
            List<Prediction> predictions = predictionService.findAllByMatchId(matchId);
            model.addAttribute("selectedMatch", selectedMatch);
            model.addAttribute("predictions", predictions);
        }

        model.addAttribute("matchId", matchId);
        return "admin/predictions";
    }

    // --- Override a prediction ---

    @PostMapping("/{id}/override")
    public String overridePrediction(@PathVariable Long id,
                                     @RequestParam @Min(0) int homeScore,
                                     @RequestParam @Min(0) int awayScore,
                                     @AuthenticationPrincipal(expression = "id") Long adminId,
                                     RedirectAttributes redirectAttributes) {
        Prediction prediction = predictionService.overridePrediction(id, homeScore, awayScore);
        auditLogService.log(adminId, "OVERRIDE_PREDICTION", "PREDICTION", id,
                "Override: " + homeScore + "–" + awayScore
                + " for prediction by userId=" + prediction.getUser().getId()
                + " on matchId=" + prediction.getMatch().getId());
        redirectAttributes.addFlashAttribute("successMessage",
                "Prediction #" + id + " overridden to " + homeScore + "–" + awayScore);
        return "redirect:/admin/predictions?matchId=" + prediction.getMatch().getId();
    }
}
```

- [ ] **Step 2: Ensure PredictionService has required methods**

Verify (or add) to `PredictionService`:

```java
// Returns ALL predictions for a match, regardless of lock status (admin use only)
List<Prediction> findAllByMatchId(Long matchId);

// Updates the homeScore/awayScore of an existing prediction; re-scores if match has a result
Prediction overridePrediction(Long predictionId, int homeScore, int awayScore);
```

Example `overridePrediction` implementation:

```java
@Transactional
public Prediction overridePrediction(Long predictionId, int homeScore, int awayScore) {
    Prediction p = predictionRepository.findById(predictionId)
            .orElseThrow(() -> new EntityNotFoundException("Prediction not found: " + predictionId));
    p.setHomeScore(homeScore);
    p.setAwayScore(awayScore);
    // Re-score immediately if the match result is already recorded
    if (p.getMatch().isCompleted()) {
        int points = scoringService.calculatePoints(
                homeScore, awayScore,
                p.getMatch().getHomeScore(), p.getMatch().getAwayScore());
        p.setPoints(points);
    }
    return predictionRepository.save(p);
}
```

- [ ] **Step 3: Create predictions template**

```html
<!-- src/main/resources/templates/admin/predictions.html -->
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{admin/layout}">
<head><title>Predictions</title></head>
<body>

<th:block layout:fragment="page-title">Predictions — Admin View</th:block>

<th:block layout:fragment="content">

    <!-- Match selector -->
    <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-6">
        <h2 class="text-sm font-semibold text-gray-700 mb-3">Select a Match</h2>
        <form th:action="@{/admin/predictions}" method="get" class="flex items-center gap-3 flex-wrap">
            <select name="matchId"
                    class="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-1 focus:ring-admin-light focus:outline-none min-w-64">
                <option value="">-- Choose match --</option>
                <option th:each="m : ${allMatches}"
                        th:value="${m.id}"
                        th:selected="${m.id == matchId}"
                        th:text="${m.homeTeam.name} + ' vs ' + ${m.awayTeam.name} + ' (' + ${#temporals.format(m.kickoffTime, 'dd MMM')} + ')'">
                    Match
                </option>
            </select>
            <button type="submit"
                    class="px-4 py-2 text-sm font-medium bg-admin-dark hover:bg-admin-mid text-white rounded-lg transition-colors duration-150">
                Load Predictions
            </button>
        </form>
    </div>

    <!-- Predictions table (shown only when a match is selected) -->
    <div th:if="${selectedMatch != null}"
         class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">

        <!-- Match header -->
        <div class="px-6 py-4 border-b border-gray-100 bg-gray-50 flex items-center justify-between">
            <div class="flex items-center gap-3">
                <img th:src="'https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/' + ${selectedMatch.homeTeam.flagCode} + '.svg'"
                     class="w-6 h-6 rounded-full object-cover" alt=""/>
                <span class="font-semibold text-gray-900" th:text="${selectedMatch.homeTeam.name}">Home</span>
                <span class="text-gray-400">vs</span>
                <span class="font-semibold text-gray-900" th:text="${selectedMatch.awayTeam.name}">Away</span>
                <img th:src="'https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/' + ${selectedMatch.awayTeam.flagCode} + '.svg'"
                     class="w-6 h-6 rounded-full object-cover" alt=""/>
            </div>
            <div class="flex items-center gap-3 text-sm">
                <span class="text-gray-500"
                      th:text="${#temporals.format(selectedMatch.kickoffTime, 'dd MMM yyyy HH:mm')}"></span>
                <span th:if="${selectedMatch.completed}"
                      class="font-mono font-bold text-gray-900"
                      th:text="'Result: ' + ${selectedMatch.homeScore} + '–' + ${selectedMatch.awayScore}"></span>
                <span th:unless="${selectedMatch.completed}"
                      class="text-amber-600 text-xs font-medium">No result yet</span>
                <span class="text-gray-400 text-xs"
                      th:text="${#lists.size(predictions)} + ' predictions'"></span>
            </div>
        </div>

        <!-- Table -->
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

                <tr th:each="pred : ${predictions}"
                    class="hover:bg-gray-50 transition-colors duration-100"
                    x-data="{ editing: false }">

                    <!-- Participant -->
                    <td class="px-6 py-3">
                        <div class="flex items-center gap-2">
                            <img th:src="${pred.user.avatarUrl ?: '/images/default-avatar.png'}"
                                 class="w-7 h-7 rounded-full object-cover" alt=""/>
                            <span class="font-medium text-gray-800"
                                  th:text="${pred.user.displayName}">User</span>
                        </div>
                    </td>

                    <!-- Predicted score -->
                    <td class="px-4 py-3">
                        <span class="font-mono font-semibold text-gray-900"
                              th:text="${pred.homeScore} + '–' + ${pred.awayScore}">0–0</span>
                    </td>

                    <!-- Points with color coding -->
                    <td class="px-4 py-3">
                        <span th:if="${pred.points != null}"
                              th:text="${pred.points}"
                              th:class="${pred.points == 3} ? 'font-bold text-green-600' :
                                        ${pred.points == 2} ? 'font-bold text-blue-600' :
                                        ${pred.points == 1} ? 'font-bold text-yellow-600' :
                                        'font-bold text-red-500'">0</span>
                        <span th:unless="${pred.points != null}" class="text-gray-400 text-xs">–</span>
                    </td>

                    <!-- Submitted at -->
                    <td class="px-4 py-3 text-xs text-gray-500"
                        th:text="${pred.submittedAt != null ? #temporals.format(pred.submittedAt, 'dd MMM HH:mm') : '–'}"></td>

                    <!-- Override form (Alpine toggle) -->
                    <td class="px-6 py-3">
                        <button @click="editing = !editing"
                                class="px-3 py-1 text-xs font-medium rounded-md bg-amber-100 hover:bg-amber-200 text-amber-800 transition-colors duration-150">
                            Override
                        </button>

                        <div x-show="editing"
                             x-transition
                             class="mt-2">
                            <form th:action="@{'/admin/predictions/' + ${pred.id} + '/override'}" method="post"
                                  class="flex items-center gap-2 bg-amber-50 rounded-lg px-3 py-2 border border-amber-200">
                                <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
                                <label class="text-xs text-gray-500">H</label>
                                <input type="number" name="homeScore" min="0" max="99"
                                       th:value="${pred.homeScore}"
                                       class="w-12 text-center border border-gray-300 rounded px-1 py-1 text-sm focus:ring-1 focus:ring-amber-400 focus:outline-none"/>
                                <span class="text-gray-400">–</span>
                                <input type="number" name="awayScore" min="0" max="99"
                                       th:value="${pred.awayScore}"
                                       class="w-12 text-center border border-gray-300 rounded px-1 py-1 text-sm focus:ring-1 focus:ring-amber-400 focus:outline-none"/>
                                <label class="text-xs text-gray-500">A</label>
                                <button type="submit"
                                        class="px-3 py-1 text-xs font-medium rounded-md bg-amber-500 hover:bg-amber-600 text-white transition-colors duration-150">
                                    Save
                                </button>
                                <button type="button" @click="editing = false"
                                        class="px-2 py-1 text-xs text-gray-500 hover:text-gray-700">
                                    Cancel
                                </button>
                            </form>
                        </div>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>

    <!-- Placeholder when no match selected -->
    <div th:unless="${selectedMatch != null}"
         class="bg-white rounded-xl shadow-sm border border-gray-100 p-12 text-center text-gray-400 text-sm">
        Select a match above to view and manage predictions.
    </div>

</th:block>
</body>
</html>
```

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(admin): add AdminPredictionController and predictions template"
```

---

### Task 9: Integration Tests (MockMvc)

**Files:**
- Create: `src/test/java/com/worldcup/prediction/controller/admin/AdminUserControllerTest.java`
- Create: `src/test/java/com/worldcup/prediction/controller/admin/AdminMatchControllerTest.java`

- [ ] **Step 1: AdminUserControllerTest**

```java
// src/test/java/com/worldcup/prediction/controller/admin/AdminUserControllerTest.java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.model.User;
import com.worldcup.prediction.service.AuditLogService;
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminUserController.class)
class AdminUserControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean UserService     userService;
    @MockBean AuditLogService auditLogService;
    @MockBean EmailService    emailService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void listUsers_returnsUsersPage() throws Exception {
        when(userService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/users"))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/users"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void approveUser_setsStatusActiveAndReturnsFragment() throws Exception {
        User approved = buildUser(1L, User.Status.ACTIVE);
        when(userService.setStatus(1L, User.Status.ACTIVE)).thenReturn(approved);

        mockMvc.perform(post("/admin/users/1/approve").with(csrf()))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/users :: userRow"));

        verify(auditLogService).log(any(), eq("APPROVE_USER"), eq("USER"), eq(1L), anyString());
        verify(emailService).sendApprovalEmail(approved);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectUser_setsStatusRejectedAndReturnsFragment() throws Exception {
        User rejected = buildUser(2L, User.Status.REJECTED);
        when(userService.setStatus(2L, User.Status.REJECTED)).thenReturn(rejected);

        mockMvc.perform(post("/admin/users/2/reject").with(csrf()))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/users :: userRow"));

        verify(auditLogService).log(any(), eq("REJECT_USER"), eq("USER"), eq(2L), anyString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void disableUser_setsStatusDisabled() throws Exception {
        User disabled = buildUser(3L, User.Status.DISABLED);
        when(userService.setStatus(3L, User.Status.DISABLED)).thenReturn(disabled);

        mockMvc.perform(post("/admin/users/3/disable").with(csrf()))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/users :: userRow"));
    }

    @Test
    @WithMockUser(roles = "PARTICIPANT")
    void listUsers_forbiddenForParticipant() throws Exception {
        mockMvc.perform(get("/admin/users"))
               .andExpect(status().isForbidden());
    }

    private User buildUser(Long id, User.Status status) {
        User u = new User();
        u.setId(id);
        u.setStatus(status);
        u.setEmail("test" + id + "@example.com");
        u.setDisplayName("Test User " + id);
        return u;
    }
}
```

- [ ] **Step 2: AdminMatchControllerTest**

```java
// src/test/java/com/worldcup/prediction/controller/admin/AdminMatchControllerTest.java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.model.Match;
import com.worldcup.prediction.model.Team;
import com.worldcup.prediction.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminMatchController.class)
class AdminMatchControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean MatchService    matchService;
    @MockBean ScoringService  scoringService;
    @MockBean AuditLogService auditLogService;
    @MockBean EmailService    emailService;
    @MockBean UserService     userService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void listMatches_returnsMatchesPage() throws Exception {
        when(matchService.findAllOrderByKickoffAsc()).thenReturn(List.of());

        mockMvc.perform(get("/admin/matches"))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/matches"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void enterResult_scoresAndRedirects() throws Exception {
        Match match = buildMatch(10L, "Brazil", "Argentina");
        when(matchService.setResult(10L, 2, 1)).thenReturn(match);

        mockMvc.perform(post("/admin/matches/10/result")
                       .param("homeScore", "2")
                       .param("awayScore", "1")
                       .with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/matches"));

        verify(scoringService).scoreAllPredictions(10L);
        verify(auditLogService).log(any(), eq("ENTER_RESULT"), eq("MATCH"), eq(10L), anyString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void openWindow_opensAndRedirects() throws Exception {
        Match match = buildMatch(5L, "France", "Germany");
        when(matchService.setPredictionWindowOpen(5L, true)).thenReturn(match);

        mockMvc.perform(post("/admin/matches/5/open-window").with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/matches"));

        verify(auditLogService).log(any(), eq("OPEN_WINDOW"), eq("MATCH"), eq(5L), anyString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void closeWindow_closesAndRedirects() throws Exception {
        Match match = buildMatch(5L, "France", "Germany");
        when(matchService.setPredictionWindowOpen(5L, false)).thenReturn(match);

        mockMvc.perform(post("/admin/matches/5/close-window").with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/matches"));

        verify(auditLogService).log(any(), eq("CLOSE_WINDOW"), eq("MATCH"), eq(5L), anyString());
    }

    private Match buildMatch(Long id, String homeName, String awayName) {
        Team home = new Team(); home.setName(homeName); home.setFlagCode("br");
        Team away = new Team(); away.setName(awayName); away.setFlagCode("ar");
        Match m = new Match();
        m.setId(id);
        m.setHomeTeam(home);
        m.setAwayTeam(away);
        m.setKickoffTime(LocalDateTime.now().plusDays(1));
        return m;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git commit -m "test(admin): add MockMvc integration tests for AdminUserController and AdminMatchController"
```

---

### Task 10: Wire @PreAuthorize and Enable Method Security

**Files:**
- Edit: `src/main/java/com/worldcup/prediction/config/SecurityConfig.java`

- [ ] **Step 1: Enable method-level security**

Add `@EnableMethodSecurity` to `SecurityConfig` (or the existing security configuration class):

```java
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // <-- add this
public class SecurityConfig {
    // ... existing config unchanged
}
```

This allows `@PreAuthorize("hasRole('ADMIN')")` on controller methods to be enforced. Without it, the annotation is silently ignored.

- [ ] **Step 2: Commit**

```bash
git commit -m "feat(admin): enable method-level security for @PreAuthorize"
```

---

### Task 11: Final Smoke Test and Wiring Check

- [ ] **Step 1: Run all tests**

```bash
./mvnw test
```

Confirm: AuditLogServiceTest passes, AdminUserControllerTest passes, AdminMatchControllerTest passes.

- [ ] **Step 2: Manually verify admin routes are 403 for non-admins**

If the application can be started locally:

```bash
./mvnw spring-boot:run
# Then curl as an unauthenticated user:
curl -i http://localhost:8080/admin
# Expect: 302 redirect to login (not 200 or 500)
```

- [ ] **Step 3: Confirm Thymeleaf layout dialect is on the classpath**

Verify `pom.xml` contains:

```xml
<dependency>
    <groupId>nz.net.ultraq.thymeleaf</groupId>
    <artifactId>thymeleaf-layout-dialect</artifactId>
</dependency>
```

Spring Boot auto-configures this when present on the classpath. Add it if missing.

- [ ] **Step 4: Final commit**

```bash
git commit -m "feat(admin): Part 7 admin panel complete — dashboard, users, matches, predictions, audit log"
```

---

## Key Design Decisions

### HTMX Row Replacement Pattern
User action endpoints (`/approve`, `/reject`, `/enable`, `/disable`) return `"admin/users :: userRow"` — a named Thymeleaf fragment. The button uses `hx-target="#user-row-{id}"` and `hx-swap="outerHTML"` so only that `<tr>` is replaced in-place. The CSRF token is included via a hidden input with `hx-include="[name='_csrf']"`, which HTMX picks up automatically on the same form.

### Alpine.js for Inline Forms
Result entry and prediction override forms are hidden by default and toggled with `x-show="showResultForm"` / `x-data`. This avoids full page reloads for the form reveal while still using standard HTML form POST for submission (which then redirects). This matches the existing Alpine.js dependency in the stack.

### Audit Log is Fire-and-Forget
`AuditLogService.log()` is `@Transactional` but does NOT participate in the caller's transaction (`Propagation.REQUIRES_NEW` is intentionally avoided here for simplicity). If an audit log write fails, it will bubble up as a runtime exception — acceptable because audit logging failure should be visible. If the business requirement changes to "audit log failure should not roll back the admin action", change the propagation to `REQUIRES_NEW`.

### Email Stub Contract
`EmailService` is annotated `@Service` and injected normally. Part 8 can replace the method bodies with real SMTP calls without changing any controller code — the interface (method signatures) is stable.

### scoreAllPredictions Placement
Scoring is triggered **immediately** when the admin enters a result via `POST /admin/matches/{id}/result`. It does not run asynchronously. For 100–200 users this is acceptable synchronously; if needed later it can be moved to a `@Async` method.
