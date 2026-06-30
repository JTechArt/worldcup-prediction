package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Community;
import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.PlayoffWinnerPick;
import com.worldcup.prediction.dto.PredictionDto;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import com.worldcup.prediction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages match score predictions: submission, visibility, and window enforcement.
 *
 * Window rules:
 *   Round-level: window is determined by RoundWindowService (auto from kickoff times + admin override).
 *   All-or-nothing: all matches in the submitted batch must be in an open round.
 *   Visibility: non-admin users see predictions only after the round window closes.
 */
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final RoundWindowService roundWindowService;
    private final TournamentSettingsService tournamentSettingsService;
    private final PredictionWindowService predictionWindowService;

    // -------------------------------------------------------
    //  Window helpers
    // -------------------------------------------------------

    public boolean isWindowOpen(Match match, LocalDateTime now, Long communityId) {
        return switch (tournamentSettingsService.getEffectiveMode(communityId)) {
            case ROUND -> roundWindowService.isRoundOpen(match.getRoundLabel(), now);
            case DAILY -> predictionWindowService.isWindowOpen(match, now, communityId);
        };
    }

    // Backward-compatible overload for callers without a community context (always uses ROUND logic).
    public boolean isWindowOpen(Match match, LocalDateTime now) {
        return roundWindowService.isRoundOpen(match.getRoundLabel(), now);
    }

    // -------------------------------------------------------
    //  Submission
    // -------------------------------------------------------

    /**
     * Submit (or update) predictions for a user — all-or-nothing per invocation.
     *
     * @param userId      the participant's ID
     * @param dtos        list of predictions (must not be empty)
     * @param now         current time (injectable for testability)
     * @param communityId the community context for these predictions
     * @return saved Prediction entities
     * @throws IllegalArgumentException        if dtos is empty
     * @throws MatchNotFoundException          if any matchId does not exist
     * @throws PredictionWindowClosedException if any match window is not currently open
     */
    @Transactional
    public List<Prediction> submitPredictions(Long userId, List<PredictionDto> dtos, LocalDateTime now, Long communityId) {
        if (dtos == null || dtos.isEmpty()) {
            throw new IllegalArgumentException("Prediction list cannot be empty");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<Long> matchIds = dtos.stream().map(PredictionDto::getMatchId).toList();
        Map<Long, Match> matchMap = matchRepository.findAllById(matchIds)
                .stream().collect(Collectors.toMap(Match::getId, Function.identity()));

        for (Long id : matchIds) {
            if (!matchMap.containsKey(id)) {
                throw new MatchNotFoundException("Match not found: " + id);
            }
        }

        // All-or-nothing: validate all windows before persisting any
        for (Long id : matchIds) {
            if (!isWindowOpen(matchMap.get(id), now, communityId)) {
                throw new PredictionWindowClosedException(
                        "The prediction window for match " + id + " is not open.");
            }
        }

        var community = communityRepository.findById(communityId)
                .orElseThrow(() -> new IllegalArgumentException("Community not found: " + communityId));

        List<Prediction> saved = new ArrayList<>();
        for (PredictionDto dto : dtos) {
            Match match = matchMap.get(dto.getMatchId());
            Optional<Prediction> existing =
                    predictionRepository.findByUserIdAndMatchIdAndCommunityId(userId, dto.getMatchId(), communityId);

            PlayoffWinnerPick winnerPick = null;
            if ("HOME".equals(dto.getPlayoffWinner())) winnerPick = PlayoffWinnerPick.HOME;
            else if ("AWAY".equals(dto.getPlayoffWinner())) winnerPick = PlayoffWinnerPick.AWAY;

            Prediction prediction;
            if (existing.isPresent()) {
                prediction = existing.get();
                prediction.setPredictedHome(dto.getHomeScore());
                prediction.setPredictedAway(dto.getAwayScore());
                prediction.setPredictedPlayoffWinner(winnerPick);
            } else {
                prediction = Prediction.builder()
                        .user(user)
                        .match(match)
                        .community(community)
                        .predictedHome(dto.getHomeScore())
                        .predictedAway(dto.getAwayScore())
                        .predictedPlayoffWinner(winnerPick)
                        .build();
            }
            saved.add(predictionRepository.save(prediction));
        }
        return saved;
    }

    // -------------------------------------------------------
    //  Retrieval
    // -------------------------------------------------------

    /**
     * Get all predictions for a match within a community.
     * Non-admin users get an empty list until the window closes; admins always see all.
     */
    public List<Prediction> getPredictionsForMatch(Match match, LocalDateTime now, boolean isAdmin, Long communityId) {
        boolean windowClosed = !isWindowOpen(match, now, communityId);
        if (!isAdmin && !windowClosed) return List.of();
        return predictionRepository.findByMatchIdAndCommunityId(match.getId(), communityId);
    }

    /** Get all predictions submitted by a user within a community. Own predictions are always visible. */
    public List<Prediction> getPredictionsForUser(Long userId, Long communityId) {
        return predictionRepository.findByUserIdAndCommunityId(userId, communityId);
    }

    // -------------------------------------------------------
    //  Admin operations
    // -------------------------------------------------------

    /** Returns ALL predictions for a match (admin use — bypasses window checks). */
    public List<Prediction> findAllByMatchId(Long matchId) {
        return predictionRepository.findByMatchIdWithUsers(matchId);
    }

    /** Returns predictions for a match within a specific community (admin use — bypasses window checks). */
    public List<Prediction> findAllByMatchIdAndCommunityId(Long matchId, Long communityId) {
        return predictionRepository.findByMatchIdAndCommunityIdWithUsers(matchId, communityId);
    }

    /**
     * Creates a prediction on behalf of a user — bypasses window lock checks.
     * Admin backdoor for members who submitted predictions via email.
     * Re-scores immediately if the match result is already recorded.
     */
    @Transactional
    public Prediction createOnBehalfOf(Long userId, Long matchId, Long communityId,
                                        int homeScore, int awayScore,
                                        ScoringService scoringService) {
        if (predictionRepository.existsByUserIdAndMatchIdAndCommunityId(userId, matchId, communityId)) {
            throw new IllegalStateException(
                    "Prediction already exists for user " + userId + " / match " + matchId +
                    " / community " + communityId + "; use override instead.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
        Community community = communityRepository.findById(communityId)
                .orElseThrow(() -> new IllegalArgumentException("Community not found: " + communityId));

        Prediction prediction = Prediction.builder()
                .user(user)
                .match(match)
                .community(community)
                .predictedHome(homeScore)
                .predictedAway(awayScore)
                .editedByAdmin(true)
                .adminEditNote("Created on behalf of user by admin")
                .build();

        if (match.isCompleted() && match.getHomeScore() != null && match.getAwayScore() != null) {
            int pts = scoringService.calculatePoints(
                    match.getEffectiveHomeScore(), match.getEffectiveAwayScore(),
                    homeScore, awayScore);
            prediction.setPointsAwarded(pts);
            prediction.setScoreResult(scoringService.determineScoreResult(
                    match.getEffectiveHomeScore(), match.getEffectiveAwayScore(),
                    homeScore, awayScore));
        }

        return predictionRepository.save(prediction);
    }

    /**
     * Overrides a prediction's predicted scores.
     * Re-scores immediately if the match result is already recorded.
     */
    @Transactional
    public Prediction overridePrediction(Long predictionId, int homeScore, int awayScore,
                                         ScoringService scoringService) {
        Prediction p = predictionRepository.findById(predictionId)
                .orElseThrow(() -> new IllegalArgumentException("Prediction not found: " + predictionId));
        p.setPredictedHome(homeScore);
        p.setPredictedAway(awayScore);
        p.setEditedByAdmin(true);
        if (p.getMatch().isCompleted()
                && p.getMatch().getHomeScore() != null
                && p.getMatch().getAwayScore() != null) {
            int pts = scoringService.calculatePoints(
                    p.getMatch().getEffectiveHomeScore(),
                    p.getMatch().getEffectiveAwayScore(),
                    homeScore, awayScore);
            p.setPointsAwarded(pts);
        }
        return predictionRepository.save(p);
    }

    // -------------------------------------------------------
    //  Custom exceptions
    // -------------------------------------------------------

    public static class PredictionWindowClosedException extends RuntimeException {
        public PredictionWindowClosedException(String message) { super(message); }
    }

    public static class MatchNotFoundException extends RuntimeException {
        public MatchNotFoundException(String message) { super(message); }
    }
}
