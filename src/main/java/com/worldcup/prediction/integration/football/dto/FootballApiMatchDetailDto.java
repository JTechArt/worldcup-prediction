package com.worldcup.prediction.integration.football.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballApiMatchDetailDto(Long id, String utcDate, String status,
    Integer matchday, String stage, String group,
    FootballApiTeamDto homeTeam, FootballApiTeamDto awayTeam,
    FootballApiScoreDto score, List<FootballApiGoalDto> goals) {}
