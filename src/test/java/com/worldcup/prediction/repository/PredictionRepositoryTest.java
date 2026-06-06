package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.domain.enums.PredictionScore;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PredictionRepositoryTest {

    @Autowired
    private PredictionRepository predictionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User alice;
    private User bob;
    private Match match1;
    private Match match2;
    private Community community;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM predictions");
        jdbcTemplate.execute("DELETE FROM tournament_winner_predictions");
        jdbcTemplate.execute("DELETE FROM community_memberships");
        jdbcTemplate.execute("DELETE FROM matches");
        jdbcTemplate.execute("DELETE FROM group_standings");
        jdbcTemplate.execute("DELETE FROM group_teams");
        jdbcTemplate.execute("DELETE FROM teams");
        jdbcTemplate.execute("DELETE FROM groups");
        jdbcTemplate.execute("DELETE FROM communities");
        jdbcTemplate.execute("DELETE FROM users");

        alice = userRepository.save(User.builder()
                .email("alice@example.com").firstName("Alice").lastName("Smith")
                .status(UserStatus.ACTIVE).role(UserRole.USER).build());

        bob = userRepository.save(User.builder()
                .email("bob@example.com").firstName("Bob").lastName("Jones")
                .status(UserStatus.ACTIVE).role(UserRole.USER).build());

        match1 = matchRepository.save(Match.builder()
                .matchNumber(1).stage(MatchStage.GROUP).roundLabel("Group Stage Round 1")
                .kickoffTime(LocalDateTime.now().plusDays(1)).status(MatchStatus.SCHEDULED)
                .build());

        match2 = matchRepository.save(Match.builder()
                .matchNumber(2).stage(MatchStage.GROUP).roundLabel("Group Stage Round 1")
                .kickoffTime(LocalDateTime.now().plusDays(2)).status(MatchStatus.SCHEDULED)
                .build());

        community = communityRepository.save(Community.builder()
                .name("Test Community").slug("test-community").build());
    }

    @Test
    void findByUserIdAndMatchId_whenExists_returnsPrediction() {
        predictionRepository.save(Prediction.builder()
                .user(alice).match(match1).community(community).predictedHome(2).predictedAway(1).build());

        Optional<Prediction> found = predictionRepository.findByUserIdAndMatchId(alice.getId(), match1.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getPredictedHome()).isEqualTo(2);
        assertThat(found.get().getPredictedAway()).isEqualTo(1);
    }

    @Test
    void existsByUserIdAndMatchId_whenExists_returnsTrue() {
        predictionRepository.save(Prediction.builder()
                .user(alice).match(match1).community(community).predictedHome(1).predictedAway(0).build());

        assertThat(predictionRepository.existsByUserIdAndMatchId(alice.getId(), match1.getId())).isTrue();
        assertThat(predictionRepository.existsByUserIdAndMatchId(bob.getId(), match1.getId())).isFalse();
    }

    @Test
    void findByUserId_returnsAllPredictionsForUser() {
        predictionRepository.save(Prediction.builder().user(alice).match(match1).community(community).predictedHome(2).predictedAway(0).build());
        predictionRepository.save(Prediction.builder().user(alice).match(match2).community(community).predictedHome(1).predictedAway(1).build());
        predictionRepository.save(Prediction.builder().user(bob).match(match1).community(community).predictedHome(0).predictedAway(0).build());

        assertThat(predictionRepository.findByUserId(alice.getId())).hasSize(2);
        assertThat(predictionRepository.findByUserId(bob.getId())).hasSize(1);
    }

    @Test
    void countByMatchIdAndScoreResult_returnsCorrectCount() {
        predictionRepository.save(Prediction.builder().user(alice).match(match1).community(community)
                .predictedHome(2).predictedAway(1).scoreResult(PredictionScore.EXACT).pointsAwarded(3).build());
        predictionRepository.save(Prediction.builder().user(bob).match(match1).community(community)
                .predictedHome(1).predictedAway(1).scoreResult(PredictionScore.WRONG).pointsAwarded(0).build());

        assertThat(predictionRepository.countByMatchIdAndScoreResult(match1.getId(), PredictionScore.EXACT)).isEqualTo(1);
        assertThat(predictionRepository.countByMatchIdAndScoreResult(match1.getId(), PredictionScore.WRONG)).isEqualTo(1);
    }

    @Test
    void prediction_uniqueConstraint_preventsDoubleSubmit() {
        predictionRepository.save(Prediction.builder()
                .user(alice).match(match1).community(community).predictedHome(2).predictedAway(1).build());

        // SQLite raises JpaSystemException; PostgreSQL raises DataIntegrityViolationException.
        // Both extend DataAccessException — accept either.
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> predictionRepository.saveAndFlush(Prediction.builder()
                        .user(alice).match(match1).community(community).predictedHome(1).predictedAway(0).build())
        );
    }

    @Test
    void isPredictedDraw_whenScoresEqual_returnsTrue() {
        Prediction draw = Prediction.builder().user(alice).match(match1).predictedHome(1).predictedAway(1).build();
        assertThat(draw.isPredictedDraw()).isTrue();

        Prediction nonDraw = Prediction.builder().user(alice).match(match1).predictedHome(2).predictedAway(1).build();
        assertThat(nonDraw.isPredictedDraw()).isFalse();
    }
}
