package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.AuditLog;
import com.worldcup.prediction.domain.enums.AuditAction;
import com.worldcup.prediction.repository.AuditLogRepository;
import com.worldcup.prediction.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuditLogServiceTest {

    private AuditLogRepository auditLogRepository;
    private UserRepository userRepository;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        userRepository = mock(UserRepository.class);
        auditLogService = new AuditLogService(auditLogRepository, userRepository);
    }

    @Test
    void log_savesEntryWithCorrectFields() {
        AuditLog saved = AuditLog.builder()
                .action(AuditAction.USER_APPROVED)
                .targetType("USER")
                .targetId(42L)
                .note("User approved")
                .build();
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(saved);

        AuditLog result = auditLogService.log(0L, AuditAction.USER_APPROVED, "USER", 42L, "User approved");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog captured = captor.getValue();
        assertThat(captured.getAction()).isEqualTo(AuditAction.USER_APPROVED);
        assertThat(captured.getTargetType()).isEqualTo("USER");
        assertThat(captured.getTargetId()).isEqualTo(42L);
        assertThat(captured.getNote()).isEqualTo("User approved");

        assertThat(result.getAction()).isEqualTo(AuditAction.USER_APPROVED);
    }

    @Test
    void log_persistsNullNoteWithoutThrowing() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLog result = auditLogService.log(0L, AuditAction.ROUND_WINDOW_CLOSED, "ROUND", 7L, null);

        assertThat(result.getNote()).isNull();
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void getRecent_returnsPageContent() {
        AuditLog entry = AuditLog.builder().action(AuditAction.REMINDER_SENT).build();
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        List<AuditLog> result = auditLogService.getRecent(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo(AuditAction.REMINDER_SENT);
    }
}
