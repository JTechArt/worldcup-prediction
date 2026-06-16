# Exact Score Heroes — Team Name UI Fix

**Date:** 2026-06-17  
**Scope:** `community/home.html` · `DailyExactPredictorDto` · `DailyExactPredictorService`

## Problem

In the EXACT SCORE HEROES section, match rows show team flags and full team names. Two issues:

1. **Web view:** Long names (e.g. "Bosnia and Herzegovina") overflow the card because `.hero-match-team` grid cells lack `min-width: 0`, so `1fr` columns don't constrain text content despite `text-overflow: ellipsis` being set.
2. **Mobile view:** Team names take up too much horizontal space in the compact 320px card; names should be hidden and replaced with a always-visible 3-letter FIFA country code (TLA) below each flag.

## Design

### Web view — truncation fix

Add `min-width: 0` to `.hero-match-team` in the existing CSS. This allows the `1fr` grid columns to actually constrain their children, so the existing `white-space: nowrap; overflow: hidden; text-overflow: ellipsis` on `.hero-match-name` will kick in correctly.

No layout change — names still display inline next to flags, long names just show "…".

### Mobile view — flag + TLA (always visible)

At `≤768px`:

- Hide `.hero-match-name` via media query.
- Wrap each flag in a `.hero-flag-wrap` flex column that shows the flag on top and a `.hero-match-tla` span below it.
- The TLA is always visible — no tap/click interaction needed.

This requires adding `homeTeamFifaCode` and `awayTeamFifaCode` to the DTO so the template has the correct FIFA 3-letter codes (e.g. "KSA" for Saudi Arabia, not a naive substring of the name).

### Data layer

**`DailyExactPredictorDto.ExactMatchDto`** — add two fields:
```
private String homeTeamFifaCode;
private String awayTeamFifaCode;
```

**`DailyExactPredictorService`** — populate from `m.getHomeTeam().getFifaCode()` and `m.getAwayTeam().getFifaCode()` (null-safe, same pattern as existing `flagCode` population).

**Template** — pass the TLA into each flag wrapper via `th:text="${match.homeTeamFifaCode}"` on the `.hero-match-tla` span.

## Files Changed

| File | Change |
|------|--------|
| `DailyExactPredictorDto.java` | Add `homeTeamFifaCode`, `awayTeamFifaCode` to inner `ExactMatchDto` |
| `DailyExactPredictorService.java` | Populate new fields from `team.getFifaCode()` |
| `community/home.html` (CSS) | Add `min-width: 0` to `.hero-match-team` |
| `community/home.html` (CSS) | Add `.hero-flag-wrap`, `.hero-match-tla` styles + media query to hide `.hero-match-name` on mobile |
| `community/home.html` (HTML) | Wrap each flag in `.hero-flag-wrap` div with `.hero-match-tla` span below |

## Out of Scope

- No changes to other prediction pages or leaderboard views.
- No tooltip/popover interaction — the TLA is always visible on mobile.
- No changes to the Team entity or database.
