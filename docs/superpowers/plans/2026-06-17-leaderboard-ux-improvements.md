# Leaderboard UX Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix three UX issues in the community leaderboard: move the you-bar to a bottom dock so it no longer occludes 1st place, auto-scroll to today's match columns on load, and highlight today's match column headers.

**Architecture:** All changes are frontend-only in `community/leaderboard.html`, with a single one-line addition to `CommunityLeaderboardController.java` to expose today's date to the template. No service, repository, or schema changes are needed.

**Tech Stack:** Thymeleaf (server-rendered HTML), Alpine.js (modal), Vanilla JS (scroll/IntersectionObserver), CSS custom properties.

---

## File Map

| File | Change |
|------|--------|
| `src/main/resources/templates/community/leaderboard.html` | CSS + HTML + JS (you-bar, today highlight, scroll) |
| `src/main/java/com/worldcup/prediction/controller/community/CommunityLeaderboardController.java` | Add `today` model attribute (1 line) |

---

## Task 1: Add `today` model attribute to the controller

**Files:**
- Modify: `src/main/java/com/worldcup/prediction/controller/community/CommunityLeaderboardController.java:131`

- [ ] **Step 1: Add the model attribute**

In `CommunityLeaderboardController.java`, find the block of `model.addAttribute(...)` calls near line 121-135. Add this line before `return "community/leaderboard";`:

```java
model.addAttribute("today", java.time.LocalDate.now().toString());
```

The full block should look like:

```java
        model.addAttribute("community", community);
        model.addAttribute("slug", slug);
        model.addAttribute("entries", entries);
        model.addAttribute("stages", MatchStage.values());
        model.addAttribute("totalParticipants", entries.size());
        model.addAttribute("phasePoints", phasePoints);
        model.addAttribute("currentUserEntry", currentUserEntry);
        model.addAttribute("currentUserId", currentUserId);
        model.addAttribute("groupRounds", groupRounds);
        model.addAttribute("currentRoundLabel", currentRoundLabel);
        model.addAttribute("currentPhaseId", currentPhaseId);
        model.addAttribute("closedRoundLabels", closedRoundLabels);
        model.addAttribute("predsByUserAndMatch", predsByUserAndMatch);
        model.addAttribute("today", java.time.LocalDate.now().toString());
        model.addAttribute("pageTitle", community.getName() + " · Leaderboard");

        return "community/leaderboard";
```

- [ ] **Step 2: Verify build compiles**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS with no errors.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/worldcup/prediction/controller/community/CommunityLeaderboardController.java
git commit -m "feat: expose today's date to community leaderboard template"
```

---

## Task 2: Add `data-date` attribute to group match column headers

**Files:**
- Modify: `src/main/resources/templates/community/leaderboard.html` — the `lb-game-col-hd` div inside the sticky header

The sticky header's game header row is at the `<div class="lb-row-game" style="display:flex">` block (around line 411). Each match column header is a `<div ... class="lb-game-col-hd">`.

- [ ] **Step 1: Add `th:data-date` to each match column header**

Find the `lb-game-col-hd` div inside the sticky header loop. It currently starts:

```html
<div th:each="m : ${entry.value}" class="lb-game-col-hd">
```

Change it to:

```html
<div th:each="m : ${entry.value}"
     class="lb-game-col-hd"
     th:data-date="${#temporals.format(m.kickoffTime, 'yyyy-MM-dd')}">
```

- [ ] **Step 2: Verify the template renders correctly**

Start the app locally and open any community leaderboard. In browser DevTools, inspect a group match column header and confirm it has a `data-date` attribute like `data-date="2026-06-12"`.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/community/leaderboard.html
git commit -m "feat: add data-date attribute to group match column headers"
```

---

## Task 3: Highlight today's match columns

**Files:**
- Modify: `src/main/resources/templates/community/leaderboard.html` — CSS block + JS DOMContentLoaded block

- [ ] **Step 1: Add CSS for today highlight**

In the `<style>` block, find the `/* ── INDIVIDUAL GAME COLUMN HEADER (group stage) ── */` comment and add these rules after the `.lb-game-col-hd` block (after line ~119):

```css
/* ── TODAY highlight ── */
.lb-game-col-hd.today {
  background: rgba(255, 214, 0, .15);
  border-top: 2px solid rgba(255, 214, 0, .65);
  border-bottom-color: rgba(255, 214, 0, .65);
}
.lb-game-col-hd.today .lb-ghd {
  color: #c8900a;
  font-weight: 800;
}
.lb-today-pill {
  display: inline-block;
  background: rgba(255, 214, 0, .85);
  color: #7a5000;
  font-size: 7px;
  font-weight: 800;
  padding: 1px 4px;
  border-radius: 3px;
  letter-spacing: .5px;
  text-transform: uppercase;
  vertical-align: middle;
  margin-right: 3px;
}
```

- [ ] **Step 2: Add JS to mark today's columns at DOMContentLoaded**

In the `<script>` block inside the `DOMContentLoaded` listener, find the `btns.forEach(b => { b.classList.toggle(...) })` line at the end of the existing JS. After it, add:

```js
// Mark today's match columns
const today = /*[[${today}]]*/ '';
if (today) {
  document.querySelectorAll('.lb-game-col-hd[data-date="' + today + '"]').forEach(col => {
    col.classList.add('today');
    const ghd = col.querySelector('.lb-ghd');
    if (ghd) ghd.innerHTML = '<span class="lb-today-pill">TODAY</span>' + ghd.textContent;
  });
}
```

- [ ] **Step 3: Verify visually**

Open a community leaderboard in the browser. If today's date matches any group match, that column header should have a yellow tint, a darker date label, and a "TODAY" pill badge. If no match is today, nothing should change.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/community/leaderboard.html
git commit -m "feat: highlight today's match columns in community leaderboard"
```

---

## Task 4: Auto-scroll to today's matches on load

**Files:**
- Modify: `src/main/resources/templates/community/leaderboard.html` — JS DOMContentLoaded block

Currently the page auto-scrolls to `currentPhaseId` on load with:
```js
scrollToPhase(currentId, false);
```

- [ ] **Step 1: Replace the initial scroll with today-aware logic**

Find the line `scrollToPhase(currentId, false);` in the `DOMContentLoaded` block (around the `btns.forEach` section).

Replace it with:

```js
// Auto-scroll: prefer today's first match column; fall back to current phase
const todayCol = today ? document.querySelector('.lb-game-col-hd[data-date="' + today + '"]') : null;
if (todayCol && mid && midHdr) {
  const midR = midHdr.getBoundingClientRect();
  const colR = todayCol.getBoundingClientRect();
  const newLeft = mid.scrollLeft + (colR.left - midR.left);
  mid.scrollTo({ left: newLeft, behavior: 'instant' });
  midHdr.scrollLeft = newLeft;
} else {
  scrollToPhase(currentId, false);
}
```

Note: `today` is already defined in the previous task's JS snippet (Task 3). Ensure Task 3's `const today = ...` line comes before this block. The ordering in the script should be:
1. sync scroll listener
2. `scrollToPhase` function definition
3. `btns.forEach` click listeners
4. Initial scroll (this task — replaces the old `scrollToPhase(currentId, false)`)
5. `btns.forEach` active class toggle
6. Today's column marking (Task 3)
7. You-bar observer (Task 5)

- [ ] **Step 2: Verify visually**

Reload a community leaderboard. If today has matches, the horizontal mid panel should open with today's columns visible at the left edge. If no matches today, it should fall back to the current phase column as before.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/community/leaderboard.html
git commit -m "feat: auto-scroll to today's match columns on leaderboard load"
```

---

## Task 5: Move you-bar to bottom dock

**Files:**
- Modify: `src/main/resources/templates/community/leaderboard.html` — CSS for `#lb-you-bar`, HTML to add jump button, JS to remove `barTop` calculation

- [ ] **Step 1: Replace the you-bar CSS**

Find the `/* ── YOU-BAR ... ── */` comment block in the `<style>` section (currently defines `#lb-you-bar` with `display:none` and `position:fixed`).

Replace the entire `#lb-you-bar` rule with:

```css
/* ── YOU-BAR: fixed bottom dock shown when your row scrolls out of view ── */
#lb-you-bar {
  display: none;
  position: fixed;
  bottom: 0;
  left: 0; right: 0;
  height: auto;
  min-height: 54px;
  background: rgba(14,165,233,.94);
  backdrop-filter: blur(8px);
  z-index: 50;
  align-items: center;
  padding: 10px 16px max(12px, env(safe-area-inset-bottom));
  gap: 10px;
  box-shadow: 0 -4px 20px rgba(14,165,233,.4);
  border-top: 2px solid rgba(14,165,233,.7);
  border-radius: 16px 16px 0 0;
  animation: lbFUp .25s ease both;
}
#lb-you-bar.visible { display: flex; }
```

The `.lb-yb-av`, `.lb-yb-name`, `.lb-yb-rank`, `.lb-yb-pts` sub-rules are unchanged — leave them in place.

- [ ] **Step 2: Add a jump-to-row button inside the you-bar HTML**

Find the `<div id="lb-you-bar" ...>` element in the template (around line 567). It currently contains avatar, name, rank, and points. Add a jump button as the last child before the closing `</div>`:

```html
<button onclick="document.querySelector('.lb-row-data.you')?.scrollIntoView({behavior:'smooth',block:'center'})"
        style="margin-left:auto;background:rgba(255,255,255,.2);border:1px solid rgba(255,255,255,.35);border-radius:8px;padding:5px 10px;color:white;font-size:12px;font-weight:700;cursor:pointer;white-space:nowrap;flex-shrink:0">
  ↑ Find me
</button>
```

- [ ] **Step 3: Remove the barTop positioning from JS**

In the `DOMContentLoaded` JS block, find the `refreshYouBar()` function:

```js
function refreshYouBar() {
  const outerRect = lbOuter.getBoundingClientRect();
  const hdrH = stickyHdr ? stickyHdr.offsetHeight : 100;
  // Bar sits just below the sticky table header
  const barTop = Math.max(outerRect.top + hdrH, 64 + hdrH);
  // Hide if table has scrolled out of view entirely
  const tableInView = outerRect.bottom > barTop + 40;
  youBar.style.top = barTop + 'px';
  youBar.classList.toggle('visible', youRowHidden && tableInView);
}
```

Replace it with:

```js
function refreshYouBar() {
  const outerRect = lbOuter.getBoundingClientRect();
  // Hide bar if the entire table has scrolled out of view
  const tableInView = outerRect.bottom > 100;
  youBar.classList.toggle('visible', youRowHidden && tableInView);
}
```

- [ ] **Step 4: Verify visually**

Reload the leaderboard. Scroll down past your row — the blue bar should appear at the **bottom** of the screen (not the top). Clicking "↑ Find me" should smoothly scroll the page to your row. The 1st-place row should now be fully visible at the top of the leaderboard.

- [ ] **Step 5: Run tests**

```bash
./mvnw test -q
```

Expected: BUILD SUCCESS. The existing controller tests don't test the you-bar but confirm nothing is broken.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/community/leaderboard.html
git commit -m "feat: move you-bar to bottom dock to stop occluding 1st-place row"
```

---

## Self-Review Notes

- Task 1 (controller) must run before Tasks 2-4 since `today` is used in the template.
- Task 3 defines `const today` in JS — Task 4 reuses it; both steps are in the same `<script>` block. The ordering note in Task 4 Step 1 ensures no reference-before-assignment.
- Task 5's `refreshYouBar` no longer references `stickyHdr` for `hdrH` calculation; that variable is still declared above the function (no removal needed — just unused, which is harmless).
- If the app is not running with real match data for today's date, Tasks 3 and 4 visual checks will show the fallback behavior (no highlight, scroll to current phase) — that is the correct fallback and passes.
