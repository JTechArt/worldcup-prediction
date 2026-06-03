package com.worldcup.prediction.integration.football.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiScorersResponseDto(Integer count, List<FootballApiScorerEntryDto> scorers) {}
