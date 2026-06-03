package com.worldcup.prediction.integration.football.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiLineupPlayerDto(FootballApiGoalPersonDto player, String position, Integer shirtNumber) {}
