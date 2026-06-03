package com.worldcup.prediction.integration.football.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiTeamsResponseDto(Integer count, List<FootballApiTeamWithSquadDto> teams) {}
