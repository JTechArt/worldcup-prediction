# Leaderboard & Predictions UI Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Four UI improvements: sticky "your row" in leaderboard, user card modal on player click, mobile name hiding, and team name truncation on the predictions page.

**Architecture:** Pure frontend — CSS, HTML, and lightweight Alpine.js (already loaded via community-base.html). No backend changes needed: `LeaderboardEntryDto` already has `exactCount`, `correctWinnerCount`, and `drawCount`. The modal is a standalone Alpine `x-data` component at the bottom of the leaderboard page that listens for a native `CustomEvent('show-user-card')` dispatched from player row click handlers.

**Tech Stack:** Thymeleaf, Alpine.js 3.x, CSS — no build step, edit source files directly.

---

## File Map

| File | Changes |
|------|---------|
| `src/main/resources/static/css/predictions.css` | Add `max-width` to `.mr-name`; replace `.card-team-name` with 2-line clamp; update mobile overrides |
| `src/main/resources/templates/fragments/predictions-round-content.html` | Add `:title` bindings to team name elements in list view |
| `src/main/resources/templates/predictions.html` | Add `th:title` to team name spans in past-rounds accordion |
| `src/main/resources/templates/community/leaderboard.html` | Sticky row CSS, `overflow-y: clip` fix, `data-uc-*` attrs, click handlers, mobile name hiding, modal HTML+CSS |

---

### Task 1: Predictions page — truncate long team names in list view

**Files:**
- Modify: `src/main/resources/static/css/predictions.css` — line 84 (`.mr-name`)
- Modify: `src/main/resources/templates/fragments/predictions-round-content.html` — lines ~72 and ~104
- Modify: `src/main/resources/templates/predictions.html` — lines ~135 and ~137

- [ ] **Step 1: Add `max-width` to `.mr-name` in predictions.css**

Line 84 currently reads:
```css
.mr-name { font-size:13px; font-weight:800; color:var(--text); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
```

Replace with:
```css
.mr-name { font-size:13px; font-weight:800; color:var(--text); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:90px; }
```

- [ ] **Step 2: Also add `max-width` to `.pm-name` (past rounds)**

Line 187 currently reads:
```css
.pm-name { font-size:12px; font-weight:700; color:var(--text); white-space:nowrap; }
```

Replace with:
```css
.pm-name { font-size:12px; font-weight:700; color:var(--text); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:80px; }
```

- [ ] **Step 3: Add `:title` bindings to list view team names in predictions-round-content.html**

Find these two elements (home team ~line 72, away team ~line 104):
```html
<div class="mr-name" x-text="match.homeTeamName"></div>
```
```html
<div class="mr-name" x-text="match.awayTeamName"></div>
```

Change both to:
```html
<div class="mr-name" x-text="match.homeTeamName" :title="match.homeTeamName"></div>
```
```html
<div class="mr-name" x-text="match.awayTeamName" :title="match.awayTeamName"></div>
```

- [ ] **Step 4: Add `th:title` to past-rounds team name spans in predictions.html**

Find these two spans (~lines 135 and 137):
```html
<span class="pm-name" th:text="${match.homeTeamName}">France</span>
```
```html
<span class="pm-name" th:text="${match.awayTeamName}">Germany</span>
```

Change to:
```html
<span class="pm-name" th:text="${match.homeTeamName}" th:title="${match.homeTeamName}">France</span>
```
```html
<span class="pm-name" th:text="${match.awayTeamName}" th:title="${match.awayTeamName}">Germany</span>
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/css/predictions.css \
        src/main/resources/templates/fragments/predictions-round-content.html \
        src/main/resources/templates/predictions.html
git commit -m "fix: truncate long team names in list and past-rounds views"
```

---

### Task 2: Predictions page — fix team name overflow in card view

**Files:**
- Modify: `src/main/resources/static/css/predictions.css` — line 122 (`.card-team-name`) and line 233 (mobile override)

- [ ] **Step 1: Replace `.card-team-name` rule with 2-line clamp**

Line 122 currently reads:
```css
.card-team-name { font-family:'Bebas Neue',sans-serif; font-size:24px; letter-spacing:3px; text-align:center; }
```

Replace with:
```css
.card-team-name {
  font-family:'Bebas Neue',sans-serif;
  font-size:20px;
  letter-spacing:2px;
  text-align:center;
  display:-webkit-box;
  -webkit-line-clamp:2;
  -webkit-box-orient:vertical;
  overflow:hidden;
  max-width:100px;
  line-height:1.15;
}
```

- [ ] **Step 2: Update the mobile override for `.card-team-name`**

The existing mobile rule inside `@media (max-width:640px)` (~line 233) currently reads:
```css
.card-team-name { font-size:18px; }
```

Replace with:
```css
.card-team-name { font-size:14px; letter-spacing:1px; max-width:80px; }
```

- [ ] **Step 3: Verify visually**

Start the app (`./mvnw spring-boot:run`) and navigate to `/predictions`. Switch to **Card view**. Confirm a long team name wraps to 2 lines cleanly. At ≤640px viewport width confirm the name stays inside the card without overflow.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/predictions.css
git commit -m "fix: 2-line clamp for long team names in card view"
```

---

### Task 3: Leaderboard — sticky "your row"

**Files:**
- Modify: `src/main/resources/templates/community/leaderboard.html` — `<style>` block

- [ ] **Step 1: Fix `.lb-mid` to allow sticky to propagate**

In the `<style>` block find the `.lb-mid` rule (~line 70):
```css
.lb-mid   { flex: 1; overflow-x: auto; overflow-y: hidden; scrollbar-width: thin; scrollbar-color: rgba(0,160,70,.25) rgba(0,160,70,.04); }
```

Change `overflow-y: hidden` → `overflow-y: clip`:
```css
.lb-mid   { flex: 1; overflow-x: auto; overflow-y: clip; scrollbar-width: thin; scrollbar-color: rgba(0,160,70,.25) rgba(0,160,70,.04); }
```

`clip` visually clips overflow without creating a scroll container, so `position: sticky` can propagate up to the page scroll.

- [ ] **Step 2: Add `position: sticky` to `.lb-row-data.you`**

Find the `.lb-row-data.you` rule and replace it entirely with:
```css
.lb-row-data.you {
  --h-row: 78px;
  height: 78px;
  position: sticky;
  top: calc(64px + var(--h-phase) + var(--h-game));
  background: rgba(14,165,233,.12) !important;
  border-top: 2px solid rgba(14,165,233,.65) !important;
  border-bottom: 2px solid rgba(14,165,233,.65) !important;
  box-shadow:
    inset 5px 0 0 #0ea5e9,
    0 8px 28px rgba(14,165,233,.28),
    0 -8px 28px rgba(14,165,233,.18),
    0 2px 6px rgba(0,0,0,.08);
  z-index: 20;
}
```

`top: calc(64px + var(--h-phase) + var(--h-game))` = 64px nav + 32px phase row + 68px game header = **164px**. All three panels' `.you` rows share this value and the same page scroll container, so they snap at the same vertical position across the full width.

- [ ] **Step 3: Verify visually**

Navigate to a leaderboard with multiple participants while logged in. Scroll down — the sky-blue "your row" should stick just below the sticky phase/game header and remain visible as you scroll through other rows. The prediction cells in the mid panel should stay horizontally aligned.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/community/leaderboard.html
git commit -m "feat: sticky 'your row' in leaderboard while page-scrolling"
```

---

### Task 4: Leaderboard — add `data-uc-*` attributes and click handlers

**Files:**
- Modify: `src/main/resources/templates/community/leaderboard.html` — left-panel Thymeleaf loop

- [ ] **Step 1: Add `data-uc-*` attributes to the left-panel row div**

In the `<div class="lb-left">` section, find the row div rendered by the `th:each` loop. It currently starts with:
```html
<div th:class="'lb-row-data ' + ${entry.rankCssClass}
               + (${#authentication != null
                   and !#authentication.principal.equals('anonymousUser')
                   and #authentication.principal.userId == entry.userId} ? ' you' : '')"
     th:style="'animation: lbFUp .4s ease ' + (${iter.index} * 0.04) + 's both'">
```

Add the `th:data-uc-*` attributes:
```html
<div th:class="'lb-row-data ' + ${entry.rankCssClass}
               + (${#authentication != null
                   and !#authentication.principal.equals('anonymousUser')
                   and #authentication.principal.userId == entry.userId} ? ' you' : '')"
     th:style="'animation: lbFUp .4s ease ' + (${iter.index} * 0.04) + 's both'"
     th:data-uc-name="${entry.displayName}"
     th:data-uc-rank="${entry.rank}"
     th:data-uc-points="${entry.totalPoints}"
     th:data-uc-exact="${entry.exactCount}"
     th:data-uc-winner="${entry.correctWinnerCount}"
     th:data-uc-draw="${entry.drawCount}"
     th:data-uc-avatar="${entry.avatarUrl != null ? entry.avatarUrl : ''}"
     th:data-uc-initials="${entry.initials}">
```

- [ ] **Step 2: Add click handler to the player name div (web trigger)**

Find the `.lb-pn` div inside `.lb-pc`:
```html
<div class="lb-pn">
  <span th:text="${entry.displayName}">Player</span>
```

Add `style="cursor:pointer"` and an `onclick` that reads the row's data attributes and dispatches a window event:
```html
<div class="lb-pn"
     style="cursor:pointer"
     onclick="(function(el){var d=el.closest('.lb-row-data').dataset;window.dispatchEvent(new CustomEvent('show-user-card',{detail:{name:d.ucName,rank:parseInt(d.ucRank),points:parseInt(d.ucPoints),exact:parseInt(d.ucExact),winner:parseInt(d.ucWinner),draw:parseInt(d.ucDraw),avatarUrl:d.ucAvatar||null,initials:d.ucInitials}}));})(this)">
  <span th:text="${entry.displayName}">Player</span>
```

- [ ] **Step 3: Add click handler to the avatar wrap (mobile trigger)**

Find `.lb-av-wrap`:
```html
<div class="lb-av-wrap">
```

Add the same onclick:
```html
<div class="lb-av-wrap"
     onclick="(function(el){var d=el.closest('.lb-row-data').dataset;window.dispatchEvent(new CustomEvent('show-user-card',{detail:{name:d.ucName,rank:parseInt(d.ucRank),points:parseInt(d.ucPoints),exact:parseInt(d.ucExact),winner:parseInt(d.ucWinner),draw:parseInt(d.ucDraw),avatarUrl:d.ucAvatar||null,initials:d.ucInitials}}));})(this)">
```

- [ ] **Step 4: Add mobile CSS — hide name and sub-line, pointer cursor on avatar**

In the `@media (max-width: 639px)` block inside the `<style>` tag, add these lines:
```css
.lb-pn  { display: none; }
.lb-pm  { display: none; }
.lb-av-wrap { cursor: pointer; }
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/community/leaderboard.html
git commit -m "feat: add data attributes and click handlers for user card modal"
```

---

### Task 5: Leaderboard — user card modal HTML and CSS

**Files:**
- Modify: `src/main/resources/templates/community/leaderboard.html` — `<style>` block + bottom of `main-content`

- [ ] **Step 1: Add modal CSS to the `<style>` block**

Add the following before the closing `</style>` tag:
```css
/* ── USER CARD MODAL ── */
.uc-backdrop {
  position: fixed; inset: 0;
  background: rgba(0,0,0,.45);
  backdrop-filter: blur(4px);
  z-index: 98;
}
.uc-card {
  position: fixed;
  z-index: 99;
  background: white;
  border-radius: 20px;
  padding: 28px 24px 24px;
  width: 320px;
  box-shadow: 0 24px 64px rgba(0,0,0,.25);
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  animation: ucCardIn .22s ease;
}
@keyframes ucCardIn {
  from { opacity: 0; transform: translate(-50%, -47%); }
  to   { opacity: 1; transform: translate(-50%, -50%); }
}
.uc-close {
  position: absolute; top: 14px; right: 16px;
  background: none; border: none; font-size: 18px;
  color: #667c70; cursor: pointer; line-height: 1;
  padding: 4px 6px; border-radius: 6px; transition: background .15s;
}
.uc-close:hover { background: rgba(0,0,0,.06); }
.uc-header { display: flex; align-items: center; gap: 14px; margin-bottom: 16px; }
.uc-avatar {
  width: 56px; height: 56px; flex-shrink: 0;
  border-radius: 14px; overflow: hidden;
  background: linear-gradient(135deg,#00a845,#006b2a);
  display: flex; align-items: center; justify-content: center;
  border: 2px solid white; box-shadow: 0 2px 10px rgba(0,0,0,.18);
}
.uc-avatar img { width: 100%; height: 100%; object-fit: cover; display: block; }
.uc-avatar-initials { font-size: 18px; font-weight: 900; color: white; }
.uc-name {
  font-size: 15px; font-weight: 800; color: #0d1a10;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 200px;
}
.uc-rank { font-size: 12px; font-weight: 700; color: #667c70; margin-top: 3px; }
.uc-rank-hi { color: #00a845; font-weight: 800; }
.uc-divider { height: 1px; background: rgba(0,160,70,.12); margin: 0 0 16px; }
.uc-stats { display: flex; }
.uc-stat { flex: 1; text-align: center; padding: 10px 4px; border-right: 1px solid rgba(0,160,70,.1); }
.uc-stat:last-child { border-right: none; }
.uc-stat-v { font-size: 22px; font-weight: 900; color: #0d1a10; line-height: 1; }
.uc-stat-l { font-size: 9px; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; color: #667c70; margin-top: 3px; }

@media (max-width: 639px) {
  .uc-card {
    top: auto; bottom: 0; left: 0; right: 0;
    width: 100%; transform: none;
    border-radius: 20px 20px 0 0;
    padding-bottom: max(24px, env(safe-area-inset-bottom));
    animation: ucCardInMobile .25s ease;
  }
  @keyframes ucCardInMobile {
    from { opacity: 0; transform: translateY(30px); }
    to   { opacity: 1; transform: translateY(0); }
  }
}
```

- [ ] **Step 2: Add modal HTML at the bottom of `main-content`**

Find the closing `</div>` of the main content wrapper (the one just before `</th:block>`, around line 441). Insert the modal block immediately before it:

```html
<!-- ── USER CARD MODAL ── -->
<div x-data="{ open: false, entry: null }"
     @show-user-card.window="entry = $event.detail; open = true">

  <!-- Backdrop -->
  <div x-show="open" x-transition class="uc-backdrop" @click="open = false"></div>

  <!-- Card -->
  <div x-show="open" class="uc-card" @click.stop x-cloak>
    <button class="uc-close" @click="open = false">✕</button>

    <!-- Header: avatar + name + rank/pts -->
    <div class="uc-header" x-show="entry">
      <div class="uc-avatar">
        <img x-show="entry && entry.avatarUrl"
             :src="entry ? entry.avatarUrl : ''"
             :alt="entry ? entry.name : ''"
             onerror="this.style.display='none'">
        <span class="uc-avatar-initials"
              x-show="!entry || !entry.avatarUrl"
              x-text="entry ? entry.initials : ''"></span>
      </div>
      <div>
        <div class="uc-name" x-text="entry ? entry.name : ''"></div>
        <div class="uc-rank">
          <span class="uc-rank-hi" x-text="entry ? '#' + entry.rank : ''"></span>
          &nbsp;·&nbsp;
          <span x-text="entry ? entry.points + ' pts' : ''"></span>
        </div>
      </div>
    </div>

    <div class="uc-divider"></div>

    <!-- Stats row -->
    <div class="uc-stats" x-show="entry">
      <div class="uc-stat">
        <div class="uc-stat-v" x-text="entry ? entry.exact : 0"></div>
        <div class="uc-stat-l">✦ Exact</div>
      </div>
      <div class="uc-stat">
        <div class="uc-stat-v" x-text="entry ? entry.winner : 0"></div>
        <div class="uc-stat-l">Win</div>
      </div>
      <div class="uc-stat">
        <div class="uc-stat-v" x-text="entry ? entry.draw : 0"></div>
        <div class="uc-stat-l">Draw</div>
      </div>
    </div>
  </div>

</div>
```

- [ ] **Step 3: Add `[x-cloak]` CSS to hide Alpine elements before init**

In the `<style>` block add (or confirm it's in the base layout):
```css
[x-cloak] { display: none !important; }
```

- [ ] **Step 4: Verify the full user card flow**

1. Start the app and navigate to a community leaderboard while logged in.
2. **Desktop (≥640px):** Click a player's name — card appears centered with avatar, name, `#rank · N pts`, and exact/win/draw counts. Click the backdrop or ✕ to close.
3. **Mobile (≤639px):** Player names should be hidden (avatar only). Tap an avatar — bottom sheet slides up with the same stats.
4. Confirm the "YOU" entry also opens the card correctly.
5. Confirm avatar image falls back to initials when `avatarUrl` is empty.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/community/leaderboard.html
git commit -m "feat: user card modal — avatar/name click shows player stats"
```

---

## Self-Review Checklist

- [x] **Spec §1 (sticky row):** Covered in Task 3 — `overflow-y: clip` + `position: sticky` on `.you`
- [x] **Spec §2 (user card modal):** Covered in Tasks 4+5 — data attrs, dispatchers, Alpine modal
- [x] **Spec §3 (leaderboard name handling web):** `.lb-pn` already has `max-width:108px; text-overflow:ellipsis`; cursor + click covered in Task 4
- [x] **Spec §3 (leaderboard name hiding mobile):** Covered in Task 4 Step 4
- [x] **Spec §4 (predictions list view):** Covered in Task 1
- [x] **Spec §4 (predictions card view):** Covered in Task 2
- [x] **`drawCount` field:** Already on `LeaderboardEntryDto.getDrawCount()` — no backend change needed
- [x] **Alpine.js availability:** Confirmed loaded in `community-base.html`
- [x] **Mobile breakpoint consistency:** `max-width: 639px` used throughout (matches existing leaderboard breakpoint)
