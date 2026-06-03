package com.worldcup.prediction.integration.football.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiStandingEntryDto(Integer position, FootballApiTeamDto team,
    Integer playedGames, Integer won, Integer draw, Integer lost, Integer points,
    Integer goalsFor, Integer goalsAgainst, Integer goalDifference) {}
