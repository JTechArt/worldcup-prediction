package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByExternalId(Long externalId);

    List<Player> findByTeamIdOrderByShirtNumberAsc(Long teamId);

    List<Player> findByTournamentGoalsGreaterThanOrderByTournamentGoalsDesc(int minGoals);
}
