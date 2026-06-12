package com.worldcup.prediction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * All-or-nothing round submission. All predictions for one round must be submitted together.
 */
public class PredictionSubmitDto {

    @NotBlank
    private String roundLabel;

    private Long windowId;  // non-null in DAILY mode, null in ROUND mode

    @NotNull
    @Size(min = 1)
    @Valid
    private List<SinglePrediction> predictions;

    public String getRoundLabel() { return roundLabel; }
    public void setRoundLabel(String roundLabel) { this.roundLabel = roundLabel; }

    public Long getWindowId() { return windowId; }
    public void setWindowId(Long windowId) { this.windowId = windowId; }

    public List<SinglePrediction> getPredictions() { return predictions; }
    public void setPredictions(List<SinglePrediction> predictions) { this.predictions = predictions; }

    public static class SinglePrediction {
        @NotNull
        private Long matchId;

        @NotNull
        @Min(0)
        private Integer homeScore;

        @NotNull
        @Min(0)
        private Integer awayScore;

        public Long getMatchId() { return matchId; }
        public void setMatchId(Long matchId) { this.matchId = matchId; }

        public Integer getHomeScore() { return homeScore; }
        public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }

        public Integer getAwayScore() { return awayScore; }
        public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }
    }
}
