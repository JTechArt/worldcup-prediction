package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.TournamentWinnerPrediction;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.PredictionScore;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.repository.TournamentWinnerPredictionRepository;
import com.worldcup.prediction.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock UserRepository userRepository;
    @Mock PredictionRepository predictionRepository;
    @Mock TournamentWinnerPredictionRepository twpRepository;

    @InjectMocks LeaderboardService leaderboardService;

    // ---- helpers ----

    private User makeUser(Long id, String firstName, String lastName, LocalDateTime createdAt) {
        User u = User.builder()
                .email(firstName.toLowerCase() + "@example.com")
                .firstName(firstName).lastName(lastName)
                .status(UserStatus.ACTIVE).role(UserRole.USER)
                .build();
        u.setId(id);
        return u;
    }

    private Prediction makePrediction(User user, int points, PredictionScore score) {
        Prediction p = Prediction.builder()
                .user(user)
                .predictedHome(1).predictedAway(0)
                .scoreResult(score).pointsAwarded(points)
                .build();
        // match not needed for leaderboard service (only user + pointsAwarded + scoreResult used)
        return p;
    }

    private TournamentWinnerPrediction makeTwp(User user, String flagCode, boolean scored) {
        Team team = Team.builder().fifaCode("TST").flagCode(flagCode).name("Test").build();
        return TournamentWinnerPrediction.builder()
                .user(user).team(team).scored(scored).build();
    }

    private final LocalDateTime jan1 = LocalDateTime.of(2026, 1, 1, 0, 0);
    private final LocalDateTime jan2 = LocalDateTime.of(2026, 1, 2, 0, 0);
    private final LocalDateTime jan10 = LocalDateTime.of(2026, 1, 10, 0, 0);

    // ---- tests ----

    @Test
    void getFullLeaderboard_sortsByTotalPointsDescending() {
        User alice = makeUser(1L, "Alice", "Smith", jan1);
        User bob   = makeUser(2L, "Bob",   "Jones", jan2);

        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(alice, bob));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of(
                makePrediction(alice, 3, PredictionScore.EXACT),
                makePrediction(alice, 1, PredictionScore.CORRECT_WINNER)
        ));
        when(predictionRepository.findByUser(bob)).thenReturn(List.of(
                makePrediction(bob, 3, PredictionScore.EXACT)
        ));
        when(twpRepository.findByUser(alice)).thenReturn(Optional.empty());
        when(twpRepository.findByUser(bob)).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserId()).isEqualTo(1L); // Alice: 4pts
        assertThat(result.get(0).getTotalPoints()).isEqualTo(4);
        assertThat(result.get(1).getUserId()).isEqualTo(2L); // Bob: 3pts
        assertThat(result.get(0).getRank()).isEqualTo(1);
        assertThat(result.get(1).getRank()).isEqualTo(2);
    }

    @Test
    void getFullLeaderboard_tiebreaker_exactCountWins() {
        User alice = makeUser(1L, "Alice", "Smith", jan1);
        User bob   = makeUser(2L, "Bob",   "Jones", jan2);

        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(bob, alice));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of(
                makePrediction(alice, 3, PredictionScore.EXACT),
                makePrediction(alice, 3, PredictionScore.EXACT)
        ));
        when(predictionRepository.findByUser(bob)).thenReturn(List.of(
                makePrediction(bob, 3, PredictionScore.EXACT),
                makePrediction(bob, 2, PredictionScore.CORRECT_DRAW),
                makePrediction(bob, 1, PredictionScore.CORRECT_WINNER)
        ));
        when(twpRepository.findByUser(any())).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        // Alice wins: 2 exact vs Bob's 1 exact (both 6pts total)
        assertThat(result.get(0).getUserId()).isEqualTo(1L);
        assertThat(result.get(0).getExactCount()).isEqualTo(2);
        assertThat(result.get(1).getExactCount()).isEqualTo(1);
    }

    @Test
    void getFullLeaderboard_tiebreaker_correctWinnerCountWins() {
        User alice = makeUser(1L, "Alice", "Smith", jan1);
        User bob   = makeUser(2L, "Bob",   "Jones", jan2);

        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(bob, alice));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of(
                makePrediction(alice, 3, PredictionScore.EXACT),
                makePrediction(alice, 1, PredictionScore.CORRECT_WINNER),
                makePrediction(alice, 1, PredictionScore.CORRECT_WINNER)
        ));
        when(predictionRepository.findByUser(bob)).thenReturn(List.of(
                makePrediction(bob, 3, PredictionScore.EXACT),
                makePrediction(bob, 2, PredictionScore.CORRECT_DRAW)
        ));
        when(twpRepository.findByUser(any())).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        // Alice: 5pts, 1 exact, 2 correct_winner
        // Bob:   5pts, 1 exact, 0 correct_winner → Alice wins
        assertThat(result.get(0).getUserId()).isEqualTo(1L);
        assertThat(result.get(0).getCorrectWinnerCount()).isEqualTo(2);
        assertThat(result.get(1).getCorrectWinnerCount()).isEqualTo(0);
    }

    @Test
    void getFullLeaderboard_tiebreaker_tournamentWinnerBonusFlag() {
        User alice = makeUser(1L, "Alice", "Smith", jan1);
        User bob   = makeUser(2L, "Bob",   "Jones", jan2);

        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(bob, alice));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of(
                makePrediction(alice, 3, PredictionScore.EXACT)
        ));
        when(predictionRepository.findByUser(bob)).thenReturn(List.of(
                makePrediction(bob, 3, PredictionScore.EXACT)
        ));
        when(twpRepository.findByUser(alice)).thenReturn(Optional.of(makeTwp(alice, "br", true)));
        when(twpRepository.findByUser(bob)).thenReturn(Optional.of(makeTwp(bob, "fr", false)));

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        assertThat(result.get(0).isTournamentWinnerCorrect()).isTrue();
        assertThat(result.get(1).isTournamentWinnerCorrect()).isFalse();
    }

    @Test
    void getFullLeaderboard_tiebreaker_earliestRegistrationWins() {
        User alice = makeUser(1L, "Alice", "Smith", jan1);  // earlier
        User bob   = makeUser(2L, "Bob",   "Jones", jan10); // later
        // Set createdAt manually since builder doesn't use it directly in unit tests
        alice.setCreatedAt(jan1);
        bob.setCreatedAt(jan10);

        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(bob, alice));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of(
                makePrediction(alice, 3, PredictionScore.EXACT)
        ));
        when(predictionRepository.findByUser(bob)).thenReturn(List.of(
                makePrediction(bob, 3, PredictionScore.EXACT)
        ));
        when(twpRepository.findByUser(alice)).thenReturn(Optional.of(makeTwp(alice, "br", true)));
        when(twpRepository.findByUser(bob)).thenReturn(Optional.of(makeTwp(bob, "br", true)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        assertThat(result.get(0).getUserId()).isEqualTo(1L); // alice registered earlier
    }

    @Test
    void getFullLeaderboard_excludesPendingAndDisabledUsers() {
        User active = makeUser(1L, "Active", "User", jan1);

        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(active));
        when(predictionRepository.findByUser(active)).thenReturn(List.of());
        when(twpRepository.findByUser(active)).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(1L);
    }

    @Test
    void getFullLeaderboard_userWithNoTwp_flagUrlIsEmpty() {
        User alice = makeUser(1L, "Alice", "Smith", jan1);
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(alice));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of());
        when(twpRepository.findByUser(alice)).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        assertThat(result.get(0).getPredictedWinnerFlagCode()).isNull();
        assertThat(result.get(0).getFlagUrl()).isEmpty();
    }

    @Test
    void getTopN_returnsFirstNEntries() {
        List<User> users = List.of(
                makeUser(1L, "A", "One",   jan1),
                makeUser(2L, "B", "Two",   jan2),
                makeUser(3L, "C", "Three", LocalDateTime.of(2026,1,3,0,0)),
                makeUser(4L, "D", "Four",  LocalDateTime.of(2026,1,4,0,0)),
                makeUser(5L, "E", "Five",  LocalDateTime.of(2026,1,5,0,0))
        );
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(users);
        users.forEach(u -> {
            when(predictionRepository.findByUser(u)).thenReturn(List.of(
                    makePrediction(u, 3, PredictionScore.EXACT)
            ));
            when(twpRepository.findByUser(u)).thenReturn(Optional.empty());
        });

        List<LeaderboardEntryDto> top3 = leaderboardService.getTopN(3);

        assertThat(top3).hasSize(3);
        assertThat(top3.get(0).getRank()).isEqualTo(1);
        assertThat(top3.get(2).getRank()).isEqualTo(3);
    }

    @Test
    void getTopN_whenFewerThanNUsers_returnsAll() {
        User alice = makeUser(1L, "Alice", "Smith", jan1);
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(alice));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of());
        when(twpRepository.findByUser(alice)).thenReturn(Optional.empty());

        assertThat(leaderboardService.getTopN(10)).hasSize(1);
    }

    @Test
    void getEntryForUser_returnsCorrectRankAndData() {
        User alice = makeUser(1L, "Alice", "Smith", jan1);
        User bob   = makeUser(2L, "Bob",   "Jones", jan2);

        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(alice, bob));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of(
                makePrediction(alice, 3, PredictionScore.EXACT)
        ));
        when(predictionRepository.findByUser(bob)).thenReturn(List.of(
                makePrediction(bob, 1, PredictionScore.CORRECT_WINNER)
        ));
        when(twpRepository.findByUser(any())).thenReturn(Optional.empty());

        Optional<LeaderboardEntryDto> entry = leaderboardService.getEntryForUser(2L);

        assertThat(entry).isPresent();
        assertThat(entry.get().getRank()).isEqualTo(2);
        assertThat(entry.get().getTotalPoints()).isEqualTo(1);
    }

    @Test
    void getEntryForUser_returnsEmptyForUnknownUser() {
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of());

        assertThat(leaderboardService.getEntryForUser(999L)).isEmpty();
    }

    @Test
    void getFullLeaderboard_countsOutcomesCorrectly() {
        User alice = makeUser(1L, "Alice", "Smith", jan1);
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(alice));
        when(predictionRepository.findByUser(alice)).thenReturn(List.of(
                makePrediction(alice, 3, PredictionScore.EXACT),
                makePrediction(alice, 3, PredictionScore.EXACT),
                makePrediction(alice, 2, PredictionScore.CORRECT_DRAW),
                makePrediction(alice, 1, PredictionScore.CORRECT_WINNER),
                makePrediction(alice, 0, PredictionScore.WRONG),
                makePrediction(alice, 0, PredictionScore.PENDING)
        ));
        when(twpRepository.findByUser(alice)).thenReturn(Optional.empty());

        List<LeaderboardEntryDto> result = leaderboardService.getFullLeaderboard();

        assertThat(result.get(0).getTotalPoints()).isEqualTo(9);
        assertThat(result.get(0).getExactCount()).isEqualTo(2);
        assertThat(result.get(0).getDrawCount()).isEqualTo(1);
        assertThat(result.get(0).getCorrectWinnerCount()).isEqualTo(1);
    }
}
