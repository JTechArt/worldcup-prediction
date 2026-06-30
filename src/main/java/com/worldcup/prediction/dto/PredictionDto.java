package com.worldcup.prediction.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PredictionDto {

    @NotNull
    private Long matchId;

    @NotNull
    @Min(0)
    private Integer homeScore;

    @NotNull
    @Min(0)
    private Integer awayScore;

    private String playoffWinner; // "HOME", "AWAY", or null

    public PredictionDto() {}

    public PredictionDto(Long matchId, Integer homeScore, Integer awayScore) {
        this.matchId = matchId;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
    }

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }

    public Integer getHomeScore() { return homeScore; }
    public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }

    public Integer getAwayScore() { return awayScore; }
    public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }

    public String getPlayoffWinner() { return playoffWinner; }
    public void setPlayoffWinner(String playoffWinner) { this.playoffWinner = playoffWinner; }
}
