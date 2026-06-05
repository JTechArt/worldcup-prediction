# Email Test UI — Design Spec

**Date:** 2026-06-05
**Scope:** A dedicated admin page at `/admin/email-test` for sending test emails for each of the 7 template types, with pre-filled form fields and no deduplication guard.

---

## Overview

Add a new super-admin page `/admin/email-test` with 7 cards — one per email template type. Each card has an independent form with the fields that template needs. Submitting sends a real email to the provided address using `EmailService` directly, bypassing `NotificationService` deduplication so the same test can be triggered multiple times.

---

## Architecture

### New files

| File | Responsibility |
|------|---------------|
| `src/main/java/com/worldcup/prediction/controller/admin/AdminEmailTestController.java` | `GET /admin/email-test` + 7 POST handlers |
| `src/main/resources/templates/admin/email-test.html` | Thymeleaf page, extends `admin/layout` |

### Modified files

| File | Change |
|------|--------|
| `src/main/resources/templates/admin/layout.html` | Add "Email Test" nav link in sidebar |

### Access control

`@PreAuthorize("hasRole('SUPER_ADMIN')")` — same as all other admin controllers.

### No deduplication

The controller calls `EmailService` methods directly, not `NotificationService`. This means:
- No `NotificationLog` entries are created
- The same test can be sent multiple times without "already sent" skipping
- `MAIL_ENABLED=false` still suppresses sending (logs intent instead) — no special test mode needed

---

## Endpoints

| Method | Path | Action |
|--------|------|--------|
| GET | `/admin/email-test` | Render the test page |
| POST | `/admin/email-test/invitation` | Send test invitation email |
| POST | `/admin/email-test/approval` | Send test approval email |
| POST | `/admin/email-test/rejection` | Send test rejection email |
| POST | `/admin/email-test/window-open` | Send test prediction-window-open email |
| POST | `/admin/email-test/reminder` | Send test prediction-reminder email |
| POST | `/admin/email-test/results` | Send test results-published email |
| POST | `/admin/email-test/leaderboard` | Send test leaderboard-digest email |

All POST handlers:
- Accept form params
- Build minimal `User` object (only `email` + `firstName` populated — no DB lookup)
- Call the appropriate `EmailService` method
- Add `successMessage` or `errorMessage` flash attribute
- Redirect to `GET /admin/email-test`

---

## Cards and Fields

Each card has a "Send Test Email" button. All fields are pre-filled with defaults so the admin can fire a test immediately.

### Card 1 — Invitation
| Field | Input | Default |
|-------|-------|---------|
| To email | `<input type="email" name="to">` | `arthurho@adobe.com` |
| Inviter name | `<input type="text" name="inviterName">` | `Arthur` |

Controller: calls `emailService.sendInvitation(to, fakeInviterUser)` where `fakeInviterUser` is a `User` with `firstName = inviterName`.

### Card 2 — Approval
| Field | Input | Default |
|-------|-------|---------|
| To email | `<input type="email" name="to">` | `arthurho@adobe.com` |
| First name | `<input type="text" name="firstName">` | `Arthur` |

Controller: builds `User(email=to, firstName=firstName)`, calls `emailService.sendApprovalEmail(user)`.

### Card 3 — Rejection
| Field | Input | Default |
|-------|-------|---------|
| To email | `<input type="email" name="to">` | `arthurho@adobe.com` |
| First name | `<input type="text" name="firstName">` | `Arthur` |

Controller: builds `User(email=to, firstName=firstName)`, calls `emailService.sendRejectionEmail(user)`.

### Card 4 — Prediction Window Open
| Field | Input | Default |
|-------|-------|---------|
| To email | `<input type="email" name="to">` | `arthurho@adobe.com` |
| First name | `<input type="text" name="firstName">` | `Arthur` |
| Match label | `<input type="text" name="matchLabel">` | `France vs Brazil` |
| Kickoff | `<input type="text" name="kickoff">` | `15 Jun 20:00` |

Controller: calls `emailService.sendTestWindowOpen(to, firstName, matchLabel, kickoff)` — a new helper method added to EmailService that builds the model directly without needing a `Match` entity. See implementation notes below.

### Card 5 — Prediction Reminder
| Field | Input | Default |
|-------|-------|---------|
| To email | `<input type="email" name="to">` | `arthurho@adobe.com` |
| First name | `<input type="text" name="firstName">` | `Arthur` |
| Match label | `<input type="text" name="matchLabel">` | `France vs Brazil` |
| Kickoff | `<input type="text" name="kickoff">` | `15 Jun 20:00` |
| Hours left | `<input type="number" name="hoursLeft">` | `3` |

Controller: calls `emailService.sendPredictionReminder(user, matchInfo)` — the single-user overload that takes a plain `String matchInfo`. This is the simplest path.

### Card 6 — Results Published
| Field | Input | Default |
|-------|-------|---------|
| To email | `<input type="email" name="to">` | `arthurho@adobe.com` |
| First name | `<input type="text" name="firstName">` | `Arthur` |
| Match label | `<input type="text" name="matchLabel">` | `France vs Brazil` |
| Score | `<input type="text" name="score">` | `2 - 1` |

Controller: builds minimal `Match`-like data and calls `emailService.sendResultsPublished`. Since `sendResultsPublished(List<User>, Match)` needs a real `Match` entity, the controller will use `FreemarkerEmailRenderer` directly if needed, or a new single-user overload is added to `EmailService`.

### Card 7 — Leaderboard Digest
| Field | Input | Default |
|-------|-------|---------|
| To email | `<input type="email" name="to">` | `arthurho@adobe.com` |
| First name | `<input type="text" name="firstName">` | `Arthur` |
| Your rank | `<input type="number" name="rank">` | `3` |
| Your points | `<input type="number" name="points">` | `42` |

Controller: auto-generates a synthetic top-10 table (10 dummy names/points) and calls `emailService.sendLeaderboardDigest(user, rank, points, syntheticTopEntries, syntheticMatchResults)`.

---

## Implementation Notes

### Building a fake User

`EmailService` methods accept `User` domain objects. For testing, build a transient `User` with only the fields the email templates use (`email`, `firstName`):

```java
User fakeUser = User.builder()
    .email(to)
    .firstName(firstName)
    .build();
```

No `id`, no `status`, no DB save — `EmailService` only reads `user.getEmail()` and `user.getFirstName()`.

### Cards 4 & 5 — Window Open / Reminder

`sendPredictionWindowOpen(List<User>, List<Match>)` and `sendPredictionReminder(List<User>, Match)` require real `Match` entities because they call `match.getHomeTeam().getName()` etc.

For the test controller, use the simpler single-user `sendPredictionReminder(User, String)` overload for the reminder card. For window-open, add a new `sendPredictionWindowOpenTest(User, String matchLabel, String kickoff)` private helper in `EmailService`, or render the template directly via `FreemarkerEmailRenderer`.

**Simplest path:** Add two new `EmailService` methods for the test controller:
- `sendTestWindowOpen(String to, String firstName, String matchLabel, String kickoff)`
- `sendTestResults(String to, String firstName, String matchLabel, String score)`

These build the model map directly and call `renderAndSend()` — no `Match` entity needed.

### Synthetic leaderboard data

The controller builds 10 synthetic entries with the recipient always appearing at their stated rank. Dummy names fill the other 9 slots (never duplicating the recipient's rank):

```java
// Build 10 dummy entries, inserting the real user at their rank position
List<Map<String, Object>> syntheticTopEntries = new ArrayList<>();
String[] dummyNames = {"Carlos M.", "Sophie L.", "Luca R.", "Emma K.",
    "Rafael T.", "Yuki N.", "Omar S.", "Priya D.", "Jonas B.", "Ana C."};
int dummyIdx = 0;
for (int i = 1; i <= 10; i++) {
    if (i == rank) {
        syntheticTopEntries.add(Map.of("rank", i, "name", firstName, "points", points));
    } else {
        int pts = Math.max(0, points + (rank - i) * 7);
        syntheticTopEntries.add(Map.of("rank", i, "name", dummyNames[dummyIdx++], "points", pts));
    }
}
List<Map<String, Object>> syntheticMatchResults = List.of(
    Map.of("label", "France vs Brazil", "score", "2 - 1"),
    Map.of("label", "Germany vs Argentina", "score", "1 - 1")
);
List<Map<String, Object>> syntheticMatchResults = List.of(
    Map.of("label", "France vs Brazil", "score", "2 - 1"),
    Map.of("label", "Germany vs Argentina", "score", "1 - 1")
);
```

---

## UI Layout

Page title: **"Email Test"**

Each card:
```
┌─────────────────────────────────────────────┐
│ 📧 Invitation                               │
│ ─────────────────────────────────────────── │
│ To email    [arthurho@adobe.com          ]  │
│ Inviter     [Arthur                      ]  │
│                                             │
│                      [Send Test Email →]    │
└─────────────────────────────────────────────┘
```

Cards are displayed in a single column, matching the existing admin notifications page layout (white cards, `rounded-xl shadow-sm border border-gray-200 p-6`).

Success/error flash messages appear at the top via the existing admin layout flash system.

---

## File Changes Summary

| Action | File |
|--------|------|
| Create | `...controller/admin/AdminEmailTestController.java` |
| Create | `...templates/admin/email-test.html` |
| Modify | `...templates/admin/layout.html` (add sidebar nav link) |
| Modify | `...service/EmailService.java` (add `sendTestWindowOpen` + `sendTestResults` helpers) |
