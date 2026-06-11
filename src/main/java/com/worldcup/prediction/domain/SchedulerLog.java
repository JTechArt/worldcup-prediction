package com.worldcup.prediction.domain;

import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "scheduler_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SchedulerLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SchedulerJobStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "items_processed", nullable = false)
    @Builder.Default
    private int itemsProcessed = 0;

    @Column(length = 500)
    private String message;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    @Transient
    public String getDurationFormatted() {
        if (startedAt == null || finishedAt == null) return "—";
        long seconds = Duration.between(startedAt, finishedAt).getSeconds();
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    @Transient
    public String getRowCssClass() {
        if (status == null) return "";
        return switch (status) {
            case FAILED      -> "bg-red-50";
            case IN_PROGRESS -> "bg-blue-50";
            case SUCCESS     -> itemsProcessed > 0 ? "bg-green-50" : "bg-gray-50";
            case SKIPPED     -> "bg-gray-50";
        };
    }
}
