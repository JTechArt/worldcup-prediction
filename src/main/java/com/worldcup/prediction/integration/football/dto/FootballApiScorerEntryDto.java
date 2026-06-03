package com.worldcup.prediction.integration.football.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiScorerEntryDto(FootballApiGoalPersonDto player, FootballApiTeamDto team, Integer goals) {}
