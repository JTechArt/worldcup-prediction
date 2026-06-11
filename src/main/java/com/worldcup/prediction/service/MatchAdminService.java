package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.domain.enums.PredictionScore;
import com.worldcup.prediction.repository.CommunityMembershipRepository;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Admin-level match operations: result entry, window control, scoring, and querying.
 * Returns domain Match entities (not FixtureViewDto) for admin use.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchAdminService {

    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final ScoringService scoringService;
    private final CommunityMembershipRepository membershipRepository;

    public List<Match> findAllOrderByKickoffAsc() {
        return matchRepository.findAllWithTeams();
    }

    public List<Match> findByKickoffDate(LocalDate date) {
        return matchRepository.findByKickoffTimeBetweenWithTeams(
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay());
    }

    public Match findById(Long matchId) {
        return matchRepository.findByIdWithTeams(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
    }

    @Transactional
    public Match setResult(Long matchId, int homeScore, int awayScore) {
        Match match = findById(matchId);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setStatus(MatchStatus.COMPLETED);
        return matchRepository.save(match);
    }

    @Transactional
    public Match resetResult(Long matchId) {
        Match match = findById(matchId);
        match.setHomeScore(null);
        match.setAwayScore(null);
        match.setHomeScore90(null);
        match.setAwayScore90(null);
        match.setStatus(MatchStatus.SCHEDULED);
        match.setResultEnteredAt(null);
        match.setResultEnteredBy(null);

        List<Prediction> predictions = predictionRepository.findByMatchId(matchId);
        Set<String> updatedMembershipKeys = new HashSet<>();
        for (Prediction p : predictions) {
            p.setPointsAwarded(0);
            p.setScoreResult(PredictionScore.PENDING);
            predictionRepository.save(p);
            if (p.getCommunity() != null) {
                String key = p.getUser().getId() + ":" + p.getCommunity().getId();
                if (updatedMembershipKeys.add(key)) {
                    recalculateMembershipStats(p.getUser().getId(), p.getCommunity().getId());
                }
            }
        }

        return matchRepository.save(match);
    }

    /**
     * Scores all predictions for a completed match across all communities. Call after setResult().
     * Updates each Prediction's pointsAwarded and scoreResult fields, then recalculates
     * community membership denormalized stats for affected user-community pairs.
     */
    @Transactional
    public void scoreAllPredictions(Long matchId) {
        Match match = findById(matchId);
        if (match.getHomeScore() == null || match.getAwayScore() == null) return;

        int actualHome = match.getEffectiveHomeScore();
        int actualAway = match.getEffectiveAwayScore();

        List<Prediction> predictions = predictionRepository.findByMatchId(matchId);
        Set<String> updatedMembershipKeys = new HashSet<>();

        for (Prediction p : predictions) {
            int pts = scoringService.calculatePoints(
                    actualHome, actualAway, p.getPredictedHome(), p.getPredictedAway());
            p.setPointsAwarded(pts);
            p.setScoreResult(scoringService.determineScoreResult(
                    actualHome, actualAway, p.getPredictedHome(), p.getPredictedAway()));
            predictionRepository.save(p);

            if (p.getCommunity() != null) {
                String key = p.getUser().getId() + ":" + p.getCommunity().getId();
                if (updatedMembershipKeys.add(key)) {
                    recalculateMembershipStats(p.getUser().getId(), p.getCommunity().getId());
                }
            }
        }
    }

    private void recalculateMembershipStats(Long userId, Long communityId) {
        membershipRepository.findByCommunityIdAndUserId(communityId, userId).ifPresent(m -> {
            List<Prediction> userPreds = predictionRepository.findByUserIdAndCommunityId(userId, communityId);
            m.setTotalPoints(userPreds.stream().mapToInt(Prediction::getPointsAwarded).sum());
            m.setExactScoreCount((int) userPreds.stream()
                    .filter(p -> p.getScoreResult() == PredictionScore.EXACT).count());
            m.setCorrectWinnerCount((int) userPreds.stream()
                    .filter(p -> p.getScoreResult() == PredictionScore.CORRECT_WINNER).count());
            m.setCorrectDrawCount((int) userPreds.stream()
                    .filter(p -> p.getScoreResult() == PredictionScore.CORRECT_DRAW).count());
            membershipRepository.save(m);
        });
    }
}
