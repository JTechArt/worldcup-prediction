package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SchedulerLogRepository extends JpaRepository<SchedulerLog, Long> {

    Optional<SchedulerLog> findFirstByJobNameOrderByStartedAtDesc(String jobName);

    List<SchedulerLog> findTop200ByOrderByStartedAtDesc();

    List<SchedulerLog> findTop200ByJobNameOrderByStartedAtDesc(String jobName);

    List<SchedulerLog> findTop200ByStatusOrderByStartedAtDesc(SchedulerJobStatus status);

    List<SchedulerLog> findTop200ByJobNameAndStatusOrderByStartedAtDesc(String jobName, SchedulerJobStatus status);

    void deleteByStartedAtBefore(LocalDateTime cutoff);
}
