package com.worldcup.prediction.integration.football.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiGoalDto(Integer minute, String type, FootballApiTeamDto team, FootballApiGoalPersonDto scorer) {}
