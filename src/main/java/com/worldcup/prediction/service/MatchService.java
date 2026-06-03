package com.worldcup.prediction.service;

import com.worldcup.prediction.dto.FixtureViewDto;
import java.util.List;

public interface MatchService {

    List<FixtureViewDto> getAllFixtures();

    List<FixtureViewDto> getGroupStageFixtures();

    List<FixtureViewDto> getKnockoutFixtures();

    List<FixtureViewDto> getTodayFixtures();

    FixtureViewDto getNextPredictableMatch(Long userId);

    int getOpenMatchCount();
}
