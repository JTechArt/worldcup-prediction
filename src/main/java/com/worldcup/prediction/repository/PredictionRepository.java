package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.PredictionScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    Optional<Prediction> findByUserIdAndMatchId(Long userId, Long matchId);

    @Query("SELECT p FROM Prediction p WHERE p.user.id = :userId AND p.match.id IN :matchIds")
    List<Prediction> findByUserIdAndMatchIdIn(@Param("userId") Long userId, @Param("matchIds") java.util.Collection<Long> matchIds);

    @Query("SELECT COUNT(p) FROM Prediction p WHERE p.user.id = :userId AND p.match.id IN :matchIds")
    long countByUserIdAndMatchIdIn(@Param("userId") Long userId, @Param("matchIds") java.util.Collection<Long> matchIds);

    List<Prediction> findByUserId(Long userId);

    List<Prediction> findByUser(com.worldcup.prediction.domain.User user);

    /**
     * Returns [userId, stage, sumPoints] aggregated for all users.
     * Used by LeaderboardController to build the per-stage phase breakdown map.
     */
    @Query("""
            SELECT p.user.id, p.match.stage, SUM(p.pointsAwarded)
            FROM Prediction p
            WHERE p.match.stage IS NOT NULL
            GROUP BY p.user.id, p.match.stage
            """)
    List<Object[]> sumPointsByUserAndStage();

    List<Prediction> findByMatchId(Long matchId);

    boolean existsByUserIdAndMatchId(Long userId, Long matchId);

    @Query("""
            SELECT p FROM Prediction p
            JOIN FETCH p.match m
            LEFT JOIN FETCH m.homeTeam
            LEFT JOIN FETCH m.awayTeam
            WHERE p.user.id = :userId
            ORDER BY m.kickoffTime ASC
            """)
    List<Prediction> findByUserIdWithMatchDetails(@Param("userId") Long userId);

    @Query("""
            SELECT p FROM Prediction p
            JOIN FETCH p.user u
            WHERE p.match.id = :matchId
            ORDER BY u.totalPoints DESC
            """)
    List<Prediction> findByMatchIdWithUsers(@Param("matchId") Long matchId);

    @Query("""
            SELECT p FROM Prediction p
            JOIN p.match m
            WHERE p.user.id = :userId
              AND m.stage = :stage
            ORDER BY m.kickoffTime ASC
            """)
    List<Prediction> findByUserIdAndMatchStage(
            @Param("userId") Long userId,
            @Param("stage") MatchStage stage);

    @Query("""
            SELECT p FROM Prediction p
            JOIN p.match m
            WHERE p.user.id = :userId
              AND m.roundLabel = :roundLabel
            ORDER BY m.kickoffTime ASC
            """)
    List<Prediction> findByUserIdAndRoundLabel(
            @Param("userId") Long userId,
            @Param("roundLabel") String roundLabel);

    @Query("""
            SELECT COUNT(p) FROM Prediction p
            JOIN p.match m
            WHERE p.user.id = :userId
              AND m.predictionWindowOpen = true
              AND p.scoreResult = 'PENDING'
            """)
    long countPendingForOpenWindows(@Param("userId") Long userId);

    @Query("""
            SELECT COUNT(p) FROM Prediction p
            WHERE p.match.id = :matchId
              AND p.scoreResult = :scoreResult
            """)
    long countByMatchIdAndScoreResult(
            @Param("matchId") Long matchId,
            @Param("scoreResult") PredictionScore scoreResult);
}
