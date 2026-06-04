package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Team;
import com.worldcup.prediction.domain.TournamentWinnerPrediction;
import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.dto.TournamentWinnerPredictionDto;
import com.worldcup.prediction.repository.CommunityRepository;
import com.worldcup.prediction.repository.TeamRepository;
import com.worldcup.prediction.repository.TournamentWinnerPredictionRepository;
import com.worldcup.prediction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Manages the single "tournament winner" prediction per participant.
 *
 * Visibility: visible to everyone immediately after submission (by design — creates discussion).
 * Points: +10 awarded once when tournament winner is confirmed via awardPoints().
 */
@Service
@RequiredArgsConstructor
public class TournamentWinnerPredictionService {

    private final TournamentWinnerPredictionRepository repository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final ScoringService scoringService;
    private final CommunityRepository communityRepository;

    /**
     * Submit or update a user's tournament winner prediction within a community.
     *
     * @param userId      the participant's ID
     * @param dto         the prediction DTO (flagCode must not be blank)
     * @param communityId the community context
     * @return the saved entity
     * @throws IllegalArgumentException if dto is null or flagCode is blank
     * @throws TeamNotFoundException    if the flagCode does not match any team
     */
    @Transactional
    public TournamentWinnerPrediction submitOrUpdate(Long userId, TournamentWinnerPredictionDto dto, Long communityId) {
        if (dto == null) {
            throw new IllegalArgumentException("TournamentWinnerPredictionDto cannot be null");
        }
        String code = dto.getFlagCode();
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("flagCode must not be blank");
        }

        Team team = teamRepository.findByFlagCodeIgnoreCase(code)
                .orElseThrow(() -> new TeamNotFoundException("Team not found for flagCode: " + code));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Optional<TournamentWinnerPrediction> existing = repository.findByUserIdAndCommunityId(userId, communityId);
        TournamentWinnerPrediction prediction;
        if (existing.isPresent()) {
            prediction = existing.get();
            prediction.setTeam(team);
        } else {
            prediction = TournamentWinnerPrediction.builder()
                    .user(user)
                    .team(team)
                    .community(communityRepository.findById(communityId).orElseThrow())
                    .build();
        }
        return repository.save(prediction);
    }

    /** Retrieve a user's tournament winner prediction within a community. Empty if not yet submitted. */
    public Optional<TournamentWinnerPrediction> getForUser(Long userId, Long communityId) {
        return repository.findByUserIdAndCommunityId(userId, communityId);
    }

    /** All submitted tournament winner predictions (public + admin view). */
    public List<TournamentWinnerPrediction> getAll() {
        return repository.findAllWithDetails();
    }

    /** All submitted tournament winner predictions for a specific community. */
    public List<TournamentWinnerPrediction> getAllByCommunity(Long communityId) {
        return repository.findAllWithDetailsByCommunityId(communityId);
    }

    /**
     * Award +10 points to all participants who correctly predicted the tournament winner.
     * Called once when the Final result is confirmed.
     *
     * @param actualWinnerFlagCode the flag code of the actual winner (e.g. "br")
     */
    @Transactional
    public void awardPoints(String actualWinnerFlagCode) {
        Team winner = teamRepository.findByFlagCodeIgnoreCase(actualWinnerFlagCode)
                .orElseThrow(() -> new TeamNotFoundException("Winner team not found: " + actualWinnerFlagCode));

        List<TournamentWinnerPrediction> correct = repository.findByTeamId(winner.getId());
        for (TournamentWinnerPrediction p : correct) {
            if (!p.isScored()) {
                int points = scoringService.tournamentWinnerPoints(
                        p.getTeam().getFlagCode(), actualWinnerFlagCode);
                p.setPointsAwarded(points);
                p.setScored(true);
                repository.save(p);
            }
        }
    }

    public static class TeamNotFoundException extends RuntimeException {
        public TeamNotFoundException(String message) { super(message); }
    }
}
