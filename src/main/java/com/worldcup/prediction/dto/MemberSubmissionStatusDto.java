package com.worldcup.prediction.dto;

import java.time.LocalDateTime;

public record MemberSubmissionStatusDto(
        Long userId,
        String displayName,
        String email,
        String avatarUrl,
        boolean submitted,
        LocalDateTime submittedAt
) {}
