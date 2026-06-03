package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MatchRepositoryTest {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Team brazil;
    private Team argentina;

    @BeforeEach
    void setUp() {
        // Delete in FK-safe order: seed data references teams from group_teams + matches
        jdbcTemplate.execute("DELETE FROM predictions");
        jdbcTemplate.execute("DELETE FROM tournament_winner_predictions");
        jdbcTemplate.execute("DELETE FROM matches");
        jdbcTemplate.execute("DELETE FROM group_standings");
        jdbcTemplate.execute("DELETE FROM group_teams");
        jdbcTemplate.execute("DELETE FROM teams");
        jdbcTemplate.execute("DELETE FROM groups");

        brazil = teamRepository.save(Team.builder()
                .name("Brazil")
                .fifaCode("BRA")
                .flagCode("br")
                .confederation("CONMEBOL")
                .build());

        argentina = teamRepository.save(Team.builder()
                .name("Argentina")
                .fifaCode("ARG")
                .flagCode("ar")
                .confederation("CONMEBOL")
                .build());
    }

    @Test
    void findByMatchNumber_returnsCorrectMatch() {
        matchRepository.save(Match.builder()
                .matchNumber(1)
                .stage(MatchStage.GROUP)
                .roundLabel("Group Stage Round 1")
                .homeTeam(brazil)
                .awayTeam(argentina)
                .kickoffTime(LocalDateTime.now().plusDays(5))
                .venue("Rose Bowl")
                .city("Los Angeles")
                .status(MatchStatus.SCHEDULED)
                .build());

        Optional<Match> found = matchRepository.findByMatchNumber(1);
        assertThat(found).isPresent();
        assertThat(found.get().getVenue()).isEqualTo("Rose Bowl");
    }

    @Test
    void findByStage_returnsAllMatchesForStage() {
        matchRepository.save(Match.builder().matchNumber(1).stage(MatchStage.GROUP).roundLabel("GS R1")
                .kickoffTime(LocalDateTime.now().plusDays(1)).status(MatchStatus.SCHEDULED).build());
        matchRepository.save(Match.builder().matchNumber(2).stage(MatchStage.GROUP).roundLabel("GS R1")
                .kickoffTime(LocalDateTime.now().plusDays(2)).status(MatchStatus.SCHEDULED).build());

        List<Match> groupMatches = matchRepository.findByStage(MatchStage.GROUP);
        assertThat(groupMatches).hasSize(2);
    }

    @Test
    void findOpenPredictionWindows_returnsOnlyOpenWindows() {
        matchRepository.save(Match.builder().matchNumber(1).stage(MatchStage.GROUP).roundLabel("GS R1")
                .kickoffTime(LocalDateTime.now().plusDays(1)).status(MatchStatus.SCHEDULED)
                .predictionWindowOpen(false).build());
        matchRepository.save(Match.builder().matchNumber(2).stage(MatchStage.GROUP).roundLabel("GS R1")
                .kickoffTime(LocalDateTime.now().plusHours(2)).status(MatchStatus.SCHEDULED)
                .predictionWindowOpen(true).predictionWindowClosesAt(LocalDateTime.now().plusHours(1)).build());

        List<Match> open = matchRepository.findOpenPredictionWindows();
        assertThat(open).hasSize(1);
        assertThat(open.get(0).getMatchNumber()).isEqualTo(2);
    }

    @Test
    void findByKickoffTimeBetween_returnsMatchesInRange() {
        matchRepository.save(Match.builder().matchNumber(1).stage(MatchStage.GROUP).roundLabel("GS R1")
                .kickoffTime(LocalDateTime.now().plusHours(2)).status(MatchStatus.SCHEDULED).build());
        matchRepository.save(Match.builder().matchNumber(2).stage(MatchStage.GROUP).roundLabel("GS R1")
                .kickoffTime(LocalDateTime.now().plusDays(5)).status(MatchStatus.SCHEDULED).build());

        LocalDateTime from = LocalDateTime.now().plusHours(1);
        LocalDateTime to = LocalDateTime.now().plusHours(3);
        List<Match> matches = matchRepository.findByKickoffTimeBetween(from, to);
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getMatchNumber()).isEqualTo(1);
    }

    @Test
    void getEffectiveScore_forGroupMatch_returnsMainScore() {
        Match completed = matchRepository.save(Match.builder()
                .matchNumber(3).stage(MatchStage.GROUP).roundLabel("GS R1")
                .kickoffTime(LocalDateTime.now().minusHours(2))
                .status(MatchStatus.COMPLETED)
                .homeScore(2).awayScore(1)
                .build());

        Match found = matchRepository.findById(completed.getId()).orElseThrow();
        assertThat(found.getEffectiveHomeScore()).isEqualTo(2);
        assertThat(found.getEffectiveAwayScore()).isEqualTo(1);
    }

    @Test
    void getEffectiveScore_forKnockoutMatch_returns90MinuteScore() {
        Match knockout = matchRepository.save(Match.builder()
                .matchNumber(50).stage(MatchStage.QUARTER_FINAL).roundLabel("Quarter Final")
                .kickoffTime(LocalDateTime.now().minusHours(3))
                .status(MatchStatus.COMPLETED)
                .homeScore(2).awayScore(1)
                .homeScore90(1).awayScore90(1)
                .build());

        Match found = matchRepository.findById(knockout.getId()).orElseThrow();
        assertThat(found.getEffectiveHomeScore()).isEqualTo(1);
        assertThat(found.getEffectiveAwayScore()).isEqualTo(1);
    }
}
