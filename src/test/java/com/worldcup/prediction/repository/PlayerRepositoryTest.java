package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Player;
import com.worldcup.prediction.domain.Team;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlayerRepositoryTest {

    @Autowired PlayerRepository playerRepo;
    @Autowired TeamRepository teamRepo;

    @Test
    void findByExternalId_returnsPlayer() {
        Team team = teamRepo.findAll().get(0);
        Player p = Player.builder().externalId(9999L).team(team).name("Test Player").build();
        playerRepo.save(p);

        assertThat(playerRepo.findByExternalId(9999L)).isPresent();
    }

    @Test
    void findByTournamentGoalsGreaterThan_onlyReturnsScorers() {
        Team team = teamRepo.findAll().get(0);
        playerRepo.save(Player.builder().externalId(1L).team(team).name("Scorer").tournamentGoals(3).build());
        playerRepo.save(Player.builder().externalId(2L).team(team).name("NoGoals").tournamentGoals(0).build());

        List<Player> scorers = playerRepo.findByTournamentGoalsGreaterThanOrderByTournamentGoalsDesc(0);
        assertThat(scorers).hasSize(1);
        assertThat(scorers.get(0).getName()).isEqualTo("Scorer");
    }
}
