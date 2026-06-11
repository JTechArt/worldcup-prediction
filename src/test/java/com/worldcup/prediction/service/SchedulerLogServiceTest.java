package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.SchedulerLog;
import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.dto.SchedulerCardDto;
import com.worldcup.prediction.repository.SchedulerLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerLogServiceTest {

    @Mock SchedulerLogRepository repository;
    @Mock Environment environment;
    @InjectMocks SchedulerLogService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "appTimezone", "Asia/Yerevan");
    }

    @Test
    void start_persistsInProgressRow() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.start("MATCH_RESULT");
        ArgumentCaptor<SchedulerLog> captor = ArgumentCaptor.forClass(SchedulerLog.class);
        verify(repository).save(captor.capture());
        SchedulerLog saved = captor.getValue();
        assertThat(saved.getJobName()).isEqualTo("MATCH_RESULT");
        assertThat(saved.getStatus()).isEqualTo(SchedulerJobStatus.IN_PROGRESS);
        assertThat(saved.getStartedAt()).isNotNull();
    }

    @Test
    void complete_updatesStatusAndFinishedAt() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SchedulerLog log = SchedulerLog.builder().id(1L).jobName("LINEUP_SYNC")
                .status(SchedulerJobStatus.IN_PROGRESS).startedAt(LocalDateTime.now()).build();
        service.complete(log, SchedulerJobStatus.SUCCESS, 3, "3 lineups fetched");
        assertThat(log.getStatus()).isEqualTo(SchedulerJobStatus.SUCCESS);
        assertThat(log.getFinishedAt()).isNotNull();
        assertThat(log.getItemsProcessed()).isEqualTo(3);
        assertThat(log.getMessage()).isEqualTo("3 lineups fetched");
        verify(repository).save(log);
    }

    @Test
    void fail_marksFailedWithErrorDetail() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SchedulerLog log = SchedulerLog.builder().id(2L).jobName("STANDING_SYNC")
                .status(SchedulerJobStatus.IN_PROGRESS).startedAt(LocalDateTime.now()).build();
        service.fail(log, "timeout", "java.net.SocketTimeoutException...");
        assertThat(log.getStatus()).isEqualTo(SchedulerJobStatus.FAILED);
        assertThat(log.getMessage()).isEqualTo("timeout");
        assertThat(log.getErrorDetail()).isEqualTo("java.net.SocketTimeoutException...");
        verify(repository).save(log);
    }

    @Test
    void fail_withNullMessage_usesUnknownError() {
        SchedulerLog log = SchedulerLog.builder().id(3L).jobName("SCORERS_SYNC")
                .status(SchedulerJobStatus.IN_PROGRESS).startedAt(LocalDateTime.now()).build();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.fail(log, null, null);
        assertThat(log.getMessage()).isEqualTo("Unknown error");
        assertThat(log.getStatus()).isEqualTo(SchedulerJobStatus.FAILED);
    }

    @Test
    void cleanup_deletesRowsOlderThan7Days() {
        service.cleanup();
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByStartedAtBefore(captor.capture());
        assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusDays(6));
    }

    @Test
    void findAll_noFilters_callsTop200() {
        when(repository.findTop200ByOrderByStartedAtDesc()).thenReturn(List.of());
        service.findAll(null, null);
        verify(repository).findTop200ByOrderByStartedAtDesc();
    }

    @Test
    void findAll_jobFilter_callsJobQuery() {
        when(repository.findTop200ByJobNameOrderByStartedAtDesc("MATCH_RESULT")).thenReturn(List.of());
        service.findAll("MATCH_RESULT", null);
        verify(repository).findTop200ByJobNameOrderByStartedAtDesc("MATCH_RESULT");
    }

    @Test
    void findAll_statusFilter_callsStatusQuery() {
        when(repository.findTop200ByStatusOrderByStartedAtDesc(SchedulerJobStatus.FAILED)).thenReturn(List.of());
        service.findAll(null, SchedulerJobStatus.FAILED);
        verify(repository).findTop200ByStatusOrderByStartedAtDesc(SchedulerJobStatus.FAILED);
    }

    @Test
    void findAll_bothFilters_callsCombinedQuery() {
        when(repository.findTop200ByJobNameAndStatusOrderByStartedAtDesc("LINEUP_SYNC", SchedulerJobStatus.SUCCESS))
                .thenReturn(List.of());
        service.findAll("LINEUP_SYNC", SchedulerJobStatus.SUCCESS);
        verify(repository).findTop200ByJobNameAndStatusOrderByStartedAtDesc("LINEUP_SYNC", SchedulerJobStatus.SUCCESS);
    }

    @Test
    void buildCards_returnsOneCardPerJobType() {
        when(environment.getProperty(anyString(), eq(Boolean.class), eq(false))).thenReturn(false);
        when(repository.findFirstByJobNameOrderByStartedAtDesc(anyString())).thenReturn(Optional.empty());
        List<SchedulerCardDto> cards = service.buildCards();
        assertThat(cards).hasSize(SchedulerJobType.values().length);
        assertThat(cards.get(0).jobName()).isEqualTo("MATCH_RESULT");
    }

    @Test
    void buildCards_enabledJobShowsNextRun() {
        when(environment.getProperty("app.football.api.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("app.notification.enabled", Boolean.class, false)).thenReturn(false);
        LocalDateTime lastFinished = LocalDateTime.now().minusMinutes(2);
        SchedulerLog recent = SchedulerLog.builder().jobName("MATCH_RESULT")
                .status(SchedulerJobStatus.SUCCESS).startedAt(lastFinished.minusSeconds(1))
                .finishedAt(lastFinished).build();
        when(repository.findFirstByJobNameOrderByStartedAtDesc("MATCH_RESULT")).thenReturn(Optional.of(recent));
        when(repository.findFirstByJobNameOrderByStartedAtDesc(argThat(s -> !s.equals("MATCH_RESULT")))).thenReturn(Optional.empty());
        List<SchedulerCardDto> cards = service.buildCards();
        SchedulerCardDto matchResultCard = cards.stream().filter(c -> c.jobName().equals("MATCH_RESULT")).findFirst().orElseThrow();
        assertThat(matchResultCard.enabled()).isTrue();
        assertThat(matchResultCard.nextRun()).isNotEqualTo("Disabled");
        assertThat(matchResultCard.nextRun()).isNotEqualTo("Pending first run");
    }

    @Test
    void buildCards_disabledJobShowsDisabled() {
        when(environment.getProperty(anyString(), eq(Boolean.class), eq(false))).thenReturn(false);
        when(repository.findFirstByJobNameOrderByStartedAtDesc(anyString())).thenReturn(Optional.empty());
        List<SchedulerCardDto> cards = service.buildCards();
        assertThat(cards).allMatch(c -> c.nextRun().equals("Disabled"));
    }
}
