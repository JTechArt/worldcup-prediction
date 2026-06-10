package com.worldcup.prediction.dto;

public record WindowBannerDto(
        String roundLabel,
        String closesAtIso,   // ISO-8601 with offset, null if FORCE_OPEN with no end time
        boolean submitted
) {}
