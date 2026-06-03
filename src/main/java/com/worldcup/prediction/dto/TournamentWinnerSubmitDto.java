package com.worldcup.prediction.dto;

import jakarta.validation.constraints.NotNull;

public class TournamentWinnerSubmitDto {

    @NotNull
    private Long teamId;

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
}
