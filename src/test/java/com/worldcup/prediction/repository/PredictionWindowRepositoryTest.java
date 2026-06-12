package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.PredictionWindow;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.domain.enums.PredictionWindowStatus;
import com.worldcup.prediction.domain.enums.RoundOverrideStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PredictionWindowRepositoryTest {

    @Autowired private PredictionWindowRepository windowRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Match match;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM prediction_window_match");
        jdbcTemplate.execute("DELETE FROM prediction_window");
        jdbcTemplate.execute("DELETE FROM predictions");
        jdbcTemplate.execute("DELETE FROM tournament_winner_predictions");
        jdbcTemplate.execute("DELETE FROM matches");

        match = matchRepository.save(Match.builder()
                .matchNumber(1).stage(MatchStage.GROUP).roundLabel("Matchday 1")
                .kickoffTime(LocalDateTime.now().plusHours(3))
                .status(MatchStatus.SCHEDULED).build());
    }

    @Test
    void findOpenGlobalWindowForMatch_returnsWindowContainingMatch() {
        PredictionWindow window = windowRepository.save(PredictionWindow.builder()
                .label("June 14")
                .openAt(LocalDateTime.now().minusHours(1))
                .status(PredictionWindowStatus.OPEN)
                .matches(new HashSet<>(Set.of(match)))
                .build());

        Optional<PredictionWindow> found = windowRepository.findOpenGlobalWindowForMatch(match.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(window.getId());
    }

    @Test
    void findOpenGlobalWindowForMatch_returnsEmpty_whenWindowNotOpen() {
        windowRepository.save(PredictionWindow.builder()
                .label("June 14")
                .openAt(LocalDateTime.now().minusHours(1))
                .status(PredictionWindowStatus.DRAFT)
                .matches(new HashSet<>(Set.of(match)))
                .build());

        Optional<PredictionWindow> found = windowRepository.findOpenGlobalWindowForMatch(match.getId());
        assertThat(found).isEmpty();
    }

    @Test
    void findByCommunityIdIsNull_returnsOnlyGlobalWindows() {
        windowRepository.save(PredictionWindow.builder()
                .label("Global").openAt(LocalDateTime.now())
                .status(PredictionWindowStatus.DRAFT).build());
        windowRepository.save(PredictionWindow.builder()
                .label("Community").openAt(LocalDateTime.now())
                .communityId(42L).status(PredictionWindowStatus.DRAFT).build());

        List<PredictionWindow> globals = windowRepository.findByCommunityIdIsNullOrderByOpenAtAsc();
        assertThat(globals).hasSize(1);
        assertThat(globals.get(0).getLabel()).isEqualTo("Global");
    }

    @Test
    void findByStatusAndOpenAtLessThanEqual_returnsScheduledReadyToActivate() {
        LocalDateTime now = LocalDateTime.now();
        windowRepository.save(PredictionWindow.builder()
                .label("Ready").openAt(now.minusMinutes(5))
                .status(PredictionWindowStatus.SCHEDULED).build());
        windowRepository.save(PredictionWindow.builder()
                .label("Future").openAt(now.plusHours(2))
                .status(PredictionWindowStatus.SCHEDULED).build());

        List<PredictionWindow> ready = windowRepository.findByStatusAndOpenAtLessThanEqual(
                PredictionWindowStatus.SCHEDULED, now);
        assertThat(ready).hasSize(1);
        assertThat(ready.get(0).getLabel()).isEqualTo("Ready");
    }

    @Test
    void findForceOpenGlobalWindowForMatch_returnsForceOpenWindow() {
        PredictionWindow window = windowRepository.save(PredictionWindow.builder()
                .label("June 15")
                .openAt(LocalDateTime.now().plusHours(2))
                .status(PredictionWindowStatus.SCHEDULED)
                .overrideStatus(RoundOverrideStatus.FORCE_OPEN)
                .matches(new HashSet<>(Set.of(match)))
                .build());

        Optional<PredictionWindow> found = windowRepository.findForceOpenGlobalWindowForMatch(match.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(window.getId());
    }

    @Test
    void findExpiredOpenWindows_returnsExpiredButNotForceOpen() {
        LocalDateTime now = LocalDateTime.now();
        // Expired OPEN window — should be returned
        windowRepository.save(PredictionWindow.builder()
                .label("Expired").openAt(now.minusHours(3))
                .effectiveCloseAt(now.minusMinutes(10))
                .status(PredictionWindowStatus.OPEN)
                .build());
        // FORCE_OPEN window past its close time — should NOT be returned
        windowRepository.save(PredictionWindow.builder()
                .label("ForceOpen").openAt(now.minusHours(3))
                .effectiveCloseAt(now.minusMinutes(5))
                .status(PredictionWindowStatus.OPEN)
                .overrideStatus(RoundOverrideStatus.FORCE_OPEN)
                .build());

        List<PredictionWindow> expired = windowRepository.findExpiredOpenWindows(
                PredictionWindowStatus.OPEN, now);

        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getLabel()).isEqualTo("Expired");
    }
}
