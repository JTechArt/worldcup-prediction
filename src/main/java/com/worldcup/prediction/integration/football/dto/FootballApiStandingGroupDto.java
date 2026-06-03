package com.worldcup.prediction.integration.football.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiStandingGroupDto(String stage, String type, String group, List<FootballApiStandingEntryDto> table) {}
