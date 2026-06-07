# Prediction Confirmation Email

## Purpose

When a user submits predictions for a matchday round, send them an email containing all submitted predictions. This serves as evidence/receipt in case a user disputes their prediction was recorded incorrectly on the website.

## Trigger

After successful return from `PredictionViewService.submitPredictionsForRound()`, fire the email asynchronously so the user's redirect is not blocked.

## Email Content

- **Subject:** `Your predictions for [Round Label] have been submitted`
- **Body:**
  - Greeting with user's first name
  - Round label and submission timestamp
  - Table of all predictions:
    - Home team name
    - Predicted score (home - away)
    - Away team name
    - Kickoff time
  - Disclaimer: "This email serves as your official confirmation of submitted predictions."
  - CTA button linking to the app

## Components

### 1. Freemarker Template: `prediction-confirmation.ftlh`

Located at `src/main/resources/templates/email/prediction-confirmation.ftlh`. Follows existing dark-themed email design with `_header.ftlh` and `_footer.ftlh` partials. Template model:

| Key             | Type                     | Description                    |
|-----------------|--------------------------|--------------------------------|
| `title`         | `String`                 | Email title for `<head>`       |
| `firstName`     | `String`                 | User's first name              |
| `roundLabel`    | `String`                 | e.g. "Group Stage MD1"         |
| `submittedAt`   | `String`                 | Formatted submission timestamp |
| `predictions`   | `List<Map<String,String>>` | Each entry has: `homeTeam`, `awayTeam`, `predictedHome`, `predictedAway`, `kickoff` |
| `appUrl`        | `String`                 | Base URL for CTA               |

### 2. `EmailService.sendPredictionConfirmation()`

New public method:

```java
public void sendPredictionConfirmation(User user, String roundLabel,
                                        List<Map<String, String>> predictions,
                                        LocalDateTime submittedAt)
```

Also a test helper: `sendTestPredictionConfirmation(String to, String firstName, String roundLabel)` for admin email test page.

### 3. Async invocation in `CommunityPredictionController.submitPredictions()`

After the successful `predictionViewService.submitPredictionsForRound()` call, build the prediction details list from the `PredictionSubmitDto` + match data, and call `emailService.sendPredictionConfirmation()` wrapped in `CompletableFuture.runAsync()` so it doesn't block the redirect.

The controller already has access to the user (via `principal`) and the submission DTO (which contains match IDs and scores). We need to load match details (team names, kickoff) to populate the email — the `matchRepository` can be injected into the controller or a helper method added to `PredictionViewService`.

### 4. Admin Test Endpoint

Add `POST /admin/email-test/prediction-confirmation` to `AdminEmailTestController` with params: `to`, `firstName`, `roundLabel`. Generates synthetic prediction data for preview.

### 5. Admin Test UI

Add a "Prediction Confirmation" section to `src/main/resources/templates/admin/email-test.html`.

### 6. Unit Test

Add test in `EmailServiceTest` for `sendPredictionConfirmation()` verifying the correct template name and model keys are passed.

## Async Strategy

Use `CompletableFuture.runAsync()` in the controller after successful submission. Email send failures are logged but do not affect the user's submission flow. This is consistent with the existing approach — no `@Async` annotation needed, keeping it simple.

## Files Changed

1. **New:** `src/main/resources/templates/email/prediction-confirmation.ftlh`
2. **Modified:** `src/main/java/com/worldcup/prediction/service/EmailService.java` — add `sendPredictionConfirmation()` + test helper
3. **Modified:** `src/main/java/com/worldcup/prediction/controller/community/CommunityPredictionController.java` — fire email after submission
4. **Modified:** `src/main/java/com/worldcup/prediction/controller/admin/AdminEmailTestController.java` — add test endpoint
5. **Modified:** `src/main/resources/templates/admin/email-test.html` — add test form
6. **Modified:** `src/test/java/com/worldcup/prediction/service/EmailServiceTest.java` — add test
