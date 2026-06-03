package com.worldcup.prediction.integration.football.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiPlayerDto(Long id, String name, String position, String dateOfBirth, String nationality, Integer shirtNumber) {}
