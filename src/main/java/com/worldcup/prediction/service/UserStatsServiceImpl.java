package com.worldcup.prediction.service;

import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserStatsServiceImpl implements UserStatsService {

    private final UserRepository userRepository;
    private final PredictionRepository predictionRepository;

    @Override
    public int getTotalPoints(Long userId) {
        return userRepository.findById(userId).map(u -> u.getTotalPoints()).orElse(0);
    }

    @Override
    public int getExactScoreCount(Long userId) {
        return userRepository.findById(userId).map(u -> u.getExactScoreCount()).orElse(0);
    }

    @Override
    public int getTotalPredicted(Long userId) {
        return predictionRepository.findByUserId(userId).size();
    }
}
