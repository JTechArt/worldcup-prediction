package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByFifaCodeIgnoreCase(String fifaCode);

    Optional<Team> findByFlagCodeIgnoreCase(String flagCode);

    Optional<Team> findByNameIgnoreCase(String name);

    List<Team> findByConfederationIgnoreCase(String confederation);

    boolean existsByFifaCodeIgnoreCase(String fifaCode);

    List<Team> findAllByOrderByNameAsc();

    Optional<Team> findByExternalId(Long externalId);
}
