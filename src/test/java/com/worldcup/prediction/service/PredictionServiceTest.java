package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.dto.PredictionDto;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictionServiceTest {

    @Mock private PredictionRepository predictionRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private UserRepository userRepository;

    private PredictionService predictionService;

    private final LocalDateTime kickoff      = LocalDateTime.of(2026, 6, 11, 18, 0);
    private final LocalDateTime openTime     = kickoff.minusHours(24);
    private final LocalDateTime lockTime     = kickoff.minusHours(1);
    private final LocalDateTime nowOpen      = kickoff.minusHours(10);  // window open
    private final LocalDateTime nowLocked    = kickoff.plusHours(2);    // past lock
    private final LocalDateTime nowBeforeOpen = kickoff.minusHours(25); // not yet open

    private User testUser;

    @BeforeEach
    void setUp() {
        predictionService = new PredictionService(predictionRepository, matchRepository, userRepository);
        testUser = User.builder().id(42L).email("test@example.com")
                .firstName("Test").lastName("User")
                .status(UserStatus.ACTIVE).role(UserRole.PARTICIPANT).build();
    }

    @Nested
    @DisplayName("isWindowOpen")
    class IsWindowOpen {

        @Test @DisplayName("returns true when now is within [openTime, lockTime)")
        void openWindow_returnsTrue() {
            assertThat(predictionService.isWindowOpen(buildMatch(1L, openTime, lockTime), nowOpen)).isTrue();
        }

        @Test @DisplayName("returns false when now is before openTime")
        void beforeOpen_returnsFalse() {
            assertThat(predictionService.isWindowOpen(buildMatch(1L, openTime, lockTime), nowBeforeOpen)).isFalse();
        }

        @Test @DisplayName("returns false when now is exactly at lockTime")
        void atLockTime_returnsFalse() {
            assertThat(predictionService.isWindowOpen(buildMatch(1L, openTime, lockTime), lockTime)).isFalse();
        }

        @Test @DisplayName("returns false when now is after lockTime")
        void afterLock_returnsFalse() {
            assertThat(predictionService.isWindowOpen(buildMatch(1L, openTime, lockTime), nowLocked)).isFalse();
        }

        @Test @DisplayName("returns true exactly at openTime")
        void atOpenTime_returnsTrue() {
            assertThat(predictionService.isWindowOpen(buildMatch(1L, openTime, lockTime), openTime)).isTrue();
        }
    }

    @Nested
    @DisplayName("submitPredictions")
    class SubmitPredictions {

        @Test @DisplayName("happy path — saves all predictions when window is open")
        void happyPath_savesAll() {
            Match m1 = buildMatch(1L, openTime, lockTime);
            Match m2 = buildMatch(2L, openTime, lockTime);

            when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));
            when(matchRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(m1, m2));
            when(predictionRepository.findByUserIdAndMatchId(eq(42L), any()))
                    .thenReturn(Optional.empty());
            when(predictionRepository.save(any(Prediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            List<Prediction> result = predictionService.submitPredictions(
                    42L, List.of(new PredictionDto(1L, 2, 1), new PredictionDto(2L, 0, 0)), nowOpen);

            assertThat(result).hasSize(2);
            verify(predictionRepository, times(2)).save(any(Prediction.class));
        }

        @Test @DisplayName("upserts existing prediction")
        void upsert_updatesExisting() {
            Match m1 = buildMatch(1L, openTime, lockTime);
            Prediction existing = Prediction.builder()
                    .user(testUser).match(m1).predictedHome(1).predictedAway(0).build();

            when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));
            when(matchRepository.findAllById(List.of(1L))).thenReturn(List.of(m1));
            when(predictionRepository.findByUserIdAndMatchId(42L, 1L))
                    .thenReturn(Optional.of(existing));
            when(predictionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            predictionService.submitPredictions(42L, List.of(new PredictionDto(1L, 3, 2)), nowOpen);

            ArgumentCaptor<Prediction> captor = ArgumentCaptor.forClass(Prediction.class);
            verify(predictionRepository).save(captor.capture());
            assertThat(captor.getValue().getPredictedHome()).isEqualTo(3);
            assertThat(captor.getValue().getPredictedAway()).isEqualTo(2);
        }

        @Test @DisplayName("throws PredictionWindowClosedException when window is locked")
        void windowLocked_throws() {
            when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));
            when(matchRepository.findAllById(List.of(1L)))
                    .thenReturn(List.of(buildMatch(1L, openTime, lockTime)));

            assertThatThrownBy(() -> predictionService.submitPredictions(
                    42L, List.of(new PredictionDto(1L, 1, 0)), nowLocked))
                    .isInstanceOf(PredictionService.PredictionWindowClosedException.class)
                    .hasMessageContaining("prediction window");
        }

        @Test @DisplayName("throws when window not yet open")
        void windowNotYetOpen_throws() {
            when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));
            when(matchRepository.findAllById(List.of(1L)))
                    .thenReturn(List.of(buildMatch(1L, openTime, lockTime)));

            assertThatThrownBy(() -> predictionService.submitPredictions(
                    42L, List.of(new PredictionDto(1L, 1, 0)), nowBeforeOpen))
                    .isInstanceOf(PredictionService.PredictionWindowClosedException.class);
        }

        @Test @DisplayName("throws IllegalArgumentException for empty list")
        void emptyList_throws() {
            assertThatThrownBy(() -> predictionService.submitPredictions(42L, List.of(), nowOpen))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test @DisplayName("throws MatchNotFoundException when matchId does not exist")
        void unknownMatch_throws() {
            when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));
            when(matchRepository.findAllById(List.of(999L))).thenReturn(List.of());

            assertThatThrownBy(() -> predictionService.submitPredictions(
                    42L, List.of(new PredictionDto(999L, 1, 0)), nowOpen))
                    .isInstanceOf(PredictionService.MatchNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getPredictionsForMatch")
    class GetPredictionsForMatch {

        @Test @DisplayName("returns predictions after window locks (non-admin)")
        void afterLock_returns() {
            Match match = buildMatch(10L, openTime, lockTime);
            Prediction pred = Prediction.builder().user(testUser).match(match)
                    .predictedHome(2).predictedAway(1).build();
            when(predictionRepository.findByMatchId(10L)).thenReturn(List.of(pred));

            assertThat(predictionService.getPredictionsForMatch(match, nowLocked, false)).hasSize(1);
        }

        @Test @DisplayName("returns empty before window locks (non-admin)")
        void beforeLock_nonAdmin_empty() {
            Match match = buildMatch(10L, openTime, lockTime);

            assertThat(predictionService.getPredictionsForMatch(match, nowOpen, false)).isEmpty();
            verify(predictionRepository, never()).findByMatchId(any());
        }

        @Test @DisplayName("admin always sees predictions")
        void admin_beforeLock_returns() {
            Match match = buildMatch(10L, openTime, lockTime);
            Prediction pred = Prediction.builder().user(testUser).match(match)
                    .predictedHome(1).predictedAway(1).build();
            when(predictionRepository.findByMatchId(10L)).thenReturn(List.of(pred));

            assertThat(predictionService.getPredictionsForMatch(match, nowOpen, true)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getPredictionsForUser")
    class GetPredictionsForUser {

        @Test @DisplayName("returns all predictions for user")
        void returnsOwn() {
            Match m1 = buildMatch(1L, openTime, lockTime);
            Match m2 = buildMatch(2L, openTime, lockTime);
            when(predictionRepository.findByUserId(5L)).thenReturn(List.of(
                    Prediction.builder().user(testUser).match(m1).predictedHome(2).predictedAway(0).build(),
                    Prediction.builder().user(testUser).match(m2).predictedHome(1).predictedAway(1).build()
            ));

            assertThat(predictionService.getPredictionsForUser(5L)).hasSize(2);
        }

        @Test @DisplayName("returns empty when no predictions")
        void noPredictions_empty() {
            when(predictionRepository.findByUserId(99L)).thenReturn(List.of());
            assertThat(predictionService.getPredictionsForUser(99L)).isEmpty();
        }
    }

    // helper — id must be set explicitly because JPA @GeneratedValue doesn't run in unit tests
    private Match buildMatch(Long id, LocalDateTime openTime, LocalDateTime closeTime) {
        Match m = Match.builder()
                .matchNumber(id.intValue())
                .stage(MatchStage.GROUP)
                .roundLabel("Group Stage Round 1")
                .kickoffTime(openTime.plusHours(24))
                .status(MatchStatus.SCHEDULED)
                .predictionWindowOpensAt(openTime)
                .predictionWindowClosesAt(closeTime)
                .build();
        m.setId(id);
        return m;
    }
}
