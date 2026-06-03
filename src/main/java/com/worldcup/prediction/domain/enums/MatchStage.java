package com.worldcup.prediction.domain.enums;

public enum MatchStage {
    GROUP("Group Stage"),
    ROUND_OF_32("Round of 32"),
    ROUND_OF_16("Round of 16"),
    QUARTER_FINAL("Quarter Final"),
    SEMI_FINAL("Semi Final"),
    THIRD_PLACE("Third Place"),
    FINAL("Final");

    private final String displayName;

    MatchStage(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
