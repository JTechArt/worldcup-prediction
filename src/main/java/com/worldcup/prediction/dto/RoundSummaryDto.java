package com.worldcup.prediction.dto;

public class RoundSummaryDto {
    private String roundLabel;
    private String displayLabel;
    private String status;        // "PAST", "OPEN", "FUTURE"
    private int totalMatches;
    private int predictedCount;
    private int pointsEarned;

    public String getRoundLabel() { return roundLabel; }
    public void setRoundLabel(String roundLabel) { this.roundLabel = roundLabel; }

    public String getDisplayLabel() { return displayLabel; }
    public void setDisplayLabel(String displayLabel) { this.displayLabel = displayLabel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalMatches() { return totalMatches; }
    public void setTotalMatches(int totalMatches) { this.totalMatches = totalMatches; }

    public int getPredictedCount() { return predictedCount; }
    public void setPredictedCount(int predictedCount) { this.predictedCount = predictedCount; }

    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }
}
