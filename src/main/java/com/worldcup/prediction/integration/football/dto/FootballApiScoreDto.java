package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiScoreDto(
    String winner,
    String duration,
    FootballApiFullTimeDto fullTime,
    FootballApiFullTimeDto halfTime,
    FootballApiFullTimeDto regularTime
) {}
