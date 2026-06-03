package com.worldcup.prediction.dto;

/**
 * Immutable snapshot of one participant's leaderboard standing.
 * Computed fresh per request by LeaderboardService.
 */
public class LeaderboardEntryDto {

    private final int rank;
    private final Long userId;
    private final String displayName;
    private final String avatarUrl;

    /**
     * ISO 2-letter flag code (lowercase) of the team predicted as tournament winner.
     * Null if user has not made a tournament winner prediction.
     */
    private final String predictedWinnerFlagCode;

    private final int totalPoints;
    private final int exactCount;
    private final int correctWinnerCount;
    private final int drawCount;

    /** True if the user's tournament winner prediction was scored (+10 awarded). */
    private final boolean tournamentWinnerCorrect;

    /**
     * Rank change vs previous snapshot.
     * Always 0 in v1 (no snapshot storage).
     */
    private final int rankChange;

    public LeaderboardEntryDto(
            int rank,
            Long userId,
            String displayName,
            String avatarUrl,
            String predictedWinnerFlagCode,
            int totalPoints,
            int exactCount,
            int correctWinnerCount,
            int drawCount,
            boolean tournamentWinnerCorrect,
            int rankChange) {
        this.rank = rank;
        this.userId = userId;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.predictedWinnerFlagCode = predictedWinnerFlagCode;
        this.totalPoints = totalPoints;
        this.exactCount = exactCount;
        this.correctWinnerCount = correctWinnerCount;
        this.drawCount = drawCount;
        this.tournamentWinnerCorrect = tournamentWinnerCorrect;
        this.rankChange = rankChange;
    }

    public int getRank() { return rank; }
    public Long getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getPredictedWinnerFlagCode() { return predictedWinnerFlagCode; }
    public int getTotalPoints() { return totalPoints; }
    public int getExactCount() { return exactCount; }
    public int getCorrectWinnerCount() { return correctWinnerCount; }
    public int getDrawCount() { return drawCount; }
    public boolean isTournamentWinnerCorrect() { return tournamentWinnerCorrect; }
    public int getRankChange() { return rankChange; }

    /** Initials for avatar fallback (max 2 chars). */
    public String getInitials() {
        if (displayName == null || displayName.isBlank()) return "?";
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (String.valueOf(parts[0].charAt(0)) + String.valueOf(parts[parts.length - 1].charAt(0))).toUpperCase();
    }

    /** CSS class for rank row highlighting: r1/r2/r3/rn. */
    public String getRankCssClass() {
        return switch (rank) {
            case 1 -> "rank-1";
            case 2 -> "rank-2";
            case 3 -> "rank-3";
            default -> "rank-other";
        };
    }

    /** Circle-flags CDN URL. Empty string if no winner prediction. */
    public String getFlagUrl() {
        if (predictedWinnerFlagCode == null || predictedWinnerFlagCode.isBlank()) return "";
        return "https://raw.githubusercontent.com/HatScripts/circle-flags/gh-pages/flags/"
                + predictedWinnerFlagCode.toLowerCase() + ".svg";
    }
}
