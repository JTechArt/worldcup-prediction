package com.worldcup.prediction.dto;

import java.time.LocalDateTime;

public class FixtureViewDto {

    private Long id;
    private String phase;
    private String groupLabel;
    private LocalDateTime kickoff;
    private String venue;
    private String city;

    private Long homeTeamId;
    private String homeTeamName;
    private String homeTeamCode;
    private Long awayTeamId;
    private String awayTeamName;
    private String awayTeamCode;

    private Integer homeScore;
    private Integer awayScore;

    private String status;

    public FixtureViewDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public String getGroupLabel() { return groupLabel; }
    public void setGroupLabel(String groupLabel) { this.groupLabel = groupLabel; }

    public LocalDateTime getKickoff() { return kickoff; }
    public void setKickoff(LocalDateTime kickoff) { this.kickoff = kickoff; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Long getHomeTeamId() { return homeTeamId; }
    public void setHomeTeamId(Long homeTeamId) { this.homeTeamId = homeTeamId; }

    public String getHomeTeamName() { return homeTeamName; }
    public void setHomeTeamName(String homeTeamName) { this.homeTeamName = homeTeamName; }

    public String getHomeTeamCode() { return homeTeamCode; }
    public void setHomeTeamCode(String homeTeamCode) { this.homeTeamCode = homeTeamCode; }

    public Long getAwayTeamId() { return awayTeamId; }
    public void setAwayTeamId(Long awayTeamId) { this.awayTeamId = awayTeamId; }

    public String getAwayTeamName() { return awayTeamName; }
    public void setAwayTeamName(String awayTeamName) { this.awayTeamName = awayTeamName; }

    public String getAwayTeamCode() { return awayTeamCode; }
    public void setAwayTeamCode(String awayTeamCode) { this.awayTeamCode = awayTeamCode; }

    public Integer getHomeScore() { return homeScore; }
    public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }

    public Integer getAwayScore() { return awayScore; }
    public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isCompleted() { return "COMPLETED".equals(status); }

    public boolean isToday() {
        if (kickoff == null) return false;
        return kickoff.toLocalDate().equals(java.time.LocalDate.now());
    }

    public boolean isGroupStage() { return groupLabel != null; }
}
