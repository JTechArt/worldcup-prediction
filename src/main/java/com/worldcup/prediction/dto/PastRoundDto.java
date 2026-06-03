package com.worldcup.prediction.dto;

import java.util.List;

public class PastRoundDto {
    private String roundLabel;
    private String displayLabel;
    private int totalPoints;
    private List<PastMatchResultDto> matches;

    public String getRoundLabel() { return roundLabel; }
    public void setRoundLabel(String roundLabel) { this.roundLabel = roundLabel; }

    public String getDisplayLabel() { return displayLabel; }
    public void setDisplayLabel(String displayLabel) { this.displayLabel = displayLabel; }

    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }

    public List<PastMatchResultDto> getMatches() { return matches; }
    public void setMatches(List<PastMatchResultDto> matches) { this.matches = matches; }
}
