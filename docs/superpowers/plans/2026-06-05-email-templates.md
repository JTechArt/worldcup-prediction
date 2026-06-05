# Email Templates Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all 7 FreeMarker email templates with branded, image-rich, production-grade HTML emails using the World Cup 2026 visual assets.

**Architecture:** Each template is a self-contained FreeMarker `.ftlh` file using table-based inline-CSS layout (Outlook-compatible). `_header.ftlh` provides the HTML document head boilerplate. `_footer.ftlh` provides a reusable dark-branded footer table row. Hero images are served as static assets from the Spring Boot app.

**Tech Stack:** FreeMarker 2.3.x, Spring Boot 3.3, HTML email (table layout, inline CSS only), ImageMagick-cropped JPEGs at `/images/email/`

---

## Shared HTML structure (reference for all templates)

Every template follows this skeleton — do not deviate:

```html
<#assign title="Email Subject">
<#include "_header.ftlh">
<body bgcolor="#0d1117" style="margin:0;padding:0;background-color:#0d1117;">
<table width="100%" cellpadding="0" cellspacing="0" bgcolor="#0d1117">
  <tr>
    <td align="center" style="padding:20px 0;">
      <table width="600" cellpadding="0" cellspacing="0" bgcolor="#ffffff"
             style="max-width:600px;width:100%;">
        <!-- HERO -->
        <tr>
          <td style="padding:0;line-height:0;">
            <img src="${appUrl}/images/email/hero-XXXX.jpg"
                 width="600" alt="ALT TEXT"
                 style="display:block;width:100%;max-width:600px;border:0;">
          </td>
        </tr>
        <!-- CONTENT -->
        <tr>
          <td bgcolor="#ffffff"
              style="background-color:#ffffff;padding:40px 32px;">
            <!-- TEMPLATE-SPECIFIC CONTENT HERE -->
          </td>
        </tr>
        <!-- FOOTER -->
        <#include "_footer.ftlh">
      </table>
    </td>
  </tr>
</table>
</body>
</html>
```

**Reusable snippets:**

Headline:
```html
<h1 style="font-family:Helvetica Neue,Arial,sans-serif;font-size:26px;font-weight:800;color:#0d1b2a;margin:0 0 16px;line-height:1.2;">HEADLINE</h1>
```

Body paragraph:
```html
<p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;color:#4a5568;line-height:1.6;margin:0 0 16px;">COPY</p>
```

CTA button + plain URL:
```html
<table width="100%" cellpadding="0" cellspacing="0">
  <tr>
    <td align="center" style="padding:24px 0 8px;">
      <a href="${appUrl}" style="display:inline-block;background-color:#D4AF37;color:#0d1b2a;font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;font-weight:700;text-decoration:none;padding:14px 36px;border-radius:6px;">CTA LABEL</a>
    </td>
  </tr>
  <tr>
    <td align="center" style="padding-bottom:8px;">
      <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#9ca3af;margin:0;">
        Or visit: <a href="${appUrl}" style="color:#D4AF37;text-decoration:none;">${appUrl}</a>
      </p>
    </td>
  </tr>
</table>
```

---

## Files

| Action | Path |
|--------|------|
| Modify | `src/main/resources/templates/email/_header.ftlh` |
| Modify | `src/main/resources/templates/email/_footer.ftlh` |
| Modify | `src/main/resources/templates/email/invitation.ftlh` |
| Modify | `src/main/resources/templates/email/approval.ftlh` |
| Modify | `src/main/resources/templates/email/rejection.ftlh` |
| Modify | `src/main/resources/templates/email/prediction-window-open.ftlh` |
| Modify | `src/main/resources/templates/email/prediction-reminder.ftlh` |
| Modify | `src/main/resources/templates/email/results-published.ftlh` |
| Modify | `src/main/resources/templates/email/leaderboard-digest.ftlh` |
| Modify | `src/main/java/com/worldcup/prediction/service/EmailService.java` (add `appUrl` to rejection model) |

---

## Task 1: Update shared partials

**Files:**
- Modify: `src/main/resources/templates/email/_header.ftlh`
- Modify: `src/main/resources/templates/email/_footer.ftlh`

- [ ] **Step 1: Rewrite `_header.ftlh`**

Replace entire file with:
```html
<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <title>${title}</title>
</head>
```

- [ ] **Step 2: Rewrite `_footer.ftlh`**

Replace entire file with:
```html
<tr>
  <td bgcolor="#0d1b2a"
      style="background-color:#0d1b2a;padding:24px 32px;text-align:center;">
    <img src="${appUrl}/images/email/logo-footer.png"
         alt="World Cup 2026 Prediction Game"
         width="100"
         style="display:block;margin:0 auto 14px;border:0;">
    <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:12px;color:#9ca3af;margin:0 0 6px;">
      &copy; 2026 World Cup Prediction Game &middot; All rights reserved
    </p>
    <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;color:#6b7280;margin:0;">
      You received this email because you registered at
      <a href="${appUrl}" style="color:#D4AF37;text-decoration:none;">fifaworldcup2026prediction.win</a>
    </p>
  </td>
</tr>
```

- [ ] **Step 3: Commit**
```bash
git add src/main/resources/templates/email/_header.ftlh \
        src/main/resources/templates/email/_footer.ftlh
git commit -m "feat: redesign email partials — dark footer, minimal header boilerplate"
```

---

## Task 2: invitation.ftlh

**Variables:** `inviterName`, `appUrl`
**Hero:** `hero-invitation.jpg` (Road to Glory stadium banner)

- [ ] **Step 1: Rewrite `invitation.ftlh`**

Replace entire file with:
```html
<#assign title="You're invited to the World Cup 2026 Prediction Game!">
<#include "_header.ftlh">
<body bgcolor="#0d1117" style="margin:0;padding:0;background-color:#0d1117;">
<table width="100%" cellpadding="0" cellspacing="0" bgcolor="#0d1117">
  <tr>
    <td align="center" style="padding:20px 0;">
      <table width="600" cellpadding="0" cellspacing="0" bgcolor="#ffffff" style="max-width:600px;width:100%;">
        <tr>
          <td style="padding:0;line-height:0;">
            <img src="${appUrl}/images/email/hero-invitation.jpg" width="600" alt="Road to Glory 2026" style="display:block;width:100%;max-width:600px;border:0;">
          </td>
        </tr>
        <tr>
          <td bgcolor="#ffffff" style="background-color:#ffffff;padding:40px 32px;">
            <h1 style="font-family:Helvetica Neue,Arial,sans-serif;font-size:26px;font-weight:800;color:#0d1b2a;margin:0 0 16px;line-height:1.2;">Join the Game. Make Your Predictions.</h1>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;color:#4a5568;line-height:1.6;margin:0 0 16px;">
              <strong>${inviterName}</strong> has invited you to compete in the <strong>World Cup 2026 Prediction Game</strong>.
            </p>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;color:#4a5568;line-height:1.6;margin:0 0 24px;">
              Predict match scores before kickoff, earn points for correct calls, and climb the leaderboard to be crowned champion.
            </p>
            <table width="100%" cellpadding="0" cellspacing="0">
              <tr>
                <td align="center" style="padding:8px 0;">
                  <a href="${appUrl}" style="display:inline-block;background-color:#D4AF37;color:#0d1b2a;font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;font-weight:700;text-decoration:none;padding:14px 36px;border-radius:6px;">Join Now</a>
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
git add src/main/resources/templates/email/invitation.ftlh
git commit -m "feat: redesign invitation email template"
```

---

## Task 3: approval.ftlh

**Variables:** `firstName`, `appUrl`
**Hero:** `hero-approval.jpg` (fan celebrating)

- [ ] **Step 1: Rewrite `approval.ftlh`**

Replace entire file with:
```html
<#assign title="You're in! Welcome to the game">
<#include "_header.ftlh">
<body bgcolor="#0d1117" style="margin:0;padding:0;background-color:#0d1117;">
<table width="100%" cellpadding="0" cellspacing="0" bgcolor="#0d1117">
  <tr>
    <td align="center" style="padding:20px 0;">
      <table width="600" cellpadding="0" cellspacing="0" bgcolor="#ffffff" style="max-width:600px;width:100%;">
        <tr>
          <td style="padding:0;line-height:0;">
            <img src="${appUrl}/images/email/hero-approval.jpg" width="600" alt="Welcome to the game" style="display:block;width:100%;max-width:600px;border:0;">
          </td>
        </tr>
        <tr>
          <td bgcolor="#ffffff" style="background-color:#ffffff;padding:40px 32px;">
            <h1 style="font-family:Helvetica Neue,Arial,sans-serif;font-size:26px;font-weight:800;color:#0d1b2a;margin:0 0 16px;line-height:1.2;">Welcome to the Game, ${firstName}!</h1>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;color:#4a5568;line-height:1.6;margin:0 0 16px;">
              Your account has been approved. You can now make predictions, score points, and compete for the top spot on the leaderboard.
            </p>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;color:#4a5568;line-height:1.6;margin:0 0 24px;">
              The World Cup starts soon — make sure your predictions are in before each kickoff!
            </p>
            <table width="100%" cellpadding="0" cellspacing="0">
              <tr>
                <td align="center" style="padding:8px 0;">
                  <a href="${appUrl}" style="display:inline-block;background-color:#D4AF37;color:#0d1b2a;font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;font-weight:700;text-decoration:none;padding:14px 36px;border-radius:6px;">Start Predicting</a>
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
git add src/main/resources/templates/email/approval.ftlh
git commit -m "feat: redesign approval email template"
```

---

## Task 4: rejection.ftlh + add appUrl to EmailService

**Variables:** `firstName`, `appUrl` (appUrl must be added to model in EmailService)
**Hero:** `hero-rejection.jpg` (WC2026 dark logo)

- [ ] **Step 1: Add `appUrl` to rejection email model in `EmailService.java`**

Find `sendRejectionEmail(User user)`. It currently builds a model without `appUrl`. Add it:
```java
model.put("appUrl", appBaseUrl);
```
The `appBaseUrl` field is already injected in `EmailService` (it's used by other methods). No other changes needed.

- [ ] **Step 2: Rewrite `rejection.ftlh`**

Replace entire file with:
```html
<#assign title="An update on your registration">
<#include "_header.ftlh">
<body bgcolor="#0d1117" style="margin:0;padding:0;background-color:#0d1117;">
<table width="100%" cellpadding="0" cellspacing="0" bgcolor="#0d1117">
  <tr>
    <td align="center" style="padding:20px 0;">
      <table width="600" cellpadding="0" cellspacing="0" bgcolor="#ffffff" style="max-width:600px;width:100%;">
        <tr>
          <td style="padding:0;line-height:0;">
            <img src="${appUrl}/images/email/hero-rejection.jpg" width="600" alt="World Cup 2026 Prediction Game" style="display:block;width:100%;max-width:600px;border:0;">
          </td>
        </tr>
        <tr>
          <td bgcolor="#ffffff" style="background-color:#ffffff;padding:40px 32px;">
            <h1 style="font-family:Helvetica Neue,Arial,sans-serif;font-size:26px;font-weight:800;color:#0d1b2a;margin:0 0 16px;line-height:1.2;">Thanks for Your Interest, ${firstName}</h1>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;color:#4a5568;line-height:1.6;margin:0 0 16px;">
              After review, we're unable to approve your registration for the World Cup 2026 Prediction Game at this time.
            </p>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;color:#4a5568;line-height:1.6;margin:0 0 24px;">
              We appreciate your interest and hope to welcome you in a future competition.
            </p>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#9ca3af;margin:0;text-align:center;">
              Visit: <a href="${appUrl}" style="color:#D4AF37;text-decoration:none;">${appUrl}</a>
            </p>
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

- [ ] **Step 3: Commit**
```bash
git add src/main/resources/templates/email/rejection.ftlh \
        src/main/java/com/worldcup/prediction/service/EmailService.java
git commit -m "feat: redesign rejection email template, add appUrl to model"
```

---

## Task 5: prediction-window-open.ftlh

**Variables:** `firstName`, `matches[]` (each has `.label`, `.kickoff`), `appUrl`
**Hero:** `hero-window-open.jpg` (trophy on pitch with confetti)

- [ ] **Step 1: Rewrite `prediction-window-open.ftlh`**

Replace entire file with:
```html
<#assign title="Predictions are open — get your picks in now!">
<#include "_header.ftlh">
<body bgcolor="#0d1117" style="margin:0;padding:0;background-color:#0d1117;">
<table width="100%" cellpadding="0" cellspacing="0" bgcolor="#0d1117">
  <tr>
    <td align="center" style="padding:20px 0;">
      <table width="600" cellpadding="0" cellspacing="0" bgcolor="#ffffff" style="max-width:600px;width:100%;">
        <tr>
          <td style="padding:0;line-height:0;">
            <img src="${appUrl}/images/email/hero-window-open.jpg" width="600" alt="Predictions are open!" style="display:block;width:100%;max-width:600px;border:0;">
          </td>
        </tr>
        <tr>
          <td bgcolor="#ffffff" style="background-color:#ffffff;padding:40px 32px;">
            <h1 style="font-family:Helvetica Neue,Arial,sans-serif;font-size:26px;font-weight:800;color:#0d1b2a;margin:0 0 16px;line-height:1.2;">The Predictions Are Open, ${firstName}!</h1>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;color:#4a5568;line-height:1.6;margin:0 0 20px;">
              Submit your predictions before kickoff to score points. Miss the window and you miss the points.
            </p>
            <#if matches?has_content>
            <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;margin-bottom:24px;">
              <tr>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;padding:0 0 8px;border-bottom:2px solid #D4AF37;">Match</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;padding:0 0 8px;border-bottom:2px solid #D4AF37;text-align:right;">Kickoff</td>
              </tr>
              <#list matches as m>
              <tr style="background-color:<#if m?index % 2 == 0>#f9fafb<#else>#ffffff</#if>;">
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:14px;font-weight:600;color:#0d1b2a;padding:10px 8px 10px 0;border-bottom:1px solid #e5e7eb;">${m.label}</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#4a5568;padding:10px 0;border-bottom:1px solid #e5e7eb;text-align:right;">${m.kickoff}</td>
              </tr>
              </#list>
            </table>
            </#if>
            <table width="100%" cellpadding="0" cellspacing="0">
              <tr>
                <td align="center" style="padding:8px 0;">
                  <a href="${appUrl}" style="display:inline-block;background-color:#D4AF37;color:#0d1b2a;font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;font-weight:700;text-decoration:none;padding:14px 36px;border-radius:6px;">Predict Now</a>
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
git add src/main/resources/templates/email/prediction-window-open.ftlh
git commit -m "feat: redesign prediction-window-open email template"
```

---

## Task 6: prediction-reminder.ftlh

**Variables:** `firstName`, `matches[]` (each has `.label`, `.kickoff`), `hoursLeft`, `appUrl`
**Hero:** `hero-reminder.jpg` (rainbow ball in motion)

- [ ] **Step 1: Rewrite `prediction-reminder.ftlh`**

Replace entire file with:
```html
<#assign title="Last chance — ${hoursLeft}h left to predict!">
<#include "_header.ftlh">
<body bgcolor="#0d1117" style="margin:0;padding:0;background-color:#0d1117;">
<table width="100%" cellpadding="0" cellspacing="0" bgcolor="#0d1117">
  <tr>
    <td align="center" style="padding:20px 0;">
      <table width="600" cellpadding="0" cellspacing="0" bgcolor="#ffffff" style="max-width:600px;width:100%;">
        <tr>
          <td style="padding:0;line-height:0;">
            <img src="${appUrl}/images/email/hero-reminder.jpg" width="600" alt="Time is running out!" style="display:block;width:100%;max-width:600px;border:0;">
          </td>
        </tr>
        <tr>
          <td bgcolor="#ffffff" style="background-color:#ffffff;padding:40px 32px;">
            <h1 style="font-family:Helvetica Neue,Arial,sans-serif;font-size:26px;font-weight:800;color:#0d1b2a;margin:0 0 16px;line-height:1.2;">${hoursLeft} Hours Left to Predict, ${firstName}!</h1>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;color:#4a5568;line-height:1.6;margin:0 0 20px;">
              Time is running out. You haven't submitted your predictions for the following matches yet:
            </p>
            <#if matches?has_content>
            <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;margin-bottom:24px;">
              <tr>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;padding:0 0 8px;border-bottom:2px solid #D4AF37;">Match</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;padding:0 0 8px;border-bottom:2px solid #D4AF37;text-align:right;">Kickoff</td>
              </tr>
              <#list matches as m>
              <tr style="background-color:<#if m?index % 2 == 0>#f9fafb<#else>#ffffff</#if>;">
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:14px;font-weight:600;color:#0d1b2a;padding:10px 8px 10px 0;border-bottom:1px solid #e5e7eb;">${m.label}</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#4a5568;padding:10px 0;border-bottom:1px solid #e5e7eb;text-align:right;">${m.kickoff}</td>
              </tr>
              </#list>
            </table>
            </#if>
            <table width="100%" cellpadding="0" cellspacing="0">
              <tr>
                <td align="center" style="padding:8px 0;">
                  <a href="${appUrl}" style="display:inline-block;background-color:#D4AF37;color:#0d1b2a;font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;font-weight:700;text-decoration:none;padding:14px 36px;border-radius:6px;">Make Your Prediction</a>
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
git add src/main/resources/templates/email/prediction-reminder.ftlh
git commit -m "feat: redesign prediction-reminder email template"
```

---

## Task 7: results-published.ftlh

**Variables:** `firstName`, `matchLabel`, `score`, `appUrl`
**Hero:** `hero-results.jpg` (trophy on pitch, confetti)

- [ ] **Step 1: Rewrite `results-published.ftlh`**

Replace entire file with:
```html
<#assign title="Results are in — see how you scored!">
<#include "_header.ftlh">
<body bgcolor="#0d1117" style="margin:0;padding:0;background-color:#0d1117;">
<table width="100%" cellpadding="0" cellspacing="0" bgcolor="#0d1117">
  <tr>
    <td align="center" style="padding:20px 0;">
      <table width="600" cellpadding="0" cellspacing="0" bgcolor="#ffffff" style="max-width:600px;width:100%;">
        <tr>
          <td style="padding:0;line-height:0;">
            <img src="${appUrl}/images/email/hero-results.jpg" width="600" alt="Results are in!" style="display:block;width:100%;max-width:600px;border:0;">
          </td>
        </tr>
        <tr>
          <td bgcolor="#ffffff" style="background-color:#ffffff;padding:40px 32px;">
            <h1 style="font-family:Helvetica Neue,Arial,sans-serif;font-size:26px;font-weight:800;color:#0d1b2a;margin:0 0 16px;line-height:1.2;">The Final Score Is In, ${firstName}!</h1>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;color:#4a5568;line-height:1.6;margin:0 0 24px;">
              Here's the result for <strong>${matchLabel}</strong>:
            </p>
            <table width="100%" cellpadding="0" cellspacing="0" style="margin-bottom:24px;">
              <tr>
                <td align="center"
                    style="background-color:#0d1b2a;border-radius:8px;padding:24px;">
                  <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:14px;color:#9ca3af;margin:0 0 8px;text-transform:uppercase;letter-spacing:2px;">${matchLabel}</p>
                  <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:40px;font-weight:800;color:#D4AF37;margin:0;letter-spacing:4px;">${score}</p>
                </td>
              </tr>
            </table>
            <table width="100%" cellpadding="0" cellspacing="0">
              <tr>
                <td align="center" style="padding:8px 0;">
                  <a href="${appUrl}" style="display:inline-block;background-color:#D4AF37;color:#0d1b2a;font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;font-weight:700;text-decoration:none;padding:14px 36px;border-radius:6px;">See Full Results</a>
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
git add src/main/resources/templates/email/results-published.ftlh
git commit -m "feat: redesign results-published email template"
```

---

## Task 8: leaderboard-digest.ftlh

**Variables:** `firstName`, `rank`, `points`, `topEntries[]` (each has `.rank`, `.name`, `.points`), `matchResults[]` (each has `.label`, `.score`), `appUrl`
**Hero:** `hero-leaderboard.jpg` (fan arms raised)

- [ ] **Step 1: Rewrite `leaderboard-digest.ftlh`**

Replace entire file with:
```html
<#assign title="Leaderboard update — you're ranked #${rank}">
<#include "_header.ftlh">
<body bgcolor="#0d1117" style="margin:0;padding:0;background-color:#0d1117;">
<table width="100%" cellpadding="0" cellspacing="0" bgcolor="#0d1117">
  <tr>
    <td align="center" style="padding:20px 0;">
      <table width="600" cellpadding="0" cellspacing="0" bgcolor="#ffffff" style="max-width:600px;width:100%;">
        <tr>
          <td style="padding:0;line-height:0;">
            <img src="${appUrl}/images/email/hero-leaderboard.jpg" width="600" alt="Leaderboard Update" style="display:block;width:100%;max-width:600px;border:0;">
          </td>
        </tr>
        <tr>
          <td bgcolor="#ffffff" style="background-color:#ffffff;padding:40px 32px;">
            <h1 style="font-family:Helvetica Neue,Arial,sans-serif;font-size:26px;font-weight:800;color:#0d1b2a;margin:0 0 8px;line-height:1.2;">You're Ranked #${rank}</h1>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;color:#4a5568;line-height:1.6;margin:0 0 24px;">
              Hi ${firstName} — you have <strong>${points} points</strong> after today's matches. Here's where things stand:
            </p>

            <#if matchResults?has_content>
            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;margin:0 0 8px;">Today's Results</p>
            <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;margin-bottom:28px;">
              <#list matchResults as r>
              <tr style="background-color:<#if r?index % 2 == 0>#f9fafb<#else>#ffffff</#if>;">
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#0d1b2a;padding:9px 8px 9px 0;border-bottom:1px solid #e5e7eb;">${r.label}</td>
                <td style="font-family:Courier New,monospace;font-size:14px;font-weight:700;color:#0d1b2a;padding:9px 0;border-bottom:1px solid #e5e7eb;text-align:right;">${r.score}</td>
              </tr>
              </#list>
            </table>
            </#if>

            <p style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:1px;margin:0 0 8px;">Top 10 Standings</p>
            <table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;margin-bottom:28px;">
              <tr>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;color:#9ca3af;padding:0 0 6px;border-bottom:2px solid #D4AF37;width:32px;text-align:center;">#</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;color:#9ca3af;padding:0 0 6px;border-bottom:2px solid #D4AF37;">Name</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:11px;color:#9ca3af;padding:0 0 6px;border-bottom:2px solid #D4AF37;text-align:right;">Points</td>
              </tr>
              <#list topEntries as e>
              <tr style="background-color:<#if e.rank == rank>#FFF8E1<#elseif e?index % 2 == 0>#f9fafb<#else>#ffffff</#if>;">
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:<#if e.rank == rank>#D4AF37<#else>#9ca3af</#if>;padding:9px 0;border-bottom:1px solid #e5e7eb;text-align:center;font-weight:<#if e.rank == rank>700<#else>400</#if>;">${e.rank}</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#0d1b2a;padding:9px 8px;border-bottom:1px solid #e5e7eb;font-weight:<#if e.rank == rank>700<#else>400</#if>;">${e.name}</td>
                <td style="font-family:Helvetica Neue,Arial,sans-serif;font-size:13px;color:#0d1b2a;padding:9px 0;border-bottom:1px solid #e5e7eb;text-align:right;font-weight:700;">${e.points}</td>
              </tr>
              </#list>
            </table>

            <table width="100%" cellpadding="0" cellspacing="0">
              <tr>
                <td align="center" style="padding:8px 0;">
                  <a href="${appUrl}" style="display:inline-block;background-color:#D4AF37;color:#0d1b2a;font-family:Helvetica Neue,Arial,sans-serif;font-size:16px;font-weight:700;text-decoration:none;padding:14px 36px;border-radius:6px;">View Leaderboard</a>
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
git add src/main/resources/templates/email/leaderboard-digest.ftlh
git commit -m "feat: redesign leaderboard-digest email template"
```

---

## Task 9: Commit images + verify

**Files:**
- New: `src/main/resources/static/images/email/` (all 8 files)
- Removed: `src/main/resources/static/email/2.png`, `a_set_of_...png`, `crop_email_images.sh`

- [ ] **Step 1: Commit static assets**
```bash
git add src/main/resources/static/images/email/ \
        src/main/resources/static/email/
git commit -m "feat: add email hero images and logo-footer, remove redundant source files"
```

- [ ] **Step 2: Build to confirm no template syntax errors**
```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS. FreeMarker template syntax is validated at render time, not compile time — but a compile pass confirms the Java side (EmailService appUrl change) is clean.

- [ ] **Step 3: Smoke-test one template via admin UI**

Start the app locally:
```bash
./mvnw spring-boot:run
```
Navigate to `http://localhost:8888/admin/notifications` → send a test invitation to your own email address. Confirm the email renders with the hero image, gold CTA, and dark footer.

- [ ] **Step 4: Final commit + push**
```bash
git push origin main
```
GitHub Actions will build and deploy. After deploy, verify images load at `https://fifaworldcup2026prediction.win/images/email/hero-invitation.jpg`.
