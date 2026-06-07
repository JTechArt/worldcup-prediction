# Prediction Confirmation Email Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send users a confirmation email with all their submitted predictions after they submit for a matchday round.

**Architecture:** Add a new Freemarker email template, a new method on `EmailService`, wire it into the controller's submit endpoint (async via `CompletableFuture.runAsync`), add admin test endpoint + UI, and unit test.

**Tech Stack:** Spring Boot, Freemarker, JavaMailSender, JUnit 5 + Mockito

---

### Task 1: Create Freemarker Email Template

**Files:**
- Create: `src/main/resources/templates/email/prediction-confirmation.ftlh`

- [ ] **Step 1: Create the email template**

```ftlh
<#assign title="Your predictions have been submitted">
<#include "_header.ftlh">
<body bgcolor="#0d1117" style="margin:0;padding:0;background-color:#0d1117;">
<table width="100%" cellpadding="0" cellspacing="0" bgcolor="#0d1117">
  <tr>
    <td align="center" style="padding:20px 0;">
      <table width="600" cellpadding="0" cellspacing="0" bgcolor="#ffffff" style="max-width:600px;width:100%;">
        <tr>
          <td bgcolor="#ffffff" style="background-color:#ffffff;padding:40px 32px;">
            <h1 style="font-family:Helvetica Neue,Arial,sans-serif;font-size:26px;font-weight:800;color:#0d1b2a;margin:0 0 16px;line-height:1.2;">Predictions Confirmed, ${firstName}!</h1>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;color:#4a5568;line-height:1.6;margin:0 0 8px;">
              Your predictions for <strong>${roundLabel}</strong> have been recorded.
            </p>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#9ca3af;line-height:1.5;margin:0 0 24px;">
              Submitted at: ${submittedAt}
            </p>
            <#if predictions?has_content>
            <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;margin-bottom:24px;">
              <tr>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;padding:0 0 8px;border-bottom:2px solid #D4AF37;">Home</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;padding:0 0 8px;border-bottom:2px solid #D4AF37;text-align:center;">Score</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;padding:0 0 8px;border-bottom:2px solid #D4AF37;text-align:right;">Away</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;padding:0 0 8px;border-bottom:2px solid #D4AF37;text-align:right;">Kickoff</td>
              </tr>
              <#list predictions as p>
              <tr style="background-color:<#if p?index % 2 == 0>#f9fafb<#else>#ffffff</#if>;">
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:14px;font-weight:600;color:#0d1b2a;padding:10px 8px 10px 0;border-bottom:1px solid #e5e7eb;">${p.homeTeam}</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:14px;font-weight:700;color:#0d1b2a;padding:10px 4px;border-bottom:1px solid #e5e7eb;text-align:center;">${p.predictedHome} - ${p.predictedAway}</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:14px;font-weight:600;color:#0d1b2a;padding:10px 0 10px 8px;border-bottom:1px solid #e5e7eb;text-align:right;">${p.awayTeam}</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#4a5568;padding:10px 0;border-bottom:1px solid #e5e7eb;text-align:right;">${p.kickoff}</td>
              </tr>
              </#list>
            </table>
            </#if>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#6b7280;line-height:1.5;margin:0 0 24px;padding:12px 16px;background-color:#f9fafb;border-left:3px solid #D4AF37;border-radius:4px;">
              This email serves as your official confirmation of submitted predictions. Keep it for your records.
            </p>
            <table width="100%" cellpadding="0" cellspacing="0">
              <tr>
                <td align="center" style="padding:8px 0;">
                  <a href="${appUrl}" style="display:inline-block;background-color:#D4AF37;color:#0d1b2a;font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;font-weight:700;text-decoration:none;padding:14px 36px;border-radius:6px;">View My Predictions</a>
                </td>
              </tr>
              <tr>
                <td align="center" style="padding:8px 0 0;">
                  <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#9ca3af;margin:0;">
                    Or visit: <a href="${appUrl}" style="color:#D4AF37;text-decoration:none;">${appUrl}</a>
                  </p>
                </td>
              </tr>
            </table>
          </td>
        </tr>
        <#include "_footer.ftlh">
      </table>
    </td>
  </tr>
</table>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/email/prediction-confirmation.ftlh
git commit -m "feat: add prediction confirmation email template"
```

---

### Task 2: Add EmailService Method + Unit Test

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/service/EmailService.java`
- Modify: `src/test/java/com/worldcup/prediction/service/EmailServiceTest.java`

- [ ] **Step 1: Write the failing test**

Add to `EmailServiceTest.java`:

```java
@Test
void sendPredictionConfirmation_invokesMailSender() {
    List<Map<String, String>> predictions = List.of(
            Map.of("homeTeam", "Mexico", "awayTeam", "Canada",
                    "predictedHome", "2", "predictedAway", "1",
                    "kickoff", "Wed, Jun 11 at 19:00 UTC"),
            Map.of("homeTeam", "USA", "awayTeam", "Brazil",
                    "predictedHome", "1", "predictedAway", "3",
                    "kickoff", "Wed, Jun 11 at 21:00 UTC")
    );

    emailService.sendPredictionConfirmation(testUser, "Group Stage MD1", predictions,
            LocalDateTime.of(2026, 6, 10, 14, 30));

    verify(mailSender, times(1)).send(any(MimeMessage.class));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl . -Dtest=EmailServiceTest#sendPredictionConfirmation_invokesMailSender -Dspring.profiles.active=test`
Expected: compilation error — `sendPredictionConfirmation` does not exist.

- [ ] **Step 3: Add `sendPredictionConfirmation` and `sendTestPredictionConfirmation` to EmailService**

Add these methods to `EmailService.java` after `sendLeaderboardDigest`:

```java
public void sendPredictionConfirmation(User user, String roundLabel,
                                        List<Map<String, String>> predictions,
                                        LocalDateTime submittedAt) {
    String subject = "Your predictions for " + roundLabel + " have been submitted";
    Map<String, Object> model = new HashMap<>();
    model.put("title", "Prediction Confirmation");
    model.put("firstName", user.getFirstName());
    model.put("roundLabel", roundLabel);
    model.put("submittedAt", submittedAt.format(DATE_FMT));
    model.put("predictions", predictions);
    model.put("appUrl", appUrl);
    String body = renderOrFallback("prediction-confirmation.ftlh", model, subject);
    send(user.getEmail(), subject, body);
}

public void sendTestPredictionConfirmation(String to, String firstName, String roundLabel) {
    String subject = "Your predictions for " + roundLabel + " have been submitted";
    List<Map<String, String>> predictions = List.of(
            Map.of("homeTeam", "Mexico", "awayTeam", "Canada",
                    "predictedHome", "2", "predictedAway", "1",
                    "kickoff", "Wed, Jun 11 at 19:00 UTC"),
            Map.of("homeTeam", "USA", "awayTeam", "Brazil",
                    "predictedHome", "1", "predictedAway", "3",
                    "kickoff", "Thu, Jun 12 at 21:00 UTC"),
            Map.of("homeTeam", "France", "awayTeam", "Germany",
                    "predictedHome", "0", "predictedAway", "0",
                    "kickoff", "Fri, Jun 13 at 18:00 UTC")
    );
    Map<String, Object> model = new HashMap<>();
    model.put("title", "Prediction Confirmation");
    model.put("firstName", firstName);
    model.put("roundLabel", roundLabel);
    model.put("submittedAt", LocalDateTime.now().format(DATE_FMT));
    model.put("predictions", predictions);
    model.put("appUrl", appUrl);
    String body = renderOrFallback("prediction-confirmation.ftlh", model, subject);
    send(to, subject, body);
}
```

Add this import at the top if not already present:

```java
import java.time.LocalDateTime;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -pl . -Dtest=EmailServiceTest#sendPredictionConfirmation_invokesMailSender -Dspring.profiles.active=test`
Expected: PASS

- [ ] **Step 5: Run all EmailService tests**

Run: `./mvnw test -pl . -Dtest=EmailServiceTest -Dspring.profiles.active=test`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/EmailService.java \
        src/test/java/com/worldcup/prediction/service/EmailServiceTest.java
git commit -m "feat: add sendPredictionConfirmation to EmailService with test"
```

---

### Task 3: Wire Email Into Controller (Async)

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/controller/community/CommunityPredictionController.java`

- [ ] **Step 1: Add dependencies to the controller**

Add `EmailService`, `MatchRepository`, and `UserRepository` to the constructor-injected fields:

```java
private final EmailService emailService;
private final MatchRepository matchRepository;
private final UserRepository userRepository;
```

Add these imports:

```java
import com.worldcup.prediction.service.EmailService;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.User;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
```

- [ ] **Step 2: Fire email after successful submission**

In the `submitPredictions` method, inside the `try` block, after the `redirectAttributes.addFlashAttribute("successMessage", ...)` line, add:

```java
final User user = userRepository.findById(principal.getUserId()).orElse(null);
if (user != null) {
    final String roundLabel = submitDto.getRoundLabel();
    final List<Match> roundMatches = matchRepository.findByRoundLabelWithTeams(roundLabel);
    final Map<Long, PredictionSubmitDto.SinglePrediction> predMap = submitDto.getPredictions().stream()
            .collect(Collectors.toMap(PredictionSubmitDto.SinglePrediction::getMatchId, p -> p));
    CompletableFuture.runAsync(() -> {
        try {
            DateTimeFormatter kickoffFmt = DateTimeFormatter.ofPattern("EEE, MMM d 'at' HH:mm 'UTC'");
            List<Map<String, String>> predictionDetails = roundMatches.stream()
                    .filter(m -> predMap.containsKey(m.getId()))
                    .map(m -> {
                        PredictionSubmitDto.SinglePrediction sp = predMap.get(m.getId());
                        String home = m.getHomeTeam() != null ? m.getHomeTeam().getName() : "TBD";
                        String away = m.getAwayTeam() != null ? m.getAwayTeam().getName() : "TBD";
                        String kickoff = m.getKickoffTime() != null ? m.getKickoffTime().format(kickoffFmt) : "";
                        return Map.of(
                                "homeTeam", home,
                                "awayTeam", away,
                                "predictedHome", String.valueOf(sp.getHomeScore()),
                                "predictedAway", String.valueOf(sp.getAwayScore()),
                                "kickoff", kickoff
                        );
                    })
                    .collect(Collectors.toList());
            emailService.sendPredictionConfirmation(user, roundLabel, predictionDetails, LocalDateTime.now());
        } catch (Exception e) {
            // logged inside EmailService.send — swallow here so async failure never affects user
        }
    });
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/community/CommunityPredictionController.java
git commit -m "feat: send prediction confirmation email async after submission"
```

---

### Task 4: Admin Test Endpoint + UI

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/controller/admin/AdminEmailTestController.java`
- Modify: `src/main/resources/templates/admin/email-test.html`

- [ ] **Step 1: Add test endpoint to AdminEmailTestController**

Add this method after the `testLeaderboard` method:

```java
@PostMapping("/prediction-confirmation")
public String testPredictionConfirmation(@RequestParam String to,
                                          @RequestParam String firstName,
                                          @RequestParam String roundLabel,
                                          RedirectAttributes ra) {
    try {
        emailService.sendTestPredictionConfirmation(to, firstName, roundLabel);
        ra.addFlashAttribute("successMessage", "Prediction confirmation test sent to " + to);
    } catch (Exception e) {
        ra.addFlashAttribute("errorMessage", "Failed: " + e.getMessage());
    }
    return "redirect:/admin/email-test";
}
```

- [ ] **Step 2: Add UI section to email-test.html**

Add this block after the Leaderboard Digest section (before the closing `</div>` of `space-y-6`):

```html
  <!-- 8. Prediction Confirmation -->
  <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
    <h2 class="text-base font-semibold text-gray-800 mb-1">Prediction Confirmation</h2>
    <p class="text-xs text-gray-400 mb-4">Template: <code class="bg-gray-100 px-1 rounded">prediction-confirmation.ftlh</code> — sends with 3 synthetic predictions.</p>
    <form th:action="@{/admin/email-test/prediction-confirmation}" method="post" class="space-y-3">
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
        <div class="col-span-2">
          <label class="block text-xs font-medium text-gray-600 mb-1">Round label</label>
          <input type="text" name="roundLabel" value="Group Stage MD1" required
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
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/admin/AdminEmailTestController.java \
        src/main/resources/templates/admin/email-test.html
git commit -m "feat: add prediction confirmation to admin email test page"
```

---

### Task 5: Full Build Verification

- [ ] **Step 1: Run all tests**

Run: `./mvnw test -Dspring.profiles.active=test`
Expected: All tests PASS

- [ ] **Step 2: Run full build**

Run: `./mvnw package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Final commit if any fixes were needed**
