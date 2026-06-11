package com.worldcup.prediction.dto;

public class PastMatchResultDto {
    private Long matchId;
    private String homeTeamName;
    private String homeTeamCode;
    private String awayTeamName;
    private String awayTeamCode;
    private String kickoffDisplay;

    private int actualHome;
    private int actualAway;

    private Integer predictedHome;
    private Integer predictedAway;

    /** "EXACT", "DRAW", "WINNER", "WRONG", "NOT_PREDICTED", "PENDING" */
    private String outcome;
    private int pointsEarned;
    private boolean resultsAvailable;

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }

    public String getHomeTeamName() { return homeTeamName; }
    public void setHomeTeamName(String homeTeamName) { this.homeTeamName = homeTeamName; }

    public String getHomeTeamCode() { return homeTeamCode; }
    public void setHomeTeamCode(String homeTeamCode) { this.homeTeamCode = homeTeamCode; }

    public String getAwayTeamName() { return awayTeamName; }
    public void setAwayTeamName(String awayTeamName) { this.awayTeamName = awayTeamName; }

    public String getAwayTeamCode() { return awayTeamCode; }
    public void setAwayTeamCode(String awayTeamCode) { this.awayTeamCode = awayTeamCode; }

    public String getKickoffDisplay() { return kickoffDisplay; }
    public void setKickoffDisplay(String kickoffDisplay) { this.kickoffDisplay = kickoffDisplay; }

    public int getActualHome() { return actualHome; }
    public void setActualHome(int actualHome) { this.actualHome = actualHome; }

    public int getActualAway() { return actualAway; }
    public void setActualAway(int actualAway) { this.actualAway = actualAway; }

    public Integer getPredictedHome() { return predictedHome; }
    public void setPredictedHome(Integer predictedHome) { this.predictedHome = predictedHome; }

    public Integer getPredictedAway() { return predictedAway; }
    public void setPredictedAway(Integer predictedAway) { this.predictedAway = predictedAway; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }

    public boolean isResultsAvailable() { return resultsAvailable; }
    public void setResultsAvailable(boolean resultsAvailable) { this.resultsAvailable = resultsAvailable; }
}
