package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.MatchStage;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.domain.enums.WindowMode;
import com.worldcup.prediction.dto.PredictionDto;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.repository.UserRepository;
import com.worldcup.prediction.service.RoundWindowService;
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
    @Mock private CommunityRepository communityRepository;
    @Mock private RoundWindowService roundWindowService;
    @Mock private TournamentSettingsService tournamentSettingsService;
    @Mock private PredictionWindowService predictionWindowService;

    private PredictionService predictionService;

    private final LocalDateTime kickoff      = LocalDateTime.of(2026, 6, 11, 18, 0);
    private final LocalDateTime openTime     = kickoff.minusHours(24);
    private final LocalDateTime lockTime     = kickoff.minusHours(1);
    private final LocalDateTime nowOpen      = kickoff.minusHours(10);
    private final LocalDateTime nowLocked    = kickoff.plusHours(2);
    private final LocalDateTime nowBeforeOpen = kickoff.minusHours(25);

    private User testUser;
    private Community testCommunity;
    private static final Long COMMUNITY_ID = 100L;

    @BeforeEach
    void setUp() {
        predictionService = new PredictionService(predictionRepository, matchRepository, userRepository, communityRepository, roundWindowService, tournamentSettingsService, predictionWindowService);
        lenient().when(tournamentSettingsService.getEffectiveMode(any())).thenReturn(WindowMode.ROUND);
        testUser = User.builder().id(42L).email("test@example.com")
                .firstName("Test").lastName("User")
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
        testCommunity = Community.builder().id(COMMUNITY_ID).name("Test").slug("test").build();
    }

    @Nested
    @DisplayName("isWindowOpen")
    class IsWindowOpen {

        @Test @DisplayName("2-arg overload uses round service directly")
        void roundOpen_returnsTrue() {
            Match m = buildMatch(1L, openTime, lockTime);
            when(roundWindowService.isRoundOpen("Group Stage Round 1", nowOpen)).thenReturn(true);
            assertThat(predictionService.isWindowOpen(m, nowOpen)).isTrue();
        }

        @Test @DisplayName("2-arg overload returns false when round is closed")
        void roundClosed_returnsFalse() {
            Match m = buildMatch(1L, openTime, lockTime);
            when(roundWindowService.isRoundOpen("Group Stage Round 1", nowLocked)).thenReturn(false);
            assertThat(predictionService.isWindowOpen(m, nowLocked)).isFalse();
        }

        @Test @DisplayName("3-arg ROUND mode delegates to roundWindowService")
        void threeArg_roundMode_delegatesToRoundService() {
            Match m = buildMatch(1L, openTime, lockTime);
            when(tournamentSettingsService.getEffectiveMode(COMMUNITY_ID)).thenReturn(WindowMode.ROUND);
            when(roundWindowService.isRoundOpen("Group Stage Round 1", nowOpen)).thenReturn(true);
            assertThat(predictionService.isWindowOpen(m, nowOpen, COMMUNITY_ID)).isTrue();
        }

        @Test @DisplayName("3-arg DAILY mode delegates to predictionWindowService")
        void threeArg_dailyMode_delegatesToPredictionWindowService() {
            Match m = buildMatch(1L, openTime, lockTime);
            when(tournamentSettingsService.getEffectiveMode(COMMUNITY_ID)).thenReturn(WindowMode.DAILY);
            when(predictionWindowService.isWindowOpen(m, nowOpen, COMMUNITY_ID)).thenReturn(true);
            assertThat(predictionService.isWindowOpen(m, nowOpen, COMMUNITY_ID)).isTrue();
            verify(roundWindowService, never()).isRoundOpen(any(), any());
        }
    }

    @Nested
    @DisplayName("submitPredictions")
    class SubmitPredictions {

        @Test @DisplayName("happy path — saves all predictions when window is open")
        void happyPath_savesAll() {
            Match m1 = buildMatch(1L, openTime, lockTime);
            Match m2 = buildMatch(2L, openTime, lockTime);

            when(roundWindowService.isRoundOpen("Group Stage Round 1", nowOpen)).thenReturn(true);
            when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));
            when(matchRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(m1, m2));
            when(predictionRepository.findByUserIdAndMatchIdAndCommunityId(eq(42L), any(), eq(COMMUNITY_ID)))
                    .thenReturn(Optional.empty());
            when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(testCommunity));
            when(predictionRepository.save(any(Prediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            List<Prediction> result = predictionService.submitPredictions(
                    42L, List.of(new PredictionDto(1L, 2, 1), new PredictionDto(2L, 0, 0)), nowOpen, COMMUNITY_ID);

            assertThat(result).hasSize(2);
            verify(predictionRepository, times(2)).save(any(Prediction.class));
        }

        @Test @DisplayName("upserts existing prediction")
        void upsert_updatesExisting() {
            Match m1 = buildMatch(1L, openTime, lockTime);
            Prediction existing = Prediction.builder()
                    .user(testUser).match(m1).predictedHome(1).predictedAway(0).build();

            when(roundWindowService.isRoundOpen("Group Stage Round 1", nowOpen)).thenReturn(true);
            when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));
            when(matchRepository.findAllById(List.of(1L))).thenReturn(List.of(m1));
            when(predictionRepository.findByUserIdAndMatchIdAndCommunityId(42L, 1L, COMMUNITY_ID))
                    .thenReturn(Optional.of(existing));
            when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(testCommunity));
            when(predictionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            predictionService.submitPredictions(42L, List.of(new PredictionDto(1L, 3, 2)), nowOpen, COMMUNITY_ID);

            ArgumentCaptor<Prediction> captor = ArgumentCaptor.forClass(Prediction.class);
            verify(predictionRepository).save(captor.capture());
            assertThat(captor.getValue().getPredictedHome()).isEqualTo(3);
            assertThat(captor.getValue().getPredictedAway()).isEqualTo(2);
        }

        @Test @DisplayName("throws PredictionWindowClosedException when window is locked")
        void windowLocked_throws() {
            when(roundWindowService.isRoundOpen("Group Stage Round 1", nowLocked)).thenReturn(false);
            when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));
            when(matchRepository.findAllById(List.of(1L)))
                    .thenReturn(List.of(buildMatch(1L, openTime, lockTime)));

            assertThatThrownBy(() -> predictionService.submitPredictions(
                    42L, List.of(new PredictionDto(1L, 1, 0)), nowLocked, COMMUNITY_ID))
                    .isInstanceOf(PredictionService.PredictionWindowClosedException.class)
                    .hasMessageContaining("prediction window");
        }

        @Test @DisplayName("throws when window not yet open")
        void windowNotYetOpen_throws() {
            when(roundWindowService.isRoundOpen("Group Stage Round 1", nowBeforeOpen)).thenReturn(false);
            when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));
            when(matchRepository.findAllById(List.of(1L)))
                    .thenReturn(List.of(buildMatch(1L, openTime, lockTime)));

            assertThatThrownBy(() -> predictionService.submitPredictions(
                    42L, List.of(new PredictionDto(1L, 1, 0)), nowBeforeOpen, COMMUNITY_ID))
                    .isInstanceOf(PredictionService.PredictionWindowClosedException.class);
        }

        @Test @DisplayName("throws IllegalArgumentException for empty list")
        void emptyList_throws() {
            assertThatThrownBy(() -> predictionService.submitPredictions(42L, List.of(), nowOpen, COMMUNITY_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test @DisplayName("throws MatchNotFoundException when matchId does not exist")
        void unknownMatch_throws() {
            when(userRepository.findById(42L)).thenReturn(Optional.of(testUser));
            when(matchRepository.findAllById(List.of(999L))).thenReturn(List.of());

            assertThatThrownBy(() -> predictionService.submitPredictions(
                    42L, List.of(new PredictionDto(999L, 1, 0)), nowOpen, COMMUNITY_ID))
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
            when(roundWindowService.isRoundOpen("Group Stage Round 1", nowLocked)).thenReturn(false);
            when(predictionRepository.findByMatchIdAndCommunityId(10L, COMMUNITY_ID)).thenReturn(List.of(pred));

            assertThat(predictionService.getPredictionsForMatch(match, nowLocked, false, COMMUNITY_ID)).hasSize(1);
        }

        @Test @DisplayName("returns empty before window locks (non-admin)")
        void beforeLock_nonAdmin_empty() {
            Match match = buildMatch(10L, openTime, lockTime);
            when(roundWindowService.isRoundOpen("Group Stage Round 1", nowOpen)).thenReturn(true);

            assertThat(predictionService.getPredictionsForMatch(match, nowOpen, false, COMMUNITY_ID)).isEmpty();
            verify(predictionRepository, never()).findByMatchIdAndCommunityId(any(), any());
        }

        @Test @DisplayName("admin always sees predictions")
        void admin_beforeLock_returns() {
            Match match = buildMatch(10L, openTime, lockTime);
            Prediction pred = Prediction.builder().user(testUser).match(match)
                    .predictedHome(1).predictedAway(1).build();
            when(roundWindowService.isRoundOpen("Group Stage Round 1", nowOpen)).thenReturn(true);
            when(predictionRepository.findByMatchIdAndCommunityId(10L, COMMUNITY_ID)).thenReturn(List.of(pred));

            assertThat(predictionService.getPredictionsForMatch(match, nowOpen, true, COMMUNITY_ID)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getPredictionsForUser")
    class GetPredictionsForUser {

        @Test @DisplayName("returns all predictions for user in community")
        void returnsOwn() {
            Match m1 = buildMatch(1L, openTime, lockTime);
            Match m2 = buildMatch(2L, openTime, lockTime);
            when(predictionRepository.findByUserIdAndCommunityId(5L, COMMUNITY_ID)).thenReturn(List.of(
                    Prediction.builder().user(testUser).match(m1).predictedHome(2).predictedAway(0).build(),
                    Prediction.builder().user(testUser).match(m2).predictedHome(1).predictedAway(1).build()
            ));

            assertThat(predictionService.getPredictionsForUser(5L, COMMUNITY_ID)).hasSize(2);
        }

        @Test @DisplayName("returns empty when no predictions")
        void noPredictions_empty() {
            when(predictionRepository.findByUserIdAndCommunityId(99L, COMMUNITY_ID)).thenReturn(List.of());
            assertThat(predictionService.getPredictionsForUser(99L, COMMUNITY_ID)).isEmpty();
        }
    }

    private Match buildMatch(Long id, LocalDateTime openTime, LocalDateTime closeTime) {
        Match m = Match.builder()
                .matchNumber(id.intValue())
                .stage(MatchStage.GROUP)
                .roundLabel("Group Stage Round 1")
                .kickoffTime(openTime.plusHours(24))
                .status(MatchStatus.SCHEDULED)
                .build();
        m.setId(id);
        return m;
    }
}
