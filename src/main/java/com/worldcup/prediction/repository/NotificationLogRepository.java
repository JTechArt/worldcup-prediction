package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.NotificationLog;
import com.worldcup.prediction.domain.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    boolean existsByReferenceKey(String referenceKey);

    List<NotificationLog> findTop50ByOrderBySentAtDesc();

    List<NotificationLog> findByTypeOrderBySentAtDesc(NotificationType type);
}
