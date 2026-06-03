package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.dto.PredictionDto;
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
 *   Open window: [match.predictionWindowOpensAt, match.predictionWindowClosesAt)
 *   All-or-nothing: all matches in the submitted batch must have open windows.
 *   Visibility: non-admin users see predictions only after predictionWindowClosesAt.
 */
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;

    // -------------------------------------------------------
    //  Window helpers
    // -------------------------------------------------------

    /**
     * True if predictions may be submitted for this match at the given time.
     * Window is [predictionWindowOpensAt, predictionWindowClosesAt) — inclusive open, exclusive close.
     */
    public boolean isWindowOpen(Match match, LocalDateTime now) {
        LocalDateTime opensAt = match.getPredictionWindowOpensAt();
        LocalDateTime closesAt = match.getPredictionWindowClosesAt();
        if (opensAt == null || closesAt == null) return false;
        return !now.isBefore(opensAt) && now.isBefore(closesAt);
    }

    // -------------------------------------------------------
    //  Submission
    // -------------------------------------------------------

    /**
     * Submit (or update) predictions for a user — all-or-nothing per invocation.
     *
     * @param userId  the participant's ID
     * @param dtos    list of predictions (must not be empty)
     * @param now     current time (injectable for testability)
     * @return saved Prediction entities
     * @throws IllegalArgumentException        if dtos is empty
     * @throws MatchNotFoundException          if any matchId does not exist
     * @throws PredictionWindowClosedException if any match window is not currently open
     */
    @Transactional
    public List<Prediction> submitPredictions(Long userId, List<PredictionDto> dtos, LocalDateTime now) {
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
            if (!isWindowOpen(matchMap.get(id), now)) {
                throw new PredictionWindowClosedException(
                        "The prediction window for match " + id + " is not open.");
            }
        }

        List<Prediction> saved = new ArrayList<>();
        for (PredictionDto dto : dtos) {
            Match match = matchMap.get(dto.getMatchId());
            Optional<Prediction> existing =
                    predictionRepository.findByUserIdAndMatchId(userId, dto.getMatchId());

            Prediction prediction;
            if (existing.isPresent()) {
                prediction = existing.get();
                prediction.setPredictedHome(dto.getHomeScore());
                prediction.setPredictedAway(dto.getAwayScore());
            } else {
                prediction = Prediction.builder()
                        .user(user)
                        .match(match)
                        .predictedHome(dto.getHomeScore())
                        .predictedAway(dto.getAwayScore())
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
     * Get all predictions for a match.
     * Non-admin users get an empty list until the window closes; admins always see all.
     */
    public List<Prediction> getPredictionsForMatch(Match match, LocalDateTime now, boolean isAdmin) {
        LocalDateTime closesAt = match.getPredictionWindowClosesAt();
        boolean windowLocked = closesAt != null && !now.isBefore(closesAt);
        if (!isAdmin && !windowLocked) {
            return List.of();
        }
        return predictionRepository.findByMatchId(match.getId());
    }

    /** Get all predictions submitted by a user. Own predictions are always visible. */
    public List<Prediction> getPredictionsForUser(Long userId) {
        return predictionRepository.findByUserId(userId);
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
