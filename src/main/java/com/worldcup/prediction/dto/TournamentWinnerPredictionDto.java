package com.worldcup.prediction.dto;

import jakarta.validation.constraints.NotBlank;

public class TournamentWinnerPredictionDto {

    @NotBlank
    private String flagCode; // lowercase ISO 3166-1 alpha-2, e.g. "br", "fr", "us"

    public TournamentWinnerPredictionDto() {}

    public TournamentWinnerPredictionDto(String flagCode) {
        this.flagCode = flagCode;
    }

    public String getFlagCode() { return flagCode; }
    public void setFlagCode(String flagCode) { this.flagCode = flagCode; }
}
