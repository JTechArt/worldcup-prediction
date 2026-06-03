package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    Optional<Match> findByMatchNumber(int matchNumber);

    Optional<Match> findByExternalId(String externalId);

    List<Match> findByStage(MatchStage stage);

    List<Match> findByStatus(MatchStatus status);

    List<Match> findByStageOrderByKickoffTimeAsc(MatchStage stage);

    List<Match> findByGroupIdOrderByKickoffTimeAsc(Long groupId);

    @Query("""
            SELECT m FROM Match m
            LEFT JOIN FETCH m.homeTeam
            LEFT JOIN FETCH m.awayTeam
            WHERE m.stage = :stage
            ORDER BY m.kickoffTime ASC
            """)
    List<Match> findByStageWithTeams(@Param("stage") MatchStage stage);

    @Query("""
            SELECT m FROM Match m
            LEFT JOIN FETCH m.homeTeam
            LEFT JOIN FETCH m.awayTeam
            WHERE m.predictionWindowOpen = true
            ORDER BY m.kickoffTime ASC
            """)
    List<Match> findOpenPredictionWindows();

    @Query("""
            SELECT m FROM Match m
            WHERE m.kickoffTime >= :from
              AND m.kickoffTime < :to
            ORDER BY m.kickoffTime ASC
            """)
    List<Match> findByKickoffTimeBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT m FROM Match m
            WHERE m.predictionWindowOpen = false
              AND m.predictionWindowOpensAt IS NOT NULL
              AND m.predictionWindowOpensAt <= :now
              AND m.status = 'SCHEDULED'
            """)
    List<Match> findMatchesWhereWindowShouldOpen(@Param("now") LocalDateTime now);

    @Query("""
            SELECT m FROM Match m
            WHERE m.predictionWindowOpen = true
              AND m.predictionWindowClosesAt IS NOT NULL
              AND m.predictionWindowClosesAt <= :now
            """)
    List<Match> findMatchesWhereWindowShouldClose(@Param("now") LocalDateTime now);

    long countByStatus(MatchStatus status);
}
