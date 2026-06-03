package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.AuditLog;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.AuditAction;
import com.worldcup.prediction.repository.AuditLogRepository;
import com.worldcup.prediction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Persists one audit log entry.
     *
     * @param adminId    ID of the admin performing the action (0 or null = anonymous)
     * @param action     AuditAction enum value
     * @param targetType Domain object type, e.g. "USER"
     * @param targetId   PK of the affected record
     * @param note       Human-readable detail string (may be null)
     */
    @Transactional
    public AuditLog log(Long adminId, AuditAction action, String targetType, Long targetId, String note) {
        User actor = null;
        if (adminId != null && adminId > 0) {
            actor = userRepository.getReferenceById(adminId);
        }
        AuditLog entry = AuditLog.builder()
                .actor(actor)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .note(note)
                .build();
        return auditLogRepository.save(entry);
    }

    /**
     * Returns the most recent {@code limit} audit log entries, newest first.
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getRecent(int limit) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).getContent();
    }
}
