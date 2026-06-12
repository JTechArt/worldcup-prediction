package com.worldcup.prediction.integration.football.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiInlinePlayerDto(Long id, String name, String position, Integer shirtNumber) {}
