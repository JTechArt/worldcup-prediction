package com.worldcup.prediction.domain.enums;

/**
 * Tournament stages in chronological order.
 * Each stage carries display metadata for the leaderboard phase header row.
 */
public enum MatchStage {

    GROUP       ("GS",    "Group Stage",   "phase-gs",    1),
    ROUND_OF_32 ("R32",   "Round of 32",   "phase-r32",   2),
    ROUND_OF_16 ("R16",   "Round of 16",   "phase-r16",   3),
    QUARTER_FINAL("QF",   "Quarter-Finals","phase-qf",    4),
    SEMI_FINAL  ("SF",    "Semi-Finals",   "phase-sf",    5),
    THIRD_PLACE ("3rd",   "Third Place",   "phase-3rd",   6),
    FINAL       ("Final", "Final",         "phase-final", 7);

    /** Short label for phase header button (e.g. "R16"). */
    private final String shortLabel;

    /** Long label for tooltip / full display. */
    private final String displayName;

    /** CSS class for phase header gradient + shimmer styling. */
    private final String cssClass;

    /** Display order (1-based). */
    private final int order;

    MatchStage(String shortLabel, String displayName, String cssClass, int order) {
        this.shortLabel  = shortLabel;
        this.displayName = displayName;
        this.cssClass    = cssClass;
        this.order       = order;
    }

    public String getShortLabel()  { return shortLabel; }
    public String getDisplayName() { return displayName; }
    public String getCssClass()    { return cssClass; }
    public int    getOrder()       { return order; }
}
