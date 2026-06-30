package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.enums.PlayoffWinner;
import com.worldcup.prediction.domain.enums.PlayoffWinnerPick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringServiceTest {

    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new ScoringService();
    }

    @Nested
    @DisplayName("calculatePoints — scoring outcomes")
    class CalculatePoints {

        @Test @DisplayName("+3 for exact score — home win")
        void exactScore_homeWin() {
            assertThat(scoringService.calculatePoints(2, 1, 2, 1)).isEqualTo(3);
        }

        @Test @DisplayName("+3 for exact score — away win")
        void exactScore_awayWin() {
            assertThat(scoringService.calculatePoints(0, 3, 0, 3)).isEqualTo(3);
        }

        @Test @DisplayName("+3 for exact score — 0-0 draw")
        void exactScore_zeroZeroDraw() {
            assertThat(scoringService.calculatePoints(0, 0, 0, 0)).isEqualTo(3);
        }

        @Test @DisplayName("+3 for exact score — high-scoring")
        void exactScore_highScoring() {
            assertThat(scoringService.calculatePoints(4, 3, 4, 3)).isEqualTo(3);
        }

        @Test @DisplayName("+2 for correct draw — different score")
        void correctDraw_differentScore() {
            assertThat(scoringService.calculatePoints(1, 1, 2, 2)).isEqualTo(2);
        }

        @Test @DisplayName("+2 for correct draw — 0-0 vs 2-2")
        void correctDraw_zeroVsOther() {
            assertThat(scoringService.calculatePoints(2, 2, 0, 0)).isEqualTo(2);
        }

        @Test @DisplayName("+1 for correct winner — home win, wrong score")
        void correctWinner_homeWin() {
            assertThat(scoringService.calculatePoints(3, 1, 1, 0)).isEqualTo(1);
        }

        @Test @DisplayName("+1 for correct winner — away win, wrong score")
        void correctWinner_awayWin() {
            assertThat(scoringService.calculatePoints(0, 2, 0, 1)).isEqualTo(1);
        }

        @Test @DisplayName("+0 predicted home win but actual away win")
        void wrongPrediction_homeVsAway() {
            assertThat(scoringService.calculatePoints(0, 1, 2, 0)).isEqualTo(0);
        }

        @Test @DisplayName("+0 predicted draw but actual home win")
        void wrongPrediction_drawVsHomeWin() {
            assertThat(scoringService.calculatePoints(2, 1, 1, 1)).isEqualTo(0);
        }

        @Test @DisplayName("+0 predicted home win but actual draw")
        void wrongPrediction_homeWinVsDraw() {
            assertThat(scoringService.calculatePoints(1, 1, 2, 0)).isEqualTo(0);
        }

        @Test @DisplayName("+0 predicted away win but actual draw")
        void wrongPrediction_awayWinVsDraw() {
            assertThat(scoringService.calculatePoints(0, 0, 0, 1)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("calculatePoints — knockout 90-min semantics")
    class KnockoutScoring {

        @Test @DisplayName("+3 when prediction matches 90-min score (match went to pens)")
        void exactScore_90minDraw_goesToPens() {
            assertThat(scoringService.calculatePoints(1, 1, 1, 1)).isEqualTo(3);
        }

        @Test @DisplayName("+2 correct draw at 90 min, different score")
        void correctDraw_90min() {
            assertThat(scoringService.calculatePoints(2, 2, 1, 1)).isEqualTo(2);
        }

        @Test @DisplayName("+1 correct winner — home wins in regular time")
        void correctWinner_90min() {
            assertThat(scoringService.calculatePoints(2, 0, 1, 0)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("calculatePoints — input validation")
    class InputValidation {

        @Test @DisplayName("throws on negative actualHome")
        void negativeActualHome() {
            assertThatThrownBy(() -> scoringService.calculatePoints(-1, 0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Score cannot be negative");
        }

        @Test @DisplayName("throws on negative actualAway")
        void negativeActualAway() {
            assertThatThrownBy(() -> scoringService.calculatePoints(0, -1, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Score cannot be negative");
        }

        @Test @DisplayName("throws on negative predictedHome")
        void negativePredictedHome() {
            assertThatThrownBy(() -> scoringService.calculatePoints(0, 0, -1, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Score cannot be negative");
        }

        @Test @DisplayName("throws on negative predictedAway")
        void negativePredictedAway() {
            assertThatThrownBy(() -> scoringService.calculatePoints(0, 0, 0, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Score cannot be negative");
        }
    }

    @Nested
    @DisplayName("tournamentWinnerPoints")
    class TournamentWinnerPoints {

        @Test @DisplayName("+10 when predicted code matches actual winner")
        void correctWinner() {
            assertThat(scoringService.tournamentWinnerPoints("br", "br")).isEqualTo(10);
        }

        @Test @DisplayName("+0 when wrong prediction")
        void wrongWinner() {
            assertThat(scoringService.tournamentWinnerPoints("fr", "br")).isEqualTo(0);
        }

        @Test @DisplayName("case-insensitive comparison")
        void caseInsensitive() {
            assertThat(scoringService.tournamentWinnerPoints("BR", "br")).isEqualTo(10);
        }

        @Test @DisplayName("throws on null predictedCode")
        void nullPredictedCode() {
            assertThatThrownBy(() -> scoringService.tournamentWinnerPoints(null, "br"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test @DisplayName("throws on null actualCode")
        void nullActualCode() {
            assertThatThrownBy(() -> scoringService.tournamentWinnerPoints("br", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("isExactScore")
    class IsExactScore {

        @Test void exactMatch() {
            assertThat(scoringService.isExactScore(2, 1, 2, 1)).isTrue();
        }

        @Test void homeScoreDiffers() {
            assertThat(scoringService.isExactScore(2, 1, 3, 1)).isFalse();
        }

        @Test void awayScoreDiffers() {
            assertThat(scoringService.isExactScore(2, 1, 2, 0)).isFalse();
        }
    }

    @Nested
    @DisplayName("isCorrectOutcome")
    class IsCorrectOutcome {

        @Test void bothHomeWin() {
            assertThat(scoringService.isCorrectOutcome(2, 0, 1, 0)).isTrue();
        }

        @Test void bothAwayWin() {
            assertThat(scoringService.isCorrectOutcome(0, 1, 0, 3)).isTrue();
        }

        @Test void bothDraw() {
            assertThat(scoringService.isCorrectOutcome(1, 1, 2, 2)).isTrue();
        }

        @Test void outcomesDiffer() {
            assertThat(scoringService.isCorrectOutcome(1, 0, 0, 1)).isFalse();
        }
    }

    @Nested
    @DisplayName("isCorrectDraw")
    class IsCorrectDraw {

        @Test void bothDraws() {
            assertThat(scoringService.isCorrectDraw(1, 1, 2, 2)).isTrue();
        }

        @Test void actualDrawPredictedWin() {
            assertThat(scoringService.isCorrectDraw(1, 1, 2, 0)).isFalse();
        }

        @Test void actualWinPredictedDraw() {
            assertThat(scoringService.isCorrectDraw(2, 0, 1, 1)).isFalse();
        }
    }

    @Nested
    @DisplayName("calculatePlayoffWinnerBonus")
    class CalculatePlayoffWinnerBonus {

        @Test
        @DisplayName("+1 when exact draw and winner correct (HOME)")
        void bonus_exactDrawCorrectWinnerHome() {
            assertThat(scoringService.calculatePlayoffWinnerBonus(
                    1, 1, PlayoffWinner.HOME_WIN,
                    1, 1, PlayoffWinnerPick.HOME)).isEqualTo(1);
        }

        @Test
        @DisplayName("+1 when exact draw and winner correct (AWAY)")
        void bonus_exactDrawCorrectWinnerAway() {
            assertThat(scoringService.calculatePlayoffWinnerBonus(
                    0, 0, PlayoffWinner.AWAY_WIN,
                    0, 0, PlayoffWinnerPick.AWAY)).isEqualTo(1);
        }

        @Test
        @DisplayName("0 when exact draw but wrong winner pick")
        void bonus_exactDrawWrongWinner() {
            assertThat(scoringService.calculatePlayoffWinnerBonus(
                    1, 1, PlayoffWinner.HOME_WIN,
                    1, 1, PlayoffWinnerPick.AWAY)).isEqualTo(0);
        }

        @Test
        @DisplayName("0 when exact draw but no winner pick")
        void bonus_exactDrawNoWinnerPick() {
            assertThat(scoringService.calculatePlayoffWinnerBonus(
                    1, 1, PlayoffWinner.HOME_WIN,
                    1, 1, null)).isEqualTo(0);
        }

        @Test
        @DisplayName("0 when predicted draw but wrong score (not exact)")
        void bonus_wrongDrawScore() {
            assertThat(scoringService.calculatePlayoffWinnerBonus(
                    1, 1, PlayoffWinner.HOME_WIN,
                    0, 0, PlayoffWinnerPick.HOME)).isEqualTo(0);
        }

        @Test
        @DisplayName("0 when match is not a draw at 90 min")
        void bonus_notADraw() {
            assertThat(scoringService.calculatePlayoffWinnerBonus(
                    2, 1, PlayoffWinner.HOME_WIN,
                    2, 1, PlayoffWinnerPick.HOME)).isEqualTo(0);
        }

        @Test
        @DisplayName("0 when match playoff winner not set")
        void bonus_noActualWinner() {
            assertThat(scoringService.calculatePlayoffWinnerBonus(
                    1, 1, null,
                    1, 1, PlayoffWinnerPick.HOME)).isEqualTo(0);
        }

        @Test
        @DisplayName("0 when prediction is not a draw")
        void bonus_predictedNotDraw() {
            assertThat(scoringService.calculatePlayoffWinnerBonus(
                    1, 1, PlayoffWinner.HOME_WIN,
                    2, 1, PlayoffWinnerPick.HOME)).isEqualTo(0);
        }
    }
}
