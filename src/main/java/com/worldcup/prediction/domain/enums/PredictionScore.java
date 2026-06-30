package com.worldcup.prediction.domain.enums;

public enum PredictionScore {
    EXACT(3),
    /** Knockout match: exact 90-min draw score predicted AND correct playoff winner picked. */
    EXACT_DRAW_WINNER(3),
    CORRECT_DRAW(2),
    CORRECT_WINNER(1),
    WRONG(0),
    PENDING(0);

    private final int points;

    PredictionScore(int points) {
        this.points = points;
    }

    public int getPoints() {
        return points;
    }
}
