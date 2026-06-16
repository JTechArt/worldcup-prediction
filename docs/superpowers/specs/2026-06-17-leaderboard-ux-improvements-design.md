# Leaderboard UX Improvements — Design

**Date**: 2026-06-17  
**Scope**: Community leaderboard (`community/leaderboard.html` + `CommunityLeaderboardController.java`)  
**Type**: Frontend-only (one line backend change)

---

## 1. You-bar — Move to Bottom Dock

### Problem
`#lb-you-bar` is currently `position: fixed; top: <table-header-bottom>px`, which places it directly over the 1st-place row. If the current user is near the top of the leaderboard, their row is hidden by the bar.

### Solution
Reposition the bar to the **bottom of the screen** (`position: fixed; bottom: 0; left: 0; right: 0`).

**CSS changes:**
- Remove dynamic `top` positioning entirely
- Use `bottom: 0` with `padding-bottom: max(12px, env(safe-area-inset-bottom))` for mobile notch safety
- Add a rounded top edge (`border-radius: 16px 16px 0 0`) so it feels like a bottom sheet
- Add a subtle upward box-shadow

**Add a "Jump" button:**
- Append a small `↑` arrow button inside `#lb-you-bar`
- On click: smooth-scroll to `.lb-row-data.you` using `scrollIntoView({ behavior: 'smooth', block: 'center' })`

**JS changes:**
- Remove the `refreshYouBar()` function's `barTop` calculation and `style.top` assignment
- Keep the `IntersectionObserver` logic (shows bar when your row is off-screen, hides when visible)

---

## 2. Auto-scroll to Today's Matches on Load

### Problem
On load, the horizontal mid-panel scrolls to the current round phase header. It does not position today's specific match columns in view.

### Solution
Find the first match column for today's date and scroll to it instead (falling back to current phase if no match today).

**Template change (`lb-game-col-hd`):**
Add `th:data-date="${#temporals.format(m.kickoffTime, 'yyyy-MM-dd')}"` to each group match column header `div.lb-game-col-hd`.

**Controller change (`CommunityLeaderboardController.java`):**
Add one line before `model.addAttribute`:
```java
model.addAttribute("today", java.time.LocalDate.now().toString());
```

**JS change:**
Replace the current `scrollToPhase(currentId, false)` initial scroll with:
```js
const today = /*[[${today}]]*/ '';
const todayCol = document.querySelector('.lb-game-col-hd[data-date="' + today + '"]');
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

---

## 3. Highlight Today's Matches

### Problem
No visual distinction between today's match columns and past/future columns.

### Solution
Apply a `today` CSS class to matching `lb-game-col-hd` elements via JS at `DOMContentLoaded`.

**CSS class (new):**
```css
.lb-game-col-hd.today {
  background: rgba(255, 214, 0, .15);
  border-top: 2px solid rgba(255, 214, 0, .65);
  border-bottom-color: rgba(255, 214, 0, .65);
}
.lb-game-col-hd.today .lb-ghd {
  color: #c8900a;
  font-weight: 800;
}
```

Add a "TODAY" pill badge inside `lb-ghd` when JS detects a today column:
```js
ghd.innerHTML = '<span class="lb-today-pill">TODAY</span>' + ghd.textContent;
```

```css
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

---

## Files Changed

| File | Change type |
|------|-------------|
| `src/main/resources/templates/community/leaderboard.html` | CSS + HTML + JS |
| `src/main/java/.../CommunityLeaderboardController.java` | Add `today` model attribute (1 line) |

---

## Out of Scope

- Global leaderboard (`leaderboard.html`) — no you-bar, no today highlight; not touched.
- Vertical page scroll to today's matches — not requested; the horizontal scroll within the mid-panel is sufficient.
- Backend query changes — none needed.
