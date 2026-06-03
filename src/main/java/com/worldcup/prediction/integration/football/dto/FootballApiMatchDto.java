package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiMatchDto(
    Long id,
    String utcDate,
    String status,
    Integer matchday,
    String stage,
    String group,
    FootballApiTeamDto homeTeam,
    FootballApiTeamDto awayTeam,
    FootballApiScoreDto score
) {}
