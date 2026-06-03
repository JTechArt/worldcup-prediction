package com.worldcup.prediction.integration.football.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiTeamWithSquadDto(Long id, String name, String shortName, String tla, List<FootballApiPlayerDto> squad) {}
