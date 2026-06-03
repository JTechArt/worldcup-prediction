package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.TournamentWinnerPrediction;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.PredictionScore;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.dto.LeaderboardEntryDto;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.repository.TournamentWinnerPredictionRepository;
import com.worldcup.prediction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Computes the leaderboard fresh on each request.
 * No caching in v1 — sufficient for 100–200 users.
 *
 * Tiebreaker chain (spec §5):
 *   1. totalPoints DESC
 *   2. exactCount DESC
 *   3. correctWinnerCount DESC
 *   4. tournamentWinnerCorrect DESC (true > false)
 *   5. createdAt ASC (earliest registration wins)
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaderboardService {

    private final UserRepository userRepository;
    private final PredictionRepository predictionRepository;
    private final TournamentWinnerPredictionRepository twpRepository;

    /** Returns the full leaderboard sorted by the 5-level tiebreaker chain. Only ACTIVE users. */
    public List<LeaderboardEntryDto> getFullLeaderboard() {
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);

        List<LeaderboardEntryDto> unsorted = activeUsers.stream()
                .map(this::buildEntry)
                .sorted(leaderboardComparator())
                .toList();

        return assignRanks(unsorted);
    }

    /** Top N entries for the home page mini-widget. */
    public List<LeaderboardEntryDto> getTopN(int n) {
        List<LeaderboardEntryDto> full = getFullLeaderboard();
        return full.subList(0, Math.min(n, full.size()));
    }

    /** Entry for a specific user including their current rank. Empty if user not ACTIVE. */
    public Optional<LeaderboardEntryDto> getEntryForUser(Long userId) {
        return getFullLeaderboard().stream()
                .filter(e -> e.getUserId().equals(userId))
                .findFirst();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private LeaderboardEntryDto buildEntry(User user) {
        List<Prediction> predictions = predictionRepository.findByUser(user);

        int totalPoints = predictions.stream().mapToInt(Prediction::getPointsAwarded).sum();
        int exactCount = (int) predictions.stream()
                .filter(p -> PredictionScore.EXACT == p.getScoreResult()).count();
        int correctWinnerCount = (int) predictions.stream()
                .filter(p -> PredictionScore.CORRECT_WINNER == p.getScoreResult()).count();
        int drawCount = (int) predictions.stream()
                .filter(p -> PredictionScore.CORRECT_DRAW == p.getScoreResult()).count();

        Optional<TournamentWinnerPrediction> twp = twpRepository.findByUser(user);
        boolean tournamentWinnerCorrect = twp.map(TournamentWinnerPrediction::isScored).orElse(false);
        String predictedWinnerFlagCode = twp
                .map(t -> t.getTeam().getFlagCode())
                .orElse(null);

        return new LeaderboardEntryDto(
                0, // rank assigned later
                user.getId(),
                user.getFullName(),
                user.getAvatarUrl(),
                predictedWinnerFlagCode,
                totalPoints,
                exactCount,
                correctWinnerCount,
                drawCount,
                tournamentWinnerCorrect,
                0  // rankChange always 0 in v1
        );
    }

    private Comparator<LeaderboardEntryDto> leaderboardComparator() {
        return Comparator
                .<LeaderboardEntryDto>comparingInt(e -> -e.getTotalPoints())
                .thenComparingInt(e -> -e.getExactCount())
                .thenComparingInt(e -> -e.getCorrectWinnerCount())
                .thenComparingInt(e -> e.isTournamentWinnerCorrect() ? 0 : 1)
                .thenComparing(e -> {
                    // Final tiebreaker: earliest registration date
                    return userRepository.findById(e.getUserId())
                            .map(u -> u.getCreatedAt() != null
                                    ? u.getCreatedAt()
                                    : LocalDateTime.MAX)
                            .orElse(LocalDateTime.MAX);
                });
    }

    private List<LeaderboardEntryDto> assignRanks(List<LeaderboardEntryDto> sorted) {
        List<LeaderboardEntryDto> ranked = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            LeaderboardEntryDto e = sorted.get(i);
            ranked.add(new LeaderboardEntryDto(
                    i + 1,
                    e.getUserId(),
                    e.getDisplayName(),
                    e.getAvatarUrl(),
                    e.getPredictedWinnerFlagCode(),
                    e.getTotalPoints(),
                    e.getExactCount(),
                    e.getCorrectWinnerCount(),
                    e.getDrawCount(),
                    e.isTournamentWinnerCorrect(),
                    e.getRankChange()
            ));
        }
        return ranked;
    }
}
