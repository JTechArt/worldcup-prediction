package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.Match;
import com.worldcup.prediction.domain.Prediction;
import com.worldcup.prediction.domain.enums.MatchStatus;
import com.worldcup.prediction.repository.MatchRepository;
import com.worldcup.prediction.repository.PredictionRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

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

    public List<Match> findAllOrderByKickoffAsc() {
        return matchRepository.findAllWithTeams();
    }

    public List<Match> findByKickoffDate(LocalDate date) {
        return matchRepository.findByKickoffTimeBetween(
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay());
    }

    public List<Match> findByPredictionWindowOpen(boolean open) {
        return matchRepository.findByPredictionWindowOpen(open);
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
        match.setPredictionWindowOpen(false);
        return matchRepository.save(match);
    }

    @Transactional
    public Match setPredictionWindowOpen(Long matchId, boolean open) {
        Match match = findById(matchId);
        match.setPredictionWindowOpen(open);
        return matchRepository.save(match);
    }

    /**
     * Scores all predictions for a completed match. Call after setResult().
     * Updates each Prediction's pointsAwarded and scoreResult fields.
     */
    @Transactional
    public void scoreAllPredictions(Long matchId) {
        Match match = findById(matchId);
        if (match.getHomeScore() == null || match.getAwayScore() == null) return;

        int actualHome = match.getEffectiveHomeScore();
        int actualAway = match.getEffectiveAwayScore();

        List<Prediction> predictions = predictionRepository.findByMatchId(matchId);
        for (Prediction p : predictions) {
            int pts = scoringService.calculatePoints(
                    actualHome, actualAway,
                    p.getPredictedHome(), p.getPredictedAway());
            p.setPointsAwarded(pts);
            predictionRepository.save(p);
        }
    }
}
