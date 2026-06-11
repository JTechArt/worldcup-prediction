package com.worldcup.prediction.dto;

import com.worldcup.prediction.domain.enums.SchedulerJobStatus;

public record SchedulerCardDto(
        String jobName,
        String displayName,
        String scheduleDescription,
        boolean enabled,
        String nextRun,
        SchedulerJobStatus lastStatus,
        String lastFinishedAt
) {}
