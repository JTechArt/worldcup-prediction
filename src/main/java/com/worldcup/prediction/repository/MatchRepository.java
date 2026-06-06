package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Query("""
            SELECT m FROM Match m
            LEFT JOIN FETCH m.homeTeam
            LEFT JOIN FETCH m.awayTeam
            WHERE m.status = :status
            ORDER BY m.kickoffTime ASC
            """)
    List<Match> findByStatusWithTeams(@Param("status") MatchStatus status);

    List<Match> findByStageOrderByKickoffTimeAsc(MatchStage stage);

    List<Match> findByGroupIdOrderByKickoffTimeAsc(Long groupId);

    @Query("""
            SELECT m FROM Match m
            LEFT JOIN FETCH m.homeTeam
            LEFT JOIN FETCH m.awayTeam
            LEFT JOIN FETCH m.group
            WHERE m.id = :id
            """)
    Optional<Match> findByIdWithTeams(@Param("id") Long id);

    @Query("""
            SELECT m FROM Match m
            LEFT JOIN FETCH m.homeTeam
            LEFT JOIN FETCH m.awayTeam
            LEFT JOIN FETCH m.group
            ORDER BY m.kickoffTime ASC
            """)
    List<Match> findAllWithTeams();

    @Query("SELECT m.roundLabel FROM Match m GROUP BY m.roundLabel ORDER BY MIN(m.kickoffTime) ASC")
    List<String> findDistinctRoundLabels();

    @Query("""
            SELECT m FROM Match m
            LEFT JOIN FETCH m.homeTeam
            LEFT JOIN FETCH m.awayTeam
            LEFT JOIN FETCH m.group
            WHERE m.roundLabel = :roundLabel
            ORDER BY m.kickoffTime ASC
            """)
    List<Match> findByRoundLabelWithTeams(@Param("roundLabel") String roundLabel);

    @Query("""
            SELECT m FROM Match m
            LEFT JOIN FETCH m.homeTeam
            LEFT JOIN FETCH m.awayTeam
            LEFT JOIN FETCH m.group
            WHERE m.stage = :stage
            ORDER BY m.kickoffTime ASC
            """)
    List<Match> findByStageWithTeams(@Param("stage") MatchStage stage);

    @Query("""
            SELECT m FROM Match m
            WHERE m.kickoffTime >= :from
              AND m.kickoffTime < :to
            ORDER BY m.kickoffTime ASC
            """)
    List<Match> findByKickoffTimeBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    long countByStatus(MatchStatus status);

    List<Match> findByStatusAndLineupFetchedFalse(MatchStatus status);

    long countByStatusAndKickoffTimeBefore(MatchStatus status, LocalDateTime time);

    long countByStatusAndUpdatedAtAfter(MatchStatus status, LocalDateTime time);

    @Query("""
            SELECT m FROM Match m
            LEFT JOIN FETCH m.homeTeam
            LEFT JOIN FETCH m.awayTeam
            WHERE m.homeTeam.id = :teamId OR m.awayTeam.id = :teamId
            ORDER BY m.kickoffTime ASC
            """)
    List<Match> findByTeamIdOrderByKickoffTimeAsc(@Param("teamId") Long teamId);

    @Query("""
            SELECT m FROM Match m
            WHERE m.homeTeam.fifaCode = :homeFifaCode
              AND m.awayTeam.fifaCode = :awayFifaCode
              AND m.kickoffTime >= :start
              AND m.kickoffTime < :end
            """)
    Optional<Match> findByHomeTeamFifaCodeAndAwayTeamFifaCodeAndKickoffBetween(
            @Param("homeFifaCode") String homeFifaCode,
            @Param("awayFifaCode") String awayFifaCode,
            @Param("start") java.time.LocalDateTime start,
            @Param("end")   java.time.LocalDateTime end);

    @Modifying
    @Query("DELETE FROM Match m")
    void deleteAllMatches();

    @Query("SELECT MAX(m.matchNumber) FROM Match m WHERE m.stage != :stage")
    Optional<Integer> findMaxMatchNumberExcludingStage(@Param("stage") MatchStage stage);

}
