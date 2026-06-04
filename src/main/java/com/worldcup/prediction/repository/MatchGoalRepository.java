package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.MatchGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchGoalRepository extends JpaRepository<MatchGoal, Long> {

    List<MatchGoal> findByMatchIdOrderByMinuteAsc(Long matchId);

    boolean existsByMatchId(Long matchId);

    @Modifying
    @Query("DELETE FROM MatchGoal mg")
    void deleteAllGoals();
}
