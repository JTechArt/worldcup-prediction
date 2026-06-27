package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.PredictionScore;
import com.worldcup.prediction.dto.DailyExactPredictorDto;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyExactPredictorServiceCumulativeTest {

    @Mock
    private PredictionRepository predictionRepository;

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private DailyExactPredictorService service;

    @Test
    void getCumulativeHeroes_emptyWhenNoExactPredictions() {
        when(predictionRepository.findCumulativeExactPredictions(1L, "all"))
                .thenReturn(List.of());

        List<DailyExactPredictorDto> result = service.getCumulativeHeroes(1L, "all");

        assertThat(result).isEmpty();
    }

    @Test
    void getCumulativeHeroes_aggregatesCountByUser() {
        User user = User.builder().id(10L).firstName("Alice").lastName("Smith").build();
        Match m1 = matchGroupMatch(1L, 2, 1);
        Match m2 = matchGroupMatch(2L, 0, 0);
        Prediction p1 = prediction(user, m1);
        Prediction p2 = prediction(user, m2);

        when(predictionRepository.findCumulativeExactPredictions(1L, "all"))
                .thenReturn(List.of(p1, p2));

        List<DailyExactPredictorDto> result = service.getCumulativeHeroes(1L, "all");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExactCount()).isEqualTo(2);
        assertThat(result.get(0).getDisplayName()).isEqualTo("Alice Smith");
    }

    @Test
    void getCumulativeHeroes_sortsDescByExactCount() {
        User userA = User.builder().id(1L).firstName("Alice").lastName("A").build();
        User userB = User.builder().id(2L).firstName("Bob").lastName("B").build();
        Match m1 = matchGroupMatch(1L, 1, 0);
        Match m2 = matchGroupMatch(2L, 2, 1);
        Match m3 = matchGroupMatch(3L, 3, 2);
        // userB has 2 exact, userA has 1
        Prediction pA = prediction(userA, m1);
        Prediction pB1 = prediction(userB, m2);
        Prediction pB2 = prediction(userB, m3);

        when(predictionRepository.findCumulativeExactPredictions(1L, "all"))
                .thenReturn(List.of(pA, pB1, pB2));

        List<DailyExactPredictorDto> result = service.getCumulativeHeroes(1L, "all");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDisplayName()).isEqualTo("Bob B");
        assertThat(result.get(1).getDisplayName()).isEqualTo("Alice A");
    }

    @Test
    void getCumulativeHeroes_capsResultAt20() {
        // Build 25 users each with 1 exact prediction
        List<Prediction> predictions = new java.util.ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            User u = User.builder().id((long) i).firstName("User").lastName(String.valueOf(i)).build();
            Match m = matchGroupMatch((long) i, 1, 0);
            predictions.add(prediction(u, m));
        }

        when(predictionRepository.findCumulativeExactPredictions(1L, "all"))
                .thenReturn(predictions);

        List<DailyExactPredictorDto> result = service.getCumulativeHeroes(1L, "all");

        assertThat(result).hasSize(20);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Match matchGroupMatch(Long id, int home, int away) {
        return Match.builder()
                .id(id)
                .stage(MatchStage.GROUP)
                .homeScore(home)
                .awayScore(away)
                .build();
    }

    private Prediction prediction(User user, Match match) {
        return Prediction.builder()
                .user(user)
                .match(match)
                .scoreResult(PredictionScore.EXACT)
                .build();
    }
}
