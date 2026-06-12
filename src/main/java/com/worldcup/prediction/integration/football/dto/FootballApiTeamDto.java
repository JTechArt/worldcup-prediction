package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiTeamDto(Long id, String name, String shortName, String tla, String crest,
    String formation, List<FootballApiInlinePlayerDto> lineup, List<FootballApiInlinePlayerDto> bench) {}
