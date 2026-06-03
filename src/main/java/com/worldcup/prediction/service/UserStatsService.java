package com.worldcup.prediction.service;

public interface UserStatsService {

    int getTotalPoints(Long userId);

    int getExactScoreCount(Long userId);

    int getTotalPredicted(Long userId);
}
