# Email Templates Redesign — Design Spec

**Date:** 2026-06-05  
**Scope:** Redesign all 7 FreeMarker email templates with branded World Cup 2026 imagery, consistent layout system, and production-ready HTML.

---

## Overview

Replace the existing plain FreeMarker email templates with visually rich, production-grade HTML emails. Each template uses a unique hero photograph (cropped from the provided brand assets), a clean white content area, a gold CTA button linking to the live site, and a shared dark footer.

---

## Visual Design System

### Layout

Every email follows this fixed structure (max-width 600px, centered):

```
outer wrapper  bg: #0d1117
└── email card  max-width: 600px
    ├── hero image       600×240px, full-bleed
    ├── content area     bg: #ffffff, padding: 40px 32px
    │   ├── headline     dark navy, bold, 26px
    │   ├── body copy    grey, 16px, line-height 1.6
    │   ├── data block   (matches / leaderboard rows — template-specific)
    │   ├── CTA button   gold bg #D4AF37, dark text #0d1b2a, border-radius 6px
    │   └── plain URL    "Or visit: https://fifaworldcup2026prediction.win"
    └── footer           bg: #0d1b2a, logo + copyright + unsubscribe
```

### Color Palette

| Token | Value | Usage |
|-------|-------|-------|
| `outer-bg` | `#0d1117` | Email outer wrapper |
| `content-bg` | `#ffffff` | Content area |
| `headline` | `#0d1b2a` | h1/h2 text |
| `body-text` | `#4a5568` | Paragraph text |
| `gold` | `#D4AF37` | CTA button, accent borders, icons |
| `cta-text` | `#0d1b2a` | Text on gold buttons |
| `footer-bg` | `#0d1b2a` | Footer background |
| `footer-text` | `#9ca3af` | Footer secondary text |
| `divider` | `#e5e7eb` | Horizontal rules in data tables |

### Typography

- **Headlines:** system font stack, bold, 26px
- **Body:** system font stack, regular, 16px, line-height 1.6
- **Data tables:** 14px, alternating row bg `#f9fafb`
- **CTA button:** bold, 16px, padding 14px 32px

### CTA Button + Plain URL

Every template ends with:
1. A gold HTML button linking to the relevant page on `${appUrl}`
2. A plain-text fallback line directly below: `Or visit: ${appUrl}`

---

## Image Assets

All images live in `src/main/resources/static/images/email/` and are served at `${appUrl}/images/email/<filename>`.

### Required Crops (all 600×240px JPEG)

| Filename | Source | Description |
|----------|--------|-------------|
| `hero-invitation.jpg` | Road to Glory '26 wide stadium banner | Full-width crop, confetti + stadium lights |
| `hero-approval.jpg` | Trophy collection sheet — bottom-center | Fan with arms raised in celebration |
| `hero-rejection.jpg` | First image — WC2026 dark logo on black | Dark branded, neutral |
| `hero-window-open.jpg` | Trophy collection sheet — top image | Large trophy on pitch, confetti |
| `hero-reminder.jpg` | Trophy collection sheet — bottom-right | Rainbow ball in motion |
| `hero-results.jpg` | Trophy collection sheet — bottom-left | Trophy with country flags |
| `hero-leaderboard.jpg` | Road to Glory '26 wide banner (alt crop) | Stadium + logo, different framing than invitation |

### Logo (Footer)

| Filename | Source | Dimensions |
|----------|--------|------------|
| `logo-footer.png` | Road to Glory '26 transparent logo | 160×60px PNG |

---

## Templates

### 1. `invitation.ftlh`

- **Subject:** You're invited to the World Cup 2026 Prediction Game!
- **Hero:** `hero-invitation.jpg`
- **Headline:** Join the Game. Make Your Predictions.
- **Body:** `${inviterName}` has invited you to compete in the World Cup 2026 Prediction Game. Predict match scores, earn points, and climb the leaderboard.
- **CTA:** "Join Now" → `${appUrl}` (invitation emails link to the main site; no invite token in current EmailService)
- **Data block:** None

### 2. `approval.ftlh`

- **Subject:** You're in! Welcome to the game 🏆
- **Hero:** `hero-approval.jpg`
- **Headline:** Welcome to the Game, `${firstName}`!
- **Body:** Your account has been approved. You can now make predictions, score points, and compete for the top spot on the leaderboard.
- **CTA:** "Start Predicting" → `${appUrl}`
- **Data block:** None

### 3. `rejection.ftlh`

- **Subject:** An update on your registration
- **Hero:** `hero-rejection.jpg`
- **Headline:** Thanks for Your Interest, `${firstName}`
- **Body:** After review, we're unable to approve your registration at this time. We appreciate your interest in the World Cup 2026 Prediction Game.
- **CTA:** None (no button — no call to action appropriate for rejection)
- **Plain URL:** `Or visit: ${appUrl}`

### 4. `prediction-window-open.ftlh`

- **Subject:** 🟢 Predictions are open — get your picks in now!
- **Hero:** `hero-window-open.jpg`
- **Headline:** The Predictions Are Open!
- **Body:** Upcoming matches are ready for your predictions. Submit before kickoff to score points.
- **CTA:** "Predict Now" → `${appUrl}`
- **Data block:** Match table — columns: Teams · Date · Kickoff Time

### 5. `prediction-reminder.ftlh`

- **Subject:** ⏰ `${hoursLeft}`h left to predict — don't miss it!
- **Hero:** `hero-reminder.jpg`
- **Headline:** `${hoursLeft}` Hours Left to Predict!
- **Body:** Time is running out. Submit your predictions before the window closes.
- **CTA:** "Make Your Prediction" → `${appUrl}`
- **Data block:** Match table — columns: Teams · Kickoff · Time Remaining

### 6. `results-published.ftlh`

- **Subject:** Results are in — see how you scored!
- **Hero:** `hero-results.jpg`
- **Headline:** The Final Score Is In
- **Body:** The match has ended. Check your prediction result and updated points.
- **CTA:** "See Full Results" → `${appUrl}`
- **Data block:** Score card — Match label, final score (large, centered), points earned

### 7. `leaderboard-digest.ftlh`

- **Subject:** 🏅 Leaderboard update — you're ranked #`${rank}`
- **Hero:** `hero-leaderboard.jpg`
- **Headline:** You're Ranked #`${rank}` with `${points}` Points
- **Body:** Here's how the leaderboard stands after today's matches.
- **CTA:** "View Leaderboard" → `${appUrl}`
- **Data block:** Top 10 table — Rank · Name · Points, with the recipient's row highlighted in gold

---

## Shared Partials

### `_header.ftlh` (updated)

Simplified to contain only the HTML boilerplate (`<!DOCTYPE>`, `<html>`, `<head>`, `<meta>` tags, `<title>${title}</title>`). It no longer renders any visual content — the hero image in each template takes that role. All templates still include it via `<#include "_header.ftlh">` for the document head.

### `_footer.ftlh` (updated)

```
[logo-footer.png]
FIFA World Cup 2026 Prediction Game
fifaworldcup2026prediction.win

© 2026 World Cup Prediction Game · All rights reserved
You received this email because you registered at fifaworldcup2026prediction.win
```

---

## Implementation Notes

- All templates are inline-CSS only (no `<style>` blocks) — required for Outlook compatibility.
- Images reference `${appUrl}/images/email/<filename>` — always absolute URLs.
- Every template includes an `alt` attribute on the hero image for clients with images disabled.
- The plain URL fallback (`Or visit: ${appUrl}`) appears as a `<p>` below every CTA button.
- The outer wrapper uses `bgcolor` attribute in addition to CSS background for Outlook.
- Max-width enforced via a wrapper `<table>` at 600px, not CSS max-width (Outlook requirement).

---

## File Changes

| Action | Path |
|--------|------|
| Add images | `src/main/resources/static/images/email/*.jpg` and `logo-footer.png` |
| Rewrite | `src/main/resources/templates/email/invitation.ftlh` |
| Rewrite | `src/main/resources/templates/email/approval.ftlh` |
| Rewrite | `src/main/resources/templates/email/rejection.ftlh` |
| Rewrite | `src/main/resources/templates/email/prediction-window-open.ftlh` |
| Rewrite | `src/main/resources/templates/email/prediction-reminder.ftlh` |
| Rewrite | `src/main/resources/templates/email/results-published.ftlh` |
| Rewrite | `src/main/resources/templates/email/leaderboard-digest.ftlh` |
| Update | `src/main/resources/templates/email/_header.ftlh` |
| Update | `src/main/resources/templates/email/_footer.ftlh` |
