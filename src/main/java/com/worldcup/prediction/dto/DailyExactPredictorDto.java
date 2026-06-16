package com.worldcup.prediction.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyExactPredictorDto {

    private Long userId;
    private String displayName;
    private String avatarUrl;
    private int exactCount;
    private List<ExactMatchDto> exactMatches;

    public String getInitials() {
        if (displayName == null || displayName.isBlank()) return "?";
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (String.valueOf(parts[0].charAt(0)) + String.valueOf(parts[parts.length - 1].charAt(0))).toUpperCase();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExactMatchDto {
        private String homeTeamName;
        private String awayTeamName;
        private String homeTeamFlagCode;
        private String awayTeamFlagCode;
        private String homeTeamFifaCode;
        private String awayTeamFifaCode;
        private int homeScore;
        private int awayScore;
    }
}
