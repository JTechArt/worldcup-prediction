package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.enums.PredictionWindowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PredictionWindowRepository extends JpaRepository<PredictionWindow, Long> {

    List<PredictionWindow> findByCommunityIdIsNullOrderByOpenAtAsc();

    List<PredictionWindow> findByCommunityIdOrderByOpenAtAsc(Long communityId);

    List<PredictionWindow> findByStatusAndOpenAtLessThanEqual(PredictionWindowStatus status, LocalDateTime now);

    @Query("SELECT pw FROM PredictionWindow pw WHERE pw.status = :status " +
           "AND pw.effectiveCloseAt <= :now AND (pw.overrideStatus IS NULL OR pw.overrideStatus <> com.worldcup.prediction.domain.enums.RoundOverrideStatus.FORCE_OPEN)")
    List<PredictionWindow> findExpiredOpenWindows(PredictionWindowStatus status, LocalDateTime now);

    @Query("SELECT pw FROM PredictionWindow pw JOIN pw.matches m " +
           "WHERE m.id = :matchId AND pw.communityId IS NULL AND pw.status = 'OPEN'")
    Optional<PredictionWindow> findOpenGlobalWindowForMatch(Long matchId);

    @Query("SELECT pw FROM PredictionWindow pw JOIN pw.matches m " +
           "WHERE m.id = :matchId AND pw.communityId = :communityId AND pw.status = 'OPEN'")
    Optional<PredictionWindow> findOpenCommunityWindowForMatch(Long matchId, Long communityId);

    @Query("SELECT pw FROM PredictionWindow pw JOIN pw.matches m " +
           "WHERE m.id = :matchId AND pw.communityId IS NULL AND pw.overrideStatus = 'FORCE_OPEN'")
    Optional<PredictionWindow> findForceOpenGlobalWindowForMatch(Long matchId);

    @Query("SELECT pw FROM PredictionWindow pw JOIN pw.matches m " +
           "WHERE m.id = :matchId AND pw.communityId = :communityId AND pw.overrideStatus = 'FORCE_OPEN'")
    Optional<PredictionWindow> findForceOpenCommunityWindowForMatch(Long matchId, Long communityId);
}
