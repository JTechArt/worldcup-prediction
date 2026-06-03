package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.MatchLineup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchLineupRepository extends JpaRepository<MatchLineup, Long> {

    List<MatchLineup> findByMatchIdAndTeamIdOrderByStartingDescShirtNumberAsc(Long matchId, Long teamId);

    boolean existsByMatchId(Long matchId);
}
