package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.enums.PlayoffWinner;
import com.worldcup.prediction.domain.enums.PlayoffWinnerPick;
import com.worldcup.prediction.domain.enums.PredictionScore;
import org.springframework.stereotype.Service;

/**
 * Pure stateless scoring logic — no repository dependencies.
 *
 * Scoring rules:
 *   +3 exact score
 *   +2 correct draw (any draw → any draw, different score)
 *   +1 correct winner (right team wins, wrong score)
 *    0 wrong prediction
 *  +10 correct tournament winner
 *
 * Knockout matches: always called with 90-min score; extra time / pens ignored at caller level.
 */
@Service
public class ScoringService {

    public int calculatePoints(int actualHome, int actualAway,
                               int predictedHome, int predictedAway) {
        validateScores(actualHome, actualAway, predictedHome, predictedAway);
        if (isExactScore(actualHome, actualAway, predictedHome, predictedAway)) return 3;
        if (isCorrectDraw(actualHome, actualAway, predictedHome, predictedAway)) return 2;
        if (isCorrectOutcome(actualHome, actualAway, predictedHome, predictedAway)) return 1;
        return 0;
    }

    public PredictionScore determineScoreResult(int actualHome, int actualAway,
                                                int predictedHome, int predictedAway) {
        if (isExactScore(actualHome, actualAway, predictedHome, predictedAway)) return PredictionScore.EXACT;
        if (isCorrectDraw(actualHome, actualAway, predictedHome, predictedAway)) return PredictionScore.CORRECT_DRAW;
        if (isCorrectOutcome(actualHome, actualAway, predictedHome, predictedAway)) return PredictionScore.CORRECT_WINNER;
        return PredictionScore.WRONG;
    }

    public int tournamentWinnerPoints(String predictedCode, String actualCode) {
        if (predictedCode == null) throw new IllegalArgumentException("predictedCode cannot be null");
        if (actualCode == null) throw new IllegalArgumentException("actualCode cannot be null");
        return predictedCode.trim().equalsIgnoreCase(actualCode.trim()) ? 10 : 0;
    }

    public boolean isExactScore(int actualHome, int actualAway,
                                int predictedHome, int predictedAway) {
        return actualHome == predictedHome && actualAway == predictedAway;
    }

    public boolean isCorrectDraw(int actualHome, int actualAway,
                                  int predictedHome, int predictedAway) {
        return isDraw(actualHome, actualAway) && isDraw(predictedHome, predictedAway);
    }

    public boolean isCorrectOutcome(int actualHome, int actualAway,
                                     int predictedHome, int predictedAway) {
        return outcome(actualHome, actualAway) == outcome(predictedHome, predictedAway);
    }

    public int calculatePlayoffWinnerBonus(int homeScore90, int awayScore90, PlayoffWinner actualWinner,
                                           int predictedHome, int predictedAway, PlayoffWinnerPick predictedWinner) {
        validateScores(homeScore90, awayScore90, predictedHome, predictedAway);
        if (actualWinner == null || predictedWinner == null) return 0;
        if (homeScore90 != awayScore90) return 0;           // not a 90-min draw
        if (predictedHome != predictedAway) return 0;       // user didn't predict draw
        if (predictedHome != homeScore90) return 0;         // not exact draw score
        boolean homeWon = actualWinner == PlayoffWinner.HOME_WIN;
        boolean pickedHome = predictedWinner == PlayoffWinnerPick.HOME;
        return homeWon == pickedHome ? 1 : 0;
    }

    private void validateScores(int actualHome, int actualAway,
                                 int predictedHome, int predictedAway) {
        if (actualHome < 0 || actualAway < 0 || predictedHome < 0 || predictedAway < 0) {
            throw new IllegalArgumentException("Score cannot be negative");
        }
    }

    private boolean isDraw(int home, int away) { return home == away; }

    private int outcome(int home, int away) { return Integer.compare(home, away); }
}
