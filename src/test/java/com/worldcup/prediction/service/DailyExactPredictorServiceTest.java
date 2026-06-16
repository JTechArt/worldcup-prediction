package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.*;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyExactPredictorServiceTest {

    @Mock MatchRepository matchRepository;
    @Mock PredictionRepository predictionRepository;
    @InjectMocks DailyExactPredictorService service;

    @Test
    void exactMatchDto_includesFifaCodes() {
        Team home = Team.builder().id(1L).name("Brazil").fifaCode("BRA").flagCode("br").build();
        Team away = Team.builder().id(2L).name("Argentina").fifaCode("ARG").flagCode("ar").build();

        Match match = new Match();
        match.setId(10L);
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setHomeScore(2);
        match.setAwayScore(1);
        match.setStatus(MatchStatus.COMPLETED);
        match.setKickoffTime(LocalDateTime.now().minusHours(2));
        match.setRoundLabel("Group Stage");

        User user = new User();
        user.setId(99L);
        user.setFirstName("Test");
        user.setLastName("User");

        Prediction prediction = new Prediction();
        prediction.setUser(user);
        prediction.setMatch(match);

        when(matchRepository.findByStatusWithTeams(MatchStatus.COMPLETED)).thenReturn(List.of(match));
        when(predictionRepository.findExactPredictionsByMatchIdsAndCommunityId(eq(List.of(10L)), eq(1L)))
                .thenReturn(List.of(prediction));

        var result = service.getLastMatchdayExactPredictors(1L, LocalDateTime.now());

        assertThat(result).hasSize(1);
        var exactMatch = result.get(0).getExactMatches().get(0);
        assertThat(exactMatch.getHomeTeamFifaCode()).isEqualTo("BRA");
        assertThat(exactMatch.getAwayTeamFifaCode()).isEqualTo("ARG");
    }
}
