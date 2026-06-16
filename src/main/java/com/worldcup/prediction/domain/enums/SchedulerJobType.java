package com.worldcup.prediction.domain.enums;

public enum SchedulerJobType {
    MATCH_RESULT    ("Match Results",       "Every 5 min",    true,  300_000L,     null,              "app.football.api.enabled"),
    LINEUP_SYNC     ("Lineup Sync",         "Every 30 min",   true,  1_800_000L,   null,              "app.lineup-sync.enabled"),
    STANDING_SYNC   ("Standings",           "Every 6 hours",  false, 0L,           "0 0 */6 * * *",   "app.football.api.enabled"),
    SCORERS_SYNC    ("Top Scorers",         "Daily at 02:00", false, 0L,           "0 0 2 * * *",     "app.football.api.enabled"),
    NOTIF_WINDOW_OPEN("Window Open Notif",    "Every 5 min",    true,  300_000L,     null,              "app.notification.enabled"),
    NOTIF_DEADLINE  ("Deadline Reminder",    "Every 15 min",   true,  900_000L,     null,              "app.notification.enabled"),
    NOTIF_DIGEST    ("Leaderboard Digest",   "Every 30 min",   true,  1_800_000L,   null,              "app.notification.enabled"),
    PREDICTION_WINDOW_ACTIVATE("Window Activate", "Every 5 min", true, 300_000L,   null,              "app.prediction-window.enabled"),
    PREDICTION_WINDOW_CLOSE   ("Window Close",    "Every 5 min", true, 300_000L,   null,              "app.prediction-window.enabled");

    private final String displayName;
    private final String scheduleDescription;
    private final boolean fixedDelay;
    private final long delayMs;
    private final String cronExpression;
    private final String enabledProperty;

    SchedulerJobType(String displayName, String scheduleDescription, boolean fixedDelay,
                     long delayMs, String cronExpression, String enabledProperty) {
        this.displayName         = displayName;
        this.scheduleDescription = scheduleDescription;
        this.fixedDelay          = fixedDelay;
        this.delayMs             = delayMs;
        this.cronExpression      = cronExpression;
        this.enabledProperty     = enabledProperty;
    }

    public String getDisplayName()          { return displayName; }
    public String getScheduleDescription()  { return scheduleDescription; }
    public boolean isFixedDelay()           { return fixedDelay; }
    public long getDelayMs()                { return delayMs; }
    public String getCronExpression()       { return cronExpression; }
    public String getEnabledProperty()      { return enabledProperty; }
}
