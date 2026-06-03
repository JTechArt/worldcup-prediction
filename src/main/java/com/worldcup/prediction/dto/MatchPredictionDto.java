package com.worldcup.prediction.dto;

/**
 * One match in an open round, plus the user's existing prediction (if any).
 * Datetime fields are pre-formatted as ISO strings for Alpine.js JS Date parsing.
 */
public class MatchPredictionDto {
    private Long matchId;
    private String roundLabel;
    private String stage;
    private String group;
    private String kickoffIso;
    private String lockTimeIso;
    private boolean locked;

    private String homeTeamName;
    private String homeTeamCode;
    private String awayTeamName;
    private String awayTeamCode;
    private String venue;

    private Integer predictedHome;
    private Integer predictedAway;
    private boolean predictionSaved;

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }

    public String getRoundLabel() { return roundLabel; }
    public void setRoundLabel(String roundLabel) { this.roundLabel = roundLabel; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getKickoffIso() { return kickoffIso; }
    public void setKickoffIso(String kickoffIso) { this.kickoffIso = kickoffIso; }

    public String getLockTimeIso() { return lockTimeIso; }
    public void setLockTimeIso(String lockTimeIso) { this.lockTimeIso = lockTimeIso; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public String getHomeTeamName() { return homeTeamName; }
    public void setHomeTeamName(String homeTeamName) { this.homeTeamName = homeTeamName; }

    public String getHomeTeamCode() { return homeTeamCode; }
    public void setHomeTeamCode(String homeTeamCode) { this.homeTeamCode = homeTeamCode; }

    public String getAwayTeamName() { return awayTeamName; }
    public void setAwayTeamName(String awayTeamName) { this.awayTeamName = awayTeamName; }

    public String getAwayTeamCode() { return awayTeamCode; }
    public void setAwayTeamCode(String awayTeamCode) { this.awayTeamCode = awayTeamCode; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public Integer getPredictedHome() { return predictedHome; }
    public void setPredictedHome(Integer predictedHome) { this.predictedHome = predictedHome; }

    public Integer getPredictedAway() { return predictedAway; }
    public void setPredictedAway(Integer predictedAway) { this.predictedAway = predictedAway; }

    public boolean isPredictionSaved() { return predictionSaved; }
    public void setPredictionSaved(boolean predictionSaved) { this.predictionSaved = predictionSaved; }
}
