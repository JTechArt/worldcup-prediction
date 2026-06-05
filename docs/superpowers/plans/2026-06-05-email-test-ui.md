# Email Test UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `/admin/email-test` page with 7 cards — one per email template type — so admins can send test emails with custom data without triggering deduplication.

**Architecture:** Three new `EmailService` helper methods bypass `Match` entity requirements for test sends. A new `AdminEmailTestController` handles GET + 7 POST endpoints, calling `EmailService` directly. A new Thymeleaf page extends `admin/layout` with one card-per-template-type. The admin sidebar gets an "Email Test" nav link.

**Tech Stack:** Spring Boot 3.3, Thymeleaf Layout Dialect, Tailwind CSS, FreeMarker (via existing EmailService/FreemarkerEmailRenderer)

---

## Files

| Action | Path |
|--------|------|
| Modify | `src/main/java/com/worldcup/prediction/service/EmailService.java` |
| Create | `src/main/java/com/worldcup/prediction/controller/admin/AdminEmailTestController.java` |
| Create | `src/main/resources/templates/admin/email-test.html` |
| Modify | `src/main/resources/templates/admin/layout.html` |

---

## Task 1: Add test helper methods to EmailService

These three methods build the FreeMarker model directly from plain strings, bypassing the need for `Match` or `User` entity objects.

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/service/EmailService.java`

- [ ] **Step 1: Add three public test-send methods after `sendInvitation`**

Open `EmailService.java`. After line 193 (end of `sendInvitation`), add:

```java
// ── Test-only helpers (bypass entity requirements, no deduplication) ──────────

public void sendTestInvitation(String to, String inviterName) {
    String subject = "You're invited to World Cup 2026 Predictions!";
    String body = renderOrFallback("invitation.ftlh", Map.of(
            "title", "You're Invited",
            "inviterName", inviterName,
            "appUrl", appUrl
    ), subject);
    send(to, subject, body);
}

public void sendTestWindowOpen(String to, String firstName, String matchLabel, String kickoff) {
    String subject = "Predictions are open! " + matchLabel;
    String body = renderOrFallback("prediction-window-open.ftlh", Map.of(
            "title", "Predictions Open",
            "firstName", firstName,
            "matches", List.of(Map.of("label", matchLabel, "kickoff", kickoff)),
            "appUrl", appUrl
    ), subject);
    send(to, subject, body);
}

public void sendTestReminder(String to, String firstName, String matchLabel, String kickoff, String hoursLeft) {
    String subject = "⚽ Predictions close in " + hoursLeft + "h — " + matchLabel;
    String body = renderOrFallback("prediction-reminder.ftlh", Map.of(
            "title", "Reminder",
            "firstName", firstName,
            "matches", List.of(Map.of("label", matchLabel, "kickoff", kickoff)),
            "hoursLeft", hoursLeft,
            "appUrl", appUrl
    ), subject);
    send(to, subject, body);
}

public void sendTestResults(String to, String firstName, String matchLabel, String score) {
    String subject = "📊 Results published — " + matchLabel;
    String body = renderOrFallback("results-published.ftlh", Map.of(
            "title", "Results Published",
            "firstName", firstName,
            "matchLabel", matchLabel,
            "score", score,
            "appUrl", appUrl
    ), subject);
    send(to, subject, body);
}
```

- [ ] **Step 2: Compile to confirm no errors**

```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS (no output).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/EmailService.java
git commit -m "feat: add test-send helper methods to EmailService"
```

---

## Task 2: Create AdminEmailTestController

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/admin/AdminEmailTestController.java`

- [ ] **Step 1: Create the controller file**

```java
package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/email-test")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminEmailTestController {

    private final EmailService emailService;

    @GetMapping
    public String emailTestPage() {
        return "admin/email-test";
    }

    @PostMapping("/invitation")
    public String testInvitation(@RequestParam String to,
                                 @RequestParam String inviterName,
                                 RedirectAttributes ra) {
        try {
            emailService.sendTestInvitation(to, inviterName);
            ra.addFlashAttribute("successMessage", "Invitation test sent to " + to);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/email-test";
    }

    @PostMapping("/approval")
    public String testApproval(@RequestParam String to,
                               @RequestParam String firstName,
                               RedirectAttributes ra) {
        try {
            emailService.sendApprovalEmail(fakeUser(to, firstName));
            ra.addFlashAttribute("successMessage", "Approval test sent to " + to);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/email-test";
    }

    @PostMapping("/rejection")
    public String testRejection(@RequestParam String to,
                                @RequestParam String firstName,
                                RedirectAttributes ra) {
        try {
            emailService.sendRejectionEmail(fakeUser(to, firstName));
            ra.addFlashAttribute("successMessage", "Rejection test sent to " + to);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/email-test";
    }

    @PostMapping("/window-open")
    public String testWindowOpen(@RequestParam String to,
                                 @RequestParam String firstName,
                                 @RequestParam String matchLabel,
                                 @RequestParam String kickoff,
                                 RedirectAttributes ra) {
        try {
            emailService.sendTestWindowOpen(to, firstName, matchLabel, kickoff);
            ra.addFlashAttribute("successMessage", "Window-open test sent to " + to);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/email-test";
    }

    @PostMapping("/reminder")
    public String testReminder(@RequestParam String to,
                               @RequestParam String firstName,
                               @RequestParam String matchLabel,
                               @RequestParam String kickoff,
                               @RequestParam String hoursLeft,
                               RedirectAttributes ra) {
        try {
            emailService.sendTestReminder(to, firstName, matchLabel, kickoff, hoursLeft);
            ra.addFlashAttribute("successMessage", "Reminder test sent to " + to);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/email-test";
    }

    @PostMapping("/results")
    public String testResults(@RequestParam String to,
                              @RequestParam String firstName,
                              @RequestParam String matchLabel,
                              @RequestParam String score,
                              RedirectAttributes ra) {
        try {
            emailService.sendTestResults(to, firstName, matchLabel, score);
            ra.addFlashAttribute("successMessage", "Results test sent to " + to);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/email-test";
    }

    @PostMapping("/leaderboard")
    public String testLeaderboard(@RequestParam String to,
                                  @RequestParam String firstName,
                                  @RequestParam int rank,
                                  @RequestParam int points,
                                  RedirectAttributes ra) {
        try {
            List<Map<String, Object>> topEntries = buildSyntheticLeaderboard(firstName, rank, points);
            List<Map<String, Object>> matchResults = List.of(
                    Map.of("label", "France vs Brazil", "score", "2 - 1"),
                    Map.of("label", "Germany vs Argentina", "score", "1 - 1")
            );
            emailService.sendLeaderboardDigest(fakeUser(to, firstName), rank, points, topEntries, matchResults);
            ra.addFlashAttribute("successMessage", "Leaderboard digest test sent to " + to);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
        }
        return "redirect:/admin/email-test";
    }

    private User fakeUser(String email, String firstName) {
        return User.builder()
                .email(email)
                .firstName(firstName)
                .lastName("")
                .build();
    }

    private List<Map<String, Object>> buildSyntheticLeaderboard(String firstName, int rank, int points) {
        String[] dummyNames = {"Carlos M.", "Sophie L.", "Luca R.", "Emma K.",
                "Rafael T.", "Yuki N.", "Omar S.", "Priya D.", "Jonas B.", "Ana C."};
        List<Map<String, Object>> entries = new ArrayList<>();
        int dummyIdx = 0;
        for (int i = 1; i <= 10; i++) {
            if (i == rank) {
                entries.add(Map.of("rank", i, "name", firstName, "points", points));
            } else {
                int pts = Math.max(0, points + (rank - i) * 7);
                entries.add(Map.of("rank", i, "name", dummyNames[dummyIdx++], "points", pts));
            }
        }
        return entries;
    }
}
```

- [ ] **Step 2: Compile**

```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/admin/AdminEmailTestController.java
git commit -m "feat: add AdminEmailTestController with 7 test email endpoints"
```

---

## Task 3: Create email-test.html template

**Files:**
- Create: `src/main/resources/templates/admin/email-test.html`

- [ ] **Step 1: Create the template**

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{admin/layout}">
<head><title>Email Test</title></head>
<body>

<th:block layout:fragment="page-title">Email Test</th:block>

<th:block layout:fragment="content">
<div class="space-y-6">

  <p class="text-sm text-gray-500">Send a real email to any address using each template. Bypasses deduplication — can be sent multiple times.</p>

  <!-- 1. Invitation -->
  <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
    <h2 class="text-base font-semibold text-gray-800 mb-1">Invitation</h2>
    <p class="text-xs text-gray-400 mb-4">Template: <code class="bg-gray-100 px-1 rounded">invitation.ftlh</code></p>
    <form th:action="@{/admin/email-test/invitation}" method="post" class="space-y-3">
      <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">To email</label>
          <input type="email" name="to" value="arthurho@adobe.com" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">Inviter name</label>
          <input type="text" name="inviterName" value="Arthur" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
      </div>
      <div class="flex justify-end">
        <button type="submit" class="bg-admin-dark hover:bg-admin-mid text-white rounded-lg px-4 py-2 text-sm font-semibold transition-colors">
          Send Test →
        </button>
      </div>
    </form>
  </div>

  <!-- 2. Approval -->
  <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
    <h2 class="text-base font-semibold text-gray-800 mb-1">Approval</h2>
    <p class="text-xs text-gray-400 mb-4">Template: <code class="bg-gray-100 px-1 rounded">approval.ftlh</code></p>
    <form th:action="@{/admin/email-test/approval}" method="post" class="space-y-3">
      <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">To email</label>
          <input type="email" name="to" value="arthurho@adobe.com" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">First name</label>
          <input type="text" name="firstName" value="Arthur" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
      </div>
      <div class="flex justify-end">
        <button type="submit" class="bg-admin-dark hover:bg-admin-mid text-white rounded-lg px-4 py-2 text-sm font-semibold transition-colors">
          Send Test →
        </button>
      </div>
    </form>
  </div>

  <!-- 3. Rejection -->
  <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
    <h2 class="text-base font-semibold text-gray-800 mb-1">Rejection</h2>
    <p class="text-xs text-gray-400 mb-4">Template: <code class="bg-gray-100 px-1 rounded">rejection.ftlh</code></p>
    <form th:action="@{/admin/email-test/rejection}" method="post" class="space-y-3">
      <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">To email</label>
          <input type="email" name="to" value="arthurho@adobe.com" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">First name</label>
          <input type="text" name="firstName" value="Arthur" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
      </div>
      <div class="flex justify-end">
        <button type="submit" class="bg-admin-dark hover:bg-admin-mid text-white rounded-lg px-4 py-2 text-sm font-semibold transition-colors">
          Send Test →
        </button>
      </div>
    </form>
  </div>

  <!-- 4. Prediction Window Open -->
  <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
    <h2 class="text-base font-semibold text-gray-800 mb-1">Prediction Window Open</h2>
    <p class="text-xs text-gray-400 mb-4">Template: <code class="bg-gray-100 px-1 rounded">prediction-window-open.ftlh</code></p>
    <form th:action="@{/admin/email-test/window-open}" method="post" class="space-y-3">
      <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">To email</label>
          <input type="email" name="to" value="arthurho@adobe.com" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">First name</label>
          <input type="text" name="firstName" value="Arthur" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">Match label</label>
          <input type="text" name="matchLabel" value="France vs Brazil" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">Kickoff</label>
          <input type="text" name="kickoff" value="15 Jun 20:00" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
      </div>
      <div class="flex justify-end">
        <button type="submit" class="bg-admin-dark hover:bg-admin-mid text-white rounded-lg px-4 py-2 text-sm font-semibold transition-colors">
          Send Test →
        </button>
      </div>
    </form>
  </div>

  <!-- 5. Prediction Reminder -->
  <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
    <h2 class="text-base font-semibold text-gray-800 mb-1">Prediction Reminder</h2>
    <p class="text-xs text-gray-400 mb-4">Template: <code class="bg-gray-100 px-1 rounded">prediction-reminder.ftlh</code></p>
    <form th:action="@{/admin/email-test/reminder}" method="post" class="space-y-3">
      <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">To email</label>
          <input type="email" name="to" value="arthurho@adobe.com" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">First name</label>
          <input type="text" name="firstName" value="Arthur" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">Match label</label>
          <input type="text" name="matchLabel" value="France vs Brazil" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">Kickoff</label>
          <input type="text" name="kickoff" value="15 Jun 20:00" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">Hours left</label>
          <input type="text" name="hoursLeft" value="3" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
      </div>
      <div class="flex justify-end">
        <button type="submit" class="bg-admin-dark hover:bg-admin-mid text-white rounded-lg px-4 py-2 text-sm font-semibold transition-colors">
          Send Test →
        </button>
      </div>
    </form>
  </div>

  <!-- 6. Results Published -->
  <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
    <h2 class="text-base font-semibold text-gray-800 mb-1">Results Published</h2>
    <p class="text-xs text-gray-400 mb-4">Template: <code class="bg-gray-100 px-1 rounded">results-published.ftlh</code></p>
    <form th:action="@{/admin/email-test/results}" method="post" class="space-y-3">
      <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">To email</label>
          <input type="email" name="to" value="arthurho@adobe.com" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">First name</label>
          <input type="text" name="firstName" value="Arthur" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">Match label</label>
          <input type="text" name="matchLabel" value="France vs Brazil" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">Score (e.g. 2 - 1)</label>
          <input type="text" name="score" value="2 - 1" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
      </div>
      <div class="flex justify-end">
        <button type="submit" class="bg-admin-dark hover:bg-admin-mid text-white rounded-lg px-4 py-2 text-sm font-semibold transition-colors">
          Send Test →
        </button>
      </div>
    </form>
  </div>

  <!-- 7. Leaderboard Digest -->
  <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
    <h2 class="text-base font-semibold text-gray-800 mb-1">Leaderboard Digest</h2>
    <p class="text-xs text-gray-400 mb-4">Template: <code class="bg-gray-100 px-1 rounded">leaderboard-digest.ftlh</code> — top 10 table auto-filled with synthetic data.</p>
    <form th:action="@{/admin/email-test/leaderboard}" method="post" class="space-y-3">
      <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">To email</label>
          <input type="email" name="to" value="arthurho@adobe.com" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">First name</label>
          <input type="text" name="firstName" value="Arthur" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">Your rank</label>
          <input type="number" name="rank" value="3" min="1" max="10" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
        <div>
          <label class="block text-xs font-medium text-gray-600 mb-1">Your points</label>
          <input type="number" name="points" value="42" min="0" required
                 class="w-full rounded-lg border-gray-300 text-sm px-3 py-2 border focus:ring-admin-light focus:border-admin-light"/>
        </div>
      </div>
      <div class="flex justify-end">
        <button type="submit" class="bg-admin-dark hover:bg-admin-mid text-white rounded-lg px-4 py-2 text-sm font-semibold transition-colors">
          Send Test →
        </button>
      </div>
    </form>
  </div>

</div>
</th:block>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/admin/email-test.html
git commit -m "feat: add email-test admin page with 7 template test cards"
```

---

## Task 4: Add sidebar nav link

**Files:**
- Modify: `src/main/resources/templates/admin/layout.html`

- [ ] **Step 1: Add "Email Test" nav link after the Notifications link**

Find the Notifications nav link block (ends around the closing `</a>` after the bell SVG). Add this immediately after it, before the `<div class="mt-4 mb-2...">System</div>` separator:

```html
      <a th:href="@{/admin/email-test}"
         th:classappend="${currentUri.startsWith('/admin/email-test')} ? ' bg-admin-mid text-white' : ' text-green-200 hover:bg-admin-mid hover:text-white'"
         class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors duration-150">
        <svg class="w-5 h-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"/>
        </svg>
        Email Test
      </a>
```

- [ ] **Step 2: Compile and run tests**

```bash
./mvnw test -q
```
Expected: BUILD SUCCESS. All existing tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/admin/layout.html
git commit -m "feat: add Email Test link to admin sidebar nav"
```

---

## Task 5: Verify end-to-end

- [ ] **Step 1: Start the app**

```bash
./mvnw spring-boot:run
```
Wait for `Started WorldCupPredictionApplication`.

- [ ] **Step 2: Open the page**

Navigate to `http://localhost:8888/admin/email-test`. Confirm:
- Page loads with 7 cards
- "Email Test" is highlighted in the sidebar nav
- All fields are pre-filled with defaults

- [ ] **Step 3: Send a test**

Click "Send Test →" on the Invitation card without changing any values. Confirm:
- Green success flash: "Invitation test sent to arthurho@adobe.com"
- If `MAIL_ENABLED=false` (local default), check the app log for: `[EMAIL LOG-ONLY] To: arthurho@adobe.com | Subject: You're invited...`
- If `MAIL_ENABLED=true`, check inbox

- [ ] **Step 4: Push**

```bash
git push origin main
```
