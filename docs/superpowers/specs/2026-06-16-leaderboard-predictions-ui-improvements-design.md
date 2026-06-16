# Leaderboard & Predictions UI Improvements

**Date:** 2026-06-16
**Status:** Approved

---

## Overview

Four UI improvements across the leaderboard and predictions pages:

1. Sticky "your row" — user's own leaderboard row stays visible while scrolling
2. User card modal — click a player name (web) or avatar (mobile) to see their stats
3. Long name handling in the leaderboard
4. Long team name handling on the predictions page (web + mobile)

---

## 1. Sticky "Your Row"

### Problem
The user's own row (`lb-row-data.you`) is buried in the leaderboard table. When scrolling through many participants the user loses sight of their own position.

### Solution
Make the `.you` row `position: sticky` so it pins below the sticky header while the user scrolls through other rows.

### Technical Details

**Root cause of current CSS sticky failure:**
- `.lb-mid` has `overflow-y: hidden`, which creates a scroll container and blocks `position: sticky` from propagating to the page scroll.

**Fix:**
- Change `.lb-mid` from `overflow-x: auto; overflow-y: hidden` → `overflow-x: auto; overflow-y: clip`
  - `clip` visually clips overflow but does NOT create a scroll container, so sticky propagates to the viewport.
- Confirm `.lb-outer` already uses `overflow: clip` (not `overflow: hidden`), which is fine — `clip` is not a scroll container.

**Sticky offset:**
```css
.lb-row-data.you {
  position: sticky;
  top: calc(64px + var(--h-phase) + var(--h-game)); /* 64px nav + 32px + 68px = 164px */
  z-index: 20;
}
```

All three panels (left, mid, right) each contain their own `.lb-row-data.you`. Because they share the same `top` value and the same page scroll container, all three snap sticky at exactly the same vertical position, maintaining full-width row alignment.

**Files changed:**
- `src/main/resources/templates/community/leaderboard.html` — CSS only

---

## 2. User Card Modal

### Problem
Player names can be truncated and there is no way to see a player's detailed prediction stats without them being in the current user's view.

### Solution
A click on a player's name (web) or avatar (mobile) opens a modal card showing full stats.

### Modal Content
```
┌─────────────────────────────┐
│  [Avatar]  Name        #3   │
│            1,247 pts        │
│  ─────────────────────────  │
│  ✦ Exact   7   WIN   12    │
│  ⊜ Draw    4               │
└─────────────────────────────┘
```

Fields: avatar, display name, rank, total points, exact count, correct winner count, correct draw count.

### Implementation

**Data flow:**
- Thymeleaf renders all entry fields as `data-*` attributes on each player's left-panel row div (e.g., `data-uc-name`, `data-uc-rank`, `data-uc-points`, `data-uc-exact`, `data-uc-winner`, `data-uc-draw`, `data-uc-avatar`, `data-uc-initials`).
- A click handler on the row reads `$el.dataset`, builds a plain JS object, and dispatches it to the window via Alpine `$dispatch('show-user-card', entry)`.
- A global `x-data` component wrapping the whole leaderboard listens at `@show-user-card.window`, stores the entry in state, and opens the modal.

**Trigger elements:**
- **Web (≥640px):** The player name `<span>` is the click target. It renders truncated (see §3) with `cursor: pointer`.
- **Mobile (<640px):** Name `<div>` is hidden. The avatar wrap `.lb-av-wrap` becomes the click target.

**Modal structure (Alpine.js):**
```
<div x-data="{ open: false, entry: null }"
     @show-user-card.window="entry = $event.detail; open = true">
  <!-- backdrop -->
  <div x-show="open" @click="open = false" class="uc-backdrop" x-transition>
  <!-- card -->
  <div x-show="open" class="uc-card" x-transition>
    ...stats...
    <button @click="open = false">✕</button>
  </div>
</div>
```

**Positioning:**
- Web: centered in viewport (`position: fixed; top: 50%; left: 50%; transform: translate(-50%,-50%)`)
- Mobile: bottom sheet (`position: fixed; bottom: 0; left: 0; right: 0; border-radius: 20px 20px 0 0`)
- Breakpoint: `@media (max-width: 639px)` matches the existing leaderboard mobile breakpoint

**Files changed:**
- `src/main/resources/templates/community/leaderboard.html` — HTML + CSS + inline JS

---

## 3. Long Name Handling in Leaderboard

### Problem
Player names longer than the column width overflow or push the layout (e.g., the player name column `--pw: 240px` / `155px` on mobile).

### Solution

**Web (≥640px):**
- `.lb-pn` (player name span) already has `max-width: 108px` and `text-overflow: ellipsis`. This is already correct.
- Add `cursor: pointer` to signal the name is clickable (ties to modal, §2).
- No max-width changes needed.

**Mobile (<639px):**
- Hide the name `<div>` entirely: `display: none` on `.lb-pn` inside the mobile breakpoint.
- Avatar becomes the sole tap target (already defined in §2).
- `.lb-pm` (the "X exact · Y winner" sub-line) also hidden on mobile to reclaim space.

**Files changed:**
- `src/main/resources/templates/community/leaderboard.html` — CSS only

---

## 4. Long Team Names on Predictions Page

### Problem
Team names like "Bosnia and Herzegovina" overflow their containers in both list and card views, especially on mobile.

### Solution

#### Web — List View (`.mr-name`)
```css
.mr-name {
  max-width: 90px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
```
Add `:title="match.homeTeamName"` / `:title="match.awayTeamName"` in Alpine template bindings for native browser tooltip on hover.

#### Web — Card View (`.card-team-name`)
Allow 2 lines:
```css
.card-team-name {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 13px;
  max-width: 80px;
  text-align: center;
}
```

#### Mobile — List View (`@media max-width: 639px`)
```css
@media (max-width: 639px) {
  .mr-name { display: none; }
}
```
Flag and score stepper remain. The row is tight and readable with flags only.

#### Mobile — Card View (`@media max-width: 639px`)
```css
@media (max-width: 639px) {
  .card-team-name {
    font-size: 11px;
    max-width: 70px;
  }
}
```
Two-line clamp is retained; font shrinks. Flag remains the primary identifier.

**Files changed:**
- `src/main/resources/static/css/predictions.css` — CSS
- `src/main/resources/templates/fragments/predictions-round-content.html` — add `:title` bindings

---

## Files Summary

| File | Changes |
|------|---------|
| `templates/community/leaderboard.html` | Sticky row CSS, user card modal HTML+CSS+JS, mobile name hiding |
| `static/css/predictions.css` | Team name truncation and mobile overrides |
| `templates/fragments/predictions-round-content.html` | `:title` tooltip bindings on team name elements |

---

## Out of Scope

- Backend changes — all data is already in the model
- Pagination or lazy-loading of leaderboard entries
- Saving or sharing user cards
