package com.worldcup.prediction.domain.enums;

/** Controls which result is used when scoring knockout-stage predictions. */
public enum KnockoutScoringMode {
    /** Score against the 90-minute result only (ignores extra time and penalties). */
    NINETY_MINUTES,
    /** Score against the full result including extra time (playoff winner bonus disabled). */
    FULL_TIME
}
