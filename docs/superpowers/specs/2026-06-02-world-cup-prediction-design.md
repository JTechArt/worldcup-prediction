# World Cup 2026 Prediction Game — Design Document

**Date:** 2026-06-02  
**Status:** Approved for implementation  
**Deadline:** Tournament kicks off June 11, 2026

---

## 1. Overview

Internal company prediction game for FIFA World Cup 2026. ~100–200 participants predict match scores across all 104 matches (group stage + knockouts), compete on a live leaderboard. Admin manages approvals, results, and prediction windows.

---

## 2. Tech Stack

| Layer | Choice |
|---|---|
| Backend | Java 21 · Spring Boot 3.x · Spring MVC · Spring Security · Spring Data JPA |
| Authentication | Spring Security OAuth2 Client (Google + LinkedIn OIDC) |
| Database | PostgreSQL |
| Frontend | Thymeleaf + HTMX + Tailwind CSS + Alpine.js |
| Animations | CSS animations + GSAP-style keyframes (no JS animation library required) |
| Deployment | Docker (single JAR + PostgreSQL container) |
| Flag images | HatScripts/circle-flags SVG CDN |

---

## 3. User Roles

### Guest (unauthenticated)
- View leaderboard, fixtures, completed results, bracket
- Cannot see any predictions (past or future)

### Participant (authenticated, admin-approved)
- Submit tournament winner prediction (visible to all immediately)
- Submit match predictions during open windows (all-or-nothing per round)
- View own predictions
- View other participants' predictions only after match completion
- Cannot modify predictions after lock time

### Administrator
- Full access to all predictions at all times
- User management: approve/reject registrations, generate temp passwords, enable/disable accounts
- Prediction management: open/close windows manually, send reminders, edit predictions (emergency)
- Tournament management: enter results, manage fixtures/groups/squads/lineups, update bracket

---

## 4. Authentication

### OAuth2 Social Login (primary method)

Login page offers two options: **Sign in with Google** and **Sign in with LinkedIn**.

**OAuth2 scopes requested:**

| Provider | Scopes | Data fetched |
|---|---|---|
| Google | `openid profile email` | First name, last name, email, avatar URL |
| LinkedIn | `openid profile email` (OIDC) | First name, last name, email, avatar URL |

Spring Boot config: `spring-boot-starter-oauth2-client`. Google is a built-in provider. LinkedIn requires a custom OIDC provider configuration pointing to `https://www.linkedin.com/oauth/v2`.

Credentials stored in environment variables: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `LINKEDIN_CLIENT_ID`, `LINKEDIN_CLIENT_SECRET`.

### Registration Flow (OAuth2)

1. User clicks "Sign in with Google" or "Sign in with LinkedIn"
2. OAuth2 flow completes → app receives: first name, last name, email, avatar URL, provider ID
3. If **first time**: account created with status **Pending**, admin notified
4. User sees: *"Your registration request has been received. You'll get an email once approved."*
5. Admin reviews → **Approve** (sends approval email) or **Reject** (sends rejection email)
6. User clicks "Sign in with Google/LinkedIn" again → now has access → status: **Active**
7. No passwords, no temp credentials — OAuth2 handles authentication entirely

### Avatar
- Fetched from Google/LinkedIn profile at first login and stored in the database
- Used in leaderboard and predictions pages
- Refreshed on each login (in case user updates their profile photo)
- Stored as a URL (external profile photo URL) — no file upload required

### Admin accounts
- Admin accounts are pre-created in the database with a linked OAuth2 provider identity
- No username/password login for admins — OAuth2 only

### Fallback
- If a user has both a Google and LinkedIn account with the same email, they are treated as the same user account
- Admin can link/unlink provider identities from the user management panel

---

## 5. Scoring System

| Outcome | Points |
|---|---|
| Exact score (e.g. predict 2–1, result 2–1) | **+3** |
| Correct draw (any draw predicted correctly) | **+2** |
| Correct winner (right team wins, wrong score) | **+1** |
| Wrong prediction | **0** |
| Tournament winner correct | **+10** |

### Tiebreakers (leaderboard order)
1. Total points
2. Exact score count
3. Correct winner count
4. Tournament winner prediction correct (bonus)
5. Earliest registration (optional fallback)

---

## 6. Prediction Rules

- Predictions open **per matchday**, maximum **24 hours before kickoff**
- Predictions lock **1 hour before kickoff**
- **All-or-nothing per open window**: participants must predict all currently-open matches together before submitting — cannot submit some and leave others blank. Matches that are not yet open (outside the 24h window) are not included in this constraint.
- **Prediction visibility**: predictions become visible to everyone (including guests) as soon as the prediction window **locks** (1 hour before kickoff) — regardless of whether all participants have submitted. Once locked, no one can change predictions and all submitted predictions are visible.
- Tournament winner prediction is **visible to everyone immediately** after submission (creates pre-tournament discussion)
- **Knockout stage scoring**: predictions are for **90 minutes only**. Extra time and penalty shootout results are not counted. If a knockout match goes to extra time/pens, the 90-minute score is used for scoring purposes (e.g. if it's 1–1 after 90 mins, a prediction of 1–1 scores +3 regardless of what happens in extra time).

---

## 7. Tournament Data

- Pre-seeded via SQL data script at startup (all 48 teams, 12 groups, 104 match slots)
- Admin enters results manually after each match
- Admin updates knockout progression manually
- Admin can override points if required (audit logged)

---

## 8. Design System

### Color Palette
- **Primary green:** `#00c853` (actions, highlights)
- **Dark green:** `#006b2a` (headers, nav)
- **Orange:** `#FF5722` (urgent, countdown, CTAs)
- **Yellow/Gold:** `#FFD600` (top-1 highlight, submit button)
- **Background:** `#f0fdf5` (light green tint)
- **Card:** `#ffffff`

### Typography
- **Headings/titles:** Bebas Neue (imported from Google Fonts)
- **Body/UI:** Inter

### Animation principles
- All page sections animate in on load (`fadeUp` keyframe, staggered)
- Flag badges use `perspective rotateY` 3D wave
- Navbar slides down on load
- Phase headers have shimmer sweep animation
- Submit button pulses with glow when active
- Rank change arrows bounce (▲ green, ▼ red)

### Flag images
- Source: `https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/{code}.svg`
- Fallback: `https://flagcdn.com/w80/{code}.png`
- Used as circular avatar badge (24×24px, bottom-right corner of avatar, 3D wave animation)

---

## 9. Pages

### 9.1 Landing Page (public)
- Full-screen background: footiehound WC2026 football image, slow Ken Burns zoom animation
- Light green overlay to keep readability
- Headlines: "PREDICT. COMPETE. WIN." (Bebas Neue, large)
- Live countdown timer to June 11 kickoff (days / hours / minutes / seconds)
- "Sign in with Google" and "Sign in with LinkedIn" OAuth2 buttons → registration/login flow
- "View Leaderboard" → public leaderboard (no login required)
- Floating particles (⚽ symbols + colored dots)
- Bottom feature chips: 48 teams · 104 matches · Live leaderboard · Bracket

### 9.2 Home Page (authenticated)
- Mini leaderboard widget (top 10 cards in 2-column grid)
  - Each card: rank badge · square avatar photo + circle flag badge (bottom-right) · name · points · exact score count · rank change arrow
  - Gold/silver/bronze ring animation for top 3
- Next match to predict pill (orange, pulsing)
- Quick stats row (your rank, your points, exact count)
- Links to Predictions, Leaderboard, Bracket

### 9.3 Leaderboard Page (public)
**Two components:**

**Mini widget** (used on Home page — top 10 only):
- Card grid layout (2 columns on desktop)
- Compact: rank · avatar+flag badge · name · points · exact count · rank arrow

**Full prediction breakdown** (Leaderboard page — all participants):
- 3-panel layout: fixed player column | scrollable game columns | fixed total column
- Phase headers (one row, all phases side-by-side): GS R1 · GS R2 · GS R3 · R32 · R16 · QF · SF · Final
  - Each phase has its own gradient color with shimmer animation
- Game column headers: date, team flags (emoji), actual score (or pulsing dots if pending)
- Data cells color-coded: green (exact +3), yellow (winner +1), blue (draw +2), red (wrong 0), transparent (pending)
- Auto-scrolls to current phase on load
- Phase jump buttons: GS R1 | GS R2 | GS R3 | ► R32 | R16 | QF | SF | Final
- Sticky player column (left) and total column (right) — 3-panel div layout (no CSS sticky table hacks)

### 9.4 Predictions Page (authenticated)

**Round navigation tabs** (top): all rounds with status badges (✅ past / ⚡ open / greyed future)

**Current open round — two view modes** (toggle):
- **List view**: all matches grouped by date, inline `−/score/+` inputs per team, status pills (⚡ open / 🔴 urgent / ✓ filled)
- **Card view**: one match at a time carousel with large circle flags, progress bar + dots, Save & Next navigation

**All-or-nothing submit**:
- Progress banner: `X / N filled` with animated progress bar
- Submit button disabled until all matches filled
- On submit → all predictions locked, success feedback

**Past rounds** (below, read-only, collapsible accordion):
- Each past round shows: actual score ← "actual · pred →" → predicted score
- Color-coded by outcome (exact/winner/draw/wrong)
- Points earned per match (+3, +2, +1, 0)
- Round total points shown in header

### 9.5 Fixtures Page (public)
- List of all matches grouped by phase and date
- Filter tabs: All · Group Stage · Knockouts · Today
- Each fixture: date · time · venue · team flags · team names · score (if completed) or "TBD"
- Completed matches link to detailed view with lineups (when available)

### 9.6 Groups Page (public)
- 12 groups displayed as cards (A–L)
- Each group: 4 teams with standings (P · W · D · L · GF · GA · GD · Pts)
- Highlight top 2 (qualify for R32) and best 8 3rd-place teams

### 9.7 Knockout Bracket Page (public)
- Visual SVG bracket: R32 → R16 → QF → SF → 3rd Place + Final
- Circle flag badges per slot, animated progression
- Completed matches show score
- TBD slots show animated pulsing dots
- Responsive horizontal scroll on smaller screens

### 9.8 Admin Panel
- **Dashboard**: pending registrations count, today's match schedule, open prediction windows
- **Users**: list with status · approve/reject · generate temp password · enable/disable · resend email
- **Matches**: enter results, open/close prediction windows, send reminders
- **Predictions**: view all predictions (including hidden pre-match) · edit emergency override
- **Tournament**: manage fixtures, squads, lineups, knockout progression

---

## 10. External Data API

### Tournament data & live results

Admin can always enter results manually (audit-logged). Optionally, results and fixture data can be fetched automatically from a third-party API.

**Recommended: football-data.org**
- Free tier available (no credit card), covers FIFA World Cup
- Provides: fixtures, live scores, match results, team squads, lineups
- REST API with simple API key header auth
- Rate limit on free tier: 10 requests/minute — sufficient for a polling scheduler
- Java: use `RestTemplate` or `WebClient` to call `https://api.football-data.org/v4/competitions/WC/matches`

**Alternative: API-Football (RapidAPI)**
- More comprehensive (lineups, events, live score updates)
- Free tier: 100 requests/day — tight for frequent polling but workable
- URL: `https://api-football-v1.p.rapidapi.com/v3/fixtures`

### Integration strategy (v1)

1. **Initial seed**: full 2026 WC fixture schedule seeded via SQL at startup (all 104 match slots with teams TBD for knockout rounds)
2. **Scheduled sync**: Spring `@Scheduled` job runs every 5 minutes to fetch completed match results from the API and update the database automatically
3. **Admin fallback**: if API is unavailable or returns incorrect data, admin can override results manually via the admin panel
4. **Knockout progression**: updated automatically from API once knockout teams are determined; admin can also set manually
5. **API key**: stored in `application.properties` (not committed); configurable via environment variable `FOOTBALL_API_KEY`

### What is NOT automated (v1)
- Opening/closing prediction windows (admin does this manually)
- Sending email notifications (admin triggers manually)
- Lineup data (admin enters or pulled from API as a future enhancement)

---

## 11. Notifications (email)  

Email provider: SMTP (configurable, to be added post-launch) — admin manually triggers:
- **Prediction reminder**: "Predictions for today's matches close in 2 hours"
- **Results published**: "Match results published. Leaderboard updated"
- **Tournament winner scored**: "World Cup winner points awarded"

---

## 11. Security

- **OAuth2 authentication only** — no username/password stored in the app; Google and LinkedIn handle credential security
- Session-based authorization after OAuth2 callback (Spring Security `HttpSession`)
- CSRF protection on all state-changing forms and HTMX requests
- Role-based authorization: `GUEST` (unauthenticated) / `PARTICIPANT` / `ADMIN`
- **Predictions visibility enforced server-side**: pre-lock predictions are never included in HTML or API responses for non-admin users, regardless of client-side state
- Account status checked on every request: disabled accounts are immediately invalidated
- Audit log for all admin actions: result entry, point overrides, user approval/rejection, window open/close
- OAuth2 client secrets in environment variables only — never committed to source

---

## 12. Non-Functional Requirements

- Target: 100–200 concurrent users, low traffic
- Mobile-friendly responsive UI (Tailwind breakpoints)
- Single deployable Docker container (Spring Boot JAR + PostgreSQL)
- External API integration: football-data.org for automated result sync (configurable, falls back to manual admin entry)
