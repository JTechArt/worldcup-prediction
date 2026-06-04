package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.TournamentWinnerPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentWinnerPredictionRepository extends JpaRepository<TournamentWinnerPrediction, Long> {

    Optional<TournamentWinnerPrediction> findByUserId(Long userId);

    Optional<TournamentWinnerPrediction> findByUser(com.worldcup.prediction.domain.User user);

    boolean existsByUserId(Long userId);

    List<TournamentWinnerPrediction> findByTeamId(Long teamId);

    @Query("""
            SELECT twp FROM TournamentWinnerPrediction twp
            JOIN FETCH twp.user u
            JOIN FETCH twp.team t
            ORDER BY u.totalPoints DESC
            """)
    List<TournamentWinnerPrediction> findAllWithDetails();

    long countByTeamId(Long teamId);

    Optional<TournamentWinnerPrediction> findByUserIdAndCommunityId(Long userId, Long communityId);

    List<TournamentWinnerPrediction> findByCommunityId(Long communityId);

    @Query("""
            SELECT twp FROM TournamentWinnerPrediction twp
            JOIN FETCH twp.user JOIN FETCH twp.team
            WHERE twp.community.id = :communityId
            """)
    List<TournamentWinnerPrediction> findAllWithDetailsByCommunityId(@Param("communityId") Long communityId);

    List<TournamentWinnerPrediction> findByTeamIdAndCommunityId(Long teamId, Long communityId);
}
