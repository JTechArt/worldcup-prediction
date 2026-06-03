package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Group;
import com.worldcup.prediction.domain.GroupStanding;
import com.worldcup.prediction.domain.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GroupStandingRepositoryTest {

    @Autowired GroupStandingRepository standingRepo;
    @Autowired GroupRepository groupRepo;
    @Autowired TeamRepository teamRepo;
    @Autowired JdbcTemplate jdbcTemplate;

    private Group groupA;
    private Team testTeam;

    @BeforeEach
    void setUp() {
        // Delete in FK-safe order
        jdbcTemplate.execute("DELETE FROM predictions");
        jdbcTemplate.execute("DELETE FROM tournament_winner_predictions");
        jdbcTemplate.execute("DELETE FROM matches");
        jdbcTemplate.execute("DELETE FROM group_standings");
        jdbcTemplate.execute("DELETE FROM group_teams");
        jdbcTemplate.execute("DELETE FROM teams");
        jdbcTemplate.execute("DELETE FROM groups");

        groupA = groupRepo.save(Group.builder().name("A").build());
        testTeam = teamRepo.save(Team.builder()
                .name("Test Team")
                .fifaCode("TST")
                .flagCode("ts")
                .confederation("TEST")
                .build());
    }

    @Test
    void findByGroupIdAndTeamId_returnsCorrectRow() {
        GroupStanding s = GroupStanding.builder()
                .group(groupA).team(testTeam).position(1).points(9).played(3).won(3).build();
        standingRepo.save(s);

        Optional<GroupStanding> found = standingRepo.findByGroupIdAndTeamId(groupA.getId(), testTeam.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getPoints()).isEqualTo(9);
    }
}
