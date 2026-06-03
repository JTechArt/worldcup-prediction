# Part 5: Public Pages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build all publicly accessible pages — base Thymeleaf layout, landing page with live countdown, fixtures list, groups standings, and authenticated home page — delivering a polished, animated UI consistent with the FIFA World Cup 2026 design system.

**Architecture:** All pages extend a shared `templates/layout/base.html` Thymeleaf layout that provides the navbar, footer, CDN assets (Tailwind, HTMX, Alpine.js), and global CSS animations. Controllers in `com.worldcup.prediction.controller` serve Thymeleaf views with data from the service layer (Parts 1–4). The landing page uses inline Alpine.js for the countdown timer and pure CSS for particles and Ken Burns background animation.

**Tech Stack:** Thymeleaf, HTMX, Alpine.js, Tailwind CDN, Spring MVC, Spring Security (Thymeleaf extras), Google Fonts CDN

**Depends on:** Part 1 (entities/DB schema), Part 2 (OAuth2 auth), Part 3 (match/group service), Part 4 (leaderboard service + mini-leaderboard fragment)

**Next parts:** Part 6 (Predictions UI), Part 7 (Admin Panel)

---

## File Structure

```
src/main/resources/
└── templates/
    ├── layout/
    │   └── base.html                          # Base Thymeleaf layout (navbar + footer + CDN)
    ├── index.html                              # Landing page (/)
    ├── home.html                               # Authenticated home (/home)
    ├── fixtures.html                           # Fixtures page (/fixtures)
    ├── groups.html                             # Groups page (/groups)
    └── fragments/
        └── fixture-rows.html                  # HTMX partial for fixture filter

src/main/java/com/worldcup/prediction/controller/
├── LandingController.java                     # GET /
├── HomeController.java                        # GET /home
├── FixtureController.java                     # GET /fixtures, GET /fixtures/filter
└── GroupController.java                       # GET /groups

src/main/java/com/worldcup/prediction/dto/
├── FixtureViewDto.java                        # View-layer DTO for a single fixture row
└── GroupStandingDto.java                      # View-layer DTO for one team's group row
```

---

### Task 1: DTOs for View Layer

**Files:**
- Create: `src/main/java/com/worldcup/prediction/dto/FixtureViewDto.java`
- Create: `src/main/java/com/worldcup/prediction/dto/GroupStandingDto.java`

These DTOs carry all data the templates need without leaking entity internals.

- [ ] **Step 1: Create FixtureViewDto.java**

```java
package com.worldcup.prediction.dto;

import java.time.LocalDateTime;

/**
 * Read-only DTO passed to fixtures.html for each match row.
 */
public class FixtureViewDto {

    private Long id;
    private String phase;          // "Group Stage", "Round of 32", "Quarter-Final", etc.
    private String groupLabel;     // "Group A" — null for knockouts
    private LocalDateTime kickoff; // UTC
    private String venue;
    private String city;

    private String homeTeamName;
    private String homeTeamCode;   // ISO 3166-1 alpha-2 for flag CDN, e.g. "br"
    private String awayTeamName;
    private String awayTeamCode;

    private Integer homeScore;     // null if not played
    private Integer awayScore;     // null if not played

    private String status;         // "SCHEDULED", "LIVE", "COMPLETED"

    public FixtureViewDto() {}

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public String getGroupLabel() { return groupLabel; }
    public void setGroupLabel(String groupLabel) { this.groupLabel = groupLabel; }

    public LocalDateTime getKickoff() { return kickoff; }
    public void setKickoff(LocalDateTime kickoff) { this.kickoff = kickoff; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getHomeTeamName() { return homeTeamName; }
    public void setHomeTeamName(String homeTeamName) { this.homeTeamName = homeTeamName; }

    public String getHomeTeamCode() { return homeTeamCode; }
    public void setHomeTeamCode(String homeTeamCode) { this.homeTeamCode = homeTeamCode; }

    public String getAwayTeamName() { return awayTeamName; }
    public void setAwayTeamName(String awayTeamName) { this.awayTeamName = awayTeamName; }

    public String getAwayTeamCode() { return awayTeamCode; }
    public void setAwayTeamCode(String awayTeamCode) { this.awayTeamCode = awayTeamCode; }

    public Integer getHomeScore() { return homeScore; }
    public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }

    public Integer getAwayScore() { return awayScore; }
    public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /** Convenience: true when the match has a result. */
    public boolean isCompleted() { return "COMPLETED".equals(status); }

    /** Convenience: true when today's date matches kickoff date. */
    public boolean isToday() {
        if (kickoff == null) return false;
        return kickoff.toLocalDate().equals(java.time.LocalDate.now());
    }

    /** Convenience: true when this is a group stage match. */
    public boolean isGroupStage() { return groupLabel != null; }
}
```

- [ ] **Step 2: Create GroupStandingDto.java**

```java
package com.worldcup.prediction.dto;

/**
 * One team's row in a group standings card.
 */
public class GroupStandingDto {

    private String teamName;
    private String teamCode;   // ISO 3166-1 alpha-2 for flag CDN
    private int played;
    private int won;
    private int drawn;
    private int lost;
    private int goalsFor;
    private int goalsAgainst;
    private int goalDifference;
    private int points;
    private int position;      // 1–4 within this group

    /**
     * Qualification status for template highlighting:
     * "QUALIFIED"   → top 2 (green highlight)
     * "THIRD"       → 3rd place (yellow, may qualify as best third)
     * "ELIMINATED"  → 4th place
     * "PENDING"     → group stage not finished yet
     */
    private String qualificationStatus;

    public GroupStandingDto() {}

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getTeamCode() { return teamCode; }
    public void setTeamCode(String teamCode) { this.teamCode = teamCode; }

    public int getPlayed() { return played; }
    public void setPlayed(int played) { this.played = played; }

    public int getWon() { return won; }
    public void setWon(int won) { this.won = won; }

    public int getDrawn() { return drawn; }
    public void setDrawn(int drawn) { this.drawn = drawn; }

    public int getLost() { return lost; }
    public void setLost(int lost) { this.lost = lost; }

    public int getGoalsFor() { return goalsFor; }
    public void setGoalsFor(int goalsFor) { this.goalsFor = goalsFor; }

    public int getGoalsAgainst() { return goalsAgainst; }
    public void setGoalsAgainst(int goalsAgainst) { this.goalsAgainst = goalsAgainst; }

    public int getGoalDifference() { return goalDifference; }
    public void setGoalDifference(int goalDifference) { this.goalDifference = goalDifference; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public String getQualificationStatus() { return qualificationStatus; }
    public void setQualificationStatus(String qualificationStatus) { this.qualificationStatus = qualificationStatus; }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worldcup/prediction/dto/FixtureViewDto.java \
        src/main/java/com/worldcup/prediction/dto/GroupStandingDto.java
git commit -m "feat(part5): add FixtureViewDto and GroupStandingDto for public pages"
```

---

### Task 2: Base Thymeleaf Layout

**Files:**
- Create: `src/main/resources/templates/layout/base.html`

This is the single layout every page extends. It provides: Google Fonts, Tailwind CDN, Alpine.js CDN, HTMX CDN, global CSS (animations, design tokens), navbar, footer. Navbar conditionally shows login button or avatar using Thymeleaf Security extras.

- [ ] **Step 1: Create templates/layout/base.html**

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security"
      th:fragment="layout(pageTitle, content)">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title th:text="${pageTitle} + ' · WC Predict 2026'">WC Predict 2026</title>

  <!-- Google Fonts: Bebas Neue + Inter -->
  <link rel="preconnect" href="https://fonts.googleapis.com" />
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
  <link href="https://fonts.googleapis.com/css2?family=Bebas+Neue&family=Inter:wght@400;500;600;700;800;900&display=swap"
        rel="stylesheet" />

  <!-- Tailwind CSS CDN (Play CDN for development; swap for built CSS in prod) -->
  <script src="https://cdn.tailwindcss.com"></script>
  <script>
    tailwind.config = {
      theme: {
        extend: {
          colors: {
            green:  { DEFAULT: '#00c853', dark: '#006b2a', light: '#e8f5e9' },
            orange: { DEFAULT: '#FF5722' },
            gold:   { DEFAULT: '#FFD600' },
            bg:     { DEFAULT: '#f0fdf5' },
          },
          fontFamily: {
            display: ['"Bebas Neue"', 'sans-serif'],
            body:    ['Inter', 'sans-serif'],
          },
        },
      },
    };
  </script>

  <!-- Alpine.js -->
  <script defer src="https://cdn.jsdelivr.net/npm/alpinejs@3.x.x/dist/cdn.min.js"></script>

  <!-- HTMX -->
  <script src="https://unpkg.com/htmx.org@1.9.12" defer></script>

  <!-- HTMX CSRF support for Spring Security -->
  <meta name="_csrf" th:content="${_csrf.token}" />
  <meta name="_csrf_header" th:content="${_csrf.headerName}" />
  <script>
    document.addEventListener('htmx:configRequest', function(evt) {
      var csrfToken  = document.querySelector('meta[name="_csrf"]').content;
      var csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
      evt.detail.headers[csrfHeader] = csrfToken;
    });
  </script>

  <!-- Global design-system CSS -->
  <style>
    /* ── Fonts ── */
    body { font-family: 'Inter', sans-serif; background: #f0fdf5; color: #1a2e1a; }
    .font-display { font-family: 'Bebas Neue', sans-serif; }

    /* ── Keyframe animations ── */
    @keyframes fadeUp {
      from { opacity: 0; transform: translateY(24px); }
      to   { opacity: 1; transform: translateY(0); }
    }
    @keyframes fadeDown {
      from { opacity: 0; transform: translateY(-20px); }
      to   { opacity: 1; transform: translateY(0); }
    }
    @keyframes shimmer {
      0%   { background-position: -200% center; }
      100% { background-position:  200% center; }
    }
    @keyframes pulse-glow {
      0%, 100% { box-shadow: 0 0 8px 0 rgba(0,200,83,0.4); }
      50%       { box-shadow: 0 0 20px 4px rgba(0,200,83,0.7); }
    }
    @keyframes bounce-rank {
      0%, 100% { transform: translateY(0); }
      50%       { transform: translateY(-4px); }
    }
    @keyframes spin-slow {
      to { transform: rotate(360deg); }
    }
    @keyframes bgZoom {
      from { transform: scale(1.00); }
      to   { transform: scale(1.08); }
    }
    @keyframes particleFly {
      0%   { transform: translateY(100vh) translateX(0) scale(0); opacity: 0; }
      10%  { opacity: 1; }
      90%  { opacity: 0.6; }
      100% { transform: translateY(-100px) translateX(var(--dx, 0px)) scale(1.5); opacity: 0; }
    }
    @keyframes pitchShift {
      from { background-position: 0 0; }
      to   { background-position: 60px 0; }
    }
    @keyframes greenPulse {
      0%, 100% { box-shadow: 0 4px 20px rgba(0,200,83,0.4); }
      50%       { box-shadow: 0 4px 36px rgba(0,200,83,0.7); }
    }

    /* ── Utility classes ── */
    .animate-fade-up   { animation: fadeUp   0.6s ease both; }
    .animate-fade-down { animation: fadeDown 0.6s ease both; }
    .animate-shimmer {
      background: linear-gradient(90deg, transparent 0%, rgba(255,255,255,0.4) 50%, transparent 100%);
      background-size: 200% 100%;
      animation: shimmer 2.5s linear infinite;
    }
    .animate-pulse-glow { animation: pulse-glow 2s ease-in-out infinite; }
    .animate-spin-slow  { animation: spin-slow 10s linear infinite; }
    .animate-green-pulse { animation: greenPulse 2.5s ease-in-out infinite; }

    /* Stagger helpers */
    .stagger-1 { animation-delay: 0.05s; }
    .stagger-2 { animation-delay: 0.10s; }
    .stagger-3 { animation-delay: 0.15s; }
    .stagger-4 { animation-delay: 0.20s; }
    .stagger-5 { animation-delay: 0.25s; }
    .stagger-6 { animation-delay: 0.30s; }

    /* ── Navbar ── */
    .navbar {
      animation: fadeDown 0.7s ease both;
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
    }

    /* ── Flag image ── */
    .flag-circle {
      display: inline-block;
      width: 28px; height: 28px;
      border-radius: 50%;
      object-fit: cover;
      border: 2px solid rgba(255,255,255,0.5);
      vertical-align: middle;
    }
    .flag-circle-sm {
      width: 20px; height: 20px;
      border-radius: 50%;
      object-fit: cover;
      border: 1px solid rgba(0,0,0,0.1);
      vertical-align: middle;
      display: inline-block;
    }

    /* ── Phase badge shimmer ── */
    .phase-badge {
      background: linear-gradient(90deg, #006b2a 0%, #00c853 40%, #FFD600 70%, #006b2a 100%);
      background-size: 200% 100%;
      animation: shimmer 3s linear infinite;
      color: white;
      border-radius: 6px;
      padding: 2px 10px;
      font-family: 'Bebas Neue', sans-serif;
      letter-spacing: 1px;
      font-size: 13px;
    }

    /* ── Scrollbar styling ── */
    ::-webkit-scrollbar { width: 6px; height: 6px; }
    ::-webkit-scrollbar-track { background: #e8f5e9; }
    ::-webkit-scrollbar-thumb { background: #00c853; border-radius: 99px; }
  </style>
</head>
<body class="min-h-screen bg-bg flex flex-col">

  <!-- ── NAVBAR ───────────────────────────────────────────────────────────── -->
  <nav class="navbar fixed top-0 left-0 right-0 z-50 bg-green-dark/95 border-b border-green/20 shadow-lg">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex items-center justify-between h-16">

        <!-- Logo -->
        <a th:href="@{/}" class="flex items-center gap-2.5 no-underline">
          <div class="w-9 h-9 rounded-full bg-gradient-to-br from-green to-green-dark
                      flex items-center justify-center text-lg
                      animate-spin-slow shadow-lg shadow-green/40">⚽</div>
          <span class="font-display text-2xl tracking-widest text-white">
            WC PREDICT&nbsp;<span class="text-gold">2026</span>
          </span>
        </a>

        <!-- Nav links (desktop) -->
        <div class="hidden md:flex items-center gap-1">
          <a th:href="@{/leaderboard}"
             class="px-4 py-2 rounded-lg text-sm font-semibold text-white/70
                    hover:text-white hover:bg-white/10 transition-all duration-200">
            🏆 Leaderboard
          </a>
          <a th:href="@{/fixtures}"
             class="px-4 py-2 rounded-lg text-sm font-semibold text-white/70
                    hover:text-white hover:bg-white/10 transition-all duration-200">
            📅 Fixtures
          </a>
          <a th:href="@{/groups}"
             class="px-4 py-2 rounded-lg text-sm font-semibold text-white/70
                    hover:text-white hover:bg-white/10 transition-all duration-200">
            🗂 Groups
          </a>
          <a th:href="@{/bracket}"
             class="px-4 py-2 rounded-lg text-sm font-semibold text-white/70
                    hover:text-white hover:bg-white/10 transition-all duration-200">
            🎯 Bracket
          </a>
        </div>

        <!-- Auth area -->
        <div class="flex items-center gap-3">
          <!-- Authenticated: show avatar + name + home link -->
          <div sec:authorize="isAuthenticated()" class="flex items-center gap-3">
            <a th:href="@{/home}"
               class="hidden sm:flex items-center gap-2 px-3 py-1.5 rounded-lg
                      text-sm font-semibold text-white/80 hover:text-white hover:bg-white/10
                      transition-all duration-200">
              🏠 Home
            </a>
            <a th:href="@{/predictions}"
               class="hidden sm:flex items-center gap-2 px-3 py-1.5 rounded-lg
                      text-sm font-semibold text-white/80 hover:text-white hover:bg-white/10
                      transition-all duration-200">
              ⚡ Predict
            </a>
            <!-- Avatar -->
            <div class="relative" x-data="{ open: false }">
              <button @click="open = !open"
                      class="flex items-center gap-2 focus:outline-none group">
                <img th:src="${#authentication.principal.avatarUrl}"
                     th:alt="${#authentication.principal.displayName}"
                     class="w-9 h-9 rounded-full border-2 border-green/50
                            group-hover:border-green transition-colors"
                     onerror="this.src='https://ui-avatars.com/api/?name='+encodeURIComponent(this.alt)+'&background=006b2a&color=fff'" />
                <span class="hidden lg:block text-sm font-semibold text-white/80"
                      th:text="${#authentication.principal.displayName}">User</span>
              </button>
              <!-- Dropdown -->
              <div x-show="open" @click.outside="open = false"
                   x-transition:enter="transition ease-out duration-150"
                   x-transition:enter-start="opacity-0 translate-y-1"
                   x-transition:enter-end="opacity-100 translate-y-0"
                   x-transition:leave="transition ease-in duration-100"
                   x-transition:leave-start="opacity-100 translate-y-0"
                   x-transition:leave-end="opacity-0 translate-y-1"
                   class="absolute right-0 top-12 w-48 bg-white rounded-xl shadow-xl
                          border border-gray-100 py-1 z-50" style="display:none">
                <a th:href="@{/home}"
                   class="flex items-center gap-2 px-4 py-2.5 text-sm text-gray-700
                          hover:bg-green-light hover:text-green-dark transition-colors">
                  🏠 Dashboard
                </a>
                <a th:href="@{/predictions}"
                   class="flex items-center gap-2 px-4 py-2.5 text-sm text-gray-700
                          hover:bg-green-light hover:text-green-dark transition-colors">
                  ⚡ My Predictions
                </a>
                <div class="border-t border-gray-100 my-1"></div>
                <form th:action="@{/logout}" method="post">
                  <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}" />
                  <button type="submit"
                          class="w-full flex items-center gap-2 px-4 py-2.5 text-sm text-red-600
                                 hover:bg-red-50 transition-colors text-left">
                    🚪 Sign out
                  </button>
                </form>
              </div>
            </div>
          </div>

          <!-- Unauthenticated: show Sign in button -->
          <div sec:authorize="isAnonymous()">
            <a th:href="@{/}"
               class="flex items-center gap-2 bg-green hover:bg-green/90 text-white
                      font-bold text-sm px-5 py-2 rounded-xl transition-all duration-200
                      animate-green-pulse shadow-lg shadow-green/30">
              Sign in
            </a>
          </div>
        </div>

      </div>
    </div>
  </nav>

  <!-- ── PAGE CONTENT (fragment injection point) ──────────────────────────── -->
  <main class="flex-1 pt-16">
    <th:block th:replace="${content}" />
  </main>

  <!-- ── FOOTER ────────────────────────────────────────────────────────────── -->
  <footer class="bg-green-dark text-white/60 py-8 mt-auto">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex flex-col sm:flex-row items-center justify-between gap-4">
        <!-- Logo -->
        <div class="flex items-center gap-2">
          <span class="text-2xl">⚽</span>
          <span class="font-display text-xl tracking-widest text-white">
            WC PREDICT <span class="text-gold">2026</span>
          </span>
        </div>
        <!-- Links -->
        <div class="flex items-center gap-6 text-sm">
          <a th:href="@{/leaderboard}" class="hover:text-white transition-colors">Leaderboard</a>
          <a th:href="@{/fixtures}"    class="hover:text-white transition-colors">Fixtures</a>
          <a th:href="@{/groups}"      class="hover:text-white transition-colors">Groups</a>
          <a th:href="@{/bracket}"     class="hover:text-white transition-colors">Bracket</a>
        </div>
        <!-- Copyright -->
        <div class="text-xs text-white/40">
          FIFA World Cup 2026 · Internal Prediction Game
        </div>
      </div>
    </div>
  </footer>

</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/layout/base.html
git commit -m "feat(part5): add base Thymeleaf layout with navbar, footer, CDN assets and global CSS animations"
```

---

### Task 3: LandingController

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/LandingController.java`

The landing page is served publicly. Authenticated users who hit `/` are redirected to `/home`.

- [ ] **Step 1: Create LandingController.java**

```java
package com.worldcup.prediction.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the public landing page at GET /.
 * Authenticated users are redirected to /home.
 */
@Controller
public class LandingController {

    @GetMapping("/")
    public String landing(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/home";
        }
        return "index";
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/LandingController.java
git commit -m "feat(part5): add LandingController — redirects authenticated users to /home"
```

---

### Task 4: Landing Page Template

**Files:**
- Create: `src/main/resources/templates/index.html`

Full landing page. Does NOT extend the base layout (it is a standalone full-screen page with its own dark theme and custom navbar). Uses Alpine.js for the live countdown timer and vanilla JS for floating particles.

- [ ] **Step 1: Create templates/index.html**

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>WC Predict 2026 — Predict. Compete. Win.</title>

  <!-- Google Fonts -->
  <link rel="preconnect" href="https://fonts.googleapis.com" />
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
  <link href="https://fonts.googleapis.com/css2?family=Bebas+Neue&family=Inter:wght@400;500;600;700;800;900&display=swap"
        rel="stylesheet" />

  <!-- Alpine.js -->
  <script defer src="https://cdn.jsdelivr.net/npm/alpinejs@3.x.x/dist/cdn.min.js"></script>

  <style>
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

    body {
      font-family: 'Inter', sans-serif;
      min-height: 100vh;
      overflow-x: hidden;
      background: #000;
      color: white;
    }

    /* ── HERO BACKGROUND ─────────────────────────────────────────────────── */
    .hero-bg {
      position: fixed;
      inset: 0;
      z-index: 0;
    }
    .bg-img {
      position: absolute;
      inset: 0;
      background-image: url('https://static0.givemesportimages.com/wordpress/wp-content/uploads/2025/09/epl_15-favourites-to-win-2026-world-cup.jpg?w=1600&h=900&fit=crop');
      background-size: cover;
      background-position: center top;
      animation: bgZoom 20s ease-in-out infinite alternate;
      filter: brightness(0.72) saturate(1.3);
    }
    @keyframes bgZoom {
      from { transform: scale(1.00); }
      to   { transform: scale(1.07); }
    }
    .bg-overlay {
      position: absolute;
      inset: 0;
      background:
        linear-gradient(to bottom,
          rgba(0,20,8,0.45)  0%,
          rgba(0,40,15,0.18) 30%,
          rgba(0,10,5,0.60)  70%,
          rgba(0,5,2,0.93)   100%),
        linear-gradient(to right,
          rgba(0,50,20,0.5)  0%,
          transparent         50%,
          rgba(0,30,80,0.3)  100%);
    }
    .pitch-lines {
      position: absolute;
      bottom: 0; left: 0; right: 0;
      height: 200px;
      background-image:
        repeating-linear-gradient(90deg,
          rgba(255,255,255,0.035) 0px, rgba(255,255,255,0.035) 1px,
          transparent 1px, transparent 60px),
        linear-gradient(to top, rgba(0,180,60,0.10), transparent);
      animation: pitchShift 8s linear infinite;
    }
    @keyframes pitchShift {
      from { background-position: 0 0; }
      to   { background-position: 60px 0; }
    }

    /* ── PARTICLES ───────────────────────────────────────────────────────── */
    .particles { position: absolute; inset: 0; overflow: hidden; pointer-events: none; }
    .particle {
      position: absolute;
      width: 4px; height: 4px;
      border-radius: 50%;
      background: rgba(0,200,83,0.6);
      animation: particleFly linear infinite;
      filter: blur(1px);
    }
    @keyframes particleFly {
      0%   { transform: translateY(100vh) translateX(0) scale(0); opacity: 0; }
      10%  { opacity: 1; }
      90%  { opacity: 0.6; }
      100% { transform: translateY(-100px) translateX(var(--dx, 0px)) scale(1.5); opacity: 0; }
    }

    /* ── NAVBAR ─────────────────────────────────────────────────────────── */
    .nav {
      position: fixed; top: 0; left: 0; right: 0; z-index: 100;
      padding: 18px 48px;
      display: flex; align-items: center; justify-content: space-between;
      animation: fadeDown 0.8s ease both;
    }
    @keyframes fadeDown {
      from { opacity: 0; transform: translateY(-20px); }
      to   { opacity: 1; transform: translateY(0); }
    }
    .logo {
      display: flex; align-items: center; gap: 10px;
      font-family: 'Bebas Neue', sans-serif;
      font-size: 24px; letter-spacing: 3px; color: white;
      text-decoration: none;
    }
    .logo-ball {
      width: 36px; height: 36px; border-radius: 50%;
      background: linear-gradient(135deg, #00e564, #007a32);
      display: flex; align-items: center; justify-content: center; font-size: 20px;
      animation: spinSlow 10s linear infinite;
      box-shadow: 0 0 16px rgba(0,229,100,0.5);
    }
    @keyframes spinSlow { to { transform: rotate(360deg); } }
    .logo em { color: #FFD600; font-style: normal; }

    .nav-links { display: flex; gap: 4px; }
    .nav-a {
      color: rgba(255,255,255,0.7); text-decoration: none;
      padding: 8px 16px; border-radius: 8px;
      font-size: 13px; font-weight: 600;
      transition: all 0.2s;
    }
    .nav-a:hover { background: rgba(255,255,255,0.1); color: white; }

    .btn-signin {
      background: rgba(255,255,255,0.12);
      backdrop-filter: blur(10px);
      color: white;
      border: 1px solid rgba(255,255,255,0.25);
      border-radius: 10px; padding: 10px 22px;
      font-size: 13px; font-weight: 700;
      cursor: pointer; transition: all 0.2s;
      text-decoration: none; display: inline-block;
    }
    .btn-signin:hover { background: rgba(255,255,255,0.22); }

    /* ── HERO CONTENT ────────────────────────────────────────────────────── */
    .hero {
      position: relative; z-index: 10;
      min-height: 100vh;
      display: flex; flex-direction: column;
      align-items: center; justify-content: center;
      padding: 90px 24px 140px;
      text-align: center;
    }

    /* WC 2026 badge */
    .wc-badge {
      display: inline-flex; align-items: center; gap: 8px;
      background: rgba(255,255,255,0.1); backdrop-filter: blur(12px);
      border: 1px solid rgba(255,255,255,0.2);
      border-radius: 99px; padding: 8px 18px;
      font-size: 11px; font-weight: 700; letter-spacing: 2px; text-transform: uppercase;
      color: white; margin-bottom: 28px;
      animation: fadeUp 0.7s ease 0.3s both;
    }
    .badge-dot {
      width: 8px; height: 8px; border-radius: 50%; background: #FFD600;
      animation: pulseDot 1.5s ease infinite;
    }
    @keyframes pulseDot { 0%,100%{transform:scale(1)} 50%{transform:scale(1.4)} }

    /* Main title */
    .hero-title {
      font-family: 'Bebas Neue', sans-serif;
      font-size: clamp(62px, 11vw, 128px);
      line-height: 0.92;
      letter-spacing: 6px;
      color: white;
      text-shadow: 0 4px 40px rgba(0,0,0,0.5);
      animation: fadeUp 0.8s ease 0.4s both;
      margin-bottom: 10px;
    }
    .title-line1 { display: block; }
    .title-line2 { display: block; color: #00e564; }
    .title-line3 {
      display: block; color: #FFD600;
      -webkit-text-stroke: 2px #FFD600;
      -webkit-text-fill-color: transparent;
    }

    .hero-sub {
      font-size: 17px; font-weight: 500;
      color: rgba(255,255,255,0.72);
      margin-top: 20px; margin-bottom: 44px;
      max-width: 500px; line-height: 1.65;
      animation: fadeUp 0.8s ease 0.55s both;
    }
    .hero-sub strong { color: white; }

    /* ── COUNTDOWN (Alpine.js) ───────────────────────────────────────────── */
    .countdown-wrap {
      margin-bottom: 48px;
      animation: fadeUp 0.8s ease 0.65s both;
    }
    .countdown-label {
      font-size: 10px; font-weight: 700; letter-spacing: 3px; text-transform: uppercase;
      color: rgba(255,255,255,0.45); margin-bottom: 16px;
    }
    .countdown {
      display: flex; gap: 10px; justify-content: center; align-items: center;
      flex-wrap: wrap;
    }
    .cd-unit {
      background: rgba(255,255,255,0.09); backdrop-filter: blur(16px);
      border: 1px solid rgba(255,255,255,0.14);
      border-radius: 16px; padding: 18px 22px;
      min-width: 88px; text-align: center;
    }
    .cd-num {
      font-family: 'Bebas Neue', sans-serif;
      font-size: 50px; line-height: 1; letter-spacing: 2px;
      display: block; font-variant-numeric: tabular-nums;
    }
    .cd-days  .cd-num { color: #FFD600; text-shadow: 0 0 20px rgba(255,214,0,0.5); }
    .cd-hours .cd-num { color: #00e564; text-shadow: 0 0 20px rgba(0,229,100,0.5); }
    .cd-mins  .cd-num { color: #FF5722; text-shadow: 0 0 20px rgba(255,87,34,0.5); }
    .cd-secs  .cd-num { color: white; }
    .cd-lbl {
      font-size: 9px; font-weight: 700; letter-spacing: 2px; text-transform: uppercase;
      color: rgba(255,255,255,0.45); margin-top: 4px;
    }
    .cd-sep {
      font-size: 34px; font-weight: 900; color: rgba(255,255,255,0.25);
      line-height: 1; padding-bottom: 18px;
      align-self: center;
    }

    /* ── OAUTH BUTTONS ───────────────────────────────────────────────────── */
    .hero-btns {
      display: flex; gap: 14px; justify-content: center; flex-wrap: wrap;
      margin-bottom: 20px;
      animation: fadeUp 0.8s ease 0.8s both;
    }
    .btn-oauth {
      display: flex; align-items: center; gap: 10px;
      border-radius: 14px; padding: 16px 32px;
      font-size: 15px; font-weight: 800;
      cursor: pointer; transition: all 0.2s;
      text-decoration: none; border: none;
    }
    .btn-google {
      background: white; color: #1a1a1a;
      box-shadow: 0 4px 24px rgba(0,0,0,0.25);
    }
    .btn-google:hover { transform: scale(1.03); box-shadow: 0 6px 32px rgba(0,0,0,0.35); }
    .btn-linkedin {
      background: #0a66c2; color: white;
      box-shadow: 0 4px 24px rgba(10,102,194,0.35);
    }
    .btn-linkedin:hover { transform: scale(1.03); background: #0958ad; }

    .btn-leaderboard {
      background: rgba(255,255,255,0.1); backdrop-filter: blur(10px);
      color: white; border: 1px solid rgba(255,255,255,0.25);
      border-radius: 14px; padding: 16px 32px;
      font-size: 15px; font-weight: 700;
      cursor: pointer; transition: all 0.2s;
      text-decoration: none; display: inline-flex; align-items: center; gap: 8px;
    }
    .btn-leaderboard:hover { background: rgba(255,255,255,0.18); }

    /* ── FEATURE CHIPS ───────────────────────────────────────────────────── */
    .features {
      position: fixed; bottom: 28px; left: 0; right: 0;
      display: flex; justify-content: center; gap: 10px; flex-wrap: wrap;
      z-index: 10;
      animation: fadeUp 1s ease 1.0s both;
      padding: 0 16px;
    }
    .feat-chip {
      display: flex; align-items: center; gap: 8px;
      background: rgba(0,0,0,0.42); backdrop-filter: blur(16px);
      border: 1px solid rgba(255,255,255,0.11);
      border-radius: 99px; padding: 9px 16px;
      font-size: 12px; font-weight: 600; color: rgba(255,255,255,0.78);
      transition: all 0.2s; white-space: nowrap;
    }
    .feat-chip:hover { background: rgba(0,200,83,0.15); border-color: rgba(0,200,83,0.3); color: white; }

    @keyframes fadeUp {
      from { opacity: 0; transform: translateY(28px); }
      to   { opacity: 1; transform: translateY(0); }
    }

    /* ── RESPONSIVE ──────────────────────────────────────────────────────── */
    @media (max-width: 640px) {
      .nav { padding: 16px 20px; }
      .nav-links { display: none; }
      .hero { padding: 80px 16px 160px; }
      .cd-unit { min-width: 72px; padding: 14px 16px; }
      .cd-num  { font-size: 40px; }
      .features { bottom: 16px; gap: 6px; }
    }
  </style>
</head>
<body>

<!-- BACKGROUND -->
<div class="hero-bg">
  <div class="bg-img"></div>
  <div class="bg-overlay"></div>
  <div class="pitch-lines"></div>
  <div class="particles" id="particles"></div>
</div>

<!-- NAVBAR -->
<nav class="nav">
  <a th:href="@{/}" class="logo">
    <div class="logo-ball">⚽</div>
    WC PREDICT&nbsp;<em>2026</em>
  </a>
  <div class="nav-links">
    <a th:href="@{/leaderboard}" class="nav-a">🏆 Leaderboard</a>
    <a th:href="@{/fixtures}"    class="nav-a">📅 Fixtures</a>
    <a th:href="@{/groups}"      class="nav-a">🗂 Groups</a>
  </div>
  <a th:href="@{/leaderboard}" class="btn-signin">View standings ↗</a>
</nav>

<!-- HERO -->
<div class="hero">

  <!-- Badge -->
  <div class="wc-badge">
    <div class="badge-dot"></div>
    FIFA World Cup 2026 · USA · Canada · Mexico
  </div>

  <!-- Title -->
  <h1 class="hero-title">
    <span class="title-line1">PREDICT.</span>
    <span class="title-line2">COMPETE.</span>
    <span class="title-line3">WIN.</span>
  </h1>

  <!-- Subheading -->
  <p class="hero-sub">
    The official internal prediction game for <strong>FIFA World Cup 2026</strong>.
    Predict every match, climb the leaderboard, and become the office champion.
  </p>

  <!-- Live Countdown (Alpine.js) -->
  <div class="countdown-wrap"
       x-data="{
         days: '00', hours: '00', mins: '00', secs: '00',
         started: false,
         init() {
           this.tick();
           setInterval(() => this.tick(), 1000);
         },
         tick() {
           const target = new Date('2026-06-11T18:00:00Z');
           const now    = new Date();
           const diff   = Math.max(0, target - now);
           if (diff === 0) { this.started = true; return; }
           this.days  = String(Math.floor(diff / 86400000)).padStart(2, '0');
           this.hours = String(Math.floor((diff % 86400000) / 3600000)).padStart(2, '0');
           this.mins  = String(Math.floor((diff % 3600000)  / 60000)).padStart(2, '0');
           this.secs  = String(Math.floor((diff % 60000)    / 1000)).padStart(2, '0');
         }
       }">

    <!-- Countdown live -->
    <template x-if="!started">
      <div>
        <div class="countdown-label">⚽ Tournament kicks off in</div>
        <div class="countdown">
          <div class="cd-unit cd-days">
            <span class="cd-num" x-text="days">00</span>
            <div class="cd-lbl">Days</div>
          </div>
          <div class="cd-sep">:</div>
          <div class="cd-unit cd-hours">
            <span class="cd-num" x-text="hours">00</span>
            <div class="cd-lbl">Hours</div>
          </div>
          <div class="cd-sep">:</div>
          <div class="cd-unit cd-mins">
            <span class="cd-num" x-text="mins">00</span>
            <div class="cd-lbl">Minutes</div>
          </div>
          <div class="cd-sep">:</div>
          <div class="cd-unit cd-secs">
            <span class="cd-num" x-text="secs">00</span>
            <div class="cd-lbl">Seconds</div>
          </div>
        </div>
      </div>
    </template>

    <!-- Tournament has started -->
    <template x-if="started">
      <div class="countdown-label" style="font-size:16px; color: #00e564; letter-spacing:1px;">
        ⚽ The tournament is LIVE!
      </div>
    </template>

  </div>

  <!-- OAuth2 Sign-in buttons -->
  <div class="hero-btns">
    <a th:href="@{/oauth2/authorization/google}" class="btn-oauth btn-google">
      <!-- Google SVG icon -->
      <svg width="20" height="20" viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">
        <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
        <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
        <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
        <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.18 1.48-4.97 2.35-8.16 2.35-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
        <path fill="none" d="M0 0h48v48H0z"/>
      </svg>
      Sign in with Google
    </a>

    <a th:href="@{/oauth2/authorization/linkedin}" class="btn-oauth btn-linkedin">
      <!-- LinkedIn SVG icon -->
      <svg width="20" height="20" viewBox="0 0 24 24" fill="white" xmlns="http://www.w3.org/2000/svg">
        <path d="M20.447 20.452H17.12v-5.569c0-1.327-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.812V9h3.2v1.561h.046c.446-.844 1.536-1.733 3.162-1.733 3.38 0 4.003 2.225 4.003 5.118v6.506zM5.337 7.433a1.862 1.862 0 01-1.857-1.857 1.857 1.857 0 111.857 1.857zm1.603 13.019H3.73V9h3.21v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z"/>
      </svg>
      Sign in with LinkedIn
    </a>
  </div>

  <!-- View Leaderboard link -->
  <a th:href="@{/leaderboard}" class="btn-leaderboard">
    🏆 View Leaderboard
  </a>

</div>

<!-- FEATURE CHIPS -->
<div class="features">
  <div class="feat-chip">🎯 48 teams</div>
  <div class="feat-chip">⚽ 104 matches</div>
  <div class="feat-chip">🏆 Live leaderboard</div>
  <div class="feat-chip">🗂 Full bracket</div>
  <div class="feat-chip">👥 Office champions</div>
</div>

<!-- Particle JS -->
<script>
(function() {
  const container = document.getElementById('particles');
  if (!container) return;
  const symbols = ['⚽', '★', '●', '◆'];
  for (let i = 0; i < 24; i++) {
    const el = document.createElement('div');
    const isSymbol = Math.random() > 0.55;
    const dur  = (9 + Math.random() * 13).toFixed(1);
    const del  = -(Math.random() * 15).toFixed(1);
    const dx   = ((Math.random() - 0.5) * 130).toFixed(0);
    const left = (Math.random() * 100).toFixed(1);
    if (isSymbol) {
      const sz = (10 + Math.random() * 14).toFixed(0);
      const op = (0.08 + Math.random() * 0.18).toFixed(2);
      el.style.cssText = `
        position:absolute; left:${left}%; font-size:${sz}px;
        width:auto; height:auto; background:transparent; filter:none;
        color:rgba(0,220,90,${op});
        animation: particleFly ${dur}s linear ${del}s infinite;
        --dx: ${dx}px;
      `;
      el.textContent = symbols[Math.floor(Math.random() * symbols.length)];
    } else {
      const sz  = (3 + Math.random() * 4).toFixed(1);
      const col = Math.random() > 0.5 ? '0,200,83' : '255,214,0';
      const op  = (0.25 + Math.random() * 0.4).toFixed(2);
      el.className = 'particle';
      el.style.cssText = `
        left:${left}%; width:${sz}px; height:${sz}px;
        animation: particleFly ${dur}s linear ${del}s infinite;
        --dx: ${dx}px;
        background: rgba(${col},${op});
      `;
    }
    container.appendChild(el);
  }
})();
</script>

</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/index.html
git commit -m "feat(part5): add landing page with Ken Burns hero, Alpine.js countdown, OAuth2 buttons and floating particles"
```

---

### Task 5: FixtureController

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/FixtureController.java`

Handles `/fixtures` (full page) and `/fixtures/filter` (HTMX partial for filter tabs).

- [ ] **Step 1: Create FixtureController.java**

```java
package com.worldcup.prediction.controller;

import com.worldcup.prediction.dto.FixtureViewDto;
import com.worldcup.prediction.service.MatchService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Public fixtures page — no authentication required.
 */
@Controller
public class FixtureController {

    private final MatchService matchService;

    public FixtureController(MatchService matchService) {
        this.matchService = matchService;
    }

    /**
     * GET /fixtures — full page, default to "all" filter.
     */
    @GetMapping("/fixtures")
    public String fixtures(
            @RequestParam(name = "filter", defaultValue = "all") String filter,
            Model model) {

        List<FixtureViewDto> fixtures = loadFixtures(filter);

        // Group by phase label then by date for template rendering
        Map<String, Map<LocalDate, List<FixtureViewDto>>> grouped = groupByPhaseAndDate(fixtures);

        model.addAttribute("fixtures", fixtures);
        model.addAttribute("grouped", grouped);
        model.addAttribute("activeFilter", filter);
        model.addAttribute("pageTitle", "Fixtures");

        return "fixtures";
    }

    /**
     * GET /fixtures/filter — HTMX partial, returns only the fixture-rows fragment.
     */
    @GetMapping("/fixtures/filter")
    public String fixturesFilter(
            @RequestParam(name = "filter", defaultValue = "all") String filter,
            Model model) {

        List<FixtureViewDto> fixtures = loadFixtures(filter);
        Map<String, Map<LocalDate, List<FixtureViewDto>>> grouped = groupByPhaseAndDate(fixtures);

        model.addAttribute("grouped", grouped);
        model.addAttribute("activeFilter", filter);

        // Return only the fragment (HTMX swap target)
        return "fragments/fixture-rows :: fixture-rows";
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<FixtureViewDto> loadFixtures(String filter) {
        return switch (filter) {
            case "group"    -> matchService.getGroupStageFixtures();
            case "knockout" -> matchService.getKnockoutFixtures();
            case "today"    -> matchService.getTodayFixtures();
            default         -> matchService.getAllFixtures();
        };
    }

    private Map<String, Map<LocalDate, List<FixtureViewDto>>> groupByPhaseAndDate(
            List<FixtureViewDto> fixtures) {

        // LinkedHashMap preserves insertion order (chronological phases)
        Map<String, Map<LocalDate, List<FixtureViewDto>>> result = new LinkedHashMap<>();

        // Group by phase first, then by date within each phase
        Map<String, List<FixtureViewDto>> byPhase = fixtures.stream()
                .collect(Collectors.groupingBy(
                        FixtureViewDto::getPhase,
                        LinkedHashMap::new,
                        Collectors.toList()));

        byPhase.forEach((phase, phaseFixtures) -> {
            Map<LocalDate, List<FixtureViewDto>> byDate = phaseFixtures.stream()
                    .collect(Collectors.groupingBy(
                            f -> f.getKickoff().toLocalDate(),
                            LinkedHashMap::new,
                            Collectors.toList()));
            result.put(phase, byDate);
        });

        return result;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/FixtureController.java
git commit -m "feat(part5): add FixtureController with full-page and HTMX partial endpoints"
```

---

### Task 6: Fixtures Templates

**Files:**
- Create: `src/main/resources/templates/fixtures.html`
- Create: `src/main/resources/templates/fragments/fixture-rows.html`

- [ ] **Step 1: Create templates/fixtures.html**

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/base :: layout(~{::title/text()}, ~{::main-content})}">
<head>
  <title>Fixtures</title>
</head>
<body>
<th:block th:fragment="main-content">

  <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-10">

    <!-- Page header -->
    <div class="mb-8 animate-fade-up">
      <h1 class="font-display text-5xl text-green-dark tracking-wider mb-1">FIXTURES</h1>
      <p class="text-gray-500 text-sm font-medium">All 104 matches · FIFA World Cup 2026</p>
    </div>

    <!-- Filter tabs -->
    <div class="flex gap-2 flex-wrap mb-8 animate-fade-up stagger-1">
      <button
        th:each="tab : ${ {'all','group','knockout','today'} }"
        th:text="${tab == 'all' ? 'All' : tab == 'group' ? 'Group Stage' : tab == 'knockout' ? 'Knockouts' : 'Today'}"
        th:classappend="${activeFilter == tab} ? 'bg-green-dark text-white shadow-md' : 'bg-white text-gray-600 hover:bg-green-light hover:text-green-dark border border-gray-200'"
        class="px-5 py-2 rounded-xl text-sm font-semibold transition-all duration-200 cursor-pointer"
        hx-get="/fixtures/filter"
        th:hx-vals="|{&quot;filter&quot;: &quot;${tab}&quot;}|"
        hx-target="#fixture-list"
        hx-swap="innerHTML"
        hx-push-url="false">
      </button>
    </div>

    <!-- Fixture rows (HTMX swap target) -->
    <div id="fixture-list">
      <div th:replace="~{fragments/fixture-rows :: fixture-rows}"></div>
    </div>

  </div>

</th:block>
</body>
</html>
```

- [ ] **Step 2: Create templates/fragments/fixture-rows.html**

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org">
<body>

<th:block th:fragment="fixture-rows">

  <!-- Empty state -->
  <div th:if="${grouped == null or grouped.isEmpty()}"
       class="text-center py-20 text-gray-400">
    <div class="text-5xl mb-4">📅</div>
    <p class="text-lg font-semibold">No fixtures found</p>
    <p class="text-sm mt-1">Try selecting a different filter.</p>
  </div>

  <!-- Phase → Date grouping -->
  <div th:each="phaseEntry, phaseStat : ${grouped}" class="mb-10">

    <!-- Phase header with shimmer badge -->
    <div class="flex items-center gap-3 mb-4"
         th:classappend="${phaseStat.index == 0} ? 'animate-fade-up' : ''">
      <span class="phase-badge" th:text="${phaseEntry.key}">Group Stage</span>
      <div class="flex-1 h-px bg-green/20"></div>
    </div>

    <!-- Date groups within this phase -->
    <div th:each="dateEntry : ${phaseEntry.value}" class="mb-6">

      <!-- Date sub-header -->
      <div class="text-xs font-bold text-gray-400 uppercase tracking-widest mb-3 pl-1"
           th:text="${#temporals.format(dateEntry.key, 'EEEE, d MMMM yyyy')}">
        Monday, 15 June 2026
      </div>

      <!-- Fixture rows for this date -->
      <div class="space-y-2">
        <div th:each="fixture, fStat : ${dateEntry.value}"
             th:classappend="'animate-fade-up stagger-' + ${fStat.index < 6 ? fStat.index + 1 : 6}"
             class="bg-white rounded-2xl border border-gray-100 shadow-sm
                    hover:shadow-md hover:border-green/30 transition-all duration-200
                    overflow-hidden group">

          <div class="flex items-center px-4 py-3 gap-3">

            <!-- Date/time + venue (left) -->
            <div class="w-28 flex-shrink-0 text-right pr-3 border-r border-gray-100">
              <div class="text-xs font-bold text-gray-700"
                   th:text="${#temporals.format(fixture.kickoff, 'HH:mm')} + ' UTC'">
                20:00 UTC
              </div>
              <div class="text-xs text-gray-400 mt-0.5 leading-tight"
                   th:text="${fixture.city}">Dallas</div>
            </div>

            <!-- Home team -->
            <div class="flex items-center gap-2 flex-1 justify-end">
              <span class="text-sm font-semibold text-gray-800 text-right"
                    th:text="${fixture.homeTeamName}">Brazil</span>
              <img th:if="${fixture.homeTeamCode != null}"
                   th:src="@{'https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/' + ${fixture.homeTeamCode} + '.svg'}"
                   th:alt="${fixture.homeTeamName}"
                   class="flag-circle w-7 h-7"
                   th:onerror="'this.src=\'https://flagcdn.com/w40/' + ${fixture.homeTeamCode} + '.png\''" />
              <span th:unless="${fixture.homeTeamCode != null}"
                    class="w-7 h-7 rounded-full bg-gray-100 border border-gray-200
                           flex items-center justify-center text-xs text-gray-400">?</span>
            </div>

            <!-- Score or VS -->
            <div class="w-20 flex-shrink-0 text-center">
              <!-- Completed: show score -->
              <div th:if="${fixture.completed}"
                   class="font-display text-xl tracking-widest text-green-dark">
                <span th:text="${fixture.homeScore}">2</span>
                <span class="text-gray-300 mx-0.5">–</span>
                <span th:text="${fixture.awayScore}">1</span>
              </div>
              <!-- Scheduled: show VS -->
              <div th:unless="${fixture.completed}">
                <span class="font-display text-base tracking-widest text-gray-300">VS</span>
              </div>
            </div>

            <!-- Away team -->
            <div class="flex items-center gap-2 flex-1 justify-start">
              <img th:if="${fixture.awayTeamCode != null}"
                   th:src="@{'https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/' + ${fixture.awayTeamCode} + '.svg'}"
                   th:alt="${fixture.awayTeamName}"
                   class="flag-circle w-7 h-7"
                   th:onerror="'this.src=\'https://flagcdn.com/w40/' + ${fixture.awayTeamCode} + '.png\''" />
              <span th:unless="${fixture.awayTeamCode != null}"
                    class="w-7 h-7 rounded-full bg-gray-100 border border-gray-200
                           flex items-center justify-center text-xs text-gray-400">?</span>
              <span class="text-sm font-semibold text-gray-800"
                    th:text="${fixture.awayTeamName}">Germany</span>
            </div>

            <!-- Status pill (right) -->
            <div class="w-20 flex-shrink-0 flex justify-end">
              <!-- Completed -->
              <span th:if="${fixture.completed}"
                    class="text-xs font-bold px-2.5 py-1 rounded-full bg-green/10 text-green-dark">
                FT
              </span>
              <!-- Today/upcoming -->
              <span th:if="${!fixture.completed and fixture.today}"
                    class="text-xs font-bold px-2.5 py-1 rounded-full bg-orange/10 text-orange
                           animate-pulse">
                TODAY
              </span>
              <!-- Future -->
              <span th:if="${!fixture.completed and !fixture.today}"
                    class="text-xs font-medium px-2.5 py-1 rounded-full bg-gray-100 text-gray-400">
                TBD
              </span>
            </div>

          </div>

          <!-- Group label sub-line (only for group stage) -->
          <div th:if="${fixture.groupLabel != null}"
               class="bg-gray-50 border-t border-gray-50 px-4 py-1.5 flex items-center gap-2">
            <span class="text-xs text-gray-400 font-medium"
                  th:text="${fixture.groupLabel}">Group A</span>
            <span class="text-gray-200">·</span>
            <span class="text-xs text-gray-400"
                  th:text="${fixture.venue}">SoFi Stadium, Los Angeles</span>
          </div>

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
git add src/main/resources/templates/fixtures.html \
        src/main/resources/templates/fragments/fixture-rows.html
git commit -m "feat(part5): add fixtures page and HTMX fixture-rows fragment with filter tabs"
```

---

### Task 7: GroupController

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/GroupController.java`

- [ ] **Step 1: Create GroupController.java**

```java
package com.worldcup.prediction.controller;

import com.worldcup.prediction.dto.GroupStandingDto;
import com.worldcup.prediction.service.GroupService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public groups standings page — no authentication required.
 */
@Controller
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    /**
     * GET /groups — displays all 12 group standings cards.
     */
    @GetMapping("/groups")
    public String groups(Model model) {
        // Returns a map of "Group A" -> List<GroupStandingDto> sorted by position (1–4)
        // The map is ordered A through L.
        Map<String, List<GroupStandingDto>> groups = groupService.getAllGroupStandings();

        // Determine which third-place teams have qualified (best 8 of 12 thirds).
        // Service returns the list of group labels whose 3rd place team has qualified.
        List<String> qualifiedThirdGroups = groupService.getQualifiedThirdPlaceGroups();

        model.addAttribute("groups", groups);
        model.addAttribute("qualifiedThirdGroups", qualifiedThirdGroups);
        model.addAttribute("pageTitle", "Groups");

        return "groups";
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/GroupController.java
git commit -m "feat(part5): add GroupController serving all 12 group standings"
```

---

### Task 8: Groups Page Template

**Files:**
- Create: `src/main/resources/templates/groups.html`

- [ ] **Step 1: Create templates/groups.html**

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/base :: layout(~{::title/text()}, ~{::main-content})}">
<head>
  <title>Groups</title>
</head>
<body>
<th:block th:fragment="main-content">

  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">

    <!-- Page header -->
    <div class="mb-8 animate-fade-up">
      <h1 class="font-display text-5xl text-green-dark tracking-wider mb-1">GROUP STANDINGS</h1>
      <p class="text-gray-500 text-sm font-medium">
        12 groups · 48 teams · Top 2 advance automatically
      </p>
    </div>

    <!-- Legend -->
    <div class="flex flex-wrap gap-4 mb-8 animate-fade-up stagger-1">
      <div class="flex items-center gap-2 text-xs font-semibold text-gray-600">
        <div class="w-3 h-3 rounded-full bg-green/70"></div>
        Qualified (R32)
      </div>
      <div class="flex items-center gap-2 text-xs font-semibold text-gray-600">
        <div class="w-3 h-3 rounded-full bg-gold/70"></div>
        Best 3rd place (may qualify)
      </div>
      <div class="flex items-center gap-2 text-xs font-semibold text-gray-600">
        <div class="w-3 h-3 rounded-full bg-gray-300"></div>
        Eliminated
      </div>
    </div>

    <!-- Group cards grid — 3 cols on large, 2 on medium, 1 on mobile -->
    <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">

      <div th:each="groupEntry, gStat : ${groups}"
           th:classappend="'animate-fade-up stagger-' + ${gStat.index < 6 ? gStat.index + 1 : 6}"
           class="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden
                  hover:shadow-lg hover:border-green/20 transition-all duration-300">

        <!-- Group header -->
        <div class="bg-gradient-to-r from-green-dark to-green px-5 py-3 flex items-center justify-between">
          <span class="font-display text-2xl text-white tracking-widest"
                th:text="${groupEntry.key}">Group A</span>
          <span class="text-xs font-semibold text-white/60">P W D L GD Pts</span>
        </div>

        <!-- Standings table -->
        <div class="divide-y divide-gray-50">

          <div th:each="team, tStat : ${groupEntry.value}"
               class="flex items-center gap-3 px-4 py-3 transition-colors duration-150"
               th:classappend="${team.qualificationStatus == 'QUALIFIED'} ? 'bg-green/5 border-l-4 border-l-green/60' :
                               (${team.qualificationStatus == 'THIRD' and qualifiedThirdGroups.contains(groupEntry.key)}) ? 'bg-gold/5 border-l-4 border-l-gold/50' :
                               'border-l-4 border-l-transparent'">

            <!-- Position number -->
            <span class="w-5 text-center text-xs font-bold flex-shrink-0"
                  th:text="${tStat.count}"
                  th:classappend="${tStat.count == 1} ? 'text-green-dark' :
                                  ${tStat.count == 2} ? 'text-green' :
                                  'text-gray-400'">1</span>

            <!-- Flag -->
            <img th:if="${team.teamCode != null}"
                 th:src="@{'https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/' + ${team.teamCode} + '.svg'}"
                 th:alt="${team.teamName}"
                 class="w-7 h-7 rounded-full border border-gray-100 flex-shrink-0 object-cover"
                 th:onerror="'this.src=\'https://flagcdn.com/w40/' + ${team.teamCode} + '.png\''" />
            <div th:unless="${team.teamCode != null}"
                 class="w-7 h-7 rounded-full bg-gray-100 flex-shrink-0
                        flex items-center justify-center text-xs text-gray-400">?</div>

            <!-- Team name -->
            <span class="flex-1 text-sm font-semibold text-gray-800 truncate"
                  th:text="${team.teamName}">Brazil</span>

            <!-- Stats: P W D L GD Pts -->
            <div class="flex items-center gap-3 text-xs flex-shrink-0">
              <span class="w-4 text-center text-gray-500" th:text="${team.played}">3</span>
              <span class="w-4 text-center text-gray-600 font-medium" th:text="${team.won}">2</span>
              <span class="w-4 text-center text-gray-500" th:text="${team.drawn}">1</span>
              <span class="w-4 text-center text-gray-500" th:text="${team.lost}">0</span>
              <span class="w-6 text-center font-medium"
                    th:text="${team.goalDifference > 0 ? '+' + team.goalDifference : team.goalDifference}"
                    th:classappend="${team.goalDifference > 0} ? 'text-green-dark' :
                                    ${team.goalDifference < 0} ? 'text-red-500' : 'text-gray-400'">+4</span>
              <span class="w-6 text-center font-bold text-green-dark"
                    th:text="${team.points}">7</span>
            </div>

            <!-- Qualification icon -->
            <div class="w-4 flex-shrink-0 text-center">
              <!-- Qualified (top 2) -->
              <span th:if="${team.qualificationStatus == 'QUALIFIED'}"
                    title="Qualified for Round of 32"
                    class="text-green text-sm">✓</span>
              <!-- Best 3rd (may qualify) -->
              <span th:if="${team.qualificationStatus == 'THIRD' and qualifiedThirdGroups.contains(groupEntry.key)}"
                    title="Qualified as best third-place team"
                    class="text-gold text-sm">✓</span>
            </div>

          </div>
        </div>

        <!-- Group footer: GF/GA summary -->
        <div class="bg-gray-50 border-t border-gray-100 px-4 py-2">
          <div class="flex justify-between text-xs text-gray-400 font-medium">
            <span>Goals: <span th:text="${groupEntry.value.stream().mapToInt(t -> t.goalsFor).sum() + ' scored / ' + groupEntry.value.stream().mapToInt(t -> t.goalsAgainst).sum() + ' conceded'}">— / —</span></span>
            <span class="text-gray-300">·</span>
            <span>Matches played: <span th:text="${groupEntry.value.stream().mapToInt(t -> t.played).sum() / 2}">0</span>/6</span>
          </div>
        </div>

      </div>
    </div>

  </div>

</th:block>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/groups.html
git commit -m "feat(part5): add groups page with 12 group standing cards and qualification highlighting"
```

---

### Task 9: HomeController

**Files:**
- Create: `src/main/java/com/worldcup/prediction/controller/HomeController.java`

The home page is only for authenticated + approved users. The controller loads the mini-leaderboard fragment data (from Part 4), the next match to predict, and the user's own stats.

- [ ] **Step 1: Create HomeController.java**

```java
package com.worldcup.prediction.controller;

import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.dto.FixtureViewDto;
import com.worldcup.prediction.security.AppUserPrincipal;
import com.worldcup.prediction.service.LeaderboardService;
import com.worldcup.prediction.service.MatchService;
import com.worldcup.prediction.service.UserStatsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Authenticated home page at GET /home.
 * Requires ROLE_PARTICIPANT or ROLE_ADMIN.
 * Unauthenticated requests are handled by Spring Security redirect to /.
 */
@Controller
@PreAuthorize("isAuthenticated()")
public class HomeController {

    private final LeaderboardService leaderboardService;
    private final MatchService       matchService;
    private final UserStatsService   userStatsService;

    public HomeController(LeaderboardService leaderboardService,
                          MatchService matchService,
                          UserStatsService userStatsService) {
        this.leaderboardService = leaderboardService;
        this.matchService       = matchService;
        this.userStatsService   = userStatsService;
    }

    /**
     * GET /home — loads dashboard data for the authenticated user.
     */
    @GetMapping("/home")
    public String home(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {

        Long userId = principal.getUserId();

        // Mini leaderboard: top 10 participants
        List<LeaderboardEntryDto> topTen = leaderboardService.getTopN(10);

        // Next open match the user can predict (null if none open)
        FixtureViewDto nextMatch = matchService.getNextPredictableMatch(userId);

        // User's own stats
        int userRank        = leaderboardService.getUserRank(userId);
        int userPoints      = userStatsService.getTotalPoints(userId);
        int userExactCount  = userStatsService.getExactScoreCount(userId);
        int totalPredicted  = userStatsService.getTotalPredicted(userId);
        int openMatchCount  = matchService.getOpenMatchCount();

        model.addAttribute("topTen",        topTen);
        model.addAttribute("nextMatch",     nextMatch);
        model.addAttribute("userRank",      userRank);
        model.addAttribute("userPoints",    userPoints);
        model.addAttribute("userExact",     userExactCount);
        model.addAttribute("totalPredicted", totalPredicted);
        model.addAttribute("openMatchCount", openMatchCount);
        model.addAttribute("pageTitle",     "Home");

        return "home";
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/HomeController.java
git commit -m "feat(part5): add HomeController with leaderboard, next-match and user stats"
```

---

### Task 10: Home Page Template

**Files:**
- Create: `src/main/resources/templates/home.html`

- [ ] **Step 1: Create templates/home.html**

```html
<!DOCTYPE html>
<html lang="en"
      xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout/base :: layout(~{::title/text()}, ~{::main-content})}">
<head>
  <title>Home</title>
</head>
<body>
<th:block th:fragment="main-content">

  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">

    <!-- Welcome banner -->
    <div class="mb-8 animate-fade-up">
      <h1 class="font-display text-4xl sm:text-5xl text-green-dark tracking-wider mb-1">
        WELCOME BACK 👋
      </h1>
      <p class="text-gray-500 text-sm font-medium">
        FIFA World Cup 2026 · Internal Prediction Game
      </p>
    </div>

    <!-- "Next match to predict" pill — only shown when an open window exists -->
    <div th:if="${nextMatch != null}"
         class="mb-8 animate-fade-up stagger-1">
      <a th:href="@{/predictions}"
         class="inline-flex items-center gap-3
                bg-orange text-white font-bold text-sm
                px-6 py-3 rounded-2xl shadow-lg shadow-orange/30
                animate-pulse-glow hover:scale-105 transition-transform duration-200
                no-underline">
        <span class="text-base animate-bounce" style="animation-duration:1.2s">⚡</span>
        <span>
          Predict now:
          <span th:text="${nextMatch.homeTeamName} + ' vs ' + ${nextMatch.awayTeamName}"
                class="ml-1">Brazil vs Argentina</span>
        </span>
        <span class="ml-1 text-white/70 font-normal">→</span>
      </a>
    </div>

    <!-- ── Quick Stats Row ─────────────────────────────────────────────── -->
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-10 animate-fade-up stagger-2">

      <!-- My Rank -->
      <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-5
                  flex flex-col items-center text-center hover:shadow-md transition-shadow">
        <div class="text-3xl mb-1">🏆</div>
        <div class="font-display text-4xl text-green-dark tracking-wider"
             th:text="${userRank > 0 ? '#' + userRank : '—'}">
          #1
        </div>
        <div class="text-xs font-semibold text-gray-400 uppercase tracking-wide mt-1">
          Your Rank
        </div>
      </div>

      <!-- My Points -->
      <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-5
                  flex flex-col items-center text-center hover:shadow-md transition-shadow">
        <div class="text-3xl mb-1">⭐</div>
        <div class="font-display text-4xl text-green tracking-wider"
             th:text="${userPoints}">42</div>
        <div class="text-xs font-semibold text-gray-400 uppercase tracking-wide mt-1">
          Points
        </div>
      </div>

      <!-- Exact Scores -->
      <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-5
                  flex flex-col items-center text-center hover:shadow-md transition-shadow">
        <div class="text-3xl mb-1">🎯</div>
        <div class="font-display text-4xl text-gold tracking-wider"
             th:text="${userExact}">5</div>
        <div class="text-xs font-semibold text-gray-400 uppercase tracking-wide mt-1">
          Exact Scores
        </div>
      </div>

      <!-- Predictions filed -->
      <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-5
                  flex flex-col items-center text-center hover:shadow-md transition-shadow">
        <div class="text-3xl mb-1">📝</div>
        <div class="font-display text-4xl text-gray-700 tracking-wider"
             th:text="${totalPredicted}">18</div>
        <div class="text-xs font-semibold text-gray-400 uppercase tracking-wide mt-1">
          Predicted
        </div>
      </div>

    </div>

    <!-- ── Two-column layout: Leaderboard + Action links ────────────────── -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">

      <!-- Mini Leaderboard (2/3 width on lg) -->
      <div class="lg:col-span-2 animate-fade-up stagger-3">

        <div class="flex items-center justify-between mb-4">
          <h2 class="font-display text-2xl text-green-dark tracking-wider">LEADERBOARD</h2>
          <a th:href="@{/leaderboard}"
             class="text-sm font-semibold text-green hover:text-green-dark transition-colors">
            View all →
          </a>
        </div>

        <!-- Include mini-leaderboard fragment from Part 4 -->
        <div th:replace="~{fragments/leaderboard-mini :: leaderboard-mini(topTen=${topTen})}">
          <!-- fallback content shown when fragment is not yet available -->
          <div class="bg-white rounded-2xl border border-gray-100 shadow-sm p-8 text-center text-gray-400">
            <div class="text-4xl mb-3">🏆</div>
            <p class="font-semibold">Leaderboard loading...</p>
          </div>
        </div>

      </div>

      <!-- Action links panel (1/3 width on lg) -->
      <div class="space-y-4 animate-fade-up stagger-4">

        <h2 class="font-display text-2xl text-green-dark tracking-wider mb-4">QUICK LINKS</h2>

        <!-- Predict link (highlighted if open window) -->
        <a th:href="@{/predictions}"
           class="flex items-center gap-4 bg-white rounded-2xl border shadow-sm p-5
                  hover:shadow-md transition-all duration-200 no-underline group"
           th:classappend="${openMatchCount > 0} ? 'border-orange/40 hover:border-orange/60' : 'border-gray-100'">
          <div class="w-10 h-10 rounded-xl flex items-center justify-center text-xl flex-shrink-0"
               th:classappend="${openMatchCount > 0} ? 'bg-orange/10' : 'bg-gray-100'">⚡</div>
          <div class="flex-1">
            <div class="font-bold text-gray-800 text-sm">My Predictions</div>
            <div class="text-xs text-gray-400 mt-0.5"
                 th:text="${openMatchCount > 0} ? ${openMatchCount} + ' match(es) open for prediction' : 'No open windows right now'">
              3 matches open
            </div>
          </div>
          <span class="text-gray-300 group-hover:text-gray-500 transition-colors">→</span>
        </a>

        <!-- Fixtures link -->
        <a th:href="@{/fixtures}"
           class="flex items-center gap-4 bg-white rounded-2xl border border-gray-100 shadow-sm p-5
                  hover:shadow-md hover:border-green/30 transition-all duration-200 no-underline group">
          <div class="w-10 h-10 rounded-xl bg-green/10 flex items-center justify-center text-xl flex-shrink-0">📅</div>
          <div class="flex-1">
            <div class="font-bold text-gray-800 text-sm">Fixtures</div>
            <div class="text-xs text-gray-400 mt-0.5">All 104 matches</div>
          </div>
          <span class="text-gray-300 group-hover:text-gray-500 transition-colors">→</span>
        </a>

        <!-- Groups link -->
        <a th:href="@{/groups}"
           class="flex items-center gap-4 bg-white rounded-2xl border border-gray-100 shadow-sm p-5
                  hover:shadow-md hover:border-green/30 transition-all duration-200 no-underline group">
          <div class="w-10 h-10 rounded-xl bg-green/10 flex items-center justify-center text-xl flex-shrink-0">🗂</div>
          <div class="flex-1">
            <div class="font-bold text-gray-800 text-sm">Groups</div>
            <div class="text-xs text-gray-400 mt-0.5">12 groups · A through L</div>
          </div>
          <span class="text-gray-300 group-hover:text-gray-500 transition-colors">→</span>
        </a>

        <!-- Bracket link -->
        <a th:href="@{/bracket}"
           class="flex items-center gap-4 bg-white rounded-2xl border border-gray-100 shadow-sm p-5
                  hover:shadow-md hover:border-green/30 transition-all duration-200 no-underline group">
          <div class="w-10 h-10 rounded-xl bg-gold/10 flex items-center justify-center text-xl flex-shrink-0">🎯</div>
          <div class="flex-1">
            <div class="font-bold text-gray-800 text-sm">Knockout Bracket</div>
            <div class="text-xs text-gray-400 mt-0.5">R32 → Final</div>
          </div>
          <span class="text-gray-300 group-hover:text-gray-500 transition-colors">→</span>
        </a>

        <!-- Full Leaderboard link -->
        <a th:href="@{/leaderboard}"
           class="flex items-center gap-4 bg-white rounded-2xl border border-gray-100 shadow-sm p-5
                  hover:shadow-md hover:border-green/30 transition-all duration-200 no-underline group">
          <div class="w-10 h-10 rounded-xl bg-green/10 flex items-center justify-center text-xl flex-shrink-0">🏆</div>
          <div class="flex-1">
            <div class="font-bold text-gray-800 text-sm">Full Leaderboard</div>
            <div class="text-xs text-gray-400 mt-0.5">All participants ranked</div>
          </div>
          <span class="text-gray-300 group-hover:text-gray-500 transition-colors">→</span>
        </a>

      </div>
    </div>

  </div>

</th:block>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/home.html
git commit -m "feat(part5): add authenticated home page with mini-leaderboard, quick stats and action links"
```

---

### Task 11: Spring Security Route Configuration

**Files:**
- Edit: `src/main/java/com/worldcup/prediction/config/SecurityConfig.java`

These rules must be added/confirmed in the existing `SecurityConfig.java`. The public pages (`/`, `/fixtures`, `/groups`, `/leaderboard`, `/bracket`, `/oauth2/**`) must be `permitAll()`. The home page (`/home`, `/predictions`) must require authentication, with unauthenticated users redirected to `/`.

- [ ] **Step 1: Verify/update SecurityConfig permit rules**

Ensure the `SecurityFilterChain` bean contains at minimum these matchers (adapt to your existing config structure):

```java
http.authorizeHttpRequests(auth -> auth
    // Public pages — no login required
    .requestMatchers("/", "/fixtures", "/fixtures/**",
                     "/groups", "/leaderboard", "/bracket",
                     "/oauth2/**", "/login/**",
                     "/css/**", "/js/**", "/images/**",
                     "/webjars/**").permitAll()
    // Authenticated only
    .requestMatchers("/home", "/predictions/**").authenticated()
    // Admin only
    .requestMatchers("/admin/**").hasRole("ADMIN")
    .anyRequest().authenticated()
)
.oauth2Login(oauth2 -> oauth2
    .defaultSuccessUrl("/home", true)
    .failureUrl("/")
)
.logout(logout -> logout
    .logoutUrl("/logout")
    .logoutSuccessUrl("/")
);
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/worldcup/prediction/config/SecurityConfig.java
git commit -m "feat(part5): configure Spring Security permit rules for public and authenticated routes"
```

---

### Task 12: Service Interface Stubs (if not yet created by Parts 1–4)

**Files:**
- Create (if missing): `src/main/java/com/worldcup/prediction/service/MatchService.java` — interface stub
- Create (if missing): `src/main/java/com/worldcup/prediction/service/GroupService.java` — interface stub
- Create (if missing): `src/main/java/com/worldcup/prediction/service/UserStatsService.java` — interface stub

These stubs compile cleanly against the controllers. Part 3 (match/group service) provides the real implementations.

- [ ] **Step 1: Create MatchService.java stub (if not already created in Part 3)**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.dto.FixtureViewDto;
import java.util.List;

/**
 * Service layer for match/fixture data.
 * Full implementation provided in Part 3.
 */
public interface MatchService {

    /** All fixtures sorted chronologically. */
    List<FixtureViewDto> getAllFixtures();

    /** Group stage fixtures only. */
    List<FixtureViewDto> getGroupStageFixtures();

    /** Knockout stage fixtures only (R32 through Final). */
    List<FixtureViewDto> getKnockoutFixtures();

    /** Fixtures with kickoff on today's date. */
    List<FixtureViewDto> getTodayFixtures();

    /**
     * Next match in an open prediction window for the given user.
     * Returns null if no window is currently open.
     */
    FixtureViewDto getNextPredictableMatch(Long userId);

    /** Number of matches currently in an open prediction window. */
    int getOpenMatchCount();
}
```

- [ ] **Step 2: Create GroupService.java stub (if not already created in Part 3)**

```java
package com.worldcup.prediction.service;

import com.worldcup.prediction.dto.GroupStandingDto;
import java.util.List;
import java.util.Map;

/**
 * Service layer for group standings data.
 * Full implementation provided in Part 3.
 */
public interface GroupService {

    /**
     * Returns ordered map of group label (e.g. "Group A") to
     * list of team standings sorted by position (1–4).
     */
    Map<String, List<GroupStandingDto>> getAllGroupStandings();

    /**
     * Returns the group labels (e.g. ["Group B", "Group F"]) whose
     * third-place team has qualified as one of the best 8 third-place teams.
     * Empty list if group stage is not yet complete.
     */
    List<String> getQualifiedThirdPlaceGroups();
}
```

- [ ] **Step 3: Create UserStatsService.java stub (if not already created in Part 4)**

```java
package com.worldcup.prediction.service;

/**
 * Per-user statistics used on the home page dashboard.
 * Full implementation provided in Part 4 (leaderboard).
 */
public interface UserStatsService {

    /** Total points earned by user. */
    int getTotalPoints(Long userId);

    /** Count of exact score predictions (+3). */
    int getExactScoreCount(Long userId);

    /** Total number of predictions submitted (any status). */
    int getTotalPredicted(Long userId);
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/worldcup/prediction/service/MatchService.java \
        src/main/java/com/worldcup/prediction/service/GroupService.java \
        src/main/java/com/worldcup/prediction/service/UserStatsService.java
git commit -m "feat(part5): add MatchService, GroupService and UserStatsService interface stubs for controller compilation"
```

---

### Task 13: Verify Compilation and Run Smoke Test

- [ ] **Step 1: Compile the project**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS with zero compilation errors.

- [ ] **Step 2: Run the application**

```bash
./mvnw spring-boot:run
```

- [ ] **Step 3: Manual smoke test checklist**

Open a browser and verify:

| URL | Expected result |
|-----|-----------------|
| `http://localhost:8080/` | Landing page loads — Ken Burns hero, countdown timer ticking, Google + LinkedIn OAuth2 buttons visible |
| `http://localhost:8080/fixtures` | Fixtures page loads — filter tabs visible, fixture rows grouped by phase + date |
| `http://localhost:8080/fixtures?filter=today` | Today's matches shown (or empty state if none) |
| `http://localhost:8080/fixtures?filter=group` | Group stage fixtures only |
| `http://localhost:8080/groups` | 12 group cards rendered with standings tables |
| `http://localhost:8080/home` (unauthenticated) | Redirected to `/` |
| `http://localhost:8080/home` (authenticated) | Dashboard page with stats row and mini-leaderboard |
| HTMX filter click on `/fixtures` | Only the fixture list updates (no full page reload) |

- [ ] **Step 4: Final commit**

```bash
git add .
git commit -m "feat(part5): complete public pages — landing, fixtures, groups, home, base layout"
```

---

## Implementation Notes

### Thymeleaf Security Dialect dependency
`base.html` uses `sec:authorize` expressions. Ensure `pom.xml` includes:
```xml
<dependency>
  <groupId>org.thymeleaf.extras</groupId>
  <artifactId>thymeleaf-extras-springsecurity6</artifactId>
</dependency>
```
Spring Boot auto-configures this when spring-security is on the classpath and this artifact is present.

### AppUserPrincipal contract
`HomeController` calls `principal.getUserId()`, `principal.getAvatarUrl()`, and `principal.getDisplayName()`. These methods must exist on `AppUserPrincipal` — this is the custom `UserDetails` implementation from Part 2.

### Flag CDN URLs
All flag images use:
```
https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/{code}.svg
```
where `{code}` is the ISO 3166-1 alpha-2 country code in **lowercase** (e.g. `br`, `de`, `us`). The `onerror` fallback uses `https://flagcdn.com/w40/{code}.png`.

### Thymeleaf layout mechanism
All pages (except `index.html` which is full-screen) extend `base.html` using the Thymeleaf Layout Dialect fragment replacement pattern:
```html
th:replace="~{layout/base :: layout(~{::title/text()}, ~{::main-content})}"
```
This requires `nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect` **or** the built-in Thymeleaf fragment approach used here (no additional dependency).

### HTMX filter tabs
The fixture filter tabs send `GET /fixtures/filter?filter=xxx` via HTMX and swap only `#fixture-list`. The `hx-push-url="false"` prevents URL bar changes — set to `true` if bookmark-friendly URLs are desired.

### Groups page: stream() in Thymeleaf expression
The GF/GA summary in the group card footer uses `th:text` with a Java stream expression. If the Thymeleaf expression engine does not support `.stream()` in SpEL (Spring Expression Language), replace it with a pre-computed value from the controller:
```java
model.addAttribute("groupGoalTotals", groupService.getGroupGoalTotals());
```
and reference it with `${groupGoalTotals[groupEntry.key]}` in the template. This is the safer approach.
