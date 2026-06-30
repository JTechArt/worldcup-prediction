package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.*;
import com.worldcup.prediction.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchAdminServiceTest {

    @Mock MatchRepository matchRepository;
    @Mock PredictionRepository predictionRepository;
    @Mock CommunityMembershipRepository membershipRepository;
    @Mock TournamentSettingsService tournamentSettingsService;

    // ScoringService is NOT mocked — use real instance for scoring logic
    @Spy ScoringService scoringService = new ScoringService();

    MatchAdminService matchAdminService;

    @BeforeEach
    void setUp() {
        matchAdminService = new MatchAdminService(matchRepository, predictionRepository, scoringService, membershipRepository, tournamentSettingsService);
    }

    private Match knockoutMatch(int home90, int away90, PlayoffWinner winner) {
        Match m = new Match();
        m.setId(1L);
        m.setStage(MatchStage.QUARTER_FINAL);
        m.setHomeScore90(home90);
        m.setAwayScore90(away90);
        m.setHomeScore(home90);
        m.setAwayScore(away90);
        m.setPlayoffWinner(winner);
        m.setStatus(MatchStatus.COMPLETED);
        return m;
    }

    private Prediction pred(Match m, Community c, User u, int home, int away, PlayoffWinnerPick pick) {
        Prediction p = new Prediction();
        p.setMatch(m);
        p.setCommunity(c);
        p.setUser(u);
        p.setPredictedHome(home);
        p.setPredictedAway(away);
        p.setPredictedPlayoffWinner(pick);
        return p;
    }

    @Nested
    @DisplayName("set90MinResult")
    class Set90MinResult {

        @Test
        @DisplayName("sets homeScore90, awayScore90, resultSource MANUAL, status COMPLETED")
        void setsFieldsCorrectly() {
            Match m = new Match();
            m.setId(1L);
            m.setStage(MatchStage.QUARTER_FINAL);
            m.setStatus(MatchStatus.SCHEDULED);
            User admin = new User();
            when(matchRepository.findByIdWithTeams(1L)).thenReturn(Optional.of(m));
            when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            matchAdminService.set90MinResult(1L, 1, 1, PlayoffWinner.HOME_WIN, admin);

            assertThat(m.getHomeScore90()).isEqualTo(1);
            assertThat(m.getAwayScore90()).isEqualTo(1);
            assertThat(m.getPlayoffWinner()).isEqualTo(PlayoffWinner.HOME_WIN);
            assertThat(m.getResultSource()).isEqualTo(ResultSource.MANUAL);
            assertThat(m.getResultEnteredBy()).isEqualTo(admin);
            assertThat(m.getResultEnteredAt()).isNotNull();
            assertThat(m.getStatus()).isEqualTo(MatchStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("scoreAllPredictions — knockout NINETY_MINUTES mode")
    class ScoreAllPredictionsKnockout {

        @Test
        @DisplayName("awards EXACT_DRAW_WINNER (3pts) for exact draw + correct winner")
        void exactDrawWithWinner() {
            Match m = knockoutMatch(1, 1, PlayoffWinner.HOME_WIN);
            Community c = new Community(); c.setId(10L);
            User u = new User(); u.setId(5L);
            Prediction p = pred(m, c, u, 1, 1, PlayoffWinnerPick.HOME);

            when(matchRepository.findByIdWithTeams(1L)).thenReturn(Optional.of(m));
            when(predictionRepository.findByMatchId(1L)).thenReturn(List.of(p));
            when(membershipRepository.findByCommunityIdAndUserId(10L, 5L)).thenReturn(Optional.empty());
            when(tournamentSettingsService.getKnockoutScoringMode())
                .thenReturn(KnockoutScoringMode.NINETY_MINUTES);

            matchAdminService.scoreAllPredictions(1L);

            assertThat(p.getPointsAwarded()).isEqualTo(3);
            assertThat(p.getScoreResult()).isEqualTo(PredictionScore.EXACT_DRAW_WINNER);
        }

        @Test
        @DisplayName("awards CORRECT_DRAW (2pts) for exact draw + wrong winner")
        void exactDrawWrongWinner() {
            Match m = knockoutMatch(1, 1, PlayoffWinner.HOME_WIN);
            Community c = new Community(); c.setId(10L);
            User u = new User(); u.setId(5L);
            Prediction p = pred(m, c, u, 1, 1, PlayoffWinnerPick.AWAY);

            when(matchRepository.findByIdWithTeams(1L)).thenReturn(Optional.of(m));
            when(predictionRepository.findByMatchId(1L)).thenReturn(List.of(p));
            when(membershipRepository.findByCommunityIdAndUserId(10L, 5L)).thenReturn(Optional.empty());
            when(tournamentSettingsService.getKnockoutScoringMode())
                .thenReturn(KnockoutScoringMode.NINETY_MINUTES);

            matchAdminService.scoreAllPredictions(1L);

            assertThat(p.getPointsAwarded()).isEqualTo(2);
            assertThat(p.getScoreResult()).isEqualTo(PredictionScore.CORRECT_DRAW);
        }
    }

    @Nested
    @DisplayName("resetResult")
    class ResetResult {

        @Test
        @DisplayName("clears resultSource, resultEnteredAt, resultEnteredBy, playoffWinner")
        void clearsNewFields() {
            Match m = new Match();
            m.setId(1L);
            m.setStage(MatchStage.QUARTER_FINAL);
            m.setStatus(MatchStatus.COMPLETED);
            m.setResultSource(ResultSource.MANUAL);
            m.setPlayoffWinner(PlayoffWinner.HOME_WIN);
            m.setResultEnteredAt(java.time.LocalDateTime.now());
            m.setResultEnteredBy(new User());

            when(matchRepository.findByIdWithTeams(1L)).thenReturn(Optional.of(m));
            when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(predictionRepository.findByMatchId(1L)).thenReturn(List.of());

            matchAdminService.resetResult(1L);

            assertThat(m.getResultSource()).isNull();
            assertThat(m.getPlayoffWinner()).isNull();
            assertThat(m.getResultEnteredAt()).isNull();
            assertThat(m.getResultEnteredBy()).isNull();
        }
    }
}
