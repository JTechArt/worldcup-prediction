package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.TournamentSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentSettingsRepository extends JpaRepository<TournamentSettings, Long> {
}
