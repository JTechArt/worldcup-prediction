package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiTeamDto(Long id, String name, String shortName, String tla, String crest) {}
